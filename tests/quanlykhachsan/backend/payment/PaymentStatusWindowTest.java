package quanlykhachsan.backend.payment;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PaymentStatusWindowTest {
    @Test void webQrRemainsOpenForOneHourAndGraceIsOnlyForPendingBookings() {
        assertEquals(60, PaymentStatus.QR_WINDOW_MINUTES);
        var created = LocalDateTime.of(2026, 10, 23, 10, 0);
        var deadline = created.plusMinutes(PaymentStatus.QR_WINDOW_MINUTES);
        var window = new PaymentStatus.BookingWindow("qr", deadline, "EXPIRED", "pending");

        assertFalse(window.qrOpen());
        assertTrue(window.reconciliationPending(deadline.plusSeconds(1)));
        assertTrue(window.reconciliationPending(deadline.plusMinutes(1)));
        assertFalse(window.reconciliationPending(deadline.plusMinutes(PaymentStatus.QR_WEBHOOK_GRACE_MINUTES)));
        assertEquals(deadline.plusMinutes(2), window.reconciliationDeadline());
    }

    @Test void graceDoesNotApplyToCounterWalkInOrCancelledBookings() {
        var now = LocalDateTime.of(2026, 10, 23, 11, 1);
        var deadline = now.minusMinutes(1);
        assertFalse(new PaymentStatus.BookingWindow("counter", deadline, "EXPIRED", "pending").reconciliationPending(now));
        assertFalse(new PaymentStatus.BookingWindow("qr", deadline, "EXPIRED", "cancelled").reconciliationPending(now));
        assertFalse(new PaymentStatus.BookingWindow("qr", deadline, "EXPIRED", "pending").reconciliationPending(deadline.plusMinutes(2)));
    }
}
