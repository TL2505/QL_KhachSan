package quanlykhachsan.backend.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.IdempotencyFilter;

/** End-to-end concurrency and failed-response replay checks against an isolated MariaDB 11 test schema. */
class IdempotencyMariaDbIntegrationTest {
    private static HttpServer server;
    private static java.util.concurrent.ExecutorService executor;
    private static String baseUrl;
    private static final AtomicInteger executions = new AtomicInteger();

    @BeforeAll
    static void startServer() throws Exception {
        String database = System.getenv("DB_NAME");
        if (!"true".equalsIgnoreCase(System.getenv("HOTEL_RUN_DB_INTEGRATION"))
                || database == null || !database.endsWith("_test")) return;
        try (Connection connection = DriverManager.getConnection(jdbcUrl(), env("DB_USER", "root"), env("DB_PASSWORD", ""));
             Statement statement = connection.createStatement()) {
            try (ResultSet version = statement.executeQuery("SELECT VERSION()")) {
                if (!version.next() || !isMariaDb11(version.getString(1))) return;
            }
            statement.execute("DROP TABLE IF EXISTS idempotency_keys");
            statement.execute("CREATE TABLE idempotency_keys (id VARCHAR(64) NOT NULL PRIMARY KEY, request_hash VARCHAR(64) NOT NULL, response_body LONGTEXT NOT NULL, status_code INT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX idx_idempotency_created_at (created_at)) ENGINE=InnoDB");
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/bookings", exchange -> {
            executions.incrementAndGet();
            boolean fail = "fail=1".equals(exchange.getRequestURI().getQuery());
            byte[] body = (fail ? "{\"error_code\":\"ERR_500_INTERNAL_SERVER_ERROR\"}"
                : "{\"bookingId\":42}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(fail ? 500 : 201, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        }).getFilters().add(new IdempotencyFilter());
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    @Test
    void simultaneousDuplicateRequestsExecuteMutationOnceAndReplayIdenticalResponse() throws Exception {
        Assumptions.assumeTrue(server != null, "MariaDB 11 test schema is not configured");
        executions.set(0);
        String key = UUID.randomUUID().toString();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpRequest first = request(client, "/api/v1/bookings", key);
        HttpRequest second = request(client, "/api/v1/bookings", key);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> client.send(first, HttpResponse.BodyHandlers.ofString()));
            var b = pool.submit(() -> client.send(second, HttpResponse.BodyHandlers.ofString()));
            HttpResponse<String> responseA = a.get();
            HttpResponse<String> responseB = b.get();
            assertEquals(201, responseA.statusCode());
            assertEquals(responseA.statusCode(), responseB.statusCode());
            assertEquals(responseA.body(), responseB.body());
            assertEquals(1, executions.get());
            assertEquals(1, persistedRows(key));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void completedFailureIsPersistedAndRetryDoesNotRepeatTheMutation() throws Exception {
        Assumptions.assumeTrue(server != null, "MariaDB 11 test schema is not configured");
        executions.set(0);
        String key = UUID.randomUUID().toString();
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> first = client.send(request(client, "/api/v1/bookings?fail=1", key),
            HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> retry = client.send(request(client, "/api/v1/bookings?fail=1", key),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(500, first.statusCode());
        assertEquals(first.statusCode(), retry.statusCode());
        assertEquals(first.body(), retry.body());
        assertEquals(1, executions.get());
        assertEquals(1, persistedRows(key));
        assertTrue(!retry.body().isBlank());
    }

    private static HttpRequest request(HttpClient client, String path, String key) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(40))
            .header("Idempotency-Key", key).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"customerId\":1}" )).build();
    }
    private static int persistedRows(String key) throws Exception {
        try (Connection connection = DatabaseConnection.getConnection();
             var query = connection.prepareStatement("SELECT COUNT(*) FROM idempotency_keys WHERE id=?")) {
            query.setString(1, key);
            try (ResultSet rows = query.executeQuery()) { rows.next(); return rows.getInt(1); }
        }
    }
    private static String jdbcUrl() {
        return "jdbc:mysql://" + env("DB_HOST", "localhost") + ":" + env("DB_PORT", "3306") + "/"
            + env("DB_NAME", "") + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC";
    }
    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
    private static boolean isMariaDb11(String version) {
        return version != null && version.startsWith("11.") && version.toLowerCase(java.util.Locale.ROOT).contains("mariadb");
    }
}
