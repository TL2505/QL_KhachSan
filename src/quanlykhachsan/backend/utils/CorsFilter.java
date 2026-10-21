package quanlykhachsan.backend.utils;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;

public class CorsFilter extends Filter {

    public static final String ALLOWED_ORIGIN = "*";
    public static final String ALLOWED_METHODS = "GET, POST, PUT, DELETE, OPTIONS";
    public static final String ALLOWED_HEADERS = "Content-Type, Authorization, X-User-Role, Idempotency-Key, X-Signature, ngrok-skip-browser-warning";
    public static final String MAX_AGE = "86400";

    /**
     * Resolves the allowed origin dynamically:
     * Accepts localhost:* or virtual network IP range 192.168.56.*.
     * Defaults to wildcard '*' for generic requests to maintain backward compatibility.
     */
    public static String resolveOrigin(HttpExchange exchange) {
        if (exchange == null) return ALLOWED_ORIGIN;
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && !origin.isBlank()) {
            origin = origin.trim();
            if (origin.matches("https?://(localhost|127\\.0\\.0\\.1)(:[0-9]+)?")
                    || origin.matches("https?://192\\.168\\.56\\.[0-9]+(:[0-9]+)?")) {
                return origin;
            }
        }
        return ALLOWED_ORIGIN;
    }

    @Override
    public String description() {
        return "Global CORS filter supporting dynamic origins for localhost and 192.168.56.*";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        // Set standard CORS response headers
        String origin = resolveOrigin(exchange);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
        if (!ALLOWED_ORIGIN.equals(origin)) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", ALLOWED_METHODS);
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", ALLOWED_HEADERS);
        exchange.getResponseHeaders().set("Access-Control-Max-Age", MAX_AGE);
        exchange.getResponseHeaders().set("Access-Control-Expose-Headers", "X-Booking-Error-Id, Retry-After");

        // REQUIRED HTTP 204 No Content according to W3C / ADR-05 standards
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        chain.doFilter(exchange);
    }
}
