package quanlykhachsan.backend.utils;

import java.sql.Connection;
import java.sql.Statement;

public class UpdateDbUtility {
    public static void main(String[] args) {
        String[] sqlCommands = {
            "ALTER TABLE users ADD COLUMN full_name VARCHAR(100) NULL AFTER status",
            "ALTER TABLE users ADD COLUMN email VARCHAR(100) NULL AFTER full_name",
            "ALTER TABLE users ADD COLUMN phone VARCHAR(20) NULL AFTER email",
            "UPDATE users SET full_name = 'Administrator', email = 'admin@hotel.com', phone = '0987654321' WHERE username = 'admin_main'",
            "UPDATE users SET full_name = 'Nguyen Van A', email = 'nva@hotel.com', phone = '0123456789' WHERE username = 'staff_01'",
            "ALTER TABLE bookings MODIFY status VARCHAR(50) DEFAULT 'pending'",
            "ALTER TABLE payments MODIFY payment_method VARCHAR(50) NOT NULL",
            "ALTER TABLE payments DROP FOREIGN KEY fk_payments_invoices" 
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
