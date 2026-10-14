package quanlykhachsan.backend.utils;

import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real MariaDB only: a skipped test does not certify the migration prerequisite. */
class DatabaseMigrationTest {
    @Test void week5SchemaIsRepeatableAndRetainsChannelData() throws Exception {
        String url = System.getenv("HOTEL_MARIADB_TEST_URL");
        String user = System.getenv("HOTEL_MARIADB_TEST_USER");
        Assumptions.assumeTrue(url != null && url.matches("jdbc:mysql://[^/]+/\\?[^#]*") && user != null,
                "Provide a database-free MariaDB 11 JDBC URL and test credentials");
        String schema = "phase2_migration_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = DriverManager.getConnection(url, user, System.getenv("HOTEL_MARIADB_TEST_PASSWORD"));
             Statement admin = c.createStatement()) {
            try (ResultSet version = admin.executeQuery("SELECT VERSION()")) {
                version.next(); assertTrue(version.getString(1).startsWith("11.") && version.getString(1).contains("MariaDB"));
            }
            admin.execute("CREATE DATABASE `" + schema + "`");
            c.setCatalog(schema);
            try (Statement s = c.createStatement()) {
                s.execute("CREATE TABLE bookings (id INT PRIMARY KEY, customer_type ENUM('OTA','Walk-in','Corporate') DEFAULT 'Walk-in')");
                s.execute("INSERT INTO bookings VALUES (1,'OTA'),(2,'Corporate')");
                UpdateDbUtility.migratePublicPortal(c);
                UpdateDbUtility.migratePublicPortal(c);
                try (ResultSet column = s.executeQuery("SHOW COLUMNS FROM bookings LIKE 'customer_type'")) {
                    assertTrue(column.next()); assertTrue(column.getString("Type").contains("'Web'"));
                }
                try (ResultSet rows = s.executeQuery("SELECT COUNT(*) FROM bookings WHERE customer_type IN ('OTA','Corporate')")) {
                    rows.next(); assertEquals(2, rows.getInt(1));
                }
                s.execute("INSERT INTO bookings VALUES (3,'Web')");
                s.execute("INSERT INTO guest_otps(phone,otp_code,expires_at) VALUES ('0901234567','123456',DATE_ADD(NOW(),INTERVAL 5 MINUTE))");
                try (ResultSet rows = s.executeQuery("SELECT consumed_at,expires_at>created_at FROM guest_otps")) {
                    assertTrue(rows.next()); assertNull(rows.getTimestamp(1)); assertTrue(rows.getBoolean(2));
                }
                try (ResultSet index = s.executeQuery("SHOW INDEX FROM guest_otps WHERE Key_name='idx_otp_phone_code'")) {
                    int count = 0; while (index.next()) count++; assertEquals(3, count);
                }
                s.execute("ALTER TABLE bookings MODIFY customer_type VARCHAR(50)");
                s.execute("INSERT INTO bookings VALUES (4,'unknown')");
                assertThrows(SQLException.class, () -> UpdateDbUtility.migratePublicPortal(c));
                try (ResultSet rows = s.executeQuery("SELECT customer_type FROM bookings WHERE id=4")) {
                    rows.next(); assertEquals("unknown", rows.getString(1));
                }
            } finally {
                try (Statement drop = c.createStatement()) { drop.execute("DROP DATABASE `" + schema + "`"); }
            }
        }
    }
}
