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

            System.out.println("[UpdateDbUtility] ✔ Cấu trúc Database đã được kiểm tra và đồng bộ.");
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
                    + "WHEN 'out_of_service' THEN 'out_of_order' "
                    + "WHEN 'cleaning' THEN 'vacant_dirty' ELSE status END");
            stmt.executeUpdate("ALTER TABLE rooms MODIFY status ENUM('"
                    + "vacant_clean','vacant_dirty','occupied_clean','occupied_dirty','out_of_order','reserved')"
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
