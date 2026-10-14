package quanlykhachsan.backend.utils;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Backward-compatibility alias delegating to DatabaseConnection.
 * Ensures all existing DAOs transparently utilize the central HikariCP connection pool.
 */
public class DBconn {

    public static Connection getConnection() throws SQLException {
        return DatabaseConnection.getConnection();
    }
}
