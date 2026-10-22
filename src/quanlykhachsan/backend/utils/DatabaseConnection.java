package quanlykhachsan.backend.utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

/** Shared HikariCP connection pool for the Night Audit and folio endpoints. */
public final class DatabaseConnection {
    private static final HikariDataSource DATA_SOURCE = createDataSource();

    private DatabaseConnection() { }

    private static HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        String jdbcUrl = quanlykhachsan.frontend.api.ApiConfig.getJdbcUrl();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(quanlykhachsan.frontend.api.ApiConfig.getDbUser());
        config.setPassword(quanlykhachsan.frontend.api.ApiConfig.getDbPassword());
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        // Business DATETIME values and bank timestamps are Vietnam wall time.
        config.setConnectionInitSql("SET time_zone = '+07:00'");
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(300000);
        config.setLeakDetectionThreshold(5000);
        config.setInitializationFailTimeout(-1);
        config.setPoolName("HotelPmsPool");
        return new HikariDataSource(config);
    }

    private static final java.util.Properties CONFIG_PROPS = loadConfigProperties();

    private static java.util.Properties loadConfigProperties() {
        java.util.Properties props = new java.util.Properties();
        java.io.File file = new java.io.File("config.properties");
        if (file.exists()) {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                props.load(fis);
            } catch (Exception ignored) {}
        }
        return props;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        if (value != null && !value.isBlank()) return value;

        String propKey = switch (name) {
            case "DB_HOST" -> "db.host";
            case "DB_PORT" -> "db.port";
            case "DB_NAME" -> "db.name";
            case "DB_USER" -> "db.user";
            case "DB_PASSWORD" -> "db.password";
            default -> null;
        };
        if (propKey != null && CONFIG_PROPS.containsKey(propKey)) {
            String pVal = CONFIG_PROPS.getProperty(propKey);
            if (pVal != null && !pVal.isBlank()) return pVal.trim();
        }

        if ("DB_HOST".equals(name) && CONFIG_PROPS.containsKey("server.url")) {
            String serverUrl = CONFIG_PROPS.getProperty("server.url", "");
            try {
                java.net.URI uri = java.net.URI.create(serverUrl);
                if (uri.getHost() != null && !uri.getHost().isBlank()) {
                    return uri.getHost();
                }
            } catch (Exception ignored) {}
        }

        return fallback;
    }

    public static Connection getConnection() throws SQLException {
        return DATA_SOURCE.getConnection();
    }

    public static void close() {
        if (!DATA_SOURCE.isClosed()) DATA_SOURCE.close();
    }
    /** Read-only pool telemetry for load verification and operational health. */
    public static int activeConnections(){return DATA_SOURCE.getHikariPoolMXBean().getActiveConnections();}
    public static int awaitingConnections(){return DATA_SOURCE.getHikariPoolMXBean().getThreadsAwaitingConnection();}
}
