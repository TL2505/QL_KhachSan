package quanlykhachsan.backend.utils;


import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/** Blocks write requests during the daily 00:00–00:05 Vietnam night-audit window. */
public final class SystemStatusFilter extends Filter {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalTime FREEZE_END = LocalTime.of(0, 5);

    @Override
    public String description() {
        return "Night Audit write freeze";

    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {

        String method = exchange.getRequestMethod();
        boolean mutation = "POST".equalsIgnoreCase(method)
            || "PUT".equalsIgnoreCase(method)
            || "DELETE".equalsIgnoreCase(method)
            || "PATCH".equalsIgnoreCase(method);
        LocalTime now = LocalTime.now(BUSINESS_ZONE);
        if (mutation && (quanlykhachsan.backend.system.SystemStateHolder.isFrozen() || now.isBefore(FREEZE_END))) {
            long retrySeconds = 300;
            byte[] body = ("{\"error_code\":\"ERR_503_SERVICE_UNAVAILABLE\","
                + "\"message\":\"Night Audit in progress (00:00 - 00:05). Mutations temporarily locked.\","
                + "\"timestamp\":\"" + Instant.now() + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().set("Retry-After", Long.toString(retrySeconds));
            exchange.sendResponseHeaders(503, body.length);
            try (java.io.OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
            return;
        }
        chain.doFilter(exchange);
    }

}
