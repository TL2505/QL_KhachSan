package quanlykhachsan.backend.utils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * UpdateDbUtility - Chạy khi server khởi động để đảm bảo
 * cấu trúc Database luôn đồng bộ với code (auto-migration).
 *
 * Mỗi ALTER TABLE đều kiểm tra trước khi thêm → an toàn khi chạy nhiều lần.
 */
public class UpdateDbUtility {

    public static void main(String[] args) {
        System.out.println("[UpdateDbUtility] Bắt đầu kiểm tra và cập nhật cấu trúc Database...");
        try (Connection conn = DBconn.getConnection()) {
            ensureColumn(conn, "bookings", "customer_type",
                    "VARCHAR(50) NOT NULL DEFAULT 'individual'");
            ensureColumn(conn, "bookings", "check_out_actual",
                    "DATETIME NULL DEFAULT NULL");
            ensureColumn(conn, "users", "theme",
                    "VARCHAR(20) NOT NULL DEFAULT 'light'");
            System.out.println("[UpdateDbUtility] ✔ Cấu trúc Database đã được kiểm tra xong.");
        } catch (SQLException e) {
            System.err.println("[UpdateDbUtility] ✘ Lỗi kết nối Database: " + e.getMessage());
            // Không ném exception — server vẫn tiếp tục khởi động
        }
    }

    /**
     * Thêm cột vào bảng nếu chưa tồn tại.
     *
     * @param conn       Connection tới DB
     * @param table      Tên bảng
     * @param column     Tên cột cần thêm
     * @param definition Kiểu dữ liệu và ràng buộc (VD: "VARCHAR(50) NOT NULL DEFAULT 'x'")
     */
    private static void ensureColumn(Connection conn, String table, String column, String definition)
            throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, table, column)) {
            if (rs.next()) {
                System.out.println("[UpdateDbUtility]   ✓ Cột `" + table + "." + column + "` đã tồn tại.");
            } else {
                String sql = "ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition;
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(sql);
                    System.out.println("[UpdateDbUtility]   ➕ Đã thêm cột `" + table + "." + column + "`.");
                }
            }
        }
    }
}
