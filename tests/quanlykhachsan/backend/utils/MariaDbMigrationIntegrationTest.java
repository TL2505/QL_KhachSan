package quanlykhachsan.backend.utils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Runs the real migration twice against an isolated MariaDB 11 schema when explicitly configured. */
class MariaDbMigrationIntegrationTest {
    @Test
    void migrationIsCleanAndRepeatableOnMariaDb11() throws Exception {
        String rootUrl = System.getenv("HOTEL_MARIADB_TEST_URL");
        String user = System.getenv("HOTEL_MARIADB_TEST_USER");
        String password = System.getenv("HOTEL_MARIADB_TEST_PASSWORD");
        Assumptions.assumeTrue(rootUrl != null && rootUrl.matches("jdbc:mysql://[^/]+/\\?[^#]*"),
            "Set HOTEL_MARIADB_TEST_URL to a database-free MariaDB JDBC URL for integration testing");
        Assumptions.assumeTrue(user != null && !user.isBlank(), "Set HOTEL_MARIADB_TEST_USER");

        String schema = "hotel_migration_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(rootUrl, user, password);
             Statement statement = admin.createStatement()) {
            try (ResultSet version = statement.executeQuery("SELECT VERSION()")) {
                assertTrue(version.next() && isMariaDb11(version.getString(1)),
                    "migration verification requires MariaDB 11");
            }
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            try (Connection connection = DriverManager.getConnection(withSchema(rootUrl, schema), user, password)) {
                createLegacySchema(connection);
                UpdateDbUtility.migrate(connection);
                UpdateDbUtility.migrate(connection);
                assertTrue(columnExists(connection, "rooms", "version"));
                assertEquals(2, indexColumnCount(connection, "idx_room_number_version"));
                assertTrue(indexColumnCount(connection, "idx_idempotency_created_at") > 0);
                assertTrue(tableExists(connection, "folios"));
                assertTrue(tableExists(connection, "folio_transactions"));
                assertTrue(tableExists(connection, "audit_checkpoints"));
                assertTrue(foreignKeyExists(connection, "folios", "fk_folios_bookings"));
                assertTrue(foreignKeyExists(connection, "folio_transactions", "fk_folio_transactions_folios"));

                String scriptSchema = "hotel_script_test_" + UUID.randomUUID().toString().replace("-", "");
                try (Statement create = connection.createStatement()) {
                    create.execute("CREATE DATABASE `" + scriptSchema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                }
                try (Connection scriptConnection = DriverManager.getConnection(withSchema(rootUrl, scriptSchema), user, password)) {
                    executeHotelSchemaScript(scriptConnection, scriptSchema);
                    UpdateDbUtility.migrate(scriptConnection);
                    assertTrue(columnExists(scriptConnection, "rooms", "version"));
                    assertTrue(tableExists(scriptConnection, "audit_checkpoints"));
                    assertTrue(foreignKeyExists(scriptConnection, "folios", "fk_folios_bookings"));
                } finally {
                    try (Statement drop = connection.createStatement()) {
                        drop.execute("DROP DATABASE IF EXISTS `" + scriptSchema + "`");
                    }
                }
            }
        } finally {
            try (Connection admin = DriverManager.getConnection(rootUrl, user, password);
                 Statement statement = admin.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
            }
        }
    }

    private static void createLegacySchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE rooms (id INT AUTO_INCREMENT PRIMARY KEY, room_number VARCHAR(20) NOT NULL UNIQUE, status ENUM('available','booked','occupied','maintenance','cleaning','out_of_service') NOT NULL DEFAULT 'available', INDEX idx_room_number (room_number)) ENGINE=InnoDB");
            statement.execute("CREATE TABLE bookings (id INT AUTO_INCREMENT PRIMARY KEY, status ENUM('pending','confirmed','checked_in','checked_out','cancelled') DEFAULT 'pending') ENGINE=InnoDB");
            statement.execute("CREATE TABLE users (id INT AUTO_INCREMENT PRIMARY KEY) ENGINE=InnoDB");
        }
    }

    private static void executeHotelSchemaScript(Connection connection, String schema) throws Exception {
        String sql = java.nio.file.Files.readString(java.nio.file.Path.of("database", "hotel.sql"))
            .replace("hotel_prod_db", schema);
        String delimiter = ";";
        StringBuilder pending = new StringBuilder();
        try (Statement statement = connection.createStatement()) {
            for (String line : sql.split("\\R")) {
                String trimmedLine = line.trim();
                if (trimmedLine.regionMatches(true, 0, "DELIMITER ", 0, 10)) {
                    delimiter = trimmedLine.substring(10).trim();
                    continue;
                }
                if (trimmedLine.startsWith("--") || trimmedLine.startsWith("#")) continue;
                pending.append(line).append('\n');
                String candidate = pending.toString().trim();
                if (candidate.endsWith(delimiter)) {
                    String command = candidate.substring(0, candidate.length() - delimiter.length()).trim();
                    if (!command.isEmpty()) statement.execute(command);
                    pending.setLength(0);
                }
            }
            if (!pending.toString().isBlank()) {
                throw new IllegalStateException("Unterminated SQL in database/hotel.sql: "
                    + pending.toString().trim());
            }
        }
    }

    private static String withSchema(String rootUrl, String schema) {
        return rootUrl.replaceFirst("/\\?", "/" + schema + "?");
    }
    private static boolean columnExists(Connection c, String table, String column) throws Exception {
        try (ResultSet rs = c.getMetaData().getColumns(c.getCatalog(), null, table, column)) { return rs.next(); }
    }
    private static boolean isMariaDb11(String version) {
        return version != null && version.startsWith("11.") && version.toLowerCase(java.util.Locale.ROOT).contains("mariadb");
    }
    private static boolean tableExists(Connection c, String table) throws Exception {
        try (ResultSet rs = c.getMetaData().getTables(c.getCatalog(), null, table, new String[]{"TABLE"})) { return rs.next(); }
    }
    private static int indexColumnCount(Connection c, String index) throws Exception {
        try (var query = c.prepareStatement("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE table_schema=DATABASE() AND index_name=?")) {
            query.setString(1, index);
            try (ResultSet rs = query.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }
    private static boolean foreignKeyExists(Connection c, String table, String name) throws Exception {
        try (var query = c.prepareStatement("SELECT 1 FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME=? AND CONSTRAINT_NAME=?")) {
            query.setString(1, table); query.setString(2, name);
            try (ResultSet rs = query.executeQuery()) { return rs.next(); }
        }
    }
}
