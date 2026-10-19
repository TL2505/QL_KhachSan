package quanlykhachsan.backend.room;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Performs a room reservation using the rooms.version optimistic lock. */
public final class RoomAllocationService {
    private RoomAllocationService() { }

    public static RoomSnapshot findRoom(int roomId) throws SQLException {
        String sql = "SELECT status, price, version FROM rooms WHERE id=?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement query = connection.prepareStatement(sql)) {
            query.setInt(1, roomId);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) return null;
                return new RoomSnapshot(result.getString("status"), result.getBigDecimal("price"),
                    result.getInt("version"));
            }
        }
    }

    public static int currentVersion(int roomId) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement query = connection.prepareStatement("SELECT version FROM rooms WHERE id=?")) {
            query.setInt(1, roomId);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) throw new SQLException("Room does not exist");
                return result.getInt(1);
            }
        }
    }

    /** Must be called in the same transaction as the booking insert. */
    public static void reserve(Connection connection, int roomId, int expectedVersion)
            throws SQLException, OptimisticLockException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE rooms SET status='reserved', version=version+1 "
              + "WHERE id=? AND version=? AND status='vacant_clean'")) {
            update.setInt(1, roomId);
            update.setInt(2, expectedVersion);
            if (update.executeUpdate() != 1) {
                try (PreparedStatement query = connection.prepareStatement("SELECT status FROM rooms WHERE id=? FOR UPDATE")) {
                    query.setInt(1, roomId);
                    try (ResultSet row = query.executeQuery()) {
                        if (!row.next() || !"vacant_clean".equals(row.getString(1))) throw new RoomUnavailableException();
                    }
                }
                throw new OptimisticLockException();
            }
        }
    }

    /** Atomically validates a date-specific stay and advances the room version. */
    public static void reserveForStay(Connection connection, int roomId, int expectedVersion,
            int guestCount, LocalDate checkIn, LocalDate checkOut)
            throws SQLException, OptimisticLockException {
        String status;
        int version;
        int capacity;
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT r.status,r.version,t.capacity FROM rooms r "
              + "JOIN room_types t ON t.id=r.room_type_id WHERE r.id=? FOR UPDATE")) {
            query.setInt(1, roomId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new RoomUnavailableException();
                status = row.getString("status");
                version = row.getInt("version");
                capacity = row.getInt("capacity");
            }
        }
        if (version != expectedVersion) throw new OptimisticLockException();
        if (guestCount < 1 || guestCount > capacity) throw new IllegalArgumentException("Guest count exceeds room capacity");
        if ("out_of_order".equals(status) || "out_of_service".equals(status)) throw new RoomUnavailableException();
        if (checkIn.equals(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))) && !"vacant_clean".equals(status)) {
            throw new RoomUnavailableException();
        }
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT id FROM bookings WHERE room_id=? AND status IN ('pending','confirmed','checked_in') "
              + "AND check_in_date<? AND check_out_date>? LIMIT 1 FOR UPDATE")) {
            query.setInt(1, roomId);
            query.setTimestamp(2, java.sql.Timestamp.valueOf(checkOut.atTime(12, 0)));
            query.setTimestamp(3, java.sql.Timestamp.valueOf(checkIn.atTime(14, 0)));
            try (ResultSet row = query.executeQuery()) {
                if (row.next()) throw new RoomUnavailableException();
            }
        }
        String update = checkIn.equals(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))
                ? "UPDATE rooms SET status='reserved',version=version+1 WHERE id=? AND version=? AND status='vacant_clean'"
                : "UPDATE rooms SET version=version+1 WHERE id=? AND version=? AND status NOT IN ('out_of_order','out_of_service')";
        try (PreparedStatement query = connection.prepareStatement(update)) {
            query.setInt(1, roomId);
            query.setInt(2, expectedVersion);
            if (query.executeUpdate() != 1) throw new RoomUnavailableException();
        }
    }

    public static final class RoomSnapshot {
        private final String status;
        private final java.math.BigDecimal price;
        private final int version;

        private RoomSnapshot(String status, java.math.BigDecimal price, int version) {
            this.status = status;
            this.price = price;
            this.version = version;
        }

        public String status() { return status; }
        public java.math.BigDecimal price() { return price; }
        public int version() { return version; }
    }
}
