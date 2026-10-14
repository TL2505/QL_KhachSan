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
        String host = env("DB_HOST", "localhost");
        String port = env("DB_PORT", "3306");
        String database = env("DB_NAME", "hotel_prod_db");
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
            + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Ho_Chi_Minh");
        config.setUsername(env("DB_USER", "root"));
        config.setPassword(env("DB_PASSWORD", ""));
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(300000);
        config.setLeakDetectionThreshold(5000);
        config.setPoolName("HotelPmsPool");
        return new HikariDataSource(config);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    public static Connection getConnection() throws SQLException {
        return DATA_SOURCE.getConnection();
    }

    public static void close() {
        if (!DATA_SOURCE.isClosed()) DATA_SOURCE.close();
    }
}
