package quanlykhachsan.backend.utils;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import quanlykhachsan.backend.system.SystemStateHolder;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * System Freeze Middleware Filter (TASK-A3.1).
 * <p>
 * During Night Audit (00:00 - 00:05 Lockout):
 * <ul>
 *   <li>Positioned in chain: {@code SecurityHardeningFilter} -> {@code CorsFilter} -> {@code SystemStatusFilter} -> {@code JwtFilter} -> {@code Router}.</li>
 *   <li>All mutating HTTP requests (POST, PUT, DELETE, PATCH) are intercepted at the Filter layer.</li>
 *   <li>Returns HTTP 503 Service Unavailable with {@code Retry-After: 300} header in &lt; 1ms without calling the database.</li>
 *   <li>Read-only queries (GET, HEAD, OPTIONS) remain 100% operational.</li>
 *   <li>Unfreeze and health check endpoints (/api/v1/system/unfreeze, /api/v1/health) are exempt.</li>
 * </ul>
 */
public class SystemStatusFilter extends Filter {

    public static final int RETRY_AFTER_SECONDS = 300;
    public static final String ERROR_CODE_SYSTEM_FROZEN = "ERR_503_SYSTEM_FROZEN";
    public static final String ERROR_CODE_SERVICE_UNAVAILABLE = "ERR_503_SERVICE_UNAVAILABLE";
    public static final String FREEZE_MESSAGE = "The system is currently in the Night Audit closing window (00:00 - 00:05). All write operations are temporarily locked. Read-only mode is active.";

    private static final byte[] FROZEN_RESPONSE_BYTES;

    static {
        JsonObject root = new JsonObject();
        root.addProperty("status", "ERROR");
        root.addProperty("error_code", ERROR_CODE_SERVICE_UNAVAILABLE);
        root.addProperty("code", ERROR_CODE_SYSTEM_FROZEN);
        root.addProperty("message", FREEZE_MESSAGE);
        root.addProperty("retry_after", RETRY_AFTER_SECONDS);

        JsonObject errorObj = new JsonObject();
        errorObj.addProperty("code", ERROR_CODE_SYSTEM_FROZEN);
        errorObj.addProperty("message", FREEZE_MESSAGE);
        root.add("error", errorObj);

        JsonObject metaObj = new JsonObject();
        metaObj.addProperty("retry_after_seconds", RETRY_AFTER_SECONDS);
        root.add("meta", metaObj);

        FROZEN_RESPONSE_BYTES = root.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();
        String rawPath = exchange.getRequestURI().getPath();
        String path = (rawPath != null) ? rawPath : "/";

        // 1. Check if system is currently locked in freeze mode
        if (SystemStateHolder.isSystemFrozen()) {
            // Unconditional passage for read-only methods
            if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
                chain.doFilter(exchange);
                return;
            }

            // Exemption for administrative unfreeze and health check endpoints
            if (isExemptEndpoint(path)) {
                chain.doFilter(exchange);
                return;
            }

            // Intercept and block mutating requests immediately without touching DB pool (< 1ms)
            if (isMutatingMethod(method)) {
                sendServiceUnavailable(exchange);
                return;
            }
        }

        // 2. System is not frozen: proceed with normal filter chain
        chain.doFilter(exchange);
    }

    /**
     * Determines whether an HTTP request path is exempt from freeze lockout.
     * <p>
     * <b>Architectural Trade-off &amp; Zero Pool Guarantee:</b>
     * {@code /api/v1/auth/login} is exempted to enable staff read-only emergency access during Night Audit.
     * To fulfill the zero database connection pool commitment, {@code AuthController} resolves credentials
     * via in-memory {@code CREDENTIAL_CACHE} without opening HikariCP connections during freeze.
     */
    public static boolean isExemptEndpoint(String path) {
        if (path == null) return false;
        String normalized = (path.length() > 1 && path.endsWith("/")) ? path.substring(0, path.length() - 1) : path;
        return normalized.equals("/api/v1/system/unfreeze")
                || normalized.equals("/system/unfreeze")
                || normalized.equals("/api/v1/health")
                || normalized.equals("/health")
                || normalized.equals("/api/v1/auth/login")
                || normalized.equals("/api/auth/login")
                || normalized.startsWith("/api/v1/system/")
                || normalized.startsWith("/system/");
    }

    /**
     * Determines if an HTTP method performs state mutations.
     */
    public static boolean isMutatingMethod(String method) {
        if (method == null) return false;
        return "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method) || "PATCH".equals(method);
    }

    /**
     * Sends immediate 503 Service Unavailable response with 'Retry-After: 300'.
     * Executes in &lt; 1ms with zero database connection overhead.
     */
    public static void sendServiceUnavailable(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Retry-After", String.valueOf(RETRY_AFTER_SECONDS));
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Idempotency-Key, Accept");

        exchange.sendResponseHeaders(503, FROZEN_RESPONSE_BYTES.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(FROZEN_RESPONSE_BYTES);
            os.flush();
        }
    }

    @Override
    public String description() {
        return "System Freeze Middleware Filter (00:00 - 00:05 Lockout)";
    }
}
