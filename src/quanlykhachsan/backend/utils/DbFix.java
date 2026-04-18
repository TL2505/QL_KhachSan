package quanlykhachsan.backend.utils;

import java.sql.*;
import quanlykhachsan.backend.utils.DBconn;

public class DbFix {
    public static void main(String[] args) {
        System.out.println("--- SEEDING MISSING ROLES ---");
        try (Connection con = DBconn.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "INSERT INTO roles (name, description) SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = ?)")) {
            
            ps.setString(1, "customer");
            ps.setString(2, "Khach hang");
            ps.setString(3, "customer");
            int rows = ps.executeUpdate();
            
            if (rows > 0) {
                System.out.println(">>> Da them quyen 'customer' thanh cong!");
            } else {
                System.out.println(">>> Quyen 'customer' da ton tai hoac khong the them.");
            }
        } catch (Exception e) {
            System.err.println("DB ERROR: " + e.getMessage());
        }
    }
}
