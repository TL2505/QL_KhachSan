package quanlykhachsan.backend.room;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Two simultaneous optimistic-lock attempts against an isolated MariaDB 11 test schema. */
class RoomAllocationMariaDbIntegrationTest {
    @Test
    void competingReservationsProduceOneWinnerOneConflictAndOneVersionIncrement() throws Exception {
        String database = System.getenv("DB_NAME");
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("HOTEL_RUN_DB_INTEGRATION"))
            && database != null && database.endsWith("_test"), "Requires an explicitly configured disposable *_test schema");
        int roomId = createTestRoom();
        try {
            var pool = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            List<java.util.concurrent.Future<Boolean>> attempts = new ArrayList<>();
            try {
                for (int i = 0; i < 2; i++) attempts.add(pool.submit(() -> {
                    try (Connection connection = DatabaseConnection.getConnection()) {
                        connection.setAutoCommit(false);
                        ready.countDown();
                        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start gate timed out");
                        try {
                            RoomAllocationService.reserve(connection, roomId, 0);
                            connection.commit();
                            return true;
                        } catch (OptimisticLockException conflict) {
                            connection.rollback();
                            return false;
                        }
                    }
                }));
                if (!ready.await(10, TimeUnit.SECONDS)) throw new AssertionError("workers did not reach start gate");
                start.countDown();
                int successes = 0;
                int conflicts = 0;
                for (var attempt : attempts) {
                    if (attempt.get(15, TimeUnit.SECONDS)) successes++; else conflicts++;
                }
                assertEquals(1, successes);
                assertEquals(1, conflicts);
                try (Connection connection = DatabaseConnection.getConnection();
                     PreparedStatement query = connection.prepareStatement("SELECT status, version FROM rooms WHERE id=?")) {
                    query.setInt(1, roomId);
                    try (ResultSet row = query.executeQuery()) {
                        if (!row.next()) throw new AssertionError("test room disappeared");
                        assertEquals("reserved", row.getString("status"));
                        assertEquals(1, row.getInt("version"));
                    }
                }
            } finally { pool.shutdownNow(); }
        } finally {
            try (Connection connection = DatabaseConnection.getConnection();
                 PreparedStatement delete = connection.prepareStatement("DELETE FROM rooms WHERE id=?")) {
                delete.setInt(1, roomId);
                delete.executeUpdate();
            }
        }
    }

    private static int createTestRoom() throws Exception {
        String host = env("DB_HOST", "localhost");
        String port = env("DB_PORT", "3306");
        String database = env("DB_NAME", "");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?serverTimezone=UTC";
        try (Connection connection = DriverManager.getConnection(url, env("DB_USER", "root"), env("DB_PASSWORD", ""));
             Statement statement = connection.createStatement()) {
            try (ResultSet version = statement.executeQuery("SELECT VERSION()")) {
                if (!version.next() || !version.getString(1).toLowerCase(java.util.Locale.ROOT).contains("mariadb")
                        || !version.getString(1).startsWith("11.")) {
                    Assumptions.abort("Concurrency integration test requires MariaDB 11");
                }
            }
            int roomTypeId;
            try (ResultSet type = statement.executeQuery("SELECT id FROM room_types ORDER BY id LIMIT 1")) {
                if (!type.next()) Assumptions.abort("Test schema must contain at least one room type");
                roomTypeId = type.getInt(1);
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO rooms (room_number, room_type_id, price, status, version) VALUES (?, ?, ?, 'vacant_clean', 0)",
                    Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, "T" + UUID.randomUUID().toString().substring(0, 18));
                insert.setInt(2, roomTypeId);
                insert.setBigDecimal(3, new BigDecimal("100.00"));
                insert.executeUpdate();
                try (ResultSet key = insert.getGeneratedKeys()) {
                    if (!key.next()) throw new AssertionError("test room insert returned no ID");
                    return key.getInt(1);
                }
            }
        }
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
