package quanlykhachsan.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.CorsFilter;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.GlobalExceptionHandler;
import quanlykhachsan.backend.utils.OptimisticLockException;
import quanlykhachsan.backend.utils.Router;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Enterprise Infrastructure Verification Suite for Dev A Week 1 (TASK-A1.1, TASK-A1.2, TASK-A1.3).
 * <p>
 * Audits:
 * 1. HikariCP Connection Pool governance & shutdown hygiene.
 * 2. Router regex path-variable extraction, 404/405 dispatch, and anti-shadowing specificity ordering.
 * 3. RFC-7807 JSON envelopes and Global Exception Pipeline with root-cause unwrapping and OptimisticLockException.
 * 4. W3C & ADR-05 CORS preflight 204 No Content with X-Signature header.
 * 5. Mounted controller fallback routing.
 */
public class InfrastructureFoundationTest {

    private static HttpServer testServer;
    private static String serverBaseUrl;
    private static HttpClient httpClient;
    private static Router router;

    @BeforeAll
    public static void setUpServer() throws IOException {
        int ephemeralPort = 8099;
        try {
            testServer = HttpServer.create(new InetSocketAddress("localhost", ephemeralPort), 0);
        } catch (IOException e) {
            testServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        }
        testServer.setExecutor(Executors.newFixedThreadPool(20)); // TASK-A1.1 Fixed Thread Pool

        router = new Router();

        // 1. Static Route
        router.addRoute("GET", "/api/v1/health", (exchange, params) -> {
            ApiResponseUtil.sendSuccess(exchange, 200, Map.of("status", "UP"), null);
        });

        // 2. Anti-Shadowing Order Verification: Register dynamic route FIRST, then static route SECOND
        router.addRoute("GET", "/api/v1/rooms/{id}", (exchange, params) -> {
            ApiResponseUtil.sendSuccess(exchange, 200, Map.of("roomId", params.get("id"), "handler", "DYNAMIC_PARAM"), Map.of("version", 1));
        });

        router.addRoute("GET", "/api/v1/rooms/public-availability", (exchange, params) -> {
            ApiResponseUtil.sendSuccess(exchange, 200, Map.of("handler", "STATIC_SPECIFIC", "available", true), null);
        });

        // 3. Multiple Dynamic Path Variables
        router.addRoute("PUT", "/api/v1/bookings/{bookingId}/items/{itemId}", (exchange, params) -> {
            ApiResponseUtil.sendSuccess(exchange, 200, Map.of(
                    "bookingId", params.get("bookingId"),
                    "itemId", params.get("itemId")
            ), null);
        });

        // 4. Exception Trigger Endpoints
        router.addRoute("GET", "/api/v1/test/bad-request", (exchange, params) -> {
            throw new IllegalArgumentException("Invalid input parameter: room number cannot be negative");
        });

        router.addRoute("GET", "/api/v1/test/forbidden", (exchange, params) -> {
            throw new SecurityException("User role Housekeeping cannot access payments");
        });

        router.addRoute("GET", "/api/v1/test/conflict", (exchange, params) -> {
            throw new IllegalStateException("Room status has changed concurrently");
        });

        router.addRoute("GET", "/api/v1/test/optimistic-lock", (exchange, params) -> {
            throw new OptimisticLockException("Optimistic lock version mismatch: room version=2, expected=1");
        });

        router.addRoute("GET", "/api/v1/test/wrapped-exception", (exchange, params) -> {
            // Service layer wrapping DAO exception
            Throwable daoError = new IllegalArgumentException("Date range start must precede end date");
            throw new RuntimeException("Service orchestration error occurred", daoError);
        });

        router.addRoute("GET", "/api/v1/test/internal-error", (exchange, params) -> {
            throw new RuntimeException("CRITICAL_DATABASE_CRASH_SECRET_CREDENTIALS_PASS=admin12345");
        });

        // 5. Mount a sample feature controller onto Router
        router.mount("/api/v1/legacy-module", exchange -> {
            ApiResponseUtil.sendSuccess(exchange, 200, Map.of("message", "Mounted controller reached successfully"), null);
        });

        var rootContext = testServer.createContext("/", router);
        rootContext.getFilters().add(new GlobalExceptionHandler());
        rootContext.getFilters().add(new CorsFilter());

        testServer.start();
        int activePort = testServer.getAddress().getPort();
        serverBaseUrl = "http://localhost:" + activePort;

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @AfterAll
    public static void tearDownServer() {
        if (testServer != null) {
            testServer.stop(0);
        }
    }

    // =========================================================================
    // TASK-A1.1: HIKARICP CONNECTION POOL & GOVERNANCE PARITY TESTS
    // =========================================================================

    @Test
    @DisplayName("TASK-A1.1: Verify HikariCP Connection Pool Governance Configuration Parity")
    public void testHikariCP_GovernanceConfigurationParity() {
        DatabaseConnection dbConn = DatabaseConnection.getInstance();
        Assertions.assertNotNull(dbConn, "DatabaseConnection singleton instance must not be null");

        DataSource ds = dbConn.getDataSource();
        Assertions.assertNotNull(ds, "Underlying DataSource must not be null");

        if (ds instanceof HikariDataSource hikariDs) {
            Assertions.assertEquals(20, hikariDs.getMaximumPoolSize(),
                    "DoD Violation: maximumPoolSize must be governed at exactly 20");
            Assertions.assertEquals(5, hikariDs.getMinimumIdle(),
                    "DoD Violation: minimumIdle must be governed at exactly 5");
            Assertions.assertEquals(300000L, hikariDs.getIdleTimeout(),
                    "DoD Violation: idleTimeout must be 300000ms (5 minutes)");
            Assertions.assertEquals(30000L, hikariDs.getConnectionTimeout(),
                    "DoD Violation: connectionTimeout must be 30000ms (30 seconds)");
            Assertions.assertEquals(5000L, hikariDs.getLeakDetectionThreshold(),
                    "DoD Violation: leakDetectionThreshold must be governed at exactly 5000ms (5 seconds)");
        }
    }

    // =========================================================================
    // TASK-A1.2: HTTP ROUTER & REGEX PATH-VARIABLE MATCHING TESTS
    // =========================================================================

    @Test
    @DisplayName("TASK-A1.2: Static Route Matching (GET /api/v1/health)")
    public void testRouter_StaticRouteMatching() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/health"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, response.statusCode());

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("SUCCESS", json.get("status").getAsString());
        Assertions.assertEquals("UP", json.getAsJsonObject("data").get("status").getAsString());
        Assertions.assertTrue(json.has("meta"));
        Assertions.assertTrue(json.getAsJsonObject("meta").has("timestamp"));
    }

    @Test
    @DisplayName("TASK-A1.2: Dynamic Path Variable Extraction - Single Param (/api/v1/rooms/{id})")
    public void testRouter_DynamicPathVariable_SingleParam() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/rooms/101"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, response.statusCode());

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("SUCCESS", json.get("status").getAsString());
        JsonObject data = json.getAsJsonObject("data");
        Assertions.assertEquals("101", data.get("roomId").getAsString());
        Assertions.assertEquals("DYNAMIC_PARAM", data.get("handler").getAsString());
        Assertions.assertEquals(1, json.getAsJsonObject("meta").get("version").getAsInt());
    }

    @Test
    @DisplayName("TASK-A1.2: Anti-Shadowing Specificity - Static route takes precedence over dynamic {id} pattern")
    public void testRouter_AntiShadowing_StaticRoutePrecedesDynamic() throws Exception {
        // Even though /api/v1/rooms/{id} was registered BEFORE /api/v1/rooms/public-availability,
        // Router specificity sorting ensures public-availability is NOT shadowed by {id}!
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/rooms/public-availability"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, response.statusCode());

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("SUCCESS", json.get("status").getAsString());
        JsonObject data = json.getAsJsonObject("data");
        Assertions.assertEquals("STATIC_SPECIFIC", data.get("handler").getAsString(),
                "Anti-shadowing failure: static route must precede dynamic parameter matching");
        Assertions.assertTrue(data.get("available").getAsBoolean());
    }

    @Test
    @DisplayName("TASK-A1.2: Dynamic Path Variable Extraction - Multiple Params (/api/v1/bookings/{bookingId}/items/{itemId})")
    public void testRouter_DynamicPathVariable_MultipleParams() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/bookings/BK-9999/items/ITM-42"))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, response.statusCode());

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("SUCCESS", json.get("status").getAsString());
        JsonObject data = json.getAsJsonObject("data");
        Assertions.assertEquals("BK-9999", data.get("bookingId").getAsString());
        Assertions.assertEquals("ITM-42", data.get("itemId").getAsString());
    }

    @Test
    @DisplayName("TASK-A1.2: Unmapped Path returns HTTP 404 Not Found formatted in standard JSON envelope")
    public void testRouter_UnmappedPathReturns404NotFoundStandardEnvelope() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/unknown-endpoint-does-not-exist"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(404, response.statusCode());

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("ERR_NOT_FOUND", json.get("error_code").getAsString());
        Assertions.assertEquals("Resource not found", json.get("message").getAsString());
        Assertions.assertTrue(json.has("timestamp"));
    }

    @Test
    @DisplayName("TASK-A1.2: Unhandled HTTP Method returns HTTP 405 Method Not Allowed formatted in standard JSON envelope")
    public void testRouter_UnhandledMethodReturns405MethodNotAllowedStandardEnvelope() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/rooms/101"))
                .DELETE() // Route is only registered for GET
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(405, response.statusCode());

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("ERR_METHOD_NOT_ALLOWED", json.get("error_code").getAsString());
        Assertions.assertEquals("Method not allowed", json.get("message").getAsString());
        Assertions.assertTrue(json.has("timestamp"));
    }

    @Test
    @DisplayName("TASK-A1.2: Mounted Fallback Controller reached seamlessly through Router")
    public void testRouter_MountedControllerFallback() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/legacy-module/test"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, response.statusCode());

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("SUCCESS", json.get("status").getAsString());
        Assertions.assertEquals("Mounted controller reached successfully", json.getAsJsonObject("data").get("message").getAsString());
    }

    // =========================================================================
    // TASK-A1.3: STANDARDIZED JSON ENVELOPE & GLOBAL EXCEPTION PIPELINE TESTS
    // =========================================================================

    @Test
    @DisplayName("TASK-A1.3: GlobalExceptionHandler converts IllegalArgumentException to 400 Bad Request")
    public void testGlobalExceptionHandler_400BadRequest() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/test/bad-request"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(400, response.statusCode());

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("ERR_400_BAD_REQUEST", json.get("error_code").getAsString());
        Assertions.assertTrue(json.get("message").getAsString().contains("Invalid input parameter"));
        Assertions.assertTrue(json.has("timestamp"));
    }

    @Test
    @DisplayName("TASK-A1.3: GlobalExceptionHandler converts SecurityException to 403 Forbidden")
    public void testGlobalExceptionHandler_403Forbidden() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/test/forbidden"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(403, response.statusCode());

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("ERR_403_FORBIDDEN", json.get("error_code").getAsString());
        Assertions.assertTrue(json.get("message").getAsString().contains("Housekeeping cannot access payments"));
        Assertions.assertTrue(json.has("timestamp"));
    }

    @Test
    @DisplayName("TASK-A1.3: GlobalExceptionHandler converts IllegalStateException to 409 Conflict")
    public void testGlobalExceptionHandler_409Conflict() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/test/conflict"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(409, response.statusCode());

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("ERR_409_CONFLICT", json.get("error_code").getAsString());
        Assertions.assertTrue(json.get("message").getAsString().contains("concurrently"));
        Assertions.assertTrue(json.has("timestamp"));
    }

    @Test
    @DisplayName("TASK-A1.3: GlobalExceptionHandler maps OptimisticLockException to 409 Conflict")
    public void testGlobalExceptionHandler_OptimisticLockException_MapsTo409() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/test/optimistic-lock"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(409, response.statusCode());

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("ERR_409_CONFLICT", json.get("error_code").getAsString());
        Assertions.assertTrue(json.get("message").getAsString().contains("Optimistic lock version mismatch"));
        Assertions.assertTrue(json.has("timestamp"));
    }

    @Test
    @DisplayName("TASK-A1.3: GlobalExceptionHandler unwraps nested cause hierarchy to return correct 400 Bad Request")
    public void testGlobalExceptionHandler_UnwrapsNestedCauseHierarchy() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/test/wrapped-exception"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(400, response.statusCode(), "Nested IllegalArgumentException must be unwrapped to HTTP 400");

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("ERR_400_BAD_REQUEST", json.get("error_code").getAsString());
        Assertions.assertTrue(json.get("message").getAsString().contains("Date range start must precede end date"));
    }

    @Test
    @DisplayName("TASK-A1.3: GlobalExceptionHandler converts RuntimeException to 500 with ZERO Stack Trace Leakage")
    public void testGlobalExceptionHandler_500InternalError_ZeroStackTraceLeakage() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/test/internal-error"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(500, response.statusCode());

        String body = response.body();
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        Assertions.assertEquals("ERR_500_SERVER_ERROR", json.get("error_code").getAsString());
        Assertions.assertEquals("Internal Server Error: An unexpected error occurred.", json.get("message").getAsString());
        Assertions.assertTrue(json.has("timestamp"));

        // Strictly verify that NO stack trace, class names, or sensitive exception messages leaked
        Assertions.assertFalse(body.contains("CRITICAL_DATABASE_CRASH"), "Sensitive error message must not be leaked");
        Assertions.assertFalse(body.contains("admin12345"), "Sensitive credentials must not be leaked");
        Assertions.assertFalse(body.contains("at quanlykhachsan"), "Stack trace line numbers must not be leaked to client");
        Assertions.assertFalse(body.contains("java.lang.RuntimeException"), "Internal exception class must not be leaked");
    }

    // =========================================================================
    // CORS PREFLIGHT (W3C & ADR-05 SPECIFICATION TESTS)
    // =========================================================================

    @Test
    @DisplayName("TASK-A1.2 / ADR-05: CORS OPTIONS Preflight returns 204 No Content with X-Signature header")
    public void testCorsPreflight_Returns204NoContentWithXSignature() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/rooms/101"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        Assertions.assertEquals(204, response.statusCode(), "W3C and ADR-05 mandate 204 No Content for CORS preflight");

        String allowedHeaders = response.headers().firstValue("Access-Control-Allow-Headers").orElse("");
        Assertions.assertTrue(allowedHeaders.contains("X-Signature"), "Access-Control-Allow-Headers must permit X-Signature for VietQR webhooks");
        Assertions.assertTrue(allowedHeaders.contains("Idempotency-Key"), "Access-Control-Allow-Headers must permit Idempotency-Key");
        Assertions.assertTrue(allowedHeaders.contains("Authorization"), "Access-Control-Allow-Headers must permit Authorization");
    }
}
