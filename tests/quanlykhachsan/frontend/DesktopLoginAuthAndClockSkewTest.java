package quanlykhachsan.frontend;

import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.JwtTokenUtil;
import quanlykhachsan.backend.utils.SecurityUtil;
import quanlykhachsan.frontend.api.ApiClient;
import quanlykhachsan.frontend.utils.SessionManagerUtil;

import static org.junit.jupiter.api.Assertions.*;

public class DesktopLoginAuthAndClockSkewTest {

    @Test
    @DisplayName("Verify ApiClient.isPublicEndpoint correctly identifies login, register and health endpoints")
    public void testPublicEndpointsRecognition() {
        assertTrue(ApiClient.isPublicEndpoint("/api/v1/auth/login"));
        assertTrue(ApiClient.isPublicEndpoint("/v1/auth/login"));
        assertTrue(ApiClient.isPublicEndpoint("/auth/login"));
        assertTrue(ApiClient.isPublicEndpoint("/api/v1/auth/register"));
        assertTrue(ApiClient.isPublicEndpoint("/v1/auth/register"));
        assertTrue(ApiClient.isPublicEndpoint("/auth/register"));
        assertTrue(ApiClient.isPublicEndpoint("/api/v1/health"));
        assertTrue(ApiClient.isPublicEndpoint("/health"));

        assertFalse(ApiClient.isPublicEndpoint("/api/v1/rooms"));
        assertFalse(ApiClient.isPublicEndpoint("/api/v1/bookings"));
        assertFalse(ApiClient.isPublicEndpoint("/api/v1/users"));
        assertFalse(ApiClient.isPublicEndpoint(null));
        assertFalse(ApiClient.isPublicEndpoint(""));
    }

    @Test
    @DisplayName("Verify SessionManagerUtil does not auto-generate mock token when user is unauthenticated")
    public void testNoSimulatedTokenWhenUnauthenticated() {
        SessionManagerUtil.clear();
        assertNull(SessionManagerUtil.getUser(), "User must be null after clear");
        assertNull(SessionManagerUtil.getAccessToken(), "Access token must be null when unauthenticated");
    }

    @Test
    @DisplayName("Verify Backend JwtParser tolerates clock skew up to 180 seconds (past expiry & future issue)")
    public void testBackendJwtClockSkewTolerance() {
        Instant now = Instant.now();

        // 1. Token expired 60 seconds ago (skew within 180s)
        String expiredSkewToken = Jwts.builder()
            .subject("101")
            .claim("username", "staff_01")
            .claim("role", "staff")
            .issuedAt(Date.from(now.minusSeconds(3600)))
            .expiration(Date.from(now.minusSeconds(60)))
            .signWith(JwtTokenUtil.signingKey())
            .compact();

        assertDoesNotThrow(() -> {
            var claims = SecurityUtil.validateAndExtractClaims(expiredSkewToken);
            assertEquals("101", claims.getSubject());
            assertEquals("staff_01", claims.get("username"));
        }, "Tokens with clock skew under 180s must be accepted");

        // 2. Token issued 60 seconds into the future (future clock drift)
        String futureSkewToken = Jwts.builder()
            .subject("102")
            .claim("username", "receptionist_02")
            .claim("role", "front_desk")
            .issuedAt(Date.from(now.plusSeconds(60)))
            .expiration(Date.from(now.plusSeconds(3600)))
            .signWith(JwtTokenUtil.signingKey())
            .compact();

        assertDoesNotThrow(() -> {
            var claims = SecurityUtil.validateAndExtractClaims(futureSkewToken);
            assertEquals("102", claims.getSubject());
            assertEquals("receptionist_02", claims.get("username"));
        }, "Tokens issued in the future within 180s clock drift must be accepted");
    }

    @Test
    @DisplayName("Verify ApiClient parseAndValidateToken tolerates 120s clock skew on Client")
    public void testClientJwtClockSkewTolerance() {
        Instant now = Instant.now();
        String skewedToken = Jwts.builder()
            .subject("201")
            .claim("username", "guest_user")
            .claim("role", "customer")
            .issuedAt(Date.from(now.minusSeconds(3600)))
            .expiration(Date.from(now.minusSeconds(50)))
            .signWith(JwtTokenUtil.signingKey())
            .compact();

        assertDoesNotThrow(() -> {
            var claims = ApiClient.parseAndValidateToken(skewedToken, JwtTokenUtil.signingKey());
            assertEquals("201", claims.getSubject());
            assertEquals("guest_user", claims.get("username"));
        }, "Client-side JWT analysis must accept tokens within 120s clock skew");
    }
}
