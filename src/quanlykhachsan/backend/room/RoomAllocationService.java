package quanlykhachsan.backend.room;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
