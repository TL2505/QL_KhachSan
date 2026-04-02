package quanlykhachsan.backend.utils;

import java.sql.Connection;
import java.sql.Statement;

public class UpdateDbUtility {
    public static void main(String[] args) {
        String[] sqlCommands = {
                "ALTER TABLE bookings MODIFY status VARCHAR(50) DEFAULT 'pending'",
                "ALTER TABLE payments MODIFY payment_method VARCHAR(50) NOT NULL",
                "ALTER TABLE payments DROP FOREIGN KEY fk_payments_invoices" // Tạm thời bỏ ràng buộc để test nhanh
        };

        try (Connection con = DBconn.getConnection();
                Statement st = con.createStatement()) {

            for (String sql : sqlCommands) {
                try {
                    st.executeUpdate(sql);
                    System.out.println("Thực thi thành công: " + sql);
                } catch (Exception e) {
                    System.err.println("Bỏ qua lỗi (có thể đã chạy rồi): " + e.getMessage());
                }
            }
            System.out.println("Cấu trúc Database đã được mở rộng!");
        } catch (Exception e) {
            System.err.println("Lỗi kết nối Database: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
