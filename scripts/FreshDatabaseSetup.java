import java.nio.file.*;
import java.sql.*;
import java.util.*;
import quanlykhachsan.backend.customer.*;
import quanlykhachsan.backend.utils.*;

/** New empty local database only. No existing schema or legacy account is changed. */
public class FreshDatabaseSetup {
    public static void main(String[] args) throws Exception {
        String schema = System.getenv("DB_NAME");
        String appUser = System.getenv("HOTEL_NEW_DB_USER");
        if (schema == null || !schema.matches("hotel_runtime_[a-z0-9_]{1,40}") || appUser == null || !appUser.matches("hotel_app_[a-f0-9]{8}"))
            throw new IllegalArgumentException("Generated new schema and application user are required");
        String password = SecretConfiguration.required("HOTEL_NEW_DB_PASSWORD");
        if (!password.matches("[A-Za-z0-9+/=]{32,100}")) throw new IllegalArgumentException("Generated DB password required");
        GuestIdentityVaultDao vault = GuestIdentityVaultDao.configured();
        String stage = "CREATE_SCHEMA";
        try (Connection c = BackendDiagnostics.openConnection()) {
            boolean finish = args.length == 1 && "--finish-bootstrap".equals(args[0]);
            if (!finish) try (Statement s = c.createStatement()) {
                s.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
            c.setCatalog(schema);
            stage = "SCHEMA";
            if (finish) {
                // Explicit recovery of this initializer's interrupted schema only.
                // Never rerun the baseline, drop tables, or replace an existing account.
                try (Statement q = c.createStatement(); ResultSet r = q.executeQuery("SELECT (SELECT COUNT(*) FROM users),(SELECT COUNT(*) FROM customers),(SELECT COUNT(*) FROM bookings),(SELECT COUNT(*) FROM guest_identity_vault)")) {
                    r.next(); if (r.getLong(1) != 1 || r.getLong(2) != 1 || r.getLong(3) != 0 || r.getLong(4) != 1)
                        throw new SQLException("Partial schema differs from the expected bootstrap state; manual inspection required", "45000");
                }
                try (Statement q = c.createStatement(); ResultSet r = q.executeQuery("SELECT password FROM users WHERE username='hotel_admin' AND role_id=1 AND status='active'")) {
                    if (!r.next() || !SecurityUtil.verifyPassword(SecretConfiguration.required("HOTEL_BOOTSTRAP_PASSWORD"),r.getString(1)))
                        throw new SQLException("Bootstrap account does not match pending configuration", "45000");
                }
                try (Statement q = c.createStatement(); ResultSet r = q.executeQuery("SELECT full_name FROM customers")) {
                    if (!r.next() || !"System vault verification (synthetic)".equals(r.getString(1)))
                        throw new SQLException("Unexpected customer data; refusing partial setup", "45000");
                }
            } else {
            String base = Files.readString(Path.of("database/hotel.sql"));
            int start = base.indexOf("CREATE TABLE IF NOT EXISTS roles");
            int end = base.indexOf("-- SEED DATA FROM PRODUCTION");
            if (start < 0 || end <= start) throw new IllegalStateException("Schema-only boundaries are missing");
            execute(c, base.substring(start, end));
            execute(c, Files.readString(Path.of("database/bootstrap/prerequisites.sql")));
            for (String migration : Files.readAllLines(Path.of("database/bootstrap/migrations.txt"))) {
                if (migration.isBlank()) continue;
                if (!migration.matches("[a-zA-Z0-9_]+\\.sql")) throw new IllegalArgumentException("Invalid migration manifest");
                execute(c, Files.readString(Path.of("database/migrations", migration)).replace("USE hotel_prod_db;", ""));
            }
            UpdateDbUtility.migrate(c);
            }
            stage = "VAULT_READINESS";
            IdentityVaultMigration.verifyReady(c);
            stage = "ADMIN_ACCOUNT";
            if (!finish) {
            String hash = SecurityUtil.hashPassword(SecretConfiguration.required("HOTEL_BOOTSTRAP_PASSWORD"));
            try (PreparedStatement q = c.prepareStatement("INSERT INTO users(username,password,password_hash,role_id,full_name,status) VALUES('hotel_admin',?,?,1,'Hotel Administrator','active')")) {
                q.setString(1, hash); q.setString(2, hash); q.executeUpdate();
            }
            // A synthetic marker proves that persisted data can be read after a process restart.
            // It contains no real person's identifier and is labelled in the customer list.
            stage = "VAULT_MARKER";
            int customer;
            try (PreparedStatement q = c.prepareStatement("INSERT INTO customers(full_name,identity_card,phone) VALUES('System vault verification (synthetic)',NULL,'SYSTEM-VERIFY')", Statement.RETURN_GENERATED_KEYS)) {
                q.executeUpdate(); try (ResultSet r = q.getGeneratedKeys()) { r.next(); customer = r.getInt(1); }
            }
            c.setAutoCommit(false);
            try { vault.save(c, customer, "VERIFY-" + UUID.randomUUID().toString().substring(0,12), null, null); c.commit(); }
            catch (Exception e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
            }
            stage = "DATABASE_ACCOUNT";
            try (Statement s = c.createStatement()) {
                s.execute("CREATE USER IF NOT EXISTS '" + appUser + "'@'localhost' IDENTIFIED BY '" + password + "'");
                // Authenticate before granting privileges if the generated username already exists.
                try (Connection verified = DriverManager.getConnection(System.getenv("DB_URL"), appUser, password)) { }
                s.execute("GRANT ALL PRIVILEGES ON `" + schema + "`.* TO '" + appUser + "'@'localhost'");
            }
            System.out.println("New database ready: " + schema + "; admin=hotel_admin; one synthetic vault verification record");
        } catch (SQLException e) {
            // SQL text for CREATE USER may contain a password; never print the JDBC message.
            System.err.println("Provisioning failed: stage=" + stage + "; SQLState=" + e.getSQLState() + "; code=" + e.getErrorCode() + ". No existing schema was reset. Keep pending configuration for recovery.");
            System.exit(2);
        }
    }

    private static void execute(Connection c, String sql) throws SQLException {
        String delimiter = ";";
        StringBuilder pending = new StringBuilder();
        try (Statement statement = c.createStatement()) {
            for (String line : sql.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--") || trimmed.startsWith("#")) continue;
                if (trimmed.toUpperCase(Locale.ROOT).startsWith("DELIMITER ")) { delimiter = trimmed.substring(10).trim(); continue; }
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
