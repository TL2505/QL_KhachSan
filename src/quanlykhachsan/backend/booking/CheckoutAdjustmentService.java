package quanlykhachsan.backend.booking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import quanlykhachsan.backend.pricing.DynamicPricingEngine;
import quanlykhachsan.backend.room.RentalType;
import quanlykhachsan.backend.room.StayWindow;
import quanlykhachsan.backend.utils.PortalValidation.DateRange;

/** Assesses actual-time room charges before a checked-in booking can close. */
public final class CheckoutAdjustmentService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final String PREFIX = "RENTAL-ADJ-";

    public CheckoutPlan assess(Connection c, int bookingId) throws SQLException {
        BookingState b;
        try (PreparedStatement q = c.prepareStatement(
                "SELECT b.rental_type,b.check_in_date,b.check_out_date,COALESCE(b.check_in_actual,b.check_in_date),"
              + "r.room_type_id,r.buffer_hourly,r.buffer_overnight,r.buffer_daily "
              + "FROM bookings b JOIN rooms r ON r.id=b.room_id WHERE b.id=? AND b.status='checked_in' FOR UPDATE")) {
            q.setInt(1, bookingId); try (ResultSet r = q.executeQuery()) {
                if (!r.next()) throw new CheckoutAdjustmentException("Only checked-in bookings can check out");
                b = new BookingState(RentalType.parse(r.getString(1)), r.getTimestamp(2).toLocalDateTime(),
                    r.getTimestamp(3).toLocalDateTime(), r.getTimestamp(4).toLocalDateTime(), r.getInt(5),
                    r.getInt(6), r.getInt(7), r.getInt(8));
            }
        }
        LocalDateTime actualOut;
        try (PreparedStatement q = c.prepareStatement("SELECT CURRENT_TIMESTAMP(6)")) {
            try (ResultSet r = q.executeQuery()) { r.next(); actualOut = r.getTimestamp(1).toLocalDateTime(); }
        }
        RentalType finalType = b.rentalType();
        boolean converted = false;
        if (b.rentalType() == RentalType.HOURLY && Duration.between(b.actualIn(), actualOut).compareTo(Duration.ofHours(6)) > 0) {
            finalType = overnightCandidate(b.actualIn(), actualOut) ? RentalType.OVERNIGHT : RentalType.DAILY;
            converted = true;
        }

        if (converted) assessConvertedPackage(c, bookingId, b, finalType, actualOut);
        else assessActualExtensions(c, bookingId, b, actualOut);
        int buffer = switch (finalType) {
            case HOURLY -> b.bufferHourly(); case OVERNIGHT -> b.bufferOvernight(); case DAILY -> b.bufferDaily();
        };
        return new CheckoutPlan(finalType, buffer, actualOut);
    }

    private static void assessConvertedPackage(Connection c, int booking, BookingState b, RentalType target, LocalDateTime out) throws SQLException {
        StayWindow targetStay;
        if (target == RentalType.OVERNIGHT) targetStay = new StayWindow(target, b.actualIn(), out);
        else {
            LocalDate outDay = out.toLocalDate().isAfter(b.actualIn().toLocalDate()) ? out.toLocalDate() : b.actualIn().toLocalDate().plusDays(1);
            targetStay = new StayWindow(target, b.actualIn().toLocalDate().atTime(14, 0), outDay.atTime(12, 0));
        }
        DynamicPricingEngine.Quote quote;
        try { quote = new DynamicPricingEngine().quote(c, b.roomTypeId(), targetStay); }
        catch (IllegalStateException ex) { throw new CheckoutAdjustmentException(ex.getMessage()); }
        Map<String, BigDecimal> original = originalComponents(c, booking);
        postDesired(c, booking, "room_rate", quote.room_subtotal().subtract(original.getOrDefault("room_rate", ZERO)).max(ZERO));
        postDesired(c, booking, "service_charge", quote.service_charge().subtract(original.getOrDefault("service_charge", ZERO)).max(ZERO));
        postDesired(c, booking, "vat", quote.vat().subtract(original.getOrDefault("vat", ZERO)).max(ZERO));
    }

    private static void assessActualExtensions(Connection c, int booking, BookingState b, LocalDateTime out) throws SQLException {
        long extraHours = 0;
        if (b.actualIn().isBefore(b.plannedIn())) extraHours += ceilHours(Duration.between(b.actualIn(), b.plannedIn()).toMinutes());
        if (out.isAfter(b.plannedOut())) extraHours += ceilHours(Duration.between(b.plannedOut(), out).toMinutes());
        if (extraHours == 0) return;
        BigDecimal hourlyRate;
        try (PreparedStatement q = c.prepareStatement("SELECT hourly_extra_hour_price FROM room_types WHERE id=?")) {
            q.setInt(1, b.roomTypeId()); try (ResultSet r = q.executeQuery()) {
                if (!r.next() || r.getBigDecimal(1) == null || r.getBigDecimal(1).signum() <= 0)
                    throw new CheckoutAdjustmentException("Hourly extra rate must be configured before checkout can be completed");
                hourlyRate = r.getBigDecimal(1);
            }
        }
        var charges = FinancialCalculator.calculate(hourlyRate.multiply(BigDecimal.valueOf(extraHours)), BigDecimal.ZERO, TaxConfig.fromRuntimeConfiguration());
        postDesired(c, booking, "room_rate", charges.getRoomFee());
        postDesired(c, booking, "service_charge", charges.getServiceFee());
        postDesired(c, booking, "vat", charges.getVat());
    }

    private static Map<String, BigDecimal> originalComponents(Connection c, int booking) throws SQLException {
        Map<String, BigDecimal> values = new HashMap<>();
        try (PreparedStatement q = c.prepareStatement("SELECT charge_type,amount FROM folio_transactions WHERE transaction_type='debit' AND reference_id=?")) {
            for (String type : new String[]{"room_rate", "service_charge", "vat"}) {
                q.setString(1, "BOOKING-" + (type.equals("room_rate") ? "ROOM" : type.equals("service_charge") ? "SERVICE" : "VAT") + "-" + booking);
                try (ResultSet r = q.executeQuery()) { BigDecimal sum = ZERO; while (r.next()) sum = sum.add(r.getBigDecimal(2)); values.put(type, sum); }
            }
        }
        return values;
    }

    private static void postDesired(Connection c, int booking, String type, BigDecimal desired) throws SQLException {
        if (desired == null || desired.signum() <= 0) return;
        desired = desired.setScale(2, RoundingMode.HALF_UP);
        String reference = PREFIX + booking + "-" + type;
        BigDecimal prior = ZERO;
        try (PreparedStatement q = c.prepareStatement("SELECT COALESCE(SUM(amount),0.00) FROM folio_transactions WHERE transaction_type='debit' AND reference_id=?")) {
            q.setString(1, reference); try (ResultSet r = q.executeQuery()) { r.next(); prior = r.getBigDecimal(1); }
        }
        BigDecimal delta = desired.subtract(prior).setScale(2, RoundingMode.HALF_UP);
        if (delta.signum() <= 0) return;
        long folioId; BigDecimal balance;
        try (PreparedStatement q = c.prepareStatement("SELECT id,balance FROM folios WHERE booking_id=? AND folio_type='master' ORDER BY id LIMIT 1 FOR UPDATE")) {
            q.setInt(1, booking); try (ResultSet r = q.executeQuery()) { if (!r.next()) throw new SQLException("Master folio not found"); folioId = r.getLong(1); balance = r.getBigDecimal(2); }
        }
        try (PreparedStatement q = c.prepareStatement("INSERT INTO folio_transactions(folio_id,transaction_type,charge_type,description,amount,business_date,reference_id) SELECT ?,'debit',?,?,?,business_date,? FROM hotel_business_state WHERE id=1")) {
            q.setLong(1, folioId); q.setString(2, type); q.setString(3, "Phụ thu thời gian thực tế / chuyển gói"); q.setBigDecimal(4, delta); q.setString(5, reference);
            if (q.executeUpdate() != 1) throw new SQLException("Hotel business date missing");
        }
        try (PreparedStatement q = c.prepareStatement("UPDATE folios SET balance=?,status='open' WHERE id=?")) {
            q.setBigDecimal(1, balance.add(delta)); q.setLong(2, folioId); if (q.executeUpdate() != 1) throw new SQLException("Master folio disappeared");
        }
    }

    private static boolean overnightCandidate(LocalDateTime in, LocalDateTime out) {
        return !in.toLocalTime().isAfter(LocalTime.of(22, 0))
            && out.toLocalDate().equals(in.toLocalDate().plusDays(1))
            && !out.toLocalTime().isBefore(LocalTime.of(6, 0))
            && !in.toLocalTime().isBefore(LocalTime.of(18, 0))
            && !out.toLocalTime().isAfter(LocalTime.NOON);
    }
    private static long ceilHours(long minutes) { return (minutes + 59) / 60; }

    private record BookingState(RentalType rentalType, LocalDateTime plannedIn, LocalDateTime plannedOut,
            LocalDateTime actualIn, int roomTypeId, int bufferHourly, int bufferOvernight, int bufferDaily) { }
    public record CheckoutPlan(RentalType rentalType, int bufferMinutes, LocalDateTime assessedAt) { }
    public static final class CheckoutAdjustmentException extends RuntimeException {
        public CheckoutAdjustmentException(String message) { super(message); }
    }
}
