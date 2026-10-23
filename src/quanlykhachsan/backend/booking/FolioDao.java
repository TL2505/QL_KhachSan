package quanlykhachsan.backend.booking;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
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
        // All order/payment writers acquire the booking before its folios.
        int booking;
        try(var q=c.prepareStatement("SELECT booking_id FROM folios WHERE id=?")){
            q.setLong(1,folioId);try(var r=q.executeQuery()){if(!r.next())return null;booking=r.getInt(1);}
        }
        String bookingStatus;
        try(var q=c.prepareStatement("SELECT status FROM bookings WHERE id=? FOR UPDATE")){
            q.setInt(1,booking);try(var r=q.executeQuery()){if(!r.next())return null;
                bookingStatus=r.getString(1);
                if(java.util.Set.of("cancelled","no_show").contains(bookingStatus))throw new IllegalStateException("Booking cannot receive payment");}
        }
        try (PreparedStatement q = c.prepareStatement("SELECT f.booking_id,f.balance,b.room_id,b.status FROM folios f JOIN bookings b ON b.id=f.booking_id WHERE f.id=? FOR UPDATE")) {
            q.setLong(1, folioId);
            try (ResultSet r = q.executeQuery()) { return r.next() ? new FolioState(r.getInt(1), r.getInt(3), r.getBigDecimal(2),r.getString(4)) : null; }
        }
    }

    public void insertPaymentCredit(Connection c, PaymentRequest p) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("INSERT INTO folio_transactions (folio_id,transaction_type,charge_type,description,amount,business_date,reference_id) SELECT ?,'credit','payment',?,?,business_date,? FROM hotel_business_state WHERE id=1")) {
            q.setLong(1, p.folioId()); q.setString(2, "Checkout payment (" + p.paymentMethod() + ")");
            q.setBigDecimal(3, p.amount());q.setString(4, p.idempotencyKey());if(q.executeUpdate()!=1)throw new SQLException("Hotel business date missing");
        }
        try(var q=c.prepareStatement("INSERT INTO staff_collections(folio_id,reference_id,amount,payment_method,collected_by,business_date) SELECT ?,?,?,?,?,business_date FROM hotel_business_state WHERE id=1")){
            q.setLong(1,p.folioId());q.setString(2,p.idempotencyKey());q.setBigDecimal(3,p.amount());
            q.setString(4,FolioService.collectionMethod(p.paymentMethod()));
            if(p.actorId()>0)q.setInt(5,p.actorId());else q.setNull(5,java.sql.Types.INTEGER);
            if(q.executeUpdate()!=1)throw new SQLException("Collection could not be recorded");
        }
    }

    public static void confirmPaidBooking(Connection c,int booking)throws SQLException {
        try(var q=c.prepareStatement("UPDATE bookings b SET status='confirmed' WHERE b.id=? AND b.status='pending' AND EXISTS(SELECT 1 FROM folios f WHERE f.booking_id=b.id) AND NOT EXISTS(SELECT 1 FROM folios f WHERE f.booking_id=b.id AND f.balance<>0) AND EXISTS(SELECT 1 FROM folio_transactions t JOIN folios f ON f.id=t.folio_id WHERE f.booking_id=b.id AND t.transaction_type='credit' AND t.charge_type='payment')")){
            q.setInt(1,booking);if(q.executeUpdate()>0)quanlykhachsan.backend.notification.LifecycleNotifications.booking(c,booking,"booking_confirmed","booking-confirmed:"+booking,"Đã xác nhận đặt phòng","Đặt phòng đã được xác nhận sau khi nhận đủ thanh toán.");
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

    /** Outstanding non-turnover charges must be settled before the room can be released. */
    public BigDecimal bookingBaseBalance(Connection c,int bookingId)throws SQLException {
        try(PreparedStatement q=c.prepareStatement(
                "SELECT GREATEST(0.00, "
              + "COALESCE((SELECT SUM(CASE WHEN t.transaction_type='debit' AND (t.reference_id IS NULL OR t.reference_id NOT LIKE 'RENTAL-ADJ-%') THEN t.amount WHEN t.transaction_type='credit' THEN -t.amount ELSE 0 END) FROM folio_transactions t JOIN folios f ON f.id=t.folio_id WHERE f.booking_id=?),0.00), "
              + "COALESCE((SELECT SUM(balance) FROM folios WHERE booking_id=?),0.00)-COALESCE((SELECT SUM(t.amount) FROM folio_transactions t JOIN folios f ON f.id=t.folio_id WHERE f.booking_id=? AND t.transaction_type='debit' AND t.reference_id LIKE 'RENTAL-ADJ-%'),0.00))")){
            q.setInt(1,bookingId);q.setInt(2,bookingId);q.setInt(3,bookingId);try(ResultSet r=q.executeQuery()){r.next();return r.getBigDecimal(1);}
        }
    }

    public BigDecimal folioBalance(Connection c,long folioId)throws SQLException {
        try(PreparedStatement q=c.prepareStatement("SELECT balance FROM folios WHERE id=? FOR UPDATE")){
            q.setLong(1,folioId);try(ResultSet r=q.executeQuery()){if(!r.next())throw new SQLException("Folio disappeared");return r.getBigDecimal(1);}
        }
    }

    public boolean checkout(Connection c, int bookingId, int roomId) throws SQLException {
        return checkout(c,bookingId,roomId,null,null);
    }

    public boolean checkout(Connection c, int bookingId, int roomId,quanlykhachsan.backend.room.RentalType finalType,Integer finalBuffer) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT id FROM bookings WHERE id=? AND room_id=? AND status='checked_in' FOR UPDATE")) {
            q.setInt(1, bookingId);q.setInt(2,roomId);
            try(ResultSet r=q.executeQuery()){if(!r.next())return false;}
        }
        try (PreparedStatement q = c.prepareStatement("UPDATE bookings SET status='checked_out',checkout_overdue_at=NULL,rental_type=COALESCE(?,rental_type),buffer_minutes=COALESCE(?,buffer_minutes),check_out_actual=CURRENT_TIMESTAMP(6) WHERE id=? AND room_id=? AND status='checked_in'")) {
            if(finalType==null)q.setNull(1,Types.VARCHAR);else q.setString(1,finalType.databaseValue());
            if(finalBuffer==null)q.setNull(2,Types.SMALLINT);else q.setInt(2,finalBuffer);
            q.setInt(3, bookingId);q.setInt(4,roomId);if(q.executeUpdate()!=1)return false;
        }
        Timestamp readyNotBefore;
        try(PreparedStatement q=c.prepareStatement("SELECT room_release_at FROM bookings WHERE id=?")){
            q.setInt(1,bookingId);try(ResultSet r=q.executeQuery()){if(!r.next())throw new SQLException("Booking disappeared during checkout");readyNotBefore=r.getTimestamp(1);}
        }
        try (PreparedStatement q = c.prepareStatement("UPDATE rooms SET status='vacant_dirty',ready_not_before=?,version=version+1 WHERE id=? AND status IN ('occupied_clean','occupied_dirty')")) {
            q.setTimestamp(1,readyNotBefore);q.setInt(2, roomId); if (q.executeUpdate() != 1) throw new SQLException("Room state changed before checkout");
        }
        try(PreparedStatement q=c.prepareStatement(
                "INSERT INTO cleaning_tasks(booking_id,room_id,rental_type,estimated_duration_minutes,due_at,expected_finish_at,status) "
              + "SELECT b.id,b.room_id,b.rental_type,CASE b.rental_type WHEN 'HOURLY' THEN 45 WHEN 'OVERNIGHT' THEN 60 ELSE 120 END,"
              + "(SELECT MIN(next_booking.check_in_date) FROM bookings next_booking WHERE next_booking.room_id=b.room_id "
              + "AND next_booking.status IN ('pending','confirmed') AND next_booking.check_in_date>CURRENT_TIMESTAMP(6)),"
              + "DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL (b.buffer_minutes+CASE b.rental_type WHEN 'HOURLY' THEN 45 WHEN 'OVERNIGHT' THEN 60 ELSE 120 END) MINUTE),'pending' "
              + "FROM bookings b WHERE b.id=?")){
            q.setInt(1,bookingId);if(q.executeUpdate()!=1)throw new SQLException("Cleaning task could not be created");
        }
        // Physical checkout and room turnover may complete while a posted
        // incidental charge is still open. Finalize the invoice only after all
        // folios settle; FolioService will finalize it after a later payment.
        if(bookingBalance(c,bookingId).signum()==0)quanlykhachsan.backend.invoice.InvoiceFinalizationService.finalizePaid(c,bookingId);
        quanlykhachsan.backend.notification.LifecycleNotifications.booking(c,bookingId,"checkout_gratitude","checkout:"+bookingId,"Đã hoàn tất kỳ lưu trú","Hệ thống đã ghi nhận trả phòng và chuyển phòng sang trạng thái cần dọn dẹp. Quý khách có thể đánh giá kỳ lưu trú để nhận 200 điểm thành viên.");
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
    public record FolioState(int bookingId, int roomId, BigDecimal balance,String bookingStatus) { }
    public record PaymentRequest(long folioId, String idempotencyKey, String requestHash, BigDecimal amount,
            String paymentMethod, boolean checkout, java.time.ZoneId businessZone, int actorId) {
        public PaymentRequest(long folioId,String key,String hash,BigDecimal amount,String method,boolean checkout,java.time.ZoneId zone){this(folioId,key,hash,amount,method,checkout,zone,0);}
        public PaymentRequest { UUID.fromString(idempotencyKey); }
    }
}
