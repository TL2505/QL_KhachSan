package quanlykhachsan.backend.utils;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Compatibility wrapper for DBConnection connecting to the centralized PMS Database.
 */
public final class DBConnection {
    public static final String DEFAULT_JDBC_URL = "jdbc:mysql://192.168.56.102:3306/hotel_prod_db?useSSL=false&serverTimezone=UTC";
    public static final String DEFAULT_API_BASE_URL = "http://192.168.56.102:8081/api/v1";

    private DBConnection() {}

    public static String getJdbcUrl() {
        return quanlykhachsan.frontend.api.ApiConfig.getJdbcUrl();
    }

    public static String getApiBaseUrl() {
        return quanlykhachsan.frontend.api.ApiConfig.getBaseUrl();
    }

    public static Connection getConnection() throws SQLException {
        return DatabaseConnection.getConnection();
    }

    public static void close() {
        DatabaseConnection.close();
    }

    public static int activeConnections() {
        return DatabaseConnection.activeConnections();
    }

    public static int awaitingConnections() {
        return DatabaseConnection.awaitingConnections();
    }
}
