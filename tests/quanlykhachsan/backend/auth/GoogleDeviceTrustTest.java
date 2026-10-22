package quanlykhachsan.backend.auth;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoogleDeviceTrustTest {
    private final GoogleOtpAuthService.GoogleIdentity identity = new GoogleOtpAuthService.GoogleIdentity("google-subject", "guest@example.com", "Guest");
    private final byte[] key = "synthetic-device-signing-key-for-tests-only".getBytes(StandardCharsets.UTF_8);
    private GoogleDeviceTrust.Account account(String email, String verified, String password) {
        return new GoogleDeviceTrust.Account(7, "google-subject", email, verified, password);
    }

    @Test void signedProofSurvivesRestartButCannotBeForgedOrUsedForAnotherAccount() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-10-22T00:00:00Z"), ZoneOffset.UTC);
        var row = account(identity.email(), identity.email(), "synthetic-password-hash");
        var first = new GoogleDeviceTrust(clock, key, subject -> row);
        String proof = first.remember(identity);
        var restarted = new GoogleDeviceTrust(clock, key, subject -> row);
        assertTrue(restarted.recognizes(identity, proof));
        assertFalse(restarted.recognizes(identity, null));
        assertFalse(restarted.recognizes(identity, proof + "tampered"));
        assertFalse(restarted.recognizes(identity, proof.replaceFirst("^[^.]+", "9999999999")));
        assertFalse(restarted.recognizes(new GoogleOtpAuthService.GoogleIdentity("other-subject",identity.email(),"Other"), proof));
        assertFalse(new GoogleDeviceTrust(clock, "different-key".getBytes(StandardCharsets.UTF_8), subject -> row).recognizes(identity, proof));
    }

    @Test void passwordEmailAccountDisableAndExpiryInvalidateRememberedDevice() throws Exception {
        Instant now = Instant.parse("2026-10-22T00:00:00Z");
        var row = new AtomicReference<>(account(identity.email(),identity.email(),"old-hash"));
        var trust = new GoogleDeviceTrust(Clock.fixed(now,ZoneOffset.UTC), key, subject -> row.get());
        String proof = trust.remember(identity);
        row.set(account(identity.email(),identity.email(),"new-hash"));
        assertFalse(trust.recognizes(identity, proof));
        row.set(account("changed@example.com",identity.email(),"old-hash"));
        assertFalse(trust.recognizes(identity, proof));
        assertNull(trust.remember(identity));
        row.set(null);
        assertFalse(trust.recognizes(identity, proof));
        row.set(account(identity.email(),identity.email(),"old-hash"));
        var expired = new GoogleDeviceTrust(Clock.fixed(now.plusSeconds(GoogleDeviceTrust.MAX_AGE_SECONDS),ZoneOffset.UTC), key, subject -> row.get());
        assertFalse(expired.recognizes(identity, proof));
    }
}
