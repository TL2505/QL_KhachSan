package quanlykhachsan.backend.booking;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

/** JDBC boundary for the folio ledger; every write uses the caller's transaction. */
public final class FolioDao {
    public PaymentReplay findReplay(Connection c, String key) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT request_hash,response_body,status_code FROM idempotency_keys WHERE id=?")) {
            q.setString(1, key);
            try (ResultSet r = q.executeQuery()) {
                return r.next() ? new PaymentReplay(r.getString(1), r.getString(2), r.getInt(3)) : null;
            }
        }
    }

    public void reserveIdempotency(Connection c, String key, String hash) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("INSERT INTO idempotency_keys (id,request_hash,response_body,status_code) VALUES (?,?,'{}',200)")) {
            q.setString(1, key); q.setString(2, hash); q.executeUpdate();
        }
    }

    public FolioState lockFolio(Connection c, long folioId) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT f.booking_id,f.balance,b.room_id FROM folios f JOIN bookings b ON b.id=f.booking_id WHERE f.id=? FOR UPDATE")) {
            q.setLong(1, folioId);
            try (ResultSet r = q.executeQuery()) { return r.next() ? new FolioState(r.getInt(1), r.getInt(3), r.getBigDecimal(2)) : null; }
        }
    }

    public void insertPaymentCredit(Connection c, PaymentRequest p) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("INSERT INTO folio_transactions (folio_id,transaction_type,charge_type,description,amount,business_date,reference_id) SELECT ?,'credit','payment',?,?,business_date,? FROM hotel_business_state WHERE id=1")) {
            q.setLong(1, p.folioId()); q.setString(2, "Checkout payment (" + p.paymentMethod() + ")");
            q.setBigDecimal(3, p.amount());q.setString(4, p.idempotencyKey());if(q.executeUpdate()!=1)throw new SQLException("Hotel business date missing");
        }
    }

    public void updateBalance(Connection c, long folioId, BigDecimal balance) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("UPDATE folios SET balance=?,status=? WHERE id=?")) {
            q.setBigDecimal(1, balance); q.setString(2, balance.signum() == 0 ? "settled" : "open"); q.setLong(3, folioId);
            if (q.executeUpdate() != 1) throw new SQLException("Folio changed before payment was applied");
        }
    }

    public BigDecimal bookingBalance(Connection c, int bookingId) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT COALESCE(SUM(balance),0.00) FROM folios WHERE booking_id=? FOR UPDATE")) {
            q.setInt(1, bookingId); try (ResultSet r = q.executeQuery()) { r.next(); return r.getBigDecimal(1); }
        }
    }

    public boolean checkout(Connection c, int bookingId, int roomId) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("UPDATE bookings SET status='checked_out',check_out_actual=CURRENT_TIMESTAMP WHERE id=? AND status='checked_in'")) {
            q.setInt(1, bookingId); if (q.executeUpdate() != 1) return false;
        }
        try (PreparedStatement q = c.prepareStatement("UPDATE rooms SET status='vacant_dirty',version=version+1 WHERE id=? AND status IN ('occupied_clean','occupied_dirty')")) {
            q.setInt(1, roomId); if (q.executeUpdate() != 1) throw new SQLException("Room state changed before checkout");
        }
        quanlykhachsan.backend.invoice.InvoiceFinalizationService.finalizePaid(c,bookingId);
        return true;
    }

    public void saveReplay(Connection c, String key, String body) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("UPDATE idempotency_keys SET response_body=?,status_code=200 WHERE id=?")) {
            q.setString(1, body); q.setString(2, key); if (q.executeUpdate() != 1) throw new SQLException("Idempotency record disappeared");
        }
    }

    public void acquireLock(Connection c, String key) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT GET_LOCK(SHA2(?,256),30)")) {
            q.setString(1, key); try (ResultSet r = q.executeQuery()) { if (!r.next() || r.getInt(1) != 1) throw new SQLException("Timed out acquiring payment idempotency lock"); }
        }
    }
    public void releaseLock(Connection c, String key) {
        try (PreparedStatement q = c.prepareStatement("SELECT RELEASE_LOCK(SHA2(?,256))")) { q.setString(1, key); try (ResultSet ignored = q.executeQuery()) { } }
        catch (SQLException ex) { System.err.println("Unable to release folio payment lock: " + ex.getClass().getSimpleName()); }
    }

    public record PaymentReplay(String requestHash, String responseBody, int statusCode) { }
    public record FolioState(int bookingId, int roomId, BigDecimal balance) { }
    public record PaymentRequest(long folioId, String idempotencyKey, String requestHash, BigDecimal amount,
            String paymentMethod, boolean checkout, java.time.ZoneId businessZone) {
        public PaymentRequest { UUID.fromString(idempotencyKey); }
    }
}
