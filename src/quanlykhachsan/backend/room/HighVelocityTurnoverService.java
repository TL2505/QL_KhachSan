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
