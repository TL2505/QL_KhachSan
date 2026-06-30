package quanlykhachsan.backend.utils;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;

public class CorsFilter extends Filter {

    @Override
    public String description() {
        return "Global CORS filter to enable browser HTTP calls with headers";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        // Set standard CORS response headers
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-User-Role, Authorization, ngrok-skip-browser-warning");
        exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400"); // 24 hours caching

        // Handle OPTIONS request pre-flight checks
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }

        // Pass control to the next handler/filter in the chain
        chain.doFilter(exchange);
    }
}
