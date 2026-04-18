package quanlykhachsan.backend.utils;

import java.sql.*;
import quanlykhachsan.backend.utils.DBconn;

public class DbDebug {
    public static void main(String[] args) {
        System.out.println("--- CHECK ROLES TABLE ---");
        try (Connection con = DBconn.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM roles")) {
            
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.println("ID: " + rs.getInt("id") + " | Name: [" + rs.getString("name") + "]");
            }
            if (!found) {
                System.out.println("!!! ROLES TABLE IS EMPTY !!!");
            }
        } catch (Exception e) {
            System.err.println("DB ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
