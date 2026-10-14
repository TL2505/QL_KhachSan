package quanlykhachsan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.UpdateDbUtility;
import quanlykhachsan.stubs.MockApiServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Base Integration Test Framework for 5★ Luxury Hotel PMS (TASK-D1.3).
 * <p>
 * Configures:
 * 1. Test lifecycle & server management (MockApiServer on port 8089).
 * 2. Database schema initialization & connection hygiene.
 * 3. High-ergonomic HTTP request utilities (GET, POST, PUT, DELETE).
 * 4. Cryptographic HMAC-SHA256 JJWT Token generation matching JwtFilter.
 * <p>
 * Abstract base class containing zero test cases to prevent test inheritance duplication.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseIntegrationTest {

    public static final int TEST_SERVER_PORT = 8089;
    protected static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    protected static MockApiServer server;
    protected static HttpClient httpClient;
    protected static String baseUrl;

    // Strict 6 allowed room statuses per docs/dev-spec/api-contracts.md
    protected static final Set<String> ALLOWED_ROOM_STATUSES = Set.of(
            "vacant_clean",
            "vacant_dirty",
            "occupied_clean",
            "occupied_dirty",
            "out_of_order",
            "reserved"
    );

    @BeforeAll
    public static void setUpTestSuite() throws Exception {
        System.out.println("==================================================================");
        System.out.println("🚀 [BaseIntegrationTest] Initializing JUnit 5 Integration Suite...");
        System.out.println("==================================================================");

        // 1. Initialize Database Schema (if database is available in environment)
        initDatabaseSchema();

        // 2. Start HTTP Server
        server = new MockApiServer(TEST_SERVER_PORT);
        server.start();

        baseUrl = server.getBaseUrl();

        // 3. Configure HTTP Client with standard timeouts
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        System.out.println("✔ [BaseIntegrationTest] Server and HTTP Client ready at: " + baseUrl);
    }

    @AfterAll
    public static void tearDownTestSuite() {
        if (server != null) {
            server.stop();
            System.out.println("🛑 [BaseIntegrationTest] Test Server terminated.");
        }
    }

    @BeforeEach
    public void resetTestFixtures() {
        if (server != null) {
            server.resetState();
        }
    }

    // =========================================================================
    // DATABASE SCHEMA INITIALIZATION & UTILITIES
    // =========================================================================

    /**
     * Attempts to connect to MariaDB/MySQL and verify or migrate schema.
     * In isolated CI environments without live database daemon, logs warning and proceeds.
     */
    public static void initDatabaseSchema() {
        if (!isDatabaseAvailable()) {
            System.out.println("ℹ [BaseIntegrationTest] Database offline or unreachable. Running in isolated container/mock mode.");
            return;
        }
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn != null && !conn.isClosed()) {
                System.out.println("✔ [BaseIntegrationTest] Database connection established: " + conn.getCatalog());

                // Run auto-migration check
                UpdateDbUtility.main(new String[]{});

                // Check if hotel.sql exists to initialize tables if database is empty
                File sqlFile = new File("database/hotel.sql");
                if (sqlFile.exists()) {
                    executeSqlScript(conn, "database/hotel.sql");
                }
            }
        } catch (Exception e) {
            System.out.println("ℹ [BaseIntegrationTest] Notice during database initialization: " + e.getMessage());
        }
    }

    public static boolean isDatabaseAvailable() {
        String host = System.getenv("DB_HOST");
        if (host == null || host.isEmpty()) host = "localhost";
        String portStr = System.getenv("DB_PORT");
        int port = 3306;
        if (portStr != null && !portStr.isEmpty()) {
            try { port = Integer.parseInt(portStr); } catch (NumberFormatException ignored) {}
        }
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress(host, port), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void executeSqlScript(Connection conn, String scriptPath) {
        File file = new File(scriptPath);
        if (!file.exists()) {
            return;
        }
        try (InputStream is = new FileInputStream(file);
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sqlBuilder = new StringBuilder();
            String line;
            try (Statement stmt = conn.createStatement()) {
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("--") || line.startsWith("/*") || line.isEmpty()) {
                        continue;
                    }
                    sqlBuilder.append(line).append(" ");
                    if (line.endsWith(";")) {
                        String sql = sqlBuilder.toString().trim();
                        try {
                            stmt.execute(sql);
                        } catch (SQLException ignored) {
                            // Ignore existing table/constraint errors during schema setup
                        }
                        sqlBuilder.setLength(0);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠ [BaseIntegrationTest] Notice during SQL script execution: " + e.getMessage());
        }
    }

    // =========================================================================
    // HTTP CLIENT UTILITY METHODS
    // =========================================================================

    protected HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return get(path, Collections.emptyMap());
    }

    protected HttpResponse<String> get(String path, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(resolveUrl(path)))
                .timeout(Duration.ofSeconds(10))
                .GET();
        attachHeaders(builder, headers);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    protected HttpResponse<String> post(String path, String jsonBody) throws IOException, InterruptedException {
        return post(path, jsonBody, Collections.emptyMap());
    }

    protected HttpResponse<String> post(String path, String jsonBody, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(resolveUrl(path)))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody != null ? jsonBody : "", StandardCharsets.UTF_8));
        attachHeaders(builder, headers);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    protected HttpResponse<String> put(String path, String jsonBody) throws IOException, InterruptedException {
        return put(path, jsonBody, Collections.emptyMap());
    }

    protected HttpResponse<String> put(String path, String jsonBody, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(resolveUrl(path)))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody != null ? jsonBody : "", StandardCharsets.UTF_8));
        attachHeaders(builder, headers);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    protected HttpResponse<String> delete(String path) throws IOException, InterruptedException {
        return delete(path, Collections.emptyMap());
    }

    protected HttpResponse<String> delete(String path, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(resolveUrl(path)))
                .timeout(Duration.ofSeconds(10))
                .DELETE();
        attachHeaders(builder, headers);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    protected JsonObject parseJson(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return new JsonObject();
        }
        JsonElement el = JsonParser.parseString(responseBody);
        return el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
    }

    protected JsonArray parseJsonArray(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return new JsonArray();
        }
        JsonElement el = JsonParser.parseString(responseBody);
        return el.isJsonArray() ? el.getAsJsonArray() : new JsonArray();
    }

    protected String createAuthHeader(String token) {
        return "Bearer " + token;
    }

    private String resolveUrl(String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return baseUrl + path;
    }

    private void attachHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers != null) {
            headers.forEach(builder::header);
        }
    }

    // =========================================================================
    // CRYPTOGRAPHIC JWT UTILITIES (Matching JwtFilter)
    // =========================================================================

    public static final String DEFAULT_JWT_SECRET = "hotel_pms_super_secure_secret_key_1234567890";

    public static SecretKey getSigningKey() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.length() < 32) {
            secret = DEFAULT_JWT_SECRET;
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates a cryptographically valid HMAC-SHA256 signed JWT token
     * compatible with production JwtFilter and MockApiServer.
     */
    public static String generateSignedJwt(String role, String username, int userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(getSigningKey())
                .compact();
    }
}
