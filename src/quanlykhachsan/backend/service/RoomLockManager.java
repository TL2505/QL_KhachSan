package quanlykhachsan.backend.service;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import quanlykhachsan.backend.utils.DatabaseConnection;

/**
 * Distributed 10-Minute Room Lock Manager.
 * Operates under Standard 4.1.0-REALISTIC-BASELINE to prevent concurrent overbooking.
 * Features an in-memory fast path and authoritative MariaDB stored procedure backend.
 */
public final class RoomLockManager {

    private static final RoomLockManager INSTANCE = new RoomLockManager();
    private static final int DEFAULT_LOCK_DURATION_SECONDS = 600; // 10 minutes

    public static final class MemoryLock {
        private final String sessionToken;
        private final Instant expiresAt;

        public MemoryLock(String sessionToken, Instant expiresAt) {
            this.sessionToken = sessionToken != null ? sessionToken : "";
            this.expiresAt = expiresAt != null ? expiresAt : Instant.now();
        }

        public String getSessionToken() { return sessionToken; }
        public Instant getExpiresAt() { return expiresAt; }
    }

    private final Map<Integer, MemoryLock> localMemoryLocks = new ConcurrentHashMap<>();

    private RoomLockManager() {}

    public static RoomLockManager getInstance() {
        return INSTANCE;
    }

    /**
     * Attempts to acquire a temporary lock on a room with custom TTL.
     * Invokes MariaDB sp_acquire_room_lock with re-entrant and in-memory fallback defense.
     */
    public synchronized boolean acquireLock(int roomId, String channel, String sessionToken, int ttlSeconds) {
        Instant now = Instant.now();
        String safeSession = (sessionToken != null && !sessionToken.isBlank()) ? sessionToken.trim() : "ANONYMOUS_SESSION";
        int duration = ttlSeconds > 0 ? ttlSeconds : DEFAULT_LOCK_DURATION_SECONDS;

        MemoryLock memoryLock = localMemoryLocks.get(roomId);
        if (memoryLock != null && memoryLock.getExpiresAt().isAfter(now)) {
            // Re-entrant lock check: same session is allowed to re-acquire / extend
            if (!memoryLock.getSessionToken().equalsIgnoreCase(safeSession)) {
                return false;
            }
        }

        String sql = "{CALL sp_acquire_room_lock(?, ?, ?, ?, ?)}";

        try (Connection con = DatabaseConnection.getConnection();
             CallableStatement cs = con.prepareCall(sql)) {

            cs.setInt(1, roomId);
            cs.setString(2, (channel != null && !channel.isBlank()) ? channel : "WEB_GUEST");
            cs.setString(3, safeSession);
            cs.setInt(4, duration);
            cs.registerOutParameter(5, Types.BOOLEAN);
            cs.execute();

            boolean acquired = cs.getBoolean(5);
            if (acquired) {
                localMemoryLocks.put(roomId, new MemoryLock(safeSession, now.plusSeconds(duration)));
            }
            return acquired;
        } catch (SQLException e) {
            // Fall back to the row lock when the procedure is absent, but never
            // report an acquired lock if the database or lock table is unavailable.
            boolean acquired = tryFallbackAcquire(roomId, channel, safeSession, duration, now);
            if (acquired) {
                localMemoryLocks.put(roomId, new MemoryLock(safeSession, now.plusSeconds(duration)));
            }
            return acquired;
        }
    }

    private boolean tryFallbackAcquire(int roomId, String channel, String sessionToken, int duration, Instant now) {
        String ch = (channel != null && !channel.isBlank()) ? channel : "WEB_GUEST";
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement check = con.prepareStatement(
                    "SELECT session_token FROM room_locks WHERE room_id = ? AND expires_at > NOW() FOR UPDATE")) {
                check.setInt(1, roomId);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        String activeSession = rs.getString(1);
                        if (activeSession != null && activeSession.equalsIgnoreCase(sessionToken)) {
                            try (PreparedStatement update = con.prepareStatement(
                                    "UPDATE room_locks SET expires_at = DATE_ADD(NOW(), INTERVAL ? SECOND) WHERE room_id = ? AND session_token = ?")) {
                                update.setInt(1, duration);
                                update.setInt(2, roomId);
                                update.setString(3, sessionToken);
                                update.executeUpdate();
                            }
                            con.commit();
                            return true;
                        }
                        con.rollback();
                        return false;
                    }
                }
                try (PreparedStatement insert = con.prepareStatement(
                        "INSERT INTO room_locks (room_id, channel, session_token, locked_at, expires_at) "
                        + "VALUES (?, ?, ?, NOW(), DATE_ADD(NOW(), INTERVAL ? SECOND))")) {
                    insert.setInt(1, roomId);
                    insert.setString(2, ch);
                    insert.setString(3, sessionToken);
                    insert.setInt(4, duration);
                    insert.executeUpdate();
                    con.commit();
                    return true;
                }
            } catch (SQLException exTable) {
                con.rollback();
                return false;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException exConn) {
            return false;
        }
    }

    /**
     * Default lock acquisition with 10-minute (600s) TTL.
     */
    public boolean acquireLock(int roomId, String channel, String sessionToken) {
        return acquireLock(roomId, channel, sessionToken, DEFAULT_LOCK_DURATION_SECONDS);
    }

    /**
     * Releases a room lock bound to a specific session token.
     */
    public void releaseLock(int roomId, String sessionToken) {
        MemoryLock lock = localMemoryLocks.get(roomId);
        if (lock != null) {
            if (sessionToken == null || sessionToken.isBlank() || lock.getSessionToken().equalsIgnoreCase(sessionToken.trim())) {
                localMemoryLocks.remove(roomId);
            }
        }
        String sql = "DELETE FROM room_locks WHERE room_id = ? AND session_token = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            ps.setString(2, sessionToken != null ? sessionToken.trim() : "");
            ps.executeUpdate();
        } catch (SQLException e) {
            // Silently ignore if table is missing or already removed
        }
    }

    /**
     * Releases any active room lock for a room (e.g. upon payment confirmation or admin override).
     */
    public void releaseLockByRoom(int roomId) {
        localMemoryLocks.remove(roomId);
        String sql = "DELETE FROM room_locks WHERE room_id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            ps.executeUpdate();
        } catch (SQLException e) {
            // Silently ignore
        }
    }

    /** Releases the temporary hold associated with a completed/cancelled booking. */
    public void releaseLockForBooking(Connection connection, int bookingId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT room_id FROM bookings WHERE id=?")) {
            query.setInt(1, bookingId);
            try (ResultSet result = query.executeQuery()) {
                if (result.next()) releaseLockByRoom(result.getInt(1));
            }
        }
    }

    /**
     * Checks if a room has an active unexpired lock.
     */
    public boolean isLocked(int roomId) {
        Instant now = Instant.now();
        MemoryLock lock = localMemoryLocks.get(roomId);
        if (lock != null && lock.getExpiresAt().isAfter(now)) {
            return true;
        }

        String sql = "SELECT COUNT(*) FROM room_locks WHERE room_id = ? AND expires_at > NOW()";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Clears all expired locks from memory and database.
     */
    public void cleanExpiredLocks() {
        Instant now = Instant.now();
        localMemoryLocks.entrySet().removeIf(entry -> entry.getValue().getExpiresAt().isBefore(now));
        String sql = "DELETE FROM room_locks WHERE expires_at <= NOW()";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }
}
