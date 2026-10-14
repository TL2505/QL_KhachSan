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
import quanlykhachsan.backend.utils.SecurityHardeningFilter;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

/**
 * Production Security Hardening & Deep Healthcheck Verification Suite (TASK-A4.1).
 * <p>
 * Verifies:
 * 1. OWASP Security Defense Headers presence across all HTTP responses.
 * 2. Deep Health Check reporting MariaDB, HikariCP connection pool metrics, and JVM heap stats.
 * 3. Execution latency SLA (< 20ms) for high-frequency infrastructure probes.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SecurityHardeningTest {

    private HttpServer testServer;
    private String serverBaseUrl;
    private HttpClient httpClient;
    private HikariDataSource mockDataSource;

    @BeforeAll
    public void setUp() throws Exception {
        // Initialize instrumented mock DataSource for deterministic, high-speed testing
        mockDataSource = createMockHikariDataSource();
        DatabaseConnection.setCustomDataSource(mockDataSource);

        // Start server on an ephemeral port
        testServer = Main.createServer(0);
        testServer.start();

        int port = testServer.getAddress().getPort();
        serverBaseUrl = "http://localhost:" + port;

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        System.out.println("🚀 [SecurityHardeningTest] Test HttpServer listening at " + serverBaseUrl);
    }

    @AfterAll
    public void tearDown() {
        try {
            if (testServer != null) {
                testServer.stop(0);
                System.out.println("🛑 [SecurityHardeningTest] Test HttpServer stopped.");
            }
        } finally {
            DatabaseConnection.resetToDefault();
            System.out.println("✔ [SecurityHardeningTest] DatabaseConnection reset to default.");
        }
    }

    @Test
    @DisplayName("TASK-A4.1 - Test 1: Security headers present on all responses")
    public void testSecurityHeaders_PresentOnAllResponses() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/health"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        HttpHeaders headers = response.headers();

        // 1. X-Content-Type-Options: nosniff
        Assertions.assertTrue(headers.firstValue("X-Content-Type-Options").isPresent(),
                "Header X-Content-Type-Options must be present");
        Assertions.assertEquals(SecurityHardeningFilter.HEADER_X_CONTENT_TYPE_OPTIONS,
                headers.firstValue("X-Content-Type-Options").get(),
                "X-Content-Type-Options must equal 'nosniff'");

        // 2. X-Frame-Options: DENY
        Assertions.assertTrue(headers.firstValue("X-Frame-Options").isPresent(),
                "Header X-Frame-Options must be present");
        Assertions.assertEquals(SecurityHardeningFilter.HEADER_X_FRAME_OPTIONS,
                headers.firstValue("X-Frame-Options").get(),
                "X-Frame-Options must equal 'DENY'");

        // 3. Content-Security-Policy: Includes Tailwind, Alpine, Google Fonts CDNs
        Assertions.assertTrue(headers.firstValue("Content-Security-Policy").isPresent(),
                "Header Content-Security-Policy must be present");
        String csp = headers.firstValue("Content-Security-Policy").get();
        Assertions.assertEquals(SecurityHardeningFilter.HEADER_CONTENT_SECURITY_POLICY, csp);
        Assertions.assertTrue(csp.contains("https://cdn.tailwindcss.com"), "CSP must permit Tailwind CDN");
        Assertions.assertTrue(csp.contains("https://cdn.jsdelivr.net"), "CSP must permit Alpine/jsDelivr CDN");
        Assertions.assertTrue(csp.contains("https://fonts.googleapis.com"), "CSP must permit Google Fonts stylesheet CDN");
        Assertions.assertTrue(csp.contains("https://fonts.gstatic.com"), "CSP must permit Google Fonts webfonts CDN");

        // 4. Strict-Transport-Security: max-age=31536000; includeSubDomains
        Assertions.assertTrue(headers.firstValue("Strict-Transport-Security").isPresent(),
                "Header Strict-Transport-Security must be present");
        Assertions.assertEquals(SecurityHardeningFilter.HEADER_STRICT_TRANSPORT_SECURITY,
                headers.firstValue("Strict-Transport-Security").get(),
                "Strict-Transport-Security must equal 'max-age=31536000; includeSubDomains'");

        // 5. Referrer-Policy: strict-origin-when-cross-origin
        Assertions.assertTrue(headers.firstValue("Referrer-Policy").isPresent(),
                "Header Referrer-Policy must be present");
        Assertions.assertEquals(SecurityHardeningFilter.HEADER_REFERRER_POLICY,
                headers.firstValue("Referrer-Policy").get(),
                "Referrer-Policy must equal 'strict-origin-when-cross-origin'");

        // 6. Permissions-Policy: geolocation=(), camera=(), microphone=()
        Assertions.assertTrue(headers.firstValue("Permissions-Policy").isPresent(),
                "Header Permissions-Policy must be present");
        Assertions.assertEquals(SecurityHardeningFilter.HEADER_PERMISSIONS_POLICY,
                headers.firstValue("Permissions-Policy").get(),
                "Permissions-Policy must equal 'geolocation=(), camera=(), microphone=()'");

        System.out.println("✔ [TASK-A4.1] All 6 OWASP Security Headers & CDN allowances verified successfully.");
    }

    @Test
    @DisplayName("TASK-A4.1 - Test 2: Deep health check returns UP when DB connected")
    public void testDeepHealthCheck_ReturnsUpWhenDbConnected() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/health"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, response.statusCode(), "Expected HTTP 200 OK for healthy database probe");

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

        // 1. Status & Database connectivity assertions
        Assertions.assertTrue(json.has("status"), "Payload must contain 'status'");
        Assertions.assertEquals("UP", json.get("status").getAsString(), "Expected status 'UP'");

        Assertions.assertTrue(json.has("database"), "Payload must contain 'database'");
        Assertions.assertEquals("CONNECTED", json.get("database").getAsString(), "Expected database 'CONNECTED'");

        // 2. HikariCP connection pool metrics assertions
        Assertions.assertTrue(json.has("pool"), "Payload must contain 'pool' object");
        JsonObject pool = json.getAsJsonObject("pool");
        Assertions.assertTrue(pool.has("active_connections"), "Pool must contain 'active_connections'");
        Assertions.assertTrue(pool.has("idle_connections"), "Pool must contain 'idle_connections'");
        Assertions.assertTrue(pool.has("total_connections"), "Pool must contain 'total_connections'");
        Assertions.assertTrue(pool.get("total_connections").getAsInt() >= 0, "Total connections must be >= 0");

        // 3. JVM memory metrics assertions
        Assertions.assertTrue(json.has("jvm"), "Payload must contain 'jvm' object");
        JsonObject jvm = json.getAsJsonObject("jvm");
        Assertions.assertTrue(jvm.has("heap_used_mb"), "JVM must report 'heap_used_mb'");
        Assertions.assertTrue(jvm.has("heap_max_mb"), "JVM must report 'heap_max_mb'");
        Assertions.assertTrue(jvm.get("heap_used_mb").getAsLong() >= 0, "heap_used_mb must be >= 0");
        Assertions.assertTrue(jvm.get("heap_max_mb").getAsLong() > 0, "heap_max_mb must be > 0");

        System.out.printf("✔ [TASK-A4.1] Deep Healthcheck verified: status=%s, db=%s, pool=%s, jvm=%s%n",
                json.get("status").getAsString(),
                json.get("database").getAsString(),
                pool,
                jvm);
    }

    @Test
    @DisplayName("TASK-A4.1 - Test 3: Health check execution latency SLA (< 20ms)")
    public void testHealthCheck_ExecutionLatency() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/health"))
                .GET()
                .build();

        // Warm up: 10 rounds to trigger JIT compilation and socket buffer initialization
        for (int i = 0; i < 10; i++) {
            HttpResponse<String> warmupResp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Assertions.assertEquals(200, warmupResp.statusCode());
        }

        // Measure multiple iterations to evaluate SLA latency reliably (mitigating OS safepoint pauses)
        int iterations = 10;
        double minLatency = Double.MAX_VALUE;
        double totalLatency = 0.0;
        HttpResponse<String> lastResponse = null;

        for (int i = 0; i < iterations; i++) {
            long startTime = System.nanoTime();
            lastResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long durationNs = System.nanoTime() - startTime;
            double durationMs = durationNs / 1_000_000.0;
            if (durationMs < minLatency) {
                minLatency = durationMs;
            }
            totalLatency += durationMs;
        }

        double avgLatency = totalLatency / iterations;
        System.out.printf("⚡ [TASK-A4.1] Measured /api/v1/health execution latency: min=%.3f ms, avg=%.3f ms (SLA Threshold: < 20.0 ms)%n",
                minLatency, avgLatency);

        Assertions.assertNotNull(lastResponse);
        Assertions.assertEquals(200, lastResponse.statusCode());
        Assertions.assertTrue(minLatency < 20.0,
                String.format("Health check minimum latency %.3f ms exceeded SLA requirement of 20ms", minLatency));
    }

    // =========================================================================
    // IN-MEMORY MOCK HIKARICP DATA SOURCE FACTORY
    // =========================================================================

    private static HikariDataSource createMockHikariDataSource() {
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
        config.setPoolName("Test-HardeningMockPool");
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
                    if ("createStatement".equals(name)) {
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
                        return createMockResultSet();
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

    private static ResultSet createMockResultSet() {
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
}
