package quanlykhachsan.backend.utils;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;

public class CorsFilter extends Filter {

    public static final String ALLOWED_ORIGIN = "*";
    public static final String ALLOWED_METHODS = "GET, POST, PUT, DELETE, OPTIONS";

    public static final String ALLOWED_HEADERS = "Content-Type, Authorization, X-User-Role, Idempotency-Key, X-Signature, ngrok-skip-browser-warning";
    public static final String MAX_AGE = "86400";


    @Override
    public String description() {
        return "Global CORS filter to enable browser HTTP calls and VietQR webhooks";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {

        // Set standard CORS response headers
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", ALLOWED_ORIGIN);
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", ALLOWED_METHODS);
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", ALLOWED_HEADERS);
        exchange.getResponseHeaders().set("Access-Control-Max-Age", MAX_AGE);

        // REQUIRED HTTP 204 No Content according to W3C / ADR-05 standards
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        chain.doFilter(exchange);
    }
}
