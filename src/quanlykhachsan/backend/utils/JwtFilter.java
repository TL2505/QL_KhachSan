package quanlykhachsan.backend.utils;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import io.jsonwebtoken.Jwts;
import java.io.IOException;
import javax.crypto.SecretKey;


/**
 * Production JWT Authentication Filter (TASK-A1.4).
 * <p>
 * Enforces token-based authentication on protected REST API endpoints.
 * Validates HMAC-SHA256 signatures via {@link SecurityUtil#validateAndExtractClaims(String)}
 * and binds authenticated claims (userId, username, role) to the request context.
 */
public class JwtFilter extends Filter {


    private static final java.util.Set<String> PUBLIC_PATHS = java.util.Set.of(
        "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/health",
        "/api/v1/auth/guest-otp/request", "/api/v1/auth/guest-otp/verify",
        "/api/v1/rooms", "/api/rooms", "/api/v1/rooms/availability",
        "/api/v1/pricing/quote", "/api/v1/promotions/validate", "/api/v1/bookings/public");


    private final SecretKey key = JwtTokenUtil.signingKey();


    @Override
    public String description() {
        return "Production JWT Authentication Filter validating Bearer tokens";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        String rawPath = exchange.getRequestURI().getPath();
        String path = (rawPath == null || rawPath.isEmpty()) ? "/" : rawPath;
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1); // Normalize trailing slash
        }

        // CORS preflight does not carry application credentials.
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            chain.doFilter(exchange);
            return;
        }

        // Public routes are explicit; caller-supplied role headers never grant access.
        if (isWhitelisted(path)) {
            chain.doFilter(exchange);
            return;
        }

        // Inspect Authorization header for protected routes.
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");

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
            claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

            // Safely extract claims avoiding ClassCastException on numeric deserialization
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
            username = claimUsername == null ? claims.getSubject() : claimUsername;
            role = claims.get("role", String.class);

        } catch (JwtException | IllegalArgumentException e) {
            System.err.printf("JWT authentication failed for path [%s]: %s%n", path, e.getMessage());
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
        chain.doFilter(exchange);
    }

    /**
     * Checks if a request path is exempted from authentication.
     */
    private boolean isWhitelisted(String path) {
        return PUBLIC_PATHS.contains(path) || path.equals("/health");
    }
}
