package quanlykhachsan.backend.utils;

import java.sql.*;
import quanlykhachsan.backend.utils.DBconn;

public class DbCheckRooms {
    public static void main(String[] args) {
        System.out.println("--- CHECK ROOMS AND TYPES ---");
        try (Connection con = DBconn.getConnection();
             Statement st = con.createStatement()) {
            
            System.out.println("\n[TABLE: room_types]");
            try (ResultSet rs = st.executeQuery("SELECT * FROM room_types")) {
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.println("ID: " + rs.getInt("id") + " | Name: [" + rs.getString("name") + "]");
                }
                if (!found) System.out.println("!!! room_types is EMPTY !!!");
            }

            System.out.println("\n[TABLE: rooms]");
            try (ResultSet rs = st.executeQuery("SELECT * FROM rooms")) {
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.println("ID: " + rs.getInt("id") + " | Number: [" + rs.getString("room_number") + "] | Status: [" + rs.getString("status") + "]");
                }
                if (!found) System.out.println("!!! rooms is EMPTY !!!");
            }
            
        } catch (Exception e) {
            System.err.println("DB ERROR: " + e.getMessage());
        }
    }
}
