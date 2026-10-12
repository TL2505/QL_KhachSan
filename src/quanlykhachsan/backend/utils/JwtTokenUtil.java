package quanlykhachsan.backend.utils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;

public final class JwtTokenUtil {
    private static final String SECRET = resolveSecret();
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private JwtTokenUtil() { }

    private static String resolveSecret() {
        String secret = System.getenv("JWT_SECRET");
        return secret == null || secret.length() < 32
            ? "hotel_pms_super_secure_secret_key_1234567890" : secret;
    }

    public static SecretKey signingKey() {
        return KEY;
    }

    public static String issueToken(int userId, String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(Integer.toString(userId))
            .claim("username", username)
            .claim("role", role)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(3600)))
            .signWith(KEY)
            .compact();
    }
}
