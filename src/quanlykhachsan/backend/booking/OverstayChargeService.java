package quanlykhachsan.backend.booking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;

/** Idempotently posts late checkout hours and the single six-hour penalty. */
final class OverstayChargeService {
    static final Duration FREE_GRACE = Duration.ofMinutes(30);
    static final Duration PENALTY_AFTER = Duration.ofHours(6);
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private OverstayChargeService() { }

    static long billableHours(LocalDateTime scheduledCheckout, LocalDateTime at) {
        if (scheduledCheckout == null || at == null || !at.isAfter(scheduledCheckout.plus(FREE_GRACE))) return 0;
        Duration billable = Duration.between(scheduledCheckout.plus(FREE_GRACE), at);
        long seconds = billable.getSeconds() + (billable.getNano() > 0 ? 1 : 0);
        return Math.max(1, (seconds + 3599) / 3600);
    }

    static boolean penaltyDue(LocalDateTime scheduledCheckout, LocalDateTime at) {
        return scheduledCheckout != null && at != null && !at.isBefore(scheduledCheckout.plus(PENALTY_AFTER));
    }

    static void reconcile(Connection c, int bookingId, LocalDateTime scheduledCheckout, LocalDateTime at,
            BigDecimal hourlyRate, boolean chargeHourly) throws SQLException {
        long hours = chargeHourly ? billableHours(scheduledCheckout, at) : 0;
        boolean penalty = penaltyDue(scheduledCheckout, at);
        if ((hours > 0 || penalty) && (hourlyRate == null || hourlyRate.signum() <= 0))
            throw new CheckoutAdjustmentService.CheckoutAdjustmentException("Hourly extra rate must be configured before checkout can be completed");
        if (hours > 0) postDesired(c, bookingId, "room_rate", "OVERTIME-HOURLY-" + bookingId,
                hourlyRate.multiply(BigDecimal.valueOf(hours)), "Phụ thu trả phòng trễ (" + hours + " giờ)");
        if (penalty) postDesired(c, bookingId, "penalty", "OVERTIME-PENALTY-" + bookingId,
                hourlyRate, "Phạt trả phòng trễ trên 6 giờ (thu một lần)");
    }

    /** The long-stay package quote replaces hourly overtime already accrued for HOURLY stays. */
    static void reverseHourlyForPackageConversion(Connection c, int bookingId) throws SQLException {
        String source = "OVERTIME-HOURLY-" + bookingId;
        String reversal = "OVERTIME-HOURLY-VOID-" + bookingId;
        BigDecimal alreadyReversed = referenceTotal(c, reversal);
        BigDecimal accrued = referenceTotal(c, source);
        BigDecimal amount = accrued.subtract(alreadyReversed).setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() > 0) postDelta(c, bookingId, "adjustment", reversal, amount,
                "Ghi giảm phụ thu giờ đã được thay bằng giá gói lưu trú");
    }

    private static void postDesired(Connection c, int booking, String type, String reference,
            BigDecimal desired, String description) throws SQLException {
        desired = desired.setScale(2, RoundingMode.HALF_UP);
        BigDecimal prior = referenceTotal(c, reference);
        BigDecimal delta = desired.subtract(prior).setScale(2, RoundingMode.HALF_UP);
        if (delta.signum() > 0) postDelta(c, booking, type, reference, delta, description);
    }

    private static BigDecimal referenceTotal(Connection c, String reference) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT COALESCE(SUM(amount),0.00) FROM folio_transactions WHERE reference_id=?")) {
            q.setString(1, reference);
            try (ResultSet r = q.executeQuery()) { r.next(); return r.getBigDecimal(1); }
        }
    }

    private static void postDelta(Connection c, int booking, String type, String reference,
            BigDecimal amount, String description) throws SQLException {
        long folioId;
        BigDecimal balance;
        try (PreparedStatement q = c.prepareStatement("SELECT id,balance FROM folios WHERE booking_id=? AND folio_type='master' ORDER BY id LIMIT 1 FOR UPDATE")) {
            q.setInt(1, booking);
            try (ResultSet r = q.executeQuery()) {
                if (!r.next()) throw new SQLException("Master folio not found");
                folioId = r.getLong(1); balance = r.getBigDecimal(2);
            }
        }
        boolean credit = type.equals("adjustment");
        try (PreparedStatement q = c.prepareStatement("INSERT INTO folio_transactions(folio_id,transaction_type,charge_type,description,amount,business_date,reference_id) SELECT ?,?,?,?,?,business_date,? FROM hotel_business_state WHERE id=1")) {
            q.setLong(1, folioId); q.setString(2, credit ? "credit" : "debit"); q.setString(3, type);
            q.setString(4, description); q.setBigDecimal(5, amount); q.setString(6, reference);
            if (q.executeUpdate() != 1) throw new SQLException("Hotel business date missing");
        }
        BigDecimal nextBalance = credit ? balance.subtract(amount) : balance.add(amount);
        try (PreparedStatement q = c.prepareStatement("UPDATE folios SET balance=?,status=? WHERE id=?")) {
            q.setBigDecimal(1, nextBalance); q.setString(2, nextBalance.signum() == 0 ? "settled" : "open"); q.setLong(3, folioId);
            if (q.executeUpdate() != 1) throw new SQLException("Master folio disappeared");
        }
    }
}
