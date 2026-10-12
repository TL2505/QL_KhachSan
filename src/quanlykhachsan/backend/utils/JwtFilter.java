package quanlykhachsan.backend.utils;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
// Requires: io.jsonwebtoken:jjwt:0.12.5
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import javax.crypto.SecretKey;

public class JwtFilter extends Filter {

    private final SecretKey key = JwtTokenUtil.signingKey();

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
