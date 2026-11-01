package quanlykhachsan.backend.room;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import quanlykhachsan.backend.room.dto.AvailableRoomResponse;
import quanlykhachsan.backend.utils.DatabaseConnection;

/**
 * High-Velocity Room Availability Engine.
 * Implements non-overlapping interval logic: Overlap iff (B_in < A_out) AND (B_out > A_in).
 * Standard 4.1.0-REALISTIC-BASELINE schedule: Check-in 14:00, Check-out 12:00 PM.
 * Automatically identifies and flags contiguous same-day arrivals (Back-to-Back Turnover).
 */
public final class HighVelocityAvailabilityService {

    public static final LocalTime STANDARD_CHECK_IN_TIME = LocalTime.of(14, 0);
    public static final LocalTime STANDARD_CHECK_OUT_TIME = LocalTime.of(12, 0);

    public List<AvailableRoomResponse> findAvailableRooms(
            LocalDate checkInDate,
            LocalDate checkOutDate,
            String sessionToken) throws SQLException {

        if (checkInDate == null || checkOutDate == null || !checkOutDate.isAfter(checkInDate)) {
            throw new IllegalArgumentException("Invalid stay dates: Check-out date must be strictly after check-in date.");
        }

        LocalDateTime checkInDateTime = LocalDateTime.of(checkInDate, STANDARD_CHECK_IN_TIME);
        LocalDateTime checkOutDateTime = LocalDateTime.of(checkOutDate, STANDARD_CHECK_OUT_TIME);

        String sql = "SELECT "
                + "    r.id AS room_id, "
                + "    r.room_number, "
                + "    r.price AS base_price, "
                + "    r.status AS physical_status, "
                + "    r.version AS optimistic_version, "
                + "    rt.id AS room_type_id, "
                + "    rt.name AS room_type_name, "
                + "    rt.capacity, "
                + "    rt.description, "
                + "    rt.image_url, "
                + "    rt.amenities, "
                + "    CASE WHEN EXISTS ("
                + "        SELECT 1 FROM bookings b_prev "
                + "        WHERE b_prev.room_id = r.id "
                + "          AND b_prev.status IN ('confirmed', 'checked_in') "
                + "          AND DATE(b_prev.check_out_date) = ? "
                + "    ) THEN TRUE ELSE FALSE END AS is_back_to_back_arrival "
                + "FROM rooms r "
                + "JOIN room_types rt ON rt.id = r.room_type_id "
                + "WHERE r.status NOT IN ('out_of_order', 'out_of_service') "
                + "  AND NOT EXISTS ("
                + "      SELECT 1 FROM bookings b "
                + "      WHERE b.room_id = r.id "
                + "        AND b.status IN ('pending', 'confirmed', 'checked_in') "
                + "        AND b.check_in_date < ? "
                + "        AND b.check_out_date > ? "
                + "  ) "
                + "  AND NOT EXISTS ("
                + "      SELECT 1 FROM room_locks rl "
                + "      WHERE rl.room_id = r.id "
                + "        AND rl.expires_at > NOW() "
                + "        AND (rl.session_token <> ?) "
                + "  ) "
                + "  AND (? > CURRENT_DATE() OR r.status = 'vacant_clean' OR EXISTS ("
                + "      SELECT 1 FROM bookings b_today "
                + "      WHERE b_today.room_id = r.id "
                + "        AND b_today.status = 'checked_in' "
                + "        AND DATE(b_today.check_out_date) = CURRENT_DATE()"
                + "  )) "
                + "ORDER BY r.price ASC, r.room_number ASC;";

        List<AvailableRoomResponse> availableRooms = new ArrayList<>();
        String safeToken = (sessionToken != null) ? sessionToken : "";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDate(1, java.sql.Date.valueOf(checkInDate));
            ps.setTimestamp(2, Timestamp.valueOf(checkOutDateTime));
            ps.setTimestamp(3, Timestamp.valueOf(checkInDateTime));
            ps.setString(4, safeToken);
            ps.setDate(5, java.sql.Date.valueOf(checkInDate));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AvailableRoomResponse room = new AvailableRoomResponse();
                    room.setRoomId(rs.getInt("room_id"));
                    room.setRoomNumber(rs.getString("room_number"));
                    room.setBasePrice(rs.getBigDecimal("base_price"));
                    room.setPhysicalStatus(rs.getString("physical_status"));
                    room.setOptimisticVersion(rs.getInt("optimistic_version"));
                    room.setRoomTypeId(rs.getInt("room_type_id"));
                    room.setRoomTypeName(rs.getString("room_type_name"));
                    room.setCapacity(rs.getInt("capacity"));
                    room.setDescription(rs.getString("description"));
                    room.setImageUrl(rs.getString("image_url"));
                    room.setAmenities(rs.getString("amenities"));
                    room.setBackToBackArrival(rs.getBoolean("is_back_to_back_arrival"));
                    availableRooms.add(room);
                }
            }
        }
        return availableRooms;
    }
}
