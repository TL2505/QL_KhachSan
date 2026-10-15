package quanlykhachsan.backend.utils;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;


import java.io.IOException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JwtFilter extends Filter {
    private static final Logger logger = LoggerFactory.getLogger(JwtFilter.class);


    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/api/v1/auth/login",
        "/api/v1/auth/register",
        "/api/v1/health",
        "/health",
        "/api/v1/rooms/availability",
        "/api/v1/pricing/quote",
        "/api/v1/promotions/validate",
        "/api/v1/bookings/public",
        "/api/v1/auth/guest-otp/request",
        "/api/v1/auth/guest-otp/verify"
    );


    @Override
    public String description() {
        return "Production JWT Authentication Filter validating Bearer tokens with Guest Web bypass";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {

        exchange = new RequestIdentityExchange(exchange);

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            chain.doFilter(exchange);
            return;
        }


        String rawPath = exchange.getRequestURI().getPath();
        String path = (rawPath == null || rawPath.isEmpty()) ? "/" : rawPath;
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }


        boolean isPublic = PUBLIC_PATHS.contains(path)
                || path.startsWith("/booking")
                || path.equals("/api/v1/rooms")
                || path.equals("/api/rooms");

        boolean isGuestRoleHeader = exchange.getRequestHeaders().containsKey("X-User-Role")
                && "guest".equalsIgnoreCase(exchange.getRequestHeaders().getFirst("X-User-Role"));


        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");

        if ((isPublic || isGuestRoleHeader) && (authHeader == null || authHeader.isBlank())) {
            if (isGuestRoleHeader) {
                exchange.setAttribute("role", "guest");
            }
            chain.doFilter(exchange);
            return;
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            ApiResponseUtil.sendError(
                    exchange,
                    401,
                    "ERR_401_UNAUTHORIZED",
                    "Missing or malformed Authorization header (Bearer <token>)"
            );
            return;
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            ApiResponseUtil.sendError(
                    exchange,
                    401,
                    "ERR_401_UNAUTHORIZED",
                    "Missing or malformed Authorization header (Bearer <token>)"
            );
            return;
        }


        // Validate credentials separately from downstream handling so handler
        // exceptions are not mistaken for invalid JWTs and the chain runs once.
        final Claims claims;
        final int userId;
        final String username;
        final String role;

        try {
            claims = SecurityUtil.validateAndExtractClaims(token);

            Object rawUserId = claims.get("userId");
            int parsedUserId = (rawUserId instanceof Number number)
                    ? number.intValue()
                    : (rawUserId != null ? Integer.parseInt(rawUserId.toString().trim()) : 0);

            if (parsedUserId == 0 && claims.getSubject() != null) {
                try {
                    parsedUserId = Integer.parseInt(claims.getSubject().trim());
                } catch (NumberFormatException ignored) {}
            }
            userId = parsedUserId;

            String claimUsername = claims.get("username", String.class);
            username = (claimUsername == null) ? claims.getSubject() : claimUsername;

            role = claims.get("role", String.class);

            if (role == null || !Set.of(
                    "admin", "staff", "customer", "guest", "front_desk", "housekeeping", "maintenance", "f&b",
                    "ADMIN", "STAFF", "CUSTOMER", "1", "2", "3", "manager").contains(role)) {
                ApiResponseUtil.sendError(exchange, 403, "ERR_403_FORBIDDEN", "Role is not authorized");
                return;
            }
        } catch (JwtException | IllegalArgumentException e) {
            logger.warn("JWT authentication failed for path [{}]: {}", path, e.getMessage());
            ApiResponseUtil.sendError(
                    exchange,
                    401,
                    "ERR_401_TOKEN_INVALID",
                    "Authentication token is invalid or expired"
            );
            return;
        }

        // Attach verified claims and continue exactly once.
        exchange.setAttribute("userId", userId);
        exchange.setAttribute("user_id", String.valueOf(userId));
        exchange.setAttribute("username", username);
        exchange.setAttribute("role", role);
        if (claims.get("customer_id") != null) {
            exchange.setAttribute("customer_id", claims.get("customer_id"));
        }
        if (claims.get("phone") != null) {
            exchange.setAttribute("phone", claims.get("phone"));
        }
        chain.doFilter(exchange);
    }

    /**
     * Checks if a request path is exempted from authentication.
     */
    private boolean isWhitelisted(String path) {
        return PUBLIC_PATHS.contains(path) || path.equals("/health");

    }
}
