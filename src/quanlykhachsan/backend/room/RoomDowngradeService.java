package quanlykhachsan.backend.room;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Manager-authorized reassignment when a reserved room is taken out of order. */
public final class RoomDowngradeService {
    public record Result(int bookingId, int roomId, BigDecimal oldRate, BigDecimal newRate,
                         BigDecimal folioCredit) { }

    public Result downgrade(int bookingId, int replacementRoomId, boolean managerOverride) throws SQLException {
        if (!managerOverride) throw new SecurityException("Manager override is required");
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                int currentRoom;
                BigDecimal oldRate;
                String status;
                try (PreparedStatement q = c.prepareStatement(
                        "SELECT b.room_id,b.status,r.price,r.status FROM bookings b JOIN rooms r ON r.id=b.room_id WHERE b.id=? FOR UPDATE")) {
                    q.setInt(1, bookingId);
                    try (ResultSet rs = q.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Booking not found");
                        currentRoom = rs.getInt(1); status = rs.getString(2);
                        oldRate = rs.getBigDecimal(3); String currentStatus = rs.getString(4);
                        if (!"out_of_order".equals(currentStatus)) throw new IllegalStateException("Reserved room is not out of order");
                        if (!"confirmed".equals(status) && !"pending".equals(status)) throw new IllegalStateException("Only a not-yet-checked-in booking can be downgraded");
                    }
                }
                BigDecimal newRate;
                try (PreparedStatement q = c.prepareStatement(
                        "SELECT price FROM rooms WHERE id=? AND status='vacant_clean' AND price<? FOR UPDATE")) {
                    q.setInt(1, replacementRoomId); q.setBigDecimal(2, oldRate);
                    try (ResultSet rs = q.executeQuery()) {
                        if (!rs.next()) throw new IllegalStateException("Replacement must be a lower-rate vacant clean room");
                        newRate = rs.getBigDecimal(1);
                    }
                }
                try (PreparedStatement q = c.prepareStatement("UPDATE rooms SET status='reserved',version=version+1 WHERE id=? AND status='vacant_clean'")) {
                    q.setInt(1, replacementRoomId);
                    if (q.executeUpdate() != 1) throw new SQLException("Replacement room state changed");
                }
                try (PreparedStatement q = c.prepareStatement("UPDATE bookings SET room_id=?,total_price=total_price-?+? WHERE id=? AND room_id=?")) {
                    q.setInt(1, replacementRoomId); q.setBigDecimal(2, oldRate); q.setBigDecimal(3, newRate);
                    q.setInt(4, bookingId); q.setInt(5, currentRoom);
                    if (q.executeUpdate() != 1) throw new SQLException("Booking changed while downgrading");
                }
                BigDecimal credit = oldRate.subtract(newRate).setScale(2, RoundingMode.HALF_UP);
                if (credit.signum() > 0) adjustMasterFolio(c, bookingId, credit);
                c.commit();
                return new Result(bookingId, replacementRoomId, oldRate, newRate, credit);
            } catch (SQLException | RuntimeException ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); }
        }
    }

    private static void adjustMasterFolio(Connection c, int bookingId, BigDecimal credit) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT id FROM folios WHERE booking_id=? AND folio_type='master' ORDER BY id LIMIT 1 FOR UPDATE")) {
            q.setInt(1, bookingId);
            try (ResultSet rs = q.executeQuery()) {
                if (!rs.next()) throw new SQLException("Master folio missing");
                long id = rs.getLong(1);
                try (PreparedStatement tx = c.prepareStatement("INSERT INTO folio_transactions(folio_id,transaction_type,charge_type,description,amount,business_date,reference_id) VALUES(?,'credit','room_adjustment','Room downgrade rate adjustment',?,CURRENT_DATE(),?)")) {
                    tx.setLong(1, id); tx.setBigDecimal(2, credit); tx.setString(3, "ROOM-DOWNGRADE-" + bookingId); tx.executeUpdate();
                }
                try (PreparedStatement update = c.prepareStatement("UPDATE folios SET balance=balance-? WHERE id=?")) {
                    update.setBigDecimal(1, credit); update.setLong(2, id); update.executeUpdate();
                }
            }
        }
    }
}
