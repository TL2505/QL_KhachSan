package quanlykhachsan.backend.utils;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
// Requires: io.jsonwebtoken:jjwt:0.12.5
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import javax.crypto.SecretKey;

public class JwtFilter extends Filter {

    private static final java.util.Set<String> PUBLIC_PATHS = java.util.Set.of(
        "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/health",
        "/api/v1/auth/guest-otp/request", "/api/v1/auth/guest-otp/verify",
        "/api/v1/rooms/availability", "/api/v1/pricing/quote", "/api/v1/promotions/validate", "/api/v1/bookings/public");

    private final SecretKey key = JwtTokenUtil.signingKey();

    @Override
    public String description() {
        return "JWT Authentication Filter";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        exchange = new RequestIdentityExchange(exchange);
        String path = exchange.getRequestURI().getPath();

        // Skip auth for open endpoints
        boolean publicPath = PUBLIC_PATHS.contains(path)
            || path.equals("/booking") || path.startsWith("/booking/");
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        // Optional tokens on public APIs must still be validated when supplied.
        if (publicPath && authHeader == null) {
            chain.doFilter(exchange);
            return;
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            GlobalExceptionHandler.sendError(exchange, 401, "ERR_401_UNAUTHORIZED", "Missing or invalid Bearer token format.");
            return;
        }

        String token = authHeader.substring(7);
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (claims.getExpiration() == null || claims.getSubject() == null
                    || claims.getSubject().isBlank() || !(claims.get("role") instanceof String)) {
                throw new IllegalArgumentException("Required JWT claims are missing");
            }
            
        } catch (Exception e) {
            GlobalExceptionHandler.sendError(exchange, 401, "ERR_401_UNAUTHORIZED", "Invalid or expired token.");
            return;
        }
        String role = claims.get("role", String.class);
        if (claims.getSubject() == null || role == null || !java.util.Set.of(
                "admin", "staff", "customer", "guest", "front_desk", "housekeeping", "maintenance", "f&b",
                "ADMIN", "STAFF", "CUSTOMER", "1", "2", "3").contains(role)) {
            GlobalExceptionHandler.sendError(exchange, 403, "ERR_403_FORBIDDEN", "Role is not authorized.");
            return;
        }
        role = role.toLowerCase(java.util.Locale.ROOT);
        role = switch(role){case "1"->"admin";case "2","staff"->"front_desk";case "3"->"customer";default->role;};
        exchange.setAttribute("user_id", claims.getSubject());
        exchange.setAttribute("role", role);
        exchange.setAttribute("customer_id", claims.get("customer_id"));
        exchange.setAttribute("phone", claims.get("phone"));
        if (role.equals("guest") && !publicPath && !path.matches("/api/v1/folios/[^/]+/(vietqr|payment-status)") && !path.matches("/api/v1/invoices/[0-9]+/pdf")) {
            GlobalExceptionHandler.sendError(exchange, 403, "ERR_403_FORBIDDEN", "Role is not authorized.");
            return;
        }
        if(java.util.Set.of("housekeeping","maintenance","f&b").contains(role) && !publicPath) {
            boolean profile=path.matches("/api/v1/users/(update-profile|change-password|update-theme)");
            boolean inventory=exchange.getRequestMethod().equals("GET") && (path.equals("/api/v1/rooms") || path.equals("/api/rooms"));
            boolean operation=role.equals("housekeeping") && path.matches("/api/v1/rooms/[0-9]+/(housekeeping|minibar)")
                || role.equals("maintenance") && path.matches("/api/v1/rooms/[0-9]+/maintenance")
                || role.equals("f&b") && path.matches("/api/v1/rooms/[0-9]+/minibar");
            if(!profile && !inventory && !operation){GlobalExceptionHandler.sendError(exchange,403,"ERR_403_FORBIDDEN","Role is not authorized.");return;}
        }
        // Downstream errors must not be misreported as JWT validation failures.
        chain.doFilter(exchange);
    }
}
