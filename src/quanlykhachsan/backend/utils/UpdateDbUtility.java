package quanlykhachsan.backend.utils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * UpdateDbUtility — Chạy một lần khi server khởi động để đảm bảo
 * cấu trúc Database luôn đồng bộ với codebase (auto-migration an toàn).
 *
 * <p>Thiết kế idempotent: mỗi thao tác DDL đều kiểm tra trước khi thực thi
 * → an toàn khi gọi nhiều lần trên cùng một DB đang chạy.</p>
 *
 * <p><b>TASK-B1.1 — Phạm vi migration:</b></p>
 * <ul>
 *   <li>Thêm cột {@code version INT NOT NULL DEFAULT 0} vào bảng {@code rooms}</li>
 *   <li>Tạo composite index {@code idx_room_number_version} trên {@code rooms(room_number, version)}</li>
 *   <li>Tạo bảng {@code idempotency_keys} cùng index {@code created_at}</li>
 *   <li>Tạo bảng {@code folios} và {@code folio_transactions}</li>
 *   <li>Tạo bảng {@code audit_checkpoints}</li>
 * </ul>
 */
public class UpdateDbUtility {

    // ─────────────────────────────────────────────────────────────────────────
    // Entry point — được gọi từ Main.java trước khi server lắng nghe request
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("[UpdateDbUtility] Bắt đầu kiểm tra và cập nhật cấu trúc Database...");
        try (Connection conn = DBconn.getConnection()) {
            migrate(conn);
        } catch (SQLException e) {
            System.err.println("[UpdateDbUtility] ✘ Lỗi trong quá trình migration: " + e.getMessage());
            throw new IllegalStateException("Database migration failed; refusing to start with an unknown schema", e);
        }
    }

    /** Runs the idempotent schema migration on a caller-owned connection. */
    public static void migrate(Connection conn) throws SQLException {
        if (conn == null || conn.isClosed()) throw new SQLException("An open migration connection is required");

            // ── TASK tiền kỳ: đảm bảo các cột cũ đã được thêm ──────────────
            ensureColumn(conn, "bookings", "customer_type",
                    "VARCHAR(50) NOT NULL DEFAULT 'individual'");
            ensureColumn(conn, "bookings", "check_out_actual",
                    "DATETIME NULL DEFAULT NULL");
            ensureColumn(conn, "users", "theme",
                    "VARCHAR(20) NOT NULL DEFAULT 'light'");

            // ── TASK-B1.1: Cột version cho Optimistic Locking ────────────────
            ensureColumn(conn, "rooms", "version",
                    "INT NOT NULL DEFAULT 0");
            migrateBookingStatuses(conn);
            migrateRoomStatuses(conn);

            // ── TASK-B1.1: Composite index rooms(room_number, version) ────────
            // Xoá index đơn idx_room_number cũ (nếu tồn tại) trước khi tạo composite
            dropIndex(conn, "rooms", "idx_room_number");
            ensureIndex(conn, "rooms", "idx_room_number_version",
                    "INDEX idx_room_number_version (room_number, version)");

            // ── TASK-B1.1: Bảng idempotency_keys ────────────────────────────
            ensureTable(conn, "idempotency_keys",
                    "CREATE TABLE IF NOT EXISTS idempotency_keys (\n"
                    + "  id          VARCHAR(64)  NOT NULL PRIMARY KEY,\n"
                    + "  request_hash VARCHAR(64) NOT NULL,\n"
                    + "  response_body LONGTEXT   NOT NULL,\n"
                    + "  status_code INT          NOT NULL,\n"
                    + "  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,\n"
                    + "  INDEX idx_idempotency_created_at (created_at)\n"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            // ── TASK-B1.1: Bảng folios ──────────────────────────────────────
            // Phụ thuộc vào bảng bookings → phải tồn tại trước
            ensureTable(conn, "folios",
                    "CREATE TABLE IF NOT EXISTS folios (\n"
                    + "  id           INT           NOT NULL AUTO_INCREMENT PRIMARY KEY,\n"
                    + "  booking_id   INT           NOT NULL,\n"
                    + "  folio_type   ENUM('master','guest','incidental') NOT NULL DEFAULT 'master',\n"
                    + "  balance      DECIMAL(12,2) NOT NULL DEFAULT 0.00,\n"
                    + "  status       ENUM('open','closed','settled') NOT NULL DEFAULT 'open',\n"
                    + "  created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,\n"
                    + "  updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n"
                    + "  CONSTRAINT fk_folios_bookings\n"
                    + "    FOREIGN KEY (booking_id) REFERENCES bookings(id)\n"
                    + "    ON DELETE RESTRICT ON UPDATE CASCADE,\n"
                    + "  INDEX idx_folio_booking_id (booking_id),\n"
                    + "  INDEX idx_folio_status (status)\n"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            // ── TASK-B1.1: Bảng folio_transactions ──────────────────────────
            // Phụ thuộc vào bảng folios → phải tồn tại trước
            ensureTable(conn, "folio_transactions",
                    "CREATE TABLE IF NOT EXISTS folio_transactions (\n"
                    + "  id               INT           NOT NULL AUTO_INCREMENT PRIMARY KEY,\n"
                    + "  folio_id         INT           NOT NULL,\n"
                    + "  transaction_type ENUM('debit','credit') NOT NULL,\n"
                    + "  charge_type      VARCHAR(50)   NOT NULL,\n"
                    + "  description      VARCHAR(255),\n"
                    + "  amount           DECIMAL(12,2) NOT NULL,\n"
                    + "  business_date    DATE          NOT NULL,\n"
                    + "  reference_id     VARCHAR(64)   NULL,\n"
                    + "  created_at       DATETIME DEFAULT CURRENT_TIMESTAMP,\n"
                    + "  CONSTRAINT fk_folio_transactions_folios\n"
                    + "    FOREIGN KEY (folio_id) REFERENCES folios(id)\n"
                    + "    ON DELETE CASCADE ON UPDATE CASCADE,\n"
                    + "  INDEX idx_ft_folio_id (folio_id),\n"
                    + "  INDEX idx_ft_business_date (business_date)\n"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            // ── TASK-B1.1: Bảng audit_checkpoints ───────────────────────────
            ensureTable(conn, "audit_checkpoints",
                    "CREATE TABLE IF NOT EXISTS audit_checkpoints (\n"
                    + "  id              INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,\n"
                    + "  audit_date      DATE         NOT NULL,\n"
                    + "  step_number     INT          NOT NULL,\n"
                    + "  step_name       VARCHAR(100) NOT NULL,\n"
                    + "  status          ENUM('pending','running','completed','failed') NOT NULL DEFAULT 'pending',\n"
                    + "  processed_count INT          NOT NULL DEFAULT 0,\n"
                    + "  error_message   TEXT         NULL,\n"
                    + "  started_at      DATETIME     NULL,\n"
                    + "  completed_at    DATETIME     NULL,\n"
                    + "  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,\n"
                    + "  UNIQUE KEY uq_audit_date_step (audit_date, step_number),\n"
                    + "  INDEX idx_checkpoint_audit_date (audit_date),\n"
                    + "  INDEX idx_checkpoint_status (status)\n"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            ensureTable(conn, "hotel_business_state",
                    "CREATE TABLE IF NOT EXISTS hotel_business_state (id TINYINT NOT NULL PRIMARY KEY, business_date DATE NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB");
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("INSERT IGNORE INTO hotel_business_state (id,business_date) VALUES (1,CURRENT_DATE())");
            }
            ensureTable(conn, "daily_business_reports",
                    "CREATE TABLE IF NOT EXISTS daily_business_reports (audit_date DATE NOT NULL PRIMARY KEY, total_revenue DECIMAL(14,2) NOT NULL DEFAULT 0.00, room_revenue DECIMAL(14,2) NOT NULL DEFAULT 0.00, occupied_rooms INT NOT NULL DEFAULT 0, available_rooms INT NOT NULL DEFAULT 0, occupancy_rate DECIMAL(7,2) NOT NULL DEFAULT 0.00, adr DECIMAL(14,2) NOT NULL DEFAULT 0.00, revpar DECIMAL(14,2) NOT NULL DEFAULT 0.00, created_at DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            migratePublicPortal(conn);
            migrateFolioAccounting(conn);
            migrateHousekeeping(conn);
            migratePromotionalWallet(conn);
            ensureColumn(conn,"daily_business_reports","snapshot_json","LONGTEXT NULL");
            System.out.println("[UpdateDbUtility] ✔ Cấu trúc Database đã được kiểm tra và đồng bộ.");
    }

    /** Refuses lossy ENUM conversion and executes the versioned portal script. */
    public static void migratePublicPortal(Connection conn) throws SQLException {
        try (Statement query = conn.createStatement();
             ResultSet rows = query.executeQuery("SELECT COUNT(*) FROM bookings WHERE customer_type IS NOT NULL "
                     + "AND customer_type NOT IN ('OTA','Walk-in','Corporate','Web')")) {
            rows.next();
            if (rows.getLong(1) != 0) throw new SQLException(
                    "Week 5 migration requires operator review of unsupported booking customer types");
        }
        runMigrationScript(conn, java.nio.file.Path.of("database", "migrations", "v2_0_week5_public_portal.sql"));
        // Online guests have not supplied a passport yet; preserve missing identity
        // as NULL rather than inventing an identification number.
        try (ResultSet tables = conn.getMetaData().getTables(conn.getCatalog(), null, "customers", new String[]{"TABLE"})) {
            if (tables.next()) {
                try (Statement statement = conn.createStatement()) {
                    statement.execute("ALTER TABLE customers MODIFY identity_card VARCHAR(20) NULL");
                }
            }
        }
    }

    public static void migrateFolioAccounting(Connection c) throws SQLException {
        boolean widen=false;
        try(ResultSet columns=c.getMetaData().getColumns(c.getCatalog(),null,"folios","id")){widen=columns.next() && columns.getInt("DATA_TYPE")!=java.sql.Types.BIGINT;}
        try(ResultSet columns=c.getMetaData().getColumns(c.getCatalog(),null,"folio_transactions","folio_id")){widen|=columns.next() && columns.getInt("DATA_TYPE")!=java.sql.Types.BIGINT;}
        if(widen) {
            try(Statement s=c.createStatement()) {
                s.execute("ALTER TABLE folio_transactions DROP FOREIGN KEY IF EXISTS fk_folio_transactions_folios");
                s.execute("ALTER TABLE folios MODIFY id BIGINT NOT NULL AUTO_INCREMENT");
                s.execute("ALTER TABLE folio_transactions MODIFY folio_id BIGINT NOT NULL");
                s.execute("ALTER TABLE folio_transactions ADD CONSTRAINT fk_folio_transactions_folios FOREIGN KEY(folio_id) REFERENCES folios(id) ON DELETE CASCADE ON UPDATE CASCADE");
            }
        }
        // Recover a migration interrupted after both columns widened but before FK recreation.
        try(PreparedStatement q=c.prepareStatement("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='folio_transactions' AND CONSTRAINT_NAME='fk_folio_transactions_folios'")) {
            try(ResultSet r=q.executeQuery()){r.next();if(r.getInt(1)==0)try(Statement s=c.createStatement()){s.execute("ALTER TABLE folio_transactions ADD CONSTRAINT fk_folio_transactions_folios FOREIGN KEY(folio_id) REFERENCES folios(id) ON DELETE CASCADE ON UPDATE CASCADE");}}
        }
        ensureColumn(c,"folios","code","VARCHAR(64) NULL");
        try(Statement s=c.createStatement()) {
            s.executeUpdate("UPDATE folios SET code=CONCAT('FOLIO-',UPPER(folio_type),'-',id) WHERE code IS NULL");
            s.execute("ALTER TABLE folios MODIFY code VARCHAR(64) NOT NULL DEFAULT (CONCAT('FOLIO-',UUID()))");
        }
        ensureIndex(c,"folios","uq_folio_code","UNIQUE INDEX uq_folio_code(code)");
        try(PreparedStatement q=c.prepareStatement("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='folios' AND CONSTRAINT_NAME='chk_folio_nonnegative'")) {
            try(ResultSet r=q.executeQuery()){r.next();if(r.getInt(1)==0)try(Statement s=c.createStatement()){s.execute("ALTER TABLE folios ADD CONSTRAINT chk_folio_nonnegative CHECK(balance>=0.00)");}}
        }
        runMigrationScript(c,java.nio.file.Path.of("database","migrations","v2_0_week6_vietqr_accounting.sql"));
    }

    public static void migrateHousekeeping(Connection c)throws SQLException {
        runMigrationScript(c,java.nio.file.Path.of("database","migrations","v2_0_week7_housekeeping_minibar.sql"));
        ensureColumn(c,"rooms","maintenance_until","DATETIME NULL");
        try(ResultSet tables=c.getMetaData().getTables(c.getCatalog(),null,"roles",new String[]{"TABLE"})){if(tables.next())try(PreparedStatement q=c.prepareStatement("INSERT IGNORE INTO roles(name,description) VALUES (?,?)")){for(String role:new String[]{"housekeeping","maintenance","f&b"}){q.setString(1,role);q.setString(2,"Phase 2 departmental role");q.executeUpdate();}}}
    }

    public static void migratePromotionalWallet(Connection c)throws SQLException {
        try(ResultSet tables=c.getMetaData().getTables(c.getCatalog(),null,"promotions",new String[]{"TABLE"})){if(!tables.next())return;}
        ensureColumn(c,"promotions","code","VARCHAR(50) NULL");
        ensureColumn(c,"promotions","max_discount","DECIMAL(12,2) NULL");
        ensureColumn(c,"promotions","minimum_spend","DECIMAL(12,2) NULL");
        ensureColumn(c,"promotions","max_uses_per_user","INT NULL");
        ensureIndex(c,"promotions","uq_promotion_code","UNIQUE INDEX uq_promotion_code(code)");
        ensureColumn(c,"customers","company_name","VARCHAR(150) NULL");
        ensureColumn(c,"customers","tax_code","VARCHAR(20) NULL");
        runMigrationScript(c,java.nio.file.Path.of("database","migrations","v2_0_week8_promotions.sql"));
    }

    /** Only checked-in simple DDL scripts are accepted; no request controls this path. */
    private static void runMigrationScript(Connection conn, java.nio.file.Path script) throws SQLException {
        try {
            StringBuilder sql = new StringBuilder();
            for (String line : java.nio.file.Files.readAllLines(script, java.nio.charset.StandardCharsets.UTF_8)) {
                if (!line.stripLeading().startsWith("--")) sql.append(line).append('\n');
            }
            try (Statement statement = conn.createStatement()) {
                for (String command : sql.toString().split(";")) {
                    if (!command.isBlank()) statement.execute(command.trim());
                }
            }
        } catch (java.io.IOException e) {
            throw new SQLException("Required migration script is unavailable: " + script.getFileName(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private DDL helpers — tất cả đều idempotent và an toàn khi gọi nhiều lần
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Thêm cột vào bảng nếu chưa tồn tại.
     *
     * @param conn       Connection tới DB (caller chịu trách nhiệm đóng)
     * @param table      Tên bảng (case-insensitive trên MariaDB)
     * @param column     Tên cột cần thêm
     * @param definition Kiểu dữ liệu và ràng buộc, ví dụ: {@code "INT NOT NULL DEFAULT 0"}
     * @throws SQLException nếu có lỗi JDBC không thể phục hồi
     */
    private static void ensureColumn(Connection conn, String table, String column,
                                     String definition) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, table, column)) {
            if (rs.next()) {
                System.out.println("[UpdateDbUtility]   ✓ Cột `" + table + "." + column + "` đã tồn tại, bỏ qua.");
            } else {
                String sql = "ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition;
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(sql);
                    System.out.println("[UpdateDbUtility]   ➕ Đã thêm cột `" + table + "." + column + "`.");
                }
            }
        }
    }

    private static void migrateRoomStatuses(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE rooms MODIFY status ENUM('"
                    + "available','booked','occupied','maintenance','cleaning','out_of_service',"
                    + "'vacant_clean','vacant_dirty','occupied_clean','occupied_dirty','out_of_order','reserved')"
                    + " NOT NULL DEFAULT 'vacant_clean'");
            stmt.executeUpdate("UPDATE rooms SET status = CASE status "
                    + "WHEN 'available' THEN 'vacant_clean' "
                    + "WHEN 'booked' THEN 'reserved' "
                    + "WHEN 'occupied' THEN 'occupied_clean' "
                    + "WHEN 'maintenance' THEN 'out_of_order' "
                    + "WHEN 'cleaning' THEN 'vacant_dirty' ELSE status END");
            stmt.executeUpdate("ALTER TABLE rooms MODIFY status ENUM('"
                    + "vacant_clean','vacant_dirty','occupied_clean','occupied_dirty','out_of_order','out_of_service','reserved')"
                    + " NOT NULL DEFAULT 'vacant_clean'");
        }
    }

    private static void migrateBookingStatuses(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE bookings MODIFY status ENUM('"
                    + "pending','confirmed','checked_in','checked_out','cancelled','no_show')"
                    + " DEFAULT 'pending'");
        }
    }

    /**
     * Tạo bảng nếu chưa tồn tại.
     * Sử dụng {@code INFORMATION_SCHEMA.TABLES} thay vì {@code CREATE TABLE IF NOT EXISTS}
     * để có log rõ ràng và phân biệt hành vi "bỏ qua" vs "tạo mới".
     *
     * @param conn      Connection tới DB
     * @param tableName Tên bảng cần kiểm tra/tạo
     * @param createDdl Câu lệnh DDL đầy đủ (bao gồm {@code CREATE TABLE IF NOT EXISTS ...})
     * @throws SQLException nếu có lỗi JDBC không thể phục hồi
     */
    private static void ensureTable(Connection conn, String tableName,
                                    String createDdl) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(conn.getCatalog(), null, tableName, new String[]{"TABLE"})) {
            if (rs.next()) {
                System.out.println("[UpdateDbUtility]   ✓ Bảng `" + tableName + "` đã tồn tại, bỏ qua.");
            } else {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(createDdl);
                    System.out.println("[UpdateDbUtility]   ➕ Đã tạo bảng `" + tableName + "`.");
                }
            }
        }
    }

    /**
     * Tạo index nếu chưa tồn tại trên bảng đã cho.
     *
     * @param conn        Connection tới DB
     * @param table       Tên bảng chứa index
     * @param indexName   Tên index cần kiểm tra
     * @param indexClause Mệnh đề ALTER TABLE hoàn chỉnh, ví dụ:
     *                    {@code "INDEX idx_room_number_version (room_number, version)"}
     * @throws SQLException nếu có lỗi JDBC không thể phục hồi
     */
    private static void ensureIndex(Connection conn, String table, String indexName,
                                    String indexClause) throws SQLException {
        if (indexExists(conn, table, indexName)) {
            System.out.println("[UpdateDbUtility]   ✓ Index `" + indexName + "` trên `"
                    + table + "` đã tồn tại, bỏ qua.");
            return;
        }
        String sql = "ALTER TABLE `" + table + "` ADD " + indexClause;
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            System.out.println("[UpdateDbUtility]   ➕ Đã tạo index `" + indexName + "` trên `" + table + "`.");
        }
    }

    /**
     * Xoá một index khỏi bảng nếu tồn tại.
     * Dùng để thay thế index đơn cũ bằng composite index mới.
     *
     * @param conn      Connection tới DB
     * @param table     Tên bảng
     * @param indexName Tên index cần xoá
     * @throws SQLException nếu có lỗi JDBC không thể phục hồi
     */
    private static void dropIndex(Connection conn, String table, String indexName)
            throws SQLException {
        if (!indexExists(conn, table, indexName)) {
            System.out.println("[UpdateDbUtility]   ✓ Index `" + indexName + "` không tồn tại, không cần xoá.");
            return;
        }
        String sql = "ALTER TABLE `" + table + "` DROP INDEX `" + indexName + "`";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            System.out.println("[UpdateDbUtility]   🗑️ Đã xoá index cũ `" + indexName + "` trên `" + table + "`.");
        }
    }

    /**
     * Kiểm tra sự tồn tại của một index bằng {@code INFORMATION_SCHEMA.STATISTICS}.
     *
     * @param conn      Connection tới DB
     * @param table     Tên bảng
     * @param indexName Tên index cần kiểm tra
     * @return {@code true} nếu index đã tồn tại
     * @throws SQLException nếu có lỗi JDBC
     */
    private static boolean indexExists(Connection conn, String table,
                                       String indexName) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.STATISTICS "
                   + "WHERE table_schema = DATABASE() "
                   + "  AND table_name = ? "
                   + "  AND index_name = ? "
                   + "LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
