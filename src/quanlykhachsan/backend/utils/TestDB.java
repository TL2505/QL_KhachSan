package quanlykhachsan.backend.utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import quanlykhachsan.backend.utils.DBconn;

public class TestDB {
    public static void main(String[] args) {
        System.out.println("Starting TestDB...");
        try (Connection con = DBconn.getConnection()) {
            System.out.println("Connection OK!");
            String query = "SELECT * FROM users WHERE username = 'admin_main'";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("User found: " + rs.getString("username"));
                        System.out.println("Password: " + rs.getString("password"));
                        System.out.println("Role ID: " + rs.getInt("role_id"));
                        System.out.println("Status: " + rs.getString("status"));
                        System.out.println("Full Name: " + rs.getString("full_name"));
                        System.out.println("Email: " + rs.getString("email"));
                        System.out.println("Phone: " + rs.getString("phone"));
                        try {
                            int cid = rs.getInt("customer_id");
                            System.out.println("Customer ID: " + (rs.wasNull() ? "null" : cid));
                        } catch (Exception e) {
                            System.out.println("Error reading customer_id: " + e.getMessage());
                        }
                    } else {
                        System.out.println("User 'admin_main' not found in DB.");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
