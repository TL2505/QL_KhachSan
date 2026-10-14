package quanlykhachsan.backend.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Proves payment ledger and balance writes roll back together on MariaDB 11. */
class FolioPaymentMariaDbIntegrationTest {
    @Test
    void injectedFailureAfterLedgerInsertLeavesNoPaymentLedgerOrBalanceMutation() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("HOTEL_RUN_DB_INTEGRATION"))
            && System.getenv("DB_NAME") != null && System.getenv("DB_NAME").endsWith("_test"),
            "Requires an explicitly configured disposable *_test schema");
        Fixture f = createFixture();
        String key = UUID.randomUUID().toString();
        try {
            String hash = "rollback-test-" + key;
            FolioDao.PaymentRequest request = new FolioDao.PaymentRequest(f.folioId, key, hash,
                new BigDecimal("25.00"), "VISA", false, ZoneId.of("Asia/Ho_Chi_Minh"));
            try (Connection connection = DatabaseConnection.getConnection()) {
                assertThrows(SQLException.class, () -> new FolioService().pay(connection, request,
                    () -> { throw new SQLException("injected failure after ledger insert"); }));
            }
            try (Connection connection = DatabaseConnection.getConnection()) {
                assertEquals(new BigDecimal("125.00"), balance(connection, f.folioId));
                assertEquals(0, count(connection, "SELECT COUNT(*) FROM folio_transactions WHERE folio_id=" + f.folioId));
                try (PreparedStatement query = connection.prepareStatement("SELECT COUNT(*) FROM idempotency_keys WHERE id=?")) {
                    query.setString(1, key);
                    try (ResultSet row = query.executeQuery()) { row.next(); assertEquals(0, row.getInt(1)); }
                }
            }
        } finally {
            try (Connection connection = DatabaseConnection.getConnection()) {
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM folios WHERE id=?")) {
                    delete.setInt(1, f.folioId); delete.executeUpdate();
                }
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM bookings WHERE id=?")) {
                    delete.setInt(1, f.bookingId); delete.executeUpdate();
                }
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM rooms WHERE id=?")) {
                    delete.setInt(1, f.roomId); delete.executeUpdate();
                }
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM customers WHERE id=?")) {
                    delete.setInt(1, f.customerId); delete.executeUpdate();
                }
            }
        }
    }

    private static Fixture createFixture() throws Exception {
        String url = "jdbc:mysql://" + env("DB_HOST", "localhost") + ":" + env("DB_PORT", "3306")
            + "/" + env("DB_NAME", "") + "?serverTimezone=UTC";
        try (Connection c = DriverManager.getConnection(url, env("DB_USER", "root"), env("DB_PASSWORD", ""));
             Statement s = c.createStatement()) {
            try (ResultSet version = s.executeQuery("SELECT VERSION()")) {
                if (!version.next() || !version.getString(1).startsWith("11.")
                        || !version.getString(1).toLowerCase(java.util.Locale.ROOT).contains("mariadb"))
                    Assumptions.abort("Folio integration requires MariaDB 11");
            }
            int roomType;
            try (ResultSet r = s.executeQuery("SELECT id FROM room_types ORDER BY id LIMIT 1")) {
                if (!r.next()) Assumptions.abort("Test schema requires a room type");
                roomType = r.getInt(1);
            }
            int roomId;
            try (PreparedStatement p = c.prepareStatement("INSERT INTO rooms(room_number,room_type_id,price,status) VALUES(?,?,100,'occupied_clean')", Statement.RETURN_GENERATED_KEYS)) {
                p.setString(1, "FP" + UUID.randomUUID().toString().substring(0, 16)); p.setInt(2, roomType); p.executeUpdate();
                try (ResultSet r = p.getGeneratedKeys()) { r.next(); roomId = r.getInt(1); }
            }
            int customerId;
            String identity = "FP" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            try (PreparedStatement p = c.prepareStatement("INSERT INTO customers(full_name,identity_card,phone) VALUES('Folio Test',?,'0000000000')", Statement.RETURN_GENERATED_KEYS)) {
                p.setString(1, identity); p.executeUpdate(); try (ResultSet r = p.getGeneratedKeys()) { r.next(); customerId = r.getInt(1); }
            }
            int bookingId;
            try (PreparedStatement p = c.prepareStatement("INSERT INTO bookings(customer_id,room_id,check_in_date,check_out_date,total_price,status) VALUES(?,?,NOW(),DATE_ADD(NOW(),INTERVAL 1 DAY),125,'checked_in')", Statement.RETURN_GENERATED_KEYS)) {
                p.setInt(1, customerId); p.setInt(2, roomId); p.executeUpdate(); try (ResultSet r = p.getGeneratedKeys()) { r.next(); bookingId = r.getInt(1); }
            }
            int folioId;
            try (PreparedStatement p = c.prepareStatement("INSERT INTO folios(booking_id,folio_type,balance) VALUES(?,'master',125.00)", Statement.RETURN_GENERATED_KEYS)) {
                p.setInt(1, bookingId); p.executeUpdate(); try (ResultSet r = p.getGeneratedKeys()) { r.next(); folioId = r.getInt(1); }
            }
            return new Fixture(customerId, roomId, bookingId, folioId);
        }
    }
    private static BigDecimal balance(Connection c, int folioId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT balance FROM folios WHERE id=?")) {
            p.setInt(1, folioId); try (ResultSet r = p.executeQuery()) { r.next(); return r.getBigDecimal(1); }
        }
    }
    private static int count(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) { r.next(); return r.getInt(1); }
    }
    private static String env(String key, String fallback) {
        String value = System.getenv(key); return value == null || value.isBlank() ? fallback : value;
    }
    private record Fixture(int customerId, int roomId, int bookingId, int folioId) { }
}
