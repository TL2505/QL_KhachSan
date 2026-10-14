package quanlykhachsan.backend.utils;

import com.sun.net.httpserver.HttpExchange;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.mindrot.jbcrypt.BCrypt;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Security & Cryptographic Utility (TASK-A1.4).
 * <p>
 * Responsibilities:
 * 1. Password hashing & verification using jBCrypt (salt cost 12).
 * 2. JWT token generation & verification using JJWT 0.12.5 (HMAC-SHA256).
 * 3. Legacy RBAC authorization helpers (backward-compatibility).
 */
public class SecurityUtil {

    public static final String DEFAULT_JWT_SECRET = "hotel_pms_super_secure_secret_key_1234567890";
    private static final SecretKey SECRET_KEY;

    static {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            secret = DEFAULT_JWT_SECRET;
        }
        SECRET_KEY = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public static SecretKey getSecretKey() {
        return SECRET_KEY;
    }

    // =========================================================================
    // 1. PASSWORD HASHING & VERIFICATION (jBCrypt 0.4)
    // =========================================================================

    /**
     * Hashes a plain text password using BCrypt with salt cost 12.
     *
     * @param plainPassword plain text password
     * @return salted BCrypt hash string
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
    }

    /**
     * Authenticates a plain text password against a stored BCrypt hash.
     *
     * @param plainPassword  input password
     * @param hashedPassword stored hash from database
     * @return true if password matches, false otherwise
     */
    public static boolean checkPassword(String plainPassword, String hashedPassword) {
        // BCrypt hashes must be non-null, start with "$2", and have length of at least 60 characters.
        // Prevents failure/exceptions from database column truncation (e.g. VARCHAR(32) or VARCHAR(50)).
        if (plainPassword == null || hashedPassword == null || !hashedPassword.startsWith("$2") || hashedPassword.length() < 60) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Backward-compatible alias for checkPassword.
     */
    public static boolean verifyPassword(String plainPassword, String hashedPassword) {
        return checkPassword(plainPassword, hashedPassword);
    }

    // =========================================================================
    // 2. JJWT 0.12.5 TOKEN MANAGEMENT
    // =========================================================================

    /**
     * Generates a signed HMAC-SHA256 JWT token with a 1-hour expiration.
     *
     * @param userId   numeric user ID
     * @param username user login handle
     * @param role     user role (e.g. "admin", "staff", "customer")
     * @return compact JWT token string
     */
    public static String generateToken(int userId, String username, String role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("username", username)
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + 3600 * 1000L)) // 1 hour duration
                .signWith(SECRET_KEY)
                .compact();
    }

    /**
     * Validates a signed JWT token and extracts claims.
     *
     * @param token compact JWT string
     * @return payload claims
     * @throws ExpiredJwtException     if token has expired
     * @throws JwtException            if token signature is invalid or malformed
     * @throws IllegalArgumentException if token is null or blank
     */
    public static Claims validateAndExtractClaims(String token) throws JwtException, IllegalArgumentException {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("JWT token cannot be null or blank");
        }
        return Jwts.parser()
                .verifyWith(SECRET_KEY)
                .build()
                .parseSignedClaims(token.trim())
                .getPayload();
    }

    // =========================================================================
    // 3. BACKWARD-COMPATIBILITY RBAC HELPERS
    // =========================================================================

    public static boolean hasPermission(HttpExchange exchange, int... allowedRoles) throws IOException {

        Object jwtRole = exchange.getAttribute("role");
        String roleHeader = jwtRole == null
            ? exchange.getRequestHeaders().getFirst("X-User-Role") : jwtRole.toString();
        

        int roleId = -1;
        if (roleHeader != null) {
            roleHeader = roleHeader.trim();
            if (roleHeader.equalsIgnoreCase("ADMIN")) {
                roleId = 1;
            } else if (roleHeader.equalsIgnoreCase("STAFF")) {
                roleId = 2;
            } else if (roleHeader.equalsIgnoreCase("CUSTOMER")) {
                roleId = 3;
            } else {
                try {
                    roleId = Integer.parseInt(roleHeader);
                } catch (NumberFormatException ignored) {}
            }
        }

        for (int allowedRole : allowedRoles) {
            if (roleId == allowedRole) return true;
        }

        sendError(exchange, 403, "Bạn không có quyền thực hiện hành động này.");
        return false;
    }

    public static boolean checkAdmin(HttpExchange exchange) throws IOException {
        return hasPermission(exchange, 1);
    }

    private static void sendError(HttpExchange exchange, int code, String message) throws IOException {
        ApiResponseUtil.sendError(exchange, code, "ERR_" + code + "_FORBIDDEN", message);
    }
}
