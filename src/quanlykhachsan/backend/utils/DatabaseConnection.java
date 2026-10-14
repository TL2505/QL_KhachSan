package quanlykhachsan.backend.utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Thread-safe Singleton managing the HikariCP Connection Pool (TASK-A1.1 / TASK-D1.3 / TASK-A4.1).
 * <p>
 * Configuration constraints:
 * - maximumPoolSize: 20
 * - minimumIdle: 5
 * - idleTimeout: 300000ms (5 minutes)
 * - connectionTimeout: 30000ms (30 seconds)
 * - leakDetectionThreshold: 5000ms (5 seconds)
 */
public class DatabaseConnection {

    private static volatile DatabaseConnection instance;
    private final HikariDataSource dataSource;

    private static String getEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    private DatabaseConnection() {
        this(createDefaultDataSource());
    }

    private DatabaseConnection(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    private static HikariDataSource createDefaultDataSource() {
        String envUrl = System.getenv("DB_URL");
        String jdbcUrl;
        if (envUrl != null && !envUrl.trim().isEmpty()) {
            jdbcUrl = envUrl.trim();
        } else {
            String host = getEnv("DB_HOST", "localhost");
            String port = getEnv("DB_PORT", "3306");
            String dbName = getEnv("DB_NAME", "hotel_prod_db");
            jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + dbName
                    + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true&useSSL=false";
        }
        // Determine robust default credentials: if targeting container MariaDB, default to hotel_user/hotel_pass
        boolean isContainerDb = jdbcUrl.contains("mariadb") || jdbcUrl.contains("hotel_db");
        String defaultUser = isContainerDb ? "hotel_user" : "root";
        String defaultPass = isContainerDb ? "hotel_pass" : "";

        // Read user with priority: DB_USER -> MYSQL_USER -> defaultUser
        String user = getEnv("DB_USER", getEnv("MYSQL_USER", defaultUser));
        // Read password with priority: DB_PASSWORD -> MYSQL_PASSWORD -> MYSQL_ROOT_PASSWORD -> defaultPass
        String password = getEnv("DB_PASSWORD", getEnv("MYSQL_PASSWORD", getEnv("MYSQL_ROOT_PASSWORD", defaultPass)));

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // Governance settings per TASK-A1.1
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(30000);
        config.setLeakDetectionThreshold(5000);
        config.setInitializationFailTimeout(-1);
        config.setPoolName("HotelPMS-HikariPool");

        // Performance optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        return new HikariDataSource(config);
    }

    public static DatabaseConnection getInstance() {
        if (instance == null) {
            synchronized (DatabaseConnection.class) {
                if (instance == null) {
                    instance = new DatabaseConnection();
                }
            }
        }
        return instance;
    }

    private static volatile boolean isCustomInjected = false;

    /**
     * Checks if a custom DataSource has been injected (e.g. for testing).
     */
    public static boolean isCustomInjected() {
        return isCustomInjected;
    }

    /**
     * Fast non-blocking connectivity probe (250ms socket check) to prevent 30-second Hikari
     * timeouts when running in isolated container or mock environments.
     */
    public static boolean isDatabaseAvailable() {
        if (isCustomInjected) {
            return true;
        }
        String host = getEnv("DB_HOST", "localhost");
        String portStr = getEnv("DB_PORT", "3306");
        int port = 3306;

        String dbUrl = System.getenv("DB_URL");
        if (dbUrl != null && dbUrl.startsWith("jdbc:mysql://")) {
            try {
                String clean = dbUrl.substring("jdbc:mysql://".length());
                int slashIdx = clean.indexOf('/');
                String hostPort = slashIdx > 0 ? clean.substring(0, slashIdx) : clean;
                if (hostPort.contains(":")) {
                    String[] parts = hostPort.split(":");
                    host = parts[0];
                    port = Integer.parseInt(parts[1]);
                } else if (!hostPort.isEmpty()) {
                    host = hostPort;
                }
            } catch (Exception ignored) {}
        } else if (portStr != null && !portStr.isEmpty()) {
            try { port = Integer.parseInt(portStr); } catch (NumberFormatException ignored) {}
        }

        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress(host, port), 250);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Injects a custom or mock DataSource for isolated testing environments (TASK-A4.1).
     */
    public static synchronized void setCustomDataSource(HikariDataSource customDataSource) {
        if (instance != null) {
            instance.shutdown();
        }
        isCustomInjected = (customDataSource != null);
        instance = new DatabaseConnection(customDataSource);
    }

    /**
     * Resets the singleton instance back to the default production DataSource.
     */
    public static synchronized void resetToDefault() {
        isCustomInjected = false;
        closePool();
    }

    /**
     * Obtains a pooled connection from HikariCP.
     * All calls MUST be enclosed in try-with-resources.
     */
    public static Connection getConnection() throws SQLException {
        try {
            return getInstance().dataSource.getConnection();
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("Unable to acquire database connection: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the HikariPoolMXBean for runtime health and metric inspection.
     */
    public static HikariPoolMXBean getPoolMXBean() {
        try {
            HikariDataSource ds = getInstance().dataSource;
            if (ds != null && !ds.isClosed()) {
                return ds.getHikariPoolMXBean();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static int getActiveConnections() {
        HikariPoolMXBean mxBean = getPoolMXBean();
        return mxBean != null ? mxBean.getActiveConnections() : 0;
    }

    public static int getIdleConnections() {
        HikariPoolMXBean mxBean = getPoolMXBean();
        return mxBean != null ? mxBean.getIdleConnections() : 0;
    }

    public static int getTotalConnections() {
        HikariPoolMXBean mxBean = getPoolMXBean();
        return mxBean != null ? mxBean.getTotalConnections() : 0;
    }

    /**
     * Closes the HikariCP connection pool and resets singleton instance.
     */
    public static synchronized void closePool() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }

    public synchronized void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }
}
