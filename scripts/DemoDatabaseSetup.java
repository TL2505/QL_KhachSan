import java.nio.file.*;
import java.sql.*;
import java.util.Locale;
import org.mindrot.jbcrypt.BCrypt;

/** Imports the repository sample into a new local demo schema, excluding server grants. */
public class DemoDatabaseSetup {
    public static void main(String[] args) throws Exception {
        String schema = System.getenv("DB_NAME");
        if (schema == null || !schema.matches("hotel_frontend_demo_[0-9a-f]{12}"))
            throw new IllegalArgumentException("A generated demo schema name is required");
        String url = "jdbc:mysql://127.0.0.1:3306/?useSSL=false&serverTimezone=UTC";
        try (var c = DriverManager.getConnection(url, System.getenv("DB_USER"), System.getenv("DB_PASSWORD"))) {
            // No IF NOT EXISTS: refuse to modify any pre-existing schema.
            try (var create = c.createStatement()) {
                create.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
            c.setCatalog(schema);
            String base = Files.readString(Path.of("database/hotel.sql"));
            int grants = base.indexOf("-- SECURITY: PHYSICAL BOUNDARY FOR AI GATEWAY");
            if (grants < 0 || !base.contains("-- SEED DATA FROM PRODUCTION"))
                throw new IllegalStateException("Sample data or server-grant boundary is missing");
            execute(c, base.substring(0, grants).replace("hotel_prod_db", schema));
            execute(c, Files.readString(Path.of("database/bootstrap/prerequisites.sql"))
                    .replace("INSERT INTO roles(", "INSERT IGNORE INTO roles("));
            // Legacy sample promotions have no codes; the newer unique index requires distinct values.
            try (var s = c.createStatement()) {
                s.execute("ALTER TABLE promotions ADD COLUMN IF NOT EXISTS code VARCHAR(50) NULL");
                s.execute("UPDATE promotions SET code=CONCAT('SAMPLE_',id) WHERE code IS NULL OR code=''");
            }
            for (String migration : Files.readAllLines(Path.of("database/bootstrap/migrations.txt"))) {
                if (migration.isBlank()) continue;
                execute(c, Files.readString(Path.of("database/migrations", migration)).replace("hotel_prod_db", schema));
            }
            quanlykhachsan.backend.utils.UpdateDbUtility.migrate(c);
            int migrated = quanlykhachsan.backend.customer.IdentityVaultMigration.backfill(c,
                    quanlykhachsan.backend.customer.GuestIdentityVaultDao.configured());
            quanlykhachsan.backend.customer.IdentityVaultMigration.verifyReady(c);
            String hash = BCrypt.hashpw("DemoHotel2026!", BCrypt.gensalt());
            try (var q = c.prepareStatement("INSERT INTO users(username,password,password_hash,role_id,full_name,status) VALUES(?,?,?,?,?,'active')")) {
                for (String[] user : new String[][]{{"demo_admin", "1", "Demo Administrator"}, {"demo_guest", "3", "Demo Guest"}}) {
                    q.setString(1, user[0]); q.setString(2, hash); q.setString(3, hash);
                    q.setInt(4, Integer.parseInt(user[1])); q.setString(5, user[2]); q.executeUpdate();
                }
            }
            try (var s = c.createStatement()) {
                s.execute("UPDATE users SET customer_id=(SELECT MIN(id) FROM customers) WHERE username='demo_guest'");
                s.execute("UPDATE customers c JOIN users u ON u.customer_id=c.id SET c.user_id=u.id WHERE u.username='demo_guest'");
            }
            System.out.println("Imported database/hotel.sql into demo schema: " + schema);
            System.out.println("Sample identities encrypted: " + migrated);
            try (var s = c.createStatement()) {
                for (String table : new String[]{"room_types", "rooms", "customers", "services", "bookings", "invoices", "payments"}) {
                    try (var rows = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
                        rows.next(); System.out.println("Sample " + table + ": " + rows.getLong(1));
                    }
                }
            }
        }
    }

    private static void execute(Connection c, String sql) throws SQLException {
        String delimiter = ";";
        var pending = new StringBuilder();
        try (var statement = c.createStatement()) {
            for (String line : sql.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("--") || trimmed.startsWith("#") || trimmed.isEmpty()) continue;
                if (trimmed.toUpperCase(Locale.ROOT).startsWith("DELIMITER ")) {
                    delimiter = trimmed.substring(10).trim(); continue;
                }
                pending.append(line).append('\n');
                String candidate = pending.toString().trim();
                if (candidate.endsWith(delimiter)) {
                    statement.execute(candidate.substring(0, candidate.length() - delimiter.length()));
                    pending.setLength(0);
                }
            }
            if (!pending.toString().isBlank()) throw new SQLException("Unterminated bootstrap SQL");
        }
    }
}
