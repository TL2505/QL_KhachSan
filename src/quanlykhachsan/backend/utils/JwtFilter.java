package quanlykhachsan.backend.utils;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Production JWT Authentication Filter (TASK-A1.4).
 * <p>
 * Enforces token-based authentication on protected REST API endpoints.
 * Validates HMAC-SHA256 signatures via {@link SecurityUtil#validateAndExtractClaims(String)}
 * and binds authenticated claims (userId, username, role) to the request context.
 */
public class JwtFilter extends Filter {

    private static final Logger logger = LoggerFactory.getLogger(JwtFilter.class);

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

        // 1. Skip authentication checks for CORS preflight (OPTIONS)
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            chain.doFilter(exchange);
            return;
        }

        // 2. Public path whitelist (Login, Register, Deep Healthcheck)
        if (isWhitelisted(path)) {
            chain.doFilter(exchange);
            return;
        }

        // 3. Inspect Authorization header
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

        // 4. Validate token and extract claims
        try {
            Claims claims = SecurityUtil.validateAndExtractClaims(token);

            // Safely extract claims avoiding ClassCastException on numeric deserialization
            Object rawUserId = claims.get("userId");
            int userId = (rawUserId instanceof Number number)
                    ? number.intValue()
                    : (rawUserId != null ? Integer.parseInt(rawUserId.toString().trim()) : 0);

            if (userId == 0 && claims.getSubject() != null) {
                try {
                    userId = Integer.parseInt(claims.getSubject().trim());
                } catch (NumberFormatException ignored) {}
            }

            String username = claims.get("username", String.class);
            if (username == null) {
                username = claims.getSubject();
            }

            String role = claims.get("role", String.class);

            // Attach user claims to the request context for downstream controllers & RBAC
            exchange.setAttribute("userId", userId);
            exchange.setAttribute("user_id", String.valueOf(userId));
            exchange.setAttribute("username", username);
            exchange.setAttribute("role", role);

            chain.doFilter(exchange);

        } catch (JwtException | IllegalArgumentException e) {
            logger.warn("JWT authentication failed for path [{}]: {}", path, e.getMessage());
            ApiResponseUtil.sendError(
                    exchange,
                    401,
                    "ERR_401_TOKEN_INVALID",
                    "Authentication token is invalid or expired"
            );
        }
    }

    /**
     * Checks if a request path is exempted from authentication.
     */
    private boolean isWhitelisted(String path) {
        return path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/register")
                || path.equals("/api/v1/health")
                || path.equals("/health");
    }
}
