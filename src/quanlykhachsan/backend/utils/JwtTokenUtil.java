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
        if (System.getenv("JWT_SECRET_FILE") != null) {
            try {
                secret = SecretConfiguration.required("JWT_SECRET");
            } catch (Exception ignored) {}
        }
        if (secret == null || secret.isBlank()) {
            secret = System.getProperty("hotel.jwt.secret");
        }
        if (secret == null || secret.isBlank()) {
            java.io.File file = new java.io.File(System.getProperty("hotel.config.file", "config.properties"));
            if (file.exists()) {
                try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                    java.util.Properties p = new java.util.Properties();
                    p.load(fis);
                    String val = p.getProperty("jwt.secret");
                    if (val != null && !val.isBlank()) secret = val.trim();
                } catch (Exception ignored) {}
            }
        }
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            secret = "AureliaGrandPalace5StarLuxuryHotelSecretKey2026SecurePmsProductionSigningKey";
        }
        return secret;
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
