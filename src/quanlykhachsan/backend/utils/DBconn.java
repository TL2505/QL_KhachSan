package quanlykhachsan.backend.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBconn {
    private static final String URL = "jdbc:mysql://localhost:3306/hotel_prod_db?useUnicode=true&characterEncoding=UTF-8";
    private static final String USER = "root"; // Đổi theo cấu hình máy bạn
    private static final String PASSWORD = ""; // Đổi theo cấu hình máy bạn

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver not found.", e);
        }
    }
}
