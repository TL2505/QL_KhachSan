package quanlykhachsan.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import quanlykhachsan.backend.Main;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.SecurityUtil;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

/**
 * Automated Verification Suite for JWT Authentication Filter & Login Controller (TASK-A1.4).
 * <p>
 * Verifies:
 * 1. POST /api/v1/auth/login with valid credentials returns HTTP 200, JWT token, Bearer type, and 3600s expiry.
 * 2. POST /api/v1/auth/login with invalid credentials returns HTTP 401 ERR_401_INVALID_CREDENTIALS.
 * 3. Protected endpoint without token is rejected with HTTP 401 ERR_401_UNAUTHORIZED.
 * 4. Protected endpoint with valid token is allowed (HTTP 200).
 * 5. Protected endpoint with tampered token is rejected with HTTP 401 ERR_401_TOKEN_INVALID.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class JwtAuthIntegrationTest {

    private HttpServer testServer;
    private String serverBaseUrl;
    private HttpClient httpClient;
    private HikariDataSource mockDataSource;

    private static final String TEST_USER = "admin_main";
    private static final String TEST_PASS = "correct_password";
    private static final String TEST_HASH = SecurityUtil.hashPassword(TEST_PASS);

    @BeforeAll
    public void setUp() throws Exception {
        // Initialize instrumented mock DataSource for deterministic, fast auth testing
        mockDataSource = createMockAuthDataSource();
        DatabaseConnection.setCustomDataSource(mockDataSource);

        // Start server on an ephemeral port
        testServer = Main.createServer(0);
        testServer.start();

        int port = testServer.getAddress().getPort();
        serverBaseUrl = "http://localhost:" + port;

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        System.out.println("🚀 [JwtAuthIntegrationTest] Server listening at " + serverBaseUrl);
    }

    @AfterAll
    public void tearDown() {
        try {
            if (testServer != null) {
                testServer.stop(0);
                System.out.println("🛑 [JwtAuthIntegrationTest] Server stopped.");
            }
        } finally {
            DatabaseConnection.resetToDefault();
            System.out.println("✔ [JwtAuthIntegrationTest] DatabaseConnection reset to default.");
        }
    }

    @Test
    @DisplayName("TASK-A1.4 - Test 1: POST /api/v1/auth/login with valid credentials returns 200 and JWT token")
    public void testLogin_ValidCredentials_Returns200AndToken() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("username", TEST_USER);
        req.addProperty("password", TEST_PASS);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(req.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, response.statusCode(), "Expected HTTP 200 OK on valid credentials");

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("SUCCESS", json.get("status").getAsString());

        JsonObject data = json.getAsJsonObject("data");
        Assertions.assertNotNull(data.get("token"), "Payload data must contain 'token'");
        String token = data.get("token").getAsString();
        Assertions.assertTrue(token.startsWith("eyJ"), "Token must be a valid JWT starting with 'eyJ'");
        Assertions.assertEquals("Bearer", data.get("token_type").getAsString());
        Assertions.assertEquals(3600, data.get("expires_in").getAsInt());

        JsonObject user = data.getAsJsonObject("user");
        Assertions.assertEquals(1, user.get("id").getAsInt());
        Assertions.assertEquals(TEST_USER, user.get("username").getAsString());
        Assertions.assertEquals("admin", user.get("role").getAsString());

        // Validate that token is cryptographically verified by SecurityUtil
        var claims = SecurityUtil.validateAndExtractClaims(token);
        Assertions.assertEquals(TEST_USER, claims.get("username", String.class));
        Assertions.assertEquals("admin", claims.get("role", String.class));

        System.out.println("✔ [TASK-A1.4] Test 1 Passed: Valid credentials returned 200 and valid JWT token.");
    }

    @Test
    @DisplayName("TASK-A1.4 - Test 2: POST /api/v1/auth/login with invalid credentials returns 401")
    public void testLogin_InvalidCredentials_Returns401() throws Exception {
        // Case A: Wrong password
        JsonObject reqWrongPass = new JsonObject();
        reqWrongPass.addProperty("username", TEST_USER);
        reqWrongPass.addProperty("password", "incorrect_secret");

        HttpRequest requestWrongPass = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(reqWrongPass.toString()))
                .build();

        HttpResponse<String> respWrongPass = httpClient.send(requestWrongPass, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(401, respWrongPass.statusCode());
        JsonObject jsonWrongPass = JsonParser.parseString(respWrongPass.body()).getAsJsonObject();
        Assertions.assertEquals("ERR_401_INVALID_CREDENTIALS", jsonWrongPass.get("error_code").getAsString());

        // Case B: Non-existent username
        JsonObject reqUnknown = new JsonObject();
        reqUnknown.addProperty("username", "non_existent_user_999");
        reqUnknown.addProperty("password", "any_password");

        HttpRequest requestUnknown = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(reqUnknown.toString()))
                .build();

        HttpResponse<String> respUnknown = httpClient.send(requestUnknown, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(401, respUnknown.statusCode());
        JsonObject jsonUnknown = JsonParser.parseString(respUnknown.body()).getAsJsonObject();
        Assertions.assertEquals("ERR_401_INVALID_CREDENTIALS", jsonUnknown.get("error_code").getAsString());

        // Case C: Inactive user account
        JsonObject reqInactive = new JsonObject();
        reqInactive.addProperty("username", "inactive_user");
        reqInactive.addProperty("password", TEST_PASS);

        HttpRequest requestInactive = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(reqInactive.toString()))
                .build();

        HttpResponse<String> respInactive = httpClient.send(requestInactive, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(401, respInactive.statusCode());
        JsonObject jsonInactive = JsonParser.parseString(respInactive.body()).getAsJsonObject();
        Assertions.assertEquals("ERR_401_INVALID_CREDENTIALS", jsonInactive.get("error_code").getAsString());

        System.out.println("✔ [TASK-A1.4] Test 2 Passed: Invalid credentials consistently rejected with 401 ERR_401_INVALID_CREDENTIALS.");
    }

    @Test
    @DisplayName("TASK-A1.4 - Test 3: Protected endpoint without token is rejected with 401")
    public void testProtectedEndpoint_WithoutToken_RejectedWith401() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/protected/ping"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(401, response.statusCode());

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("ERR_401_UNAUTHORIZED", json.get("error_code").getAsString());
        Assertions.assertTrue(json.get("message").getAsString().contains("Authorization"));

        System.out.println("✔ [TASK-A1.4] Test 3 Passed: Protected request without token rejected with 401 ERR_401_UNAUTHORIZED.");
    }

    @Test
    @DisplayName("TASK-A1.4 - Test 4: Protected endpoint with valid token is allowed (HTTP 200)")
    public void testProtectedEndpoint_WithValidToken_Allowed() throws Exception {
        String token = SecurityUtil.generateToken(1, TEST_USER, "admin");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/protected/ping"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, response.statusCode(), "Expected HTTP 200 OK with valid JWT token");

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("SUCCESS", json.get("status").getAsString());

        JsonObject data = json.getAsJsonObject("data");
        Assertions.assertEquals("pong", data.get("message").getAsString());
        Assertions.assertEquals(TEST_USER, data.get("username").getAsString());

        System.out.println("✔ [TASK-A1.4] Test 4 Passed: Protected request with valid Bearer token allowed through.");
    }

    @Test
    @DisplayName("TASK-A1.4 - Test 5: Protected endpoint with tampered token is rejected with 401")
    public void testProtectedEndpoint_WithTamperedToken_RejectedWith401() throws Exception {
        // Case A: Token with altered signature
        String validToken = SecurityUtil.generateToken(1, TEST_USER, "admin");
        String tamperedToken = validToken.substring(0, validToken.length() - 6) + "XXXXXX";

        HttpRequest requestTampered = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/protected/ping"))
                .header("Authorization", "Bearer " + tamperedToken)
                .GET()
                .build();

        HttpResponse<String> respTampered = httpClient.send(requestTampered, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(401, respTampered.statusCode());

        JsonObject jsonTampered = JsonParser.parseString(respTampered.body()).getAsJsonObject();
        Assertions.assertEquals("ERR_401_TOKEN_INVALID", jsonTampered.get("error_code").getAsString());

        // Case B: Completely malformed random token
        HttpRequest requestBogus = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/protected/ping"))
                .header("Authorization", "Bearer bogus_malformed_token_12345")
                .GET()
                .build();

        HttpResponse<String> respBogus = httpClient.send(requestBogus, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(401, respBogus.statusCode());

        JsonObject jsonBogus = JsonParser.parseString(respBogus.body()).getAsJsonObject();
        Assertions.assertEquals("ERR_401_TOKEN_INVALID", jsonBogus.get("error_code").getAsString());

        System.out.println("✔ [TASK-A1.4] Test 5 Passed: Tampered/malformed token rejected with 401 ERR_401_TOKEN_INVALID.");
    }

    // =========================================================================
    // EDGE CASES VERIFICATION TESTS (EDGE-CASE-1, EDGE-CASE-2, EDGE-CASE-3)
    // =========================================================================

    @Test
    @DisplayName("TASK-A1.4 - Edge Case 1: Long/Double numeric claim in JWT decodes safely without ClassCastException")
    public void testEdgeCase1_NumericClaimInJwt_DecodesWithoutClassCastException() throws Exception {
        // Build token explicitly placing a Long or Number in userId claim
        long largeNumericId = 999999L;
        String tokenWithLong = io.jsonwebtoken.Jwts.builder()
                .subject(TEST_USER)
                .claim("userId", largeNumericId)
                .claim("username", TEST_USER)
                .claim("role", "admin")
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 3600 * 1000L))
                .signWith(SecurityUtil.getSecretKey())
                .compact();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/protected/ping"))
                .header("Authorization", "Bearer " + tokenWithLong)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, response.statusCode(), "Protected endpoint must accept token with Long userId");

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("SUCCESS", json.get("status").getAsString());
        JsonObject data = json.getAsJsonObject("data");
        Assertions.assertEquals(999999, data.get("userId").getAsInt());

        System.out.println("✔ [TASK-A1.4] Edge Case 1 Passed: Long numeric claim decoded safely without ClassCastException.");
    }

    @Test
    @DisplayName("TASK-A1.4 - Edge Case 2: Trailing slash in whitelisted path (/api/v1/auth/login/) does NOT get 401")
    public void testEdgeCase2_WhitelistTrailingSlash_AllowsEndpointWithoutToken() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("username", TEST_USER);
        req.addProperty("password", TEST_PASS);

        // POST /api/v1/auth/login/ with trailing slash
        HttpRequest requestTrailing = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/auth/login/"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(req.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(requestTrailing, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, response.statusCode(), "Trailing slash on login endpoint must succeed with 200, not 401");

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("SUCCESS", json.get("status").getAsString());
        Assertions.assertTrue(json.getAsJsonObject("data").has("token"));

        // GET /api/v1/health/ with trailing slash
        HttpRequest healthTrailing = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/health/"))
                .GET()
                .build();

        HttpResponse<String> healthResp = httpClient.send(healthTrailing, HttpResponse.BodyHandlers.ofString());
        // Health check must not be blocked by 401 Unauthorized
        Assertions.assertNotEquals(401, healthResp.statusCode(), "Healthcheck with trailing slash must not be blocked by JwtFilter with 401");

        System.out.println("✔ [TASK-A1.4] Edge Case 2 Passed: Trailing slash on whitelisted endpoints correctly bypassed JwtFilter.");
    }

    @Test
    @DisplayName("TASK-A1.4 - Edge Case 3: Truncated or corrupt hash (< 60 chars) rejected safely without crashing")
    public void testEdgeCase3_TruncatedPasswordHash_FailsSafelyWithoutCrashing() {
        // Case A: Truncated BCrypt hash (35 chars instead of 60 chars)
        String truncatedHash = "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7";
        Assertions.assertFalse(SecurityUtil.checkPassword("password", truncatedHash),
                "Truncated BCrypt hash must return false safely");

        // Case B: MD5 hash length (32 chars)
        String md5Hash = "5f4dcc3b5aa765d61d8327deb882cf99";
        Assertions.assertFalse(SecurityUtil.checkPassword("password", md5Hash),
                "Legacy 32-char MD5 hash must return false safely");

        // Case C: Null and empty hash
        Assertions.assertFalse(SecurityUtil.checkPassword("password", null));
        Assertions.assertFalse(SecurityUtil.checkPassword("password", ""));

        // Case D: Valid 60-char BCrypt hash must still succeed
        String validHash = SecurityUtil.hashPassword("secret123");
        Assertions.assertEquals(60, validHash.length(), "Valid BCrypt hash must be exactly 60 characters");
        Assertions.assertTrue(SecurityUtil.checkPassword("secret123", validHash));

        System.out.println("✔ [TASK-A1.4] Edge Case 3 Passed: Truncated/corrupt hashes handled safely without crashing.");
    }

    // =========================================================================
    // MOCK DATA SOURCE FACTORY FOR AUTHENTICATION TESTING
    // =========================================================================

    private static HikariDataSource createMockAuthDataSource() {
        InvocationHandler dsHandler = (proxy, method, args) -> {
            String name = method.getName();
            if ("getConnection".equals(name)) {
                return createMockConnection();
            } else if ("isWrapperFor".equals(name)) {
                return false;
            } else if ("unwrap".equals(name)) {
                throw new SQLException("Mock DataSource is not a wrapper");
            } else if ("getLoginTimeout".equals(name)) {
                return 0;
            }
            if (method.getReturnType().equals(int.class)) return 0;
            if (method.getReturnType().equals(boolean.class)) return false;
            return null;
        };

        DataSource underlyingDs = (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                dsHandler
        );

        HikariConfig config = new HikariConfig();
        config.setDataSource(underlyingDs);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setPoolName("Test-AuthMockPool");
        config.setConnectionTimeout(1000);
        config.setValidationTimeout(1000);

        return new HikariDataSource(config);
    }

    private static Connection createMockConnection() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("prepareStatement".equals(name)) {
                        return createMockPreparedStatement();
                    } else if ("createStatement".equals(name)) {
                        return createMockStatement();
                    } else if ("isClosed".equals(name)) {
                        return false;
                    } else if ("isValid".equals(name)) {
                        return true;
                    } else if ("getAutoCommit".equals(name)) {
                        return true;
                    } else if ("getTransactionIsolation".equals(name)) {
                        return Connection.TRANSACTION_READ_COMMITTED;
                    } else if ("getNetworkTimeout".equals(name)) {
                        return 0;
                    } else if ("isReadOnly".equals(name)) {
                        return false;
                    } else if ("close".equals(name)) {
                        return null;
                    }
                    if (method.getReturnType().equals(int.class)) return 0;
                    if (method.getReturnType().equals(boolean.class)) return false;
                    return null;
                }
        );
    }

    private static Statement createMockStatement() {
        return (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("executeQuery".equals(name)) {
                        return createMockHealthResultSet();
                    } else if ("close".equals(name)) {
                        return null;
                    } else if ("isClosed".equals(name)) {
                        return false;
                    }
                    if (method.getReturnType().equals(int.class)) return 0;
                    if (method.getReturnType().equals(boolean.class)) return false;
                    return null;
                }
        );
    }

    private static PreparedStatement createMockPreparedStatement() {
        final String[] capturedUsername = new String[]{""};
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("setString".equals(name) && args != null && args.length >= 2) {
                        if (Integer.valueOf(1).equals(args[0]) && args[1] instanceof String val) {
                            capturedUsername[0] = val;
                        }
                        return null;
                    } else if ("executeQuery".equals(name)) {
                        return createMockUserResultSet(capturedUsername[0]);
                    } else if ("close".equals(name)) {
                        return null;
                    } else if ("isClosed".equals(name)) {
                        return false;
                    }
                    if (method.getReturnType().equals(int.class)) return 0;
                    if (method.getReturnType().equals(boolean.class)) return false;
                    return null;
                }
        );
    }

    private static ResultSet createMockHealthResultSet() {
        final boolean[] advanced = new boolean[]{false};
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("next".equals(name)) {
                        if (!advanced[0]) {
                            advanced[0] = true;
                            return true;
                        }
                        return false;
                    } else if ("getInt".equals(name)) {
                        return 1;
                    } else if ("close".equals(name)) {
                        return null;
                    } else if ("isClosed".equals(name)) {
                        return false;
                    }
                    if (method.getReturnType().equals(int.class)) return 0;
                    if (method.getReturnType().equals(boolean.class)) return false;
                    return null;
                }
        );
    }

    private static ResultSet createMockUserResultSet(String username) {
        final boolean[] advanced = new boolean[]{false};
        final boolean hasRow = TEST_USER.equals(username) || "inactive_user".equals(username);
        final String status = "inactive_user".equals(username) ? "inactive" : "active";

        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("next".equals(name)) {
                        if (hasRow && !advanced[0]) {
                            advanced[0] = true;
                            return true;
                        }
                        return false;
                    } else if ("getInt".equals(name)) {
                        return 1;
                    } else if ("getString".equals(name) && args != null && args.length > 0) {
                        String col = (String) args[0];
                        if ("username".equalsIgnoreCase(col)) {
                            return username;
                        } else if ("password_hash".equalsIgnoreCase(col) || "password".equalsIgnoreCase(col)) {
                            return TEST_HASH;
                        } else if ("role".equalsIgnoreCase(col) || "role_name".equalsIgnoreCase(col)) {
                            return "admin";
                        } else if ("status".equalsIgnoreCase(col)) {
                            return status;
                        }
                        return null;
                    } else if ("close".equals(name)) {
                        return null;
                    } else if ("isClosed".equals(name)) {
                        return false;
                    }
                    if (method.getReturnType().equals(int.class)) return 0;
                    if (method.getReturnType().equals(boolean.class)) return false;
                    return null;
                }
        );
    }
}
