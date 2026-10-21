package quanlykhachsan.backend.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.auth.dto.AuthUserInfo;
import static org.junit.jupiter.api.Assertions.*;

class GoogleOtpLifecycleTest {
    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-22T00:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    static GoogleOtpAuthService service(TestClock clock, GoogleOtpAuthService.OtpSender sender,
            GoogleOtpAuthService.LoginCompleter completer) {
        return new GoogleOtpAuthService(clock,
                token -> new GoogleOtpAuthService.GoogleIdentity("synthetic-subject", "guest@example.com", "Guest"),
                sender, completer);
    }

    @Test void slowMailDeliveryDoesNotExpireANewCodeBeforeTheFormOpens() throws Exception {
        var clock = new TestClock();
        var codes = new ArrayList<String>();
        var completed = new AtomicInteger();
        var service = service(clock, (email, code) -> { codes.add(code); clock.advance(Duration.ofMinutes(6)); },
                identity -> { completed.incrementAndGet(); return new AuthUserInfo(); });
        var start = service.begin("synthetic-token");
        assertEquals(clock.instant().plusSeconds(300), start.expiresAt());
        assertEquals(0, completed.get());
        service.verify(start.challengeId(), codes.get(0));
        assertEquals(1, completed.get());
        assertEquals(410, assertThrows(GoogleOtpAuthService.AuthFailure.class,
                () -> service.verify(start.challengeId(), codes.get(0))).status);
    }

    @Test void expiredCodeCanBeResentWithinTheVerifiedGoogleSession() throws Exception {
        var clock = new TestClock();
        List<String> codes = new ArrayList<>();
        var service = service(clock, (email, code) -> codes.add(code), identity -> new AuthUserInfo());
        var start = service.begin("synthetic-token");
        clock.advance(Duration.ofMinutes(14));
        assertEquals(422, assertThrows(GoogleOtpAuthService.AuthFailure.class,
                () -> service.verify(start.challengeId(), codes.get(0))).status);
        var resent = service.resend(start.challengeId());
        assertEquals(clock.instant().plusSeconds(300), resent.expiresAt());
        assertEquals(2, codes.size());
        clock.advance(Duration.ofMinutes(3));
        service.verify(start.challengeId(), codes.get(1));
    }

    @Test void databaseFailureDoesNotConsumeACorrectCode() throws Exception {
        var clock = new TestClock();
        List<String> codes = new ArrayList<>();
        var fail = new AtomicBoolean(true);
        var service = service(clock, (email, code) -> codes.add(code), identity -> {
            if (fail.getAndSet(false)) throw new GoogleOtpAuthService.AuthFailure(500, "Synthetic database outage");
            return new AuthUserInfo();
        });
        var start = service.begin("synthetic-token");
        assertEquals(500, assertThrows(GoogleOtpAuthService.AuthFailure.class,
                () -> service.verify(start.challengeId(), codes.get(0))).status);
        assertNotNull(service.verify(start.challengeId(), codes.get(0)));
    }

    @Test void failedResendsPreserveTheDeliveredCodeAndResendBudget() throws Exception {
        var clock = new TestClock();
        List<String> codes = new ArrayList<>();
        var fail = new AtomicBoolean(false);
        var service = service(clock, (email, code) -> {
            if (fail.get()) throw new IllegalStateException("Synthetic SMTP outage");
            codes.add(code);
        }, identity -> new AuthUserInfo());
        var start = service.begin("synthetic-token");
        fail.set(true);
        for (int i = 0; i < 3; i++) assertEquals(503,
                assertThrows(GoogleOtpAuthService.AuthFailure.class, () -> service.resend(start.challengeId())).status);
        assertNotNull(service.verify(start.challengeId(), codes.get(0)));
    }

    @Test void sessionExpiryAndUnknownChallengeRequireFreshGoogleIdentity() throws Exception {
        var clock = new TestClock();
        var service = service(clock, (email, code) -> {}, identity -> new AuthUserInfo());
        var start = service.begin("synthetic-token");
        clock.advance(Duration.ofMinutes(15));
        assertEquals(410, assertThrows(GoogleOtpAuthService.AuthFailure.class,
                () -> service.resend(start.challengeId())).status);
        assertEquals(410, assertThrows(GoogleOtpAuthService.AuthFailure.class,
                () -> service.verify("missing-challenge", "1234")).status);
    }

    @Test void resendDoesNotResetTheFiveAttemptLimit() throws Exception {
        var clock = new TestClock();
        List<String> codes = new ArrayList<>();
        var service = service(clock, (email, code) -> codes.add(code), identity -> new AuthUserInfo());
        var start = service.begin("synthetic-token");
        String wrong = codes.get(0).equals("0000") ? "0001" : "0000";
        for (int i = 0; i < 4; i++) assertEquals(401, assertThrows(GoogleOtpAuthService.AuthFailure.class,
                () -> service.verify(start.challengeId(), wrong)).status);
        service.resend(start.challengeId());
        String nextWrong = codes.get(1).equals("0000") ? "0001" : "0000";
        assertEquals(429, assertThrows(GoogleOtpAuthService.AuthFailure.class,
                () -> service.verify(start.challengeId(), nextWrong)).status);
    }
}
