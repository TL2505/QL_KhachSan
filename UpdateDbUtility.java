import quanlykhachsan.backend.utils.DBconn;
import java.sql.Connection;
import java.sql.Statement;

public class UpdateDbUtility {
    public static void main(String[] args) {
        try (Connection con = DBconn.getConnection(); 
             Statement stmt = con.createStatement()) {
            
            String sqlFullName = "ALTER TABLE users ADD COLUMN full_name VARCHAR(100) NULL AFTER status;";
            String sqlEmail = "ALTER TABLE users ADD COLUMN email VARCHAR(100) NULL AFTER full_name;";
            String sqlPhone = "ALTER TABLE users ADD COLUMN phone VARCHAR(20) NULL AFTER email;";
            
            try { stmt.execute(sqlFullName); System.out.println("Modified full_name"); } catch(Exception e) { System.out.println("full_name may exist: " + e.getMessage()); }
            try { stmt.execute(sqlEmail); System.out.println("Modified email"); } catch(Exception e) { System.out.println("email may exist: " + e.getMessage()); }
            try { stmt.execute(sqlPhone); System.out.println("Modified phone"); } catch(Exception e) { System.out.println("phone may exist: " + e.getMessage()); }
            
            String sqlUpdateAdmin = "UPDATE users SET full_name = 'Administrator', email = 'admin@hotel.com', phone = '0987654321' WHERE username = 'admin_main';";
            String sqlUpdateStaff1 = "UPDATE users SET full_name = 'Nguyen Van A', email = 'nva@hotel.com', phone = '0123456789' WHERE username = 'staff_01';";
            
            stmt.executeUpdate(sqlUpdateAdmin);
            stmt.executeUpdate(sqlUpdateStaff1);
            
            System.out.println("DB update complete.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
