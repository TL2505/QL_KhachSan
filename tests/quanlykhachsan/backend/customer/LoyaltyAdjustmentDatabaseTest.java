package quanlykhachsan.backend.customer;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import quanlykhachsan.frontend.api.ApiConfig;
import quanlykhachsan.backend.utils.DatabaseConnection;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in test creates and removes its own isolated schema; never changes hotel records. */
class LoyaltyAdjustmentDatabaseTest {
    @Test void adjustmentsPreserveBalancesHistoryAndAtomicity() throws Exception {
        Assumptions.assumeTrue("true".equals(System.getenv("HOTEL_RUN_MEMBERSHIP_DB_TESTS")));
        String schema = "hotel_membership_" + UUID.randomUUID().toString().replace("-", "") + "_test";
        String server = System.getenv().getOrDefault("MEMBERSHIP_TEST_DB_SERVER", "jdbc:mysql://127.0.0.1:3306/");
        String oldUrl = ApiConfig.getJdbcUrl();
        try (Connection admin = DriverManager.getConnection(server, ApiConfig.getDbUser(), ApiConfig.getDbPassword()); Statement setup = admin.createStatement()) {
            setup.executeUpdate("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            try {
                ApiConfig.setJdbcUrl(server + schema);
                try (Connection con = DatabaseConnection.getConnection(); Statement sql = con.createStatement()) {
                    sql.executeUpdate("CREATE TABLE customers (id INT PRIMARY KEY, loyalty_points INT NOT NULL, total_loyalty_points INT NOT NULL, loyalty_level VARCHAR(20)) ENGINE=InnoDB");
                    sql.executeUpdate("CREATE TABLE loyalty_histories (id INT AUTO_INCREMENT PRIMARY KEY, customer_id INT, points INT, type ENUM('earn','redeem'), description VARCHAR(255), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB");
                    sql.executeUpdate("INSERT INTO customers VALUES (1,100,400,'Member')");
                    sql.executeUpdate("CREATE TRIGGER fail_history BEFORE INSERT ON loyalty_histories FOR EACH ROW BEGIN IF NEW.description LIKE '%force-rollback%' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced history failure'; END IF; END");
                }
                var service = new LoyaltyService();
                Customer earned = service.adjustPoints(1, 600, "Bổ sung", "41");
                assertEquals(700, earned.getLoyaltyPoints());
                assertEquals(1000, earned.getTotalLoyaltyPoints());
                assertEquals("Gold", earned.getLoyaltyLevel());
                Customer deducted = service.adjustPoints(1, -500, "Hiệu chỉnh", "41");
                assertEquals(200, deducted.getLoyaltyPoints());
                assertEquals(1000, deducted.getTotalLoyaltyPoints());
                assertEquals("Gold", deducted.getLoyaltyLevel());
                assertThrows(IllegalStateException.class, () -> service.adjustPoints(1, -201, "Overdraft", "41"));
                assertThrows(SQLException.class, () -> service.adjustPoints(1, 100, "force-rollback", "41"));
                assertEquals(200, scalar("SELECT loyalty_points FROM customers WHERE id=1"));
                assertEquals(1000, scalar("SELECT total_loyalty_points FROM customers WHERE id=1"));
                assertEquals(2, scalar("SELECT COUNT(*) FROM loyalty_histories"));
                assertThrows(java.util.NoSuchElementException.class, () -> service.adjustPoints(99, 10, "Missing", "41"));
                var history = service.getHistory(1);
                assertEquals(-500, history.get(0).getPointsChange());
                assertTrue(history.get(0).getDescription().contains("Admin #41"));

                var pool = Executors.newFixedThreadPool(2);
                try {
                    var start = new CountDownLatch(1);
                    Callable<Boolean> debit = () -> { start.await(); try { service.adjustPoints(1, -150, "Concurrent", "41"); return true; } catch (IllegalStateException expected) { return false; } };
                    var a = pool.submit(debit); var b = pool.submit(debit); start.countDown();
                    assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS), "Only one concurrent debit may succeed");
                    assertEquals(50, scalar("SELECT loyalty_points FROM customers WHERE id=1"));
                    assertEquals(3, scalar("SELECT COUNT(*) FROM loyalty_histories"));
                } finally { pool.shutdownNow(); }
                try (Connection con = DatabaseConnection.getConnection(); Statement sql = con.createStatement()) {
                    sql.executeUpdate("UPDATE customers SET loyalty_points=2147483647 WHERE id=1");
                }
                assertThrows(IllegalArgumentException.class, () -> service.adjustPoints(1, 1, "Overflow", "41"));
                assertEquals(3, scalar("SELECT COUNT(*) FROM loyalty_histories"));
            } finally {
                DatabaseConnection.close();
                ApiConfig.setJdbcUrl(oldUrl);
                setup.executeUpdate("DROP DATABASE " + schema);
            }
        }
    }
    private long scalar(String sql) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection(); Statement s = con.createStatement(); ResultSet rs = s.executeQuery(sql)) { rs.next(); return rs.getLong(1); }
    }
}
