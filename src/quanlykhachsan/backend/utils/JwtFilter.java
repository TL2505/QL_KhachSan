package quanlykhachsan.backend.utils;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
// Requires: io.jsonwebtoken:jjwt:0.12.5
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;

public class JwtFilter extends Filter {

    private final SecretKey key;

    public JwtFilter() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.length() < 32) {
            secret = "hotel_pms_super_secure_secret_key_1234567890"; 
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String description() {
        return "JWT Authentication Filter";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        String path = exchange.getRequestURI().getPath();

        // Skip auth for open endpoints
        if (path.equals("/api/v1/auth/login") || path.equals("/api/v1/health") || path.equals("/api/v1/auth/register")) {
            chain.doFilter(exchange);
            return;
        }

        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            GlobalExceptionHandler.sendError(exchange, 401, "ERR_401_UNAUTHORIZED", "Missing or invalid Bearer token format.");
            return;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            // Set user info for downstream RBAC
            exchange.setAttribute("user_id", claims.getSubject());
            exchange.setAttribute("role", claims.get("role"));
            
            chain.doFilter(exchange);
        } catch (Exception e) {
            GlobalExceptionHandler.sendError(exchange, 401, "ERR_401_UNAUTHORIZED", "Invalid or expired token.");
        }
    }
}
