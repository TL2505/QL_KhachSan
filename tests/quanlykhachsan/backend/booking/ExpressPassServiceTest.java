package quanlykhachsan.backend.booking;

import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExpressPassServiceTest {
    @Test void localPassGenerationVerificationTamperingAndExpiryNeedNoNetwork(){
        byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);
        var clock=Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"),ZoneOffset.UTC);
        var service=new ExpressPassService(key,clock);
        String token=service.issue(12,34,56);
        var claims=service.verify(token);assertEquals(12,claims.bookingId());assertEquals(34,claims.customerId());assertEquals(56,claims.roomId());
        String svg=service.svg(token);assertTrue(svg.startsWith("<svg"));assertTrue(svg.contains("<path"));
        assertFalse(svg.contains("http://")&&!svg.contains("www.w3.org"));
        assertThrows(IllegalArgumentException.class,()->service.verify("x"+token));
        assertThrows(IllegalArgumentException.class,()->service.verify(token+"x"));
        var expired=new ExpressPassService(key,Clock.offset(clock,Duration.ofMinutes(15)));
        assertThrows(IllegalArgumentException.class,()->expired.verify(token));
        byte[] other=key.clone();other[0]^=1;
        assertThrows(IllegalArgumentException.class,()->new ExpressPassService(other,clock).verify(token));
    }
}
