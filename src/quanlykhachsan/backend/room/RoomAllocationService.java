package quanlykhachsan.backend.room;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        reserveForStay(connection, roomId, expectedVersion, guestCount,
                StayWindow.resolve(RentalType.DAILY, checkIn.atTime(14, 0), checkOut.atTime(12, 0)));
    }

    /** Locks and validates a complete stay interval using the room's configured buffer. */
    public static int reserveForStay(Connection connection, int roomId, int expectedVersion,
            int guestCount, StayWindow stay) throws SQLException, OptimisticLockException {
        String status;
        int version;
        int capacity;
        int bufferMinutes;
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT r.status,r.version,t.capacity,r.buffer_hourly,r.buffer_overnight,r.buffer_daily FROM rooms r "
              + "JOIN room_types t ON t.id=r.room_type_id WHERE r.id=? FOR UPDATE")) {
            query.setInt(1, roomId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new RoomUnavailableException();
                status = row.getString("status");
                version = row.getInt("version");
                capacity = row.getInt("capacity");
                bufferMinutes = switch (stay.rentalType()) {
                    case HOURLY -> row.getInt("buffer_hourly");
                    case OVERNIGHT -> row.getInt("buffer_overnight");
                    case DAILY -> row.getInt("buffer_daily");
                };
            }
        }
        if (version != expectedVersion) throw new OptimisticLockException();
        if (guestCount < 1 || guestCount > capacity) throw new IllegalArgumentException("Guest count exceeds room capacity");
        if ("out_of_order".equals(status) || "out_of_service".equals(status)) throw new RoomUnavailableException();
        LocalDateTime newReleaseAt = stay.releaseAt(bufferMinutes);
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT id FROM bookings WHERE room_id=? AND status IN ('pending','confirmed','checked_in','checked_out') "
              + "AND check_in_date<? AND room_release_at>? LIMIT 1 FOR UPDATE")) {
            query.setInt(1, roomId);
            query.setObject(2, newReleaseAt);
            query.setObject(3, stay.checkIn());
            try (ResultSet row = query.executeQuery()) {
                if (row.next()) throw new RoomUnavailableException();
            }
        }
        try(PreparedStatement query=connection.prepareStatement(
                "SELECT 1 FROM cleaning_tasks ct WHERE ct.room_id=? AND ct.status IN ('pending','in_progress') "
              + "AND COALESCE(ct.expected_finish_at,DATE_ADD(ct.created_at,INTERVAL ct.estimated_duration_minutes MINUTE))>? LIMIT 1 FOR UPDATE")) {
            query.setInt(1,roomId);query.setObject(2,stay.checkIn());
            try(ResultSet row=query.executeQuery()){if(row.next())throw new RoomUnavailableException();}
        }
        boolean today=stay.checkIn().toLocalDate().equals(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        if(today && "vacant_dirty".equals(status)) {
            try(PreparedStatement query=connection.prepareStatement(
                    "SELECT 1 FROM cleaning_tasks ct WHERE ct.room_id=? AND ct.status IN ('pending','in_progress') "
                  + "AND COALESCE(ct.expected_finish_at,DATE_ADD(ct.created_at,INTERVAL ct.estimated_duration_minutes MINUTE))<=? LIMIT 1 FOR UPDATE")) {
                query.setInt(1,roomId);query.setObject(2,stay.checkIn());
                try(ResultSet row=query.executeQuery()){if(!row.next())throw new RoomUnavailableException();}
            }
        }
        String update = today && "vacant_clean".equals(status)
                ? "UPDATE rooms SET status='reserved',version=version+1 WHERE id=? AND version=? AND status='vacant_clean'"
                : "UPDATE rooms SET version=version+1 WHERE id=? AND version=? AND status NOT IN ('out_of_order','out_of_service')";
        try (PreparedStatement query = connection.prepareStatement(update)) {
            query.setInt(1, roomId);
            query.setInt(2, expectedVersion);
            if (query.executeUpdate() != 1) throw new RoomUnavailableException();
        }
        return bufferMinutes;
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
