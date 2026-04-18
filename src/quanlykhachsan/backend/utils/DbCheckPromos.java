package quanlykhachsan.backend.utils;

import java.sql.*;
import quanlykhachsan.backend.utils.DBconn;

public class DbCheckPromos {
    public static void main(String[] args) {
        System.out.println("--- CHECK PROMOTIONS ---");
        try (Connection con = DBconn.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM promotions")) {
            
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.println("ID: " + rs.getInt("id") + " | Name: [" + rs.getString("name") + "]");
            }
            if (!found) System.out.println("!!! promotions is EMPTY !!!");
            
        } catch (Exception e) {
            System.err.println("DB ERROR: " + e.getMessage());
        }
    }
}
