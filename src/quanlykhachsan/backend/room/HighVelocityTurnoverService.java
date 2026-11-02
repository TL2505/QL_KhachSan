package quanlykhachsan.backend.room;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import quanlykhachsan.backend.service.RoomLockManager;
import quanlykhachsan.backend.utils.DatabaseConnection;

/**
 * High-Velocity Room Turnover Dispatch Engine.
 * Operates under Standard 4.1.0-REALISTIC-BASELINE.
 * Automatically dispatches 90-minute SLA housekeeping turnover tasks when contiguous same-day arrivals exist.
 */
public final class HighVelocityTurnoverService {

    public static final int RUSH_TURNOVER_SLA_MINUTES = 90; // Completion deadline 13:30 for 12:00 checkout

    /** Marks the B system's cleaning task as rush inside the caller's checkout transaction. */
    public static boolean dispatchForCheckout(Connection conn, int bookingId) throws SQLException {
        int roomId;
        boolean backToBack = false;
        try (PreparedStatement q = conn.prepareStatement(
                "SELECT room_id, COALESCE(is_back_to_back, FALSE) FROM bookings WHERE id=? FOR UPDATE")) {
            q.setInt(1, bookingId);
            try (ResultSet r = q.executeQuery()) {
                if (!r.next()) return false;
                roomId = r.getInt(1);
                backToBack = r.getBoolean(2);
            }
        }

        // Check dynamically if a contiguous arrival exists for the room
        int nextBookingId = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT incoming.id FROM bookings incoming JOIN bookings outgoing ON outgoing.id=? "
              + "WHERE incoming.room_id=outgoing.room_id AND incoming.id<>outgoing.id "
              + "AND incoming.status IN ('pending', 'confirmed') "
              + "AND incoming.check_in_date >= outgoing.check_out_date "
              + "AND DATE(incoming.check_in_date) <= DATE(outgoing.check_out_date) "
              + "ORDER BY incoming.check_in_date ASC LIMIT 1")) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    nextBookingId = rs.getInt(1);
                    backToBack = true;
                }
            }
        }

        if (!backToBack && nextBookingId == 0) return false;

        try (PreparedStatement q = conn.prepareStatement(
                "UPDATE cleaning_tasks task JOIN bookings outgoing ON outgoing.id=task.booking_id "
              + "SET task.turnover_priority='RUSH', "
              + "task.expected_finish_at=DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL ? MINUTE) "
              + "WHERE task.booking_id=? AND outgoing.room_id=?")) {
            q.setInt(1, RUSH_TURNOVER_SLA_MINUTES);
            q.setInt(2, bookingId);
            q.setInt(3, roomId);
            q.executeUpdate();
        }

        if (nextBookingId > 0) {
            LocalDateTime targetReadyTime = LocalDateTime.now().plusMinutes(RUSH_TURNOVER_SLA_MINUTES);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO housekeeping_turnover_tasks ("
                  + "  room_id, booking_out_id, booking_in_id, priority, target_ready_time, status"
                  + ") VALUES (?, ?, ?, 'RUSH', ?, 'PENDING') "
                  + "ON DUPLICATE KEY UPDATE priority='RUSH', target_ready_time=VALUES(target_ready_time)")) {
                ps.setInt(1, roomId);
                ps.setInt(2, bookingId);
                ps.setInt(3, nextBookingId);
                ps.setTimestamp(4, Timestamp.valueOf(targetReadyTime));
                ps.executeUpdate();
            }
        }
        return true;
    }

    /**
     * Executes check-out with automated Back-to-Back Rush turnover dispatching.
     *
     * @param bookingId Booking identifier checking out
     * @param staffId Front desk staff executing the departure
     * @return true if successfully checked out and task dispatched
     * @throws SQLException on persistence failure
     */
    public boolean processCheckoutWithRushTurnover(int bookingId, int staffId) throws SQLException {
        String queryBooking = "SELECT room_id, is_back_to_back FROM bookings WHERE id = ?;";
        String updateBooking = "UPDATE bookings SET status = 'checked_out', updated_at = NOW() WHERE id = ?;";
        String updateRoom = "UPDATE rooms SET status = 'vacant_dirty', version = version + 1 WHERE id = ?;";
        String findNextBooking = "SELECT id FROM bookings WHERE room_id = ? AND status = 'confirmed' "
                + "AND DATE(check_in_date) = CURRENT_DATE() ORDER BY check_in_date ASC LIMIT 1;";
        String insertTask = "INSERT INTO housekeeping_turnover_tasks ("
                + "  room_id, booking_out_id, booking_in_id, priority, target_ready_time, status"
                + ") VALUES (?, ?, ?, ?, ?, 'PENDING');";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int roomId = 0;
                boolean isBackToBack = false;

                try (PreparedStatement ps = conn.prepareStatement(queryBooking)) {
                    ps.setInt(1, bookingId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                        roomId = rs.getInt("room_id");
                        isBackToBack = rs.getBoolean("is_back_to_back");
                    }
                }

                // 1. Mark Booking as Checked Out
                try (PreparedStatement ps = conn.prepareStatement(updateBooking)) {
                    ps.setInt(1, bookingId);
                    ps.executeUpdate();
                }

                // 2. Transition Room State to Vacant Dirty
                try (PreparedStatement ps = conn.prepareStatement(updateRoom)) {
                    ps.setInt(1, roomId);
                    ps.executeUpdate();
                }

                // 3. If Back-to-Back, dispatch Rush Housekeeping Task with 90-minute SLA
                if (isBackToBack) {
                    int nextBookingId = 0;
                    try (PreparedStatement ps = conn.prepareStatement(findNextBooking)) {
                        ps.setInt(1, roomId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                nextBookingId = rs.getInt("id");
                            }
                        }
                    }

                    if (nextBookingId > 0) {
                        LocalDateTime targetReadyTime = LocalDateTime.now().plusMinutes(RUSH_TURNOVER_SLA_MINUTES);
                        try (PreparedStatement ps = conn.prepareStatement(insertTask)) {
                            ps.setInt(1, roomId);
                            ps.setInt(2, bookingId);
                            ps.setInt(3, nextBookingId);
                            ps.setString(4, "RUSH");
                            ps.setTimestamp(5, Timestamp.valueOf(targetReadyTime));
                            ps.executeUpdate();
                        }

                        // Update incoming booking priority to RUSH
                        String updatePriority = "UPDATE bookings SET turnover_priority = 'RUSH' WHERE id = ?;";
                        try (PreparedStatement ps = conn.prepareStatement(updatePriority)) {
                            ps.setInt(1, nextBookingId);
                            ps.executeUpdate();
                        }
                    }
                }

                // Release any lingering temporary locks
                RoomLockManager.getInstance().releaseLockByRoom(roomId);

                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}
