package quanlykhachsan.backend.booking;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CounterPaymentLifecycleTest {
    @Test
    void webCounterBookingsAreConfirmedAtReservationWhileOtherRoutesWaitForPayment() {
        assertTrue("confirmed".equals(PublicBookingService.initialStatus("Web", "counter")));
        assertTrue("pending".equals(PublicBookingService.initialStatus("Web", "qr")));
        assertTrue("pending".equals(PublicBookingService.initialStatus("Walk-in", "counter")));
    }

    @Test
    void onlyUnpaidCounterBookingsHoldAutomaticCheckout() {
        assertTrue(WebBookingLifecycleEngine.holdsCheckoutForCounterPayment("counter", new BigDecimal("1.00")));
        assertFalse(WebBookingLifecycleEngine.holdsCheckoutForCounterPayment("counter", BigDecimal.ZERO));
        assertFalse(WebBookingLifecycleEngine.holdsCheckoutForCounterPayment("qr", new BigDecimal("1.00")));
        assertFalse(WebBookingLifecycleEngine.holdsCheckoutForCounterPayment(null, new BigDecimal("1.00")));
    }

    @Test
    void overstayBillingHasThirtyMinuteGraceAndOneSixHourPenalty() {
        LocalDateTime checkout = LocalDateTime.of(2026, 10, 23, 12, 0);
        assertEquals(0, OverstayChargeService.billableHours(checkout, checkout.plusMinutes(30)));
        assertEquals(1, OverstayChargeService.billableHours(checkout, checkout.plusMinutes(31)));
        assertEquals(1, OverstayChargeService.billableHours(checkout, checkout.plusHours(1).plusMinutes(30)));
        assertEquals(2, OverstayChargeService.billableHours(checkout, checkout.plusHours(1).plusMinutes(31)));
        assertFalse(OverstayChargeService.penaltyDue(checkout, checkout.plusHours(5).plusMinutes(59)));
        assertTrue(OverstayChargeService.penaltyDue(checkout, checkout.plusHours(6)));
    }
}
