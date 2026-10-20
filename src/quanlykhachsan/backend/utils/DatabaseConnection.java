package quanlykhachsan.backend.utils;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

/**
 * Shared HikariCP connection pool delegating to intelligent DBconn.
 * Guarantees singleton pool governance, 5000ms leak detection, and auto-detecting host parity.
 */
public final class DatabaseConnection {

    private static final DatabaseConnection INSTANCE = new DatabaseConnection();

    private DatabaseConnection() { }

    public static DatabaseConnection getInstance() {
        return INSTANCE;
    }

    public HikariDataSource getDataSource() {
        return DBconn.getDataSource();
    }

    public static Connection getConnection() throws SQLException {
        return DBconn.getConnection();
    }

    public static void setCustomDataSource(DataSource ds) {
        DBconn.setCustomDataSource(ds);
    }

    public static void resetToDefault() {
        DBconn.resetToDefault();
    }

    public static void close() {
        DBconn.close();
    }

    /** Read-only pool telemetry for load verification and operational health. */
    public static int activeConnections() {
        try {
            HikariDataSource ds = DBconn.getDataSource();
            return ds != null && ds.getHikariPoolMXBean() != null ? ds.getHikariPoolMXBean().getActiveConnections() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public static int awaitingConnections() {
        try {
            HikariDataSource ds = DBconn.getDataSource();
            return ds != null && ds.getHikariPoolMXBean() != null ? ds.getHikariPoolMXBean().getThreadsAwaitingConnection() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
