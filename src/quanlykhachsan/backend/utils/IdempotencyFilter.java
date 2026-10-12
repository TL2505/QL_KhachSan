package quanlykhachsan.backend.utils;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.UUID;
import quanlykhachsan.backend.booking.IdempotencyService;

/** Enforces the Phase 1 idempotency-key contract for booking and folio mutations. */
public final class IdempotencyFilter extends Filter {
    @Override
    public String description() {
        return "Require UUIDv4 Idempotency-Key and deduplicate eligible POST requests";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()) || !isProtectedPath(path)) {
            chain.doFilter(exchange);
            return;
        }
        String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        try {
            if (key == null || !key.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                    || UUID.fromString(key).version() != 4) {
                ApiResponseUtil.write(exchange, 400,
                    "{\"error_code\":\"ERR_400_BAD_REQUEST\",\"message\":\"A UUIDv4 Idempotency-Key header is required\",\"timestamp\":\""
                        + java.time.Instant.now() + "\"}");
                return;
            }
            // FolioController owns the same transaction that updates balances and already persists
            // replay data. The booking route uses this response-capturing service.
            if (path.matches("/api/v1/folios/[0-9]+/pay")) {
                chain.doFilter(exchange);
            } else {
                IdempotencyService.execute(exchange, chain, key);
            }
        } catch (IllegalArgumentException ex) {
            ApiResponseUtil.write(exchange, 400,
                "{\"error_code\":\"ERR_400_BAD_REQUEST\",\"message\":\"Idempotency-Key must be a UUIDv4\",\"timestamp\":\""
                    + java.time.Instant.now() + "\"}");
        }
    }

    private static boolean isProtectedPath(String path) {
        return "/api/v1/bookings".equals(path)
            || "/api/bookings".equals(path)
            || path.matches("/api/v1/folios/[0-9]+/pay");
    }
}
