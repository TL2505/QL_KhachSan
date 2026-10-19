package quanlykhachsan.database;

import java.sql.Connection;
import java.sql.SQLException;
import quanlykhachsan.backend.utils.DatabaseConnection;

/**
 * Compatibility wrapper for DBConnection in quanlykhachsan.database package.
 */
public final class DBConnection {
    public static final String DEFAULT_JDBC_URL = quanlykhachsan.frontend.api.ApiConfig.DEFAULT_JDBC_URL;
    public static final String DEFAULT_API_BASE_URL = quanlykhachsan.frontend.api.ApiConfig.DEFAULT_API_BASE_URL;

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
}
