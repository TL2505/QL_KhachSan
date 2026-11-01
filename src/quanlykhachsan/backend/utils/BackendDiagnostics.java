package quanlykhachsan.backend.utils;

import java.net.URI;
import java.sql.*;
import java.util.Properties;
import quanlykhachsan.backend.customer.GuestIdentityVaultDao;
import quanlykhachsan.frontend.api.ApiConfig;

/** Operator CLI: read-only checks; never imports seeds, alters schema or resets credentials. */
public final class BackendDiagnostics {
    private BackendDiagnostics() { }

    public static void main(String[] args) {
        String mode = args.length == 0 ? "all" : args[0];
        if (!java.util.Set.of("connection", "schema", "vault", "account", "startup", "all").contains(mode))
            throw new IllegalArgumentException("Mode: connection, schema, vault, account, startup, all");
        if (!run(mode)) System.exit(2);
    }

    public static boolean run(String mode) {
        boolean ok = true;
        GuestIdentityVaultDao vault = null;
        if (java.util.Set.of("vault", "startup", "all").contains(mode)) {
            try { vault = GuestIdentityVaultDao.configured(); pass("KEY_CONFIG", "AES/HMAC format accepted; database ownership still requires stored records"); }
            catch (IllegalStateException e) { fail("KEY_CONFIG", e.getMessage()); ok = false; }
            try { JwtTokenUtil.signingKey(); pass("JWT", "Signing configuration accepted"); }
            catch (Throwable e) { fail("JWT", "Stable JWT_SECRET of at least 32 UTF-8 bytes is required"); ok = false; }
        }
        try (Connection c = openConnection()) {
            c.setReadOnly(true);
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            c.setAutoCommit(false);
            try {
                try (Statement q = c.createStatement(); ResultSet r = q.executeQuery("SELECT DATABASE(), VERSION()")) {
                    if (!r.next() || r.getString(1) == null) throw new SQLException("No schema selected", "3D000");
                    pass("CONNECTION", "Selected schema=" + r.getString(1) + "; server=" + r.getString(2));
                }
                if (!"connection".equals(mode)) {
                    try { checkSchema(c, !"startup".equals(mode)); pass("SCHEMA", "Required columns readable" ); }
                    catch (SQLException e) { sqlFailure("SCHEMA", e); ok = false; }
                }
                if (vault != null) {
                    try {
                        boolean exists;
                        try (ResultSet tables = c.getMetaData().getTables(c.getCatalog(), null, "guest_identity_vault", new String[]{"TABLE"})) { exists = tables.next(); }
                        if (!exists && "startup".equals(mode)) {
                            pass("VAULT", "No vault table yet; startup migration must create and validate it");
                        } else {
                            long records = vault.verifyExistingRecords(c);
                            try (Statement q = c.createStatement(); ResultSet r = q.executeQuery("SELECT COUNT(*) FROM customers WHERE identity_card IS NOT NULL")) {
                                r.next(); if (r.getLong(1) != 0) throw new SQLException("Plain identities require backed-up explicit migration", "VLT03");
                            }
                            pass("VAULT", "Authenticated records=" + records + (records == 0 ? "; EMPTY: cannot prove historical keys" : "; AES and HMAC match stored data"));
                        }
                    } catch (SQLException e) { sqlFailure("VAULT", e); ok = false; }
                }
                if ("account".equals(mode) || "all".equals(mode)) {
                    try { checkAccount(c); pass("ACCOUNT", "Password, active status and authorized role accepted; HTTP login still required"); }
                    catch (IllegalStateException e) { fail("ACCOUNT", e.getMessage()); ok = false; }
                    catch (SQLException e) { sqlFailure("ACCOUNT", e); ok = false; }
                }
            } finally { c.rollback(); }
        } catch (SQLException e) { sqlFailure("CONNECTION", e); ok = false; }
        catch (RuntimeException e) { fail("CONFIG", "Invalid database configuration or unavailable DB secret file"); ok = false; }
        return ok;
    }

    public static Connection openConnection() throws SQLException {
        String url = ApiConfig.getJdbcUrl();
        URI target = URI.create(url.substring("jdbc:".length()));
        System.out.println("[TARGET] " + target.getHost() + ":" + (target.getPort() > 0 ? target.getPort() : 3306) + target.getPath());
        Properties props = new Properties();
        props.setProperty("user", ApiConfig.getDbUser());
        props.setProperty("password", ApiConfig.getDbPassword());
        props.setProperty("connectTimeout", "4000");
        props.setProperty("socketTimeout", "10000");
        return DriverManager.getConnection(url, props);
    }

    static void checkSchema(Connection c, boolean complete) throws SQLException {
        String[] queries = complete ? new String[]{
            "SELECT id,username,password,role_id,status,full_name,email,phone,customer_id,google_subject,verified_email,theme FROM users LIMIT 0",
            "SELECT id,name FROM roles LIMIT 0",
            "SELECT id,identity_card,identity_hash FROM customers LIMIT 0",
            "SELECT customer_id,encrypted_id_number,encrypted_raw_payload,face_embedding_blob FROM guest_identity_vault LIMIT 0",
            "SELECT user_id,device_key_hash,user_agent,last_ip,last_login_at,last_seen_at FROM user_login_devices LIMIT 0",
            "SELECT id,version,status FROM rooms LIMIT 0",
            "SELECT id,status,check_out_actual FROM bookings LIMIT 0"
        } : new String[]{"SELECT id,username,password,role_id FROM users LIMIT 0", "SELECT id,name FROM roles LIMIT 0", "SELECT id,identity_card FROM customers LIMIT 0"};
        for (String sql : queries) try (Statement q = c.createStatement(); ResultSet ignored = q.executeQuery(sql)) { }
        if (complete) {
            try (Statement q = c.createStatement(); ResultSet r = q.executeQuery("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='customers' AND CONSTRAINT_NAME='chk_no_plain_identity'")) {
                r.next(); if (r.getLong(1) != 1) throw new SQLException("Identity constraint is missing; run normal startup", "VLT03");
            }
        }
    }

    private static void checkAccount(Connection c) throws SQLException {
        String username = SecretConfiguration.required("HOTEL_VERIFY_USERNAME");
        String password = SecretConfiguration.required("HOTEL_VERIFY_PASSWORD");
        try (PreparedStatement q = c.prepareStatement("SELECT u.password,u.status,r.name FROM users u LEFT JOIN roles r ON r.id=u.role_id WHERE u.username=?")) {
            q.setString(1, username);
            try (ResultSet r = q.executeQuery()) {
                if (!r.next() || !SecurityUtil.verifyPassword(password, r.getString(1))) throw new IllegalStateException("Account missing, unsupported password hash, or password mismatch; no password was changed");
                if (!"active".equalsIgnoreCase(r.getString(2))) throw new IllegalStateException("Account is not active");
                String role = r.getString(3);
                if (role == null || !java.util.Set.of("admin","customer","staff","front_desk","housekeeping","maintenance","f&b","manager","gm","revenue_manager").contains(role.toLowerCase(java.util.Locale.ROOT)))
                    throw new IllegalStateException("Account role is not authorized");
            }
        }
    }

    private static void pass(String stage, String detail) { System.out.println("[PASS " + stage + "] " + detail); }
    private static void fail(String stage, String detail) { System.err.println("[FAIL " + stage + "] " + detail); }
    private static void sqlFailure(String stage, SQLException e) {
        String state = e.getSQLState();
        String detail = state != null && state.startsWith("VLT") ? e.getMessage()
            : e.getErrorCode() == 1045 ? "DB authentication denied; check DB_USER/DB_PASSWORD and grants"
            : e.getErrorCode() == 1049 ? "Unknown database; check DB_NAME/DB_URL"
            : state != null && state.startsWith("08") ? "DB connection unavailable; check server, address, port and network"
            : state != null && state.startsWith("42") ? "Schema/privilege mismatch; inspect migration manifest and database grants"
            : "Database check failed; inspect schema and server availability";
        fail(stage, "SQLState=" + state + "; code=" + e.getErrorCode() + "; " + detail);
    }
}
