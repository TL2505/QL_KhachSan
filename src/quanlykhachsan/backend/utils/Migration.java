package quanlykhachsan.backend.utils;

import java.sql.Connection;
import java.sql.Statement;
import quanlykhachsan.backend.utils.DBconn;

public class Migration {
    public static void main(String[] args) {
        try (Connection conn = DBconn.getConnection();
             Statement stmt = conn.createStatement()) {
            
            System.out.println("Adding IS_VIP...");
            try {
                stmt.execute("ALTER TABLE customers ADD COLUMN is_vip BOOLEAN DEFAULT FALSE;");
                System.out.println("Added is_vip success.");
            } catch (Exception e) {
                System.out.println("is_vip might already exist: " + e.getMessage());
            }

            System.out.println("Adding customer_id to users...");
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS customer_id INT;");
                System.out.println("Added customer_id success.");
            } catch (Exception e) {
                System.out.println("Error adding customer_id: " + e.getMessage());
            }

            System.out.println("Creating reviews table...");
            String createReviews = "CREATE TABLE IF NOT EXISTS reviews (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "customer_id INT, " +
                "room_id INT, " +
                "rating INT CHECK (rating >= 1 AND rating <= 5), " +
                "comment TEXT, " +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (customer_id) REFERENCES customers(id), " +
                "FOREIGN KEY (room_id) REFERENCES rooms(id)" +
            ") ENGINE=InnoDB;";
            stmt.execute(createReviews);
            
            System.out.println("Migration complete.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
