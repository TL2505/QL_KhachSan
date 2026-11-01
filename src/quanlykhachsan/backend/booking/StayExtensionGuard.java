package quanlykhachsan.backend.booking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import quanlykhachsan.backend.utils.DatabaseConnection;

/**
 * Front Desk Anti-Overstay Defense Barrier.
 * Strictly prevents front desk staff from extending stays on rooms scheduled for contiguous same-day arrivals.
 * Mandates a "Room Move" procedure to preserve the 2-hour sanitization window (12:00 - 14:00).
 */
public final class StayExtensionGuard {

    private StayExtensionGuard() {}

    /**
     * Validates whether an existing booking's check-out time can be extended without room move.
     * Throws IllegalStateException with full conflict details if an incoming booking is scheduled.
     *
     * @param bookingId Current guest booking ID being extended
     * @param roomId Physical room number ID
     * @param proposedNewCheckOut Proposed new check-out timestamp
     * @throws IllegalStateException if a back-to-back contiguous arrival exists
     * @throws SQLException on database connectivity failure
     */
    public static void validateStayExtension(int bookingId, int roomId, LocalDateTime proposedNewCheckOut) throws SQLException {
        String query = "SELECT b.id AS conflicting_booking_id, "
                + "       b.check_in_date, "
                + "       b.check_out_date, "
                + "       c.full_name AS incoming_guest_name "
                + "FROM bookings b "
                + "JOIN customers c ON c.id = b.customer_id "
                + "WHERE b.room_id = ? "
                + "  AND b.id <> ? "
                + "  AND b.status IN ('confirmed', 'checked_in') "
                + "  AND b.check_in_date < ? "
                + "  AND b.check_out_date > CURRENT_TIMESTAMP "
                + "ORDER BY b.check_in_date ASC "
                + "LIMIT 1;";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setInt(1, roomId);
            ps.setInt(2, bookingId);
            ps.setTimestamp(3, Timestamp.valueOf(proposedNewCheckOut));

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int conflictingId = rs.getInt("conflicting_booking_id");
                    String incomingGuest = rs.getString("incoming_guest_name");
                    Timestamp nextArrival = rs.getTimestamp("check_in_date");

                    throw new IllegalStateException(String.format(
                            "ANTI-OVERSTAY VIOLATION: Cannot extend stay for Booking #%d in Room #%d. "
                            + "Room has contiguous arrival Booking #%d (%s) scheduled for %s. "
                            + "A Room Move to an available vacant unit must be processed first.",
                            bookingId, roomId, conflictingId, incomingGuest, nextArrival.toString()
                    ));
                }
            }
        }
    }
}
