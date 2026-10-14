package quanlykhachsan.backend.booking;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Enforces Master/Guest folio classification for all newly routed charges. */
public final class FolioRoutingEngine {
    public long routeCharge(Connection c, int bookingId, String chargeType) throws SQLException {
        if (bookingId < 1 || chargeType == null || chargeType.isBlank()) throw new IllegalArgumentException("booking and charge type are required");
        String type = isRoomCharge(chargeType) ? "master" : "guest";
        if ("guest".equals(type)) return ensureGuestFolio(c, bookingId);
        try (PreparedStatement q = c.prepareStatement("SELECT id FROM folios WHERE booking_id=? AND folio_type='master' ORDER BY id LIMIT 1 FOR UPDATE")) {
            q.setInt(1, bookingId); try (ResultSet r = q.executeQuery()) { if (!r.next()) throw new SQLException("Master folio not found"); return r.getLong(1); }
        }
    }

    public long ensureGuestFolio(Connection c, int bookingId) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT id FROM folios WHERE booking_id=? AND folio_type='guest' ORDER BY id LIMIT 1 FOR UPDATE")) {
            q.setInt(1, bookingId); try (ResultSet r = q.executeQuery()) { if (r.next()) return r.getLong(1); }
        }
        try (PreparedStatement q = c.prepareStatement("INSERT INTO folios(booking_id,folio_type,balance,status,code) VALUES(?,'guest',0.00,'open',?)", PreparedStatement.RETURN_GENERATED_KEYS)) {
            q.setInt(1, bookingId); q.setString(2,"FOLIO-GUEST-B"+bookingId); q.executeUpdate();
            try (ResultSet r = q.getGeneratedKeys()) { if (!r.next()) throw new SQLException("Could not create Guest Folio"); return r.getLong(1); }
        }
    }

    public boolean canCloseBooking(Connection c, int bookingId) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT COALESCE(SUM(balance),0.00) FROM folios WHERE booking_id=? FOR UPDATE")) {
            q.setInt(1, bookingId); try (ResultSet r = q.executeQuery()) { r.next(); return r.getBigDecimal(1).compareTo(BigDecimal.ZERO) == 0; }
        }
    }

    private static boolean isRoomCharge(String chargeType) {
        return switch (chargeType.toLowerCase(java.util.Locale.ROOT)) {
            case "room", "room_rate", "room_tax", "vat", "service_charge", "tax" -> true;
            default -> false;
        };
    }
}
