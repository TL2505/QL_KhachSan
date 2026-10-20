package quanlykhachsan.backend.utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * ==============================================================================
 * AURELIA GRAND PALACE 5★ PMS - INTELLIGENT DATABASE CONNECTOR
 * Adheres strictly to Architecture Standard 4.1.0-REALISTIC-BASELINE
 * 
 * Auto-detects runtime environment:
 * 1. Checks DB_HOST system environment variable.
 * 2. If empty, reads from .env in project root.
 * 3. If still empty, sequentially probes 127.0.0.1:3306 then 192.168.56.102:3306.
 * 4. Provides zero-crash defaults for Localhost IDE & Full Docker Staging VM.
 * ==============================================================================
 */
public final class DBconn {

    private static final Logger LOGGER = Logger.getLogger(DBconn.class.getName());
    private static final Map<String, String> DOT_ENV = loadDotEnv();
    private static final Object POOL_LOCK = new Object();

    private static volatile HikariDataSource dataSource = null;
    private static volatile String activeResolvedHost = null;
    private static volatile String activeJdbcUrl = null;
    private static volatile DataSource customTestDataSource = null;

    private DBconn() { }

    /**
     * Resolves the MariaDB host dynamically with sequential probe & zero-crash fallback:
     * 1. System.getenv("DB_HOST") (validating reachability if loopback)
     * 2. .env file in root directory (validating reachability if loopback)
     * 3. TCP socket ping 127.0.0.1:3306 (1000ms)
     * 4. TCP socket ping 192.168.56.102:3306 (1000ms)
     * 5. Safe fallback to "localhost"
     */
    public static String resolveDatabaseHost() {
        int probePort = 3306;
        try {
            String portStr = resolveConfigValue("DB_PORT", "3306");
            probePort = Integer.parseInt(portStr);
        } catch (NumberFormatException ignored) {}

        // 1. Check System Environment Variable
        String host = System.getenv("DB_HOST");
        if (host != null && !host.isBlank()) {
            String clean = host.trim();
            if (isLocalAddress(clean)) {
                if (isPortReachable(clean, probePort, 1000)) {
                    LOGGER.info("[DBconn] Using DB_HOST from System Environment (reachable): " + clean);
                    return clean;
                } else {
                    LOGGER.warning("[DBconn] System DB_HOST is set to '" + clean + "', but port " + probePort + 
                                   " is unreachable. Falling through to dynamic probe...");
                }
            } else {
                LOGGER.info("[DBconn] Using DB_HOST from System Environment: " + clean);
                return clean;
            }
        }

        // 2. Check .env file in project root
        String envHost = DOT_ENV.get("DB_HOST");
        if (envHost != null && !envHost.isBlank()) {
            String clean = envHost.trim();
            if (isLocalAddress(clean)) {
                if (isPortReachable(clean, probePort, 1000)) {
                    LOGGER.info("[DBconn] Using DB_HOST from .env file (reachable): " + clean);
                    return clean;
                } else {
                    LOGGER.warning("[DBconn] .env DB_HOST is set to '" + clean + "', but port " + probePort + 
                                   " is unreachable. Falling through to dynamic probe...");
                }
            } else {
                LOGGER.info("[DBconn] Using DB_HOST from .env file: " + clean);
                return clean;
            }
        }

        // 3. Sequential probe: Check Localhost container port 3306 first
        LOGGER.info("[DBconn] Probing local MariaDB instance at 127.0.0.1:" + probePort + "...");
        if (isPortReachable("127.0.0.1", probePort, 1000)) {
            LOGGER.info("[DBconn] 127.0.0.1:" + probePort + " is REACHABLE (Localhost IDE Dev Mode).");
            return "127.0.0.1";
        }

        // 4. Sequential probe: Check Linux Virtual Machine Host-Only IP 192.168.56.102:3306
        LOGGER.info("[DBconn] Probing remote Linux VM at 192.168.56.102:" + probePort + "...");
        if (isPortReachable("192.168.56.102", probePort, 1000)) {
            LOGGER.info("[DBconn] 192.168.56.102:" + probePort + " is REACHABLE (Docker VM Staging Mode).");
            return "192.168.56.102";
        }

        // 5. Default fallback
        LOGGER.warning("[DBconn] Neither 127.0.0.1 nor 192.168.56.102 answered probe. Falling back to 'localhost'.");
        return "localhost";
    }

    private static boolean isLocalAddress(String host) {
        return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host);
    }

    /**
     * Resolves a configuration value with priority:
     * System.getenv -> .env file -> fallback
     */
    public static String resolveConfigValue(String key, String fallback) {
        String sysVal = System.getenv(key);
        if (sysVal != null && !sysVal.isBlank()) return sysVal.trim();

        String dotVal = DOT_ENV.get(key);
        if (dotVal != null && !dotVal.isBlank()) return dotVal.trim();

        return fallback;
    }

    /**
     * Reads and parses a local .env file safely.
     */
    public static Map<String, String> loadDotEnv() {
        Map<String, String> map = new HashMap<>();
        File[] candidateFiles = new File[] {
            new File(".env"),
            new File("../.env"),
            new File(System.getProperty("user.dir", "."), ".env")
        };

        for (File f : candidateFiles) {
            if (f.exists() && f.isFile() && f.canRead()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(f, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        int idx = line.indexOf('=');
                        if (idx > 0) {
                            String k = line.substring(0, idx).trim();
                            String v = line.substring(idx + 1).trim();
                            if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                                v = v.substring(1, v.length() - 1);
                            }
                            map.putIfAbsent(k, v);
                        }
                    }
                    LOGGER.info("[DBconn] Loaded " + map.size() + " environment variables from " + f.getAbsolutePath());
                    return Collections.unmodifiableMap(map);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "[DBconn] Could not read .env from " + f.getAbsolutePath(), ex);
                }
            }
        }
        return Collections.emptyMap();
    }

    /**
     * Tests TCP socket connectivity with a timeout.
     */
    public static boolean isPortReachable(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Lazy-initializes and returns the shared HikariCP connection pool.
     */
    public static HikariDataSource getDataSource() {
        if (dataSource == null || dataSource.isClosed()) {
            synchronized (POOL_LOCK) {
                if (dataSource == null || dataSource.isClosed()) {
                    activeResolvedHost = resolveDatabaseHost();
                    String port = resolveConfigValue("DB_PORT", "3306");
                    String dbName = resolveConfigValue("DB_NAME", "hotel_prod_db");
                    String user = resolveConfigValue("DB_USER", "hotel_admin");
                    String pass = resolveConfigValue("DB_PASSWORD", "hotel_secret_pass");

                    activeJdbcUrl = "jdbc:mysql://" + activeResolvedHost + ":" + port + "/" + dbName
                            + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";

                    HikariConfig config = new HikariConfig();
                    config.setJdbcUrl(activeJdbcUrl);
                    config.setUsername(user);
                    config.setPassword(pass);
                    config.setDriverClassName("com.mysql.cj.jdbc.Driver");
                    config.setMaximumPoolSize(20);
                    config.setMinimumIdle(5);
                    config.setConnectionTimeout(30000);
                    config.setIdleTimeout(300000);
                    config.setLeakDetectionThreshold(5000);
                    config.setInitializationFailTimeout(-1);
                    config.setPoolName("HotelPMS-HikariPool");

                    LOGGER.info("[DBconn] Initializing HikariCP Pool -> " + activeJdbcUrl + " [user=" + user + "]");
                    dataSource = new HikariDataSource(config);
                }
            }
        }
        return dataSource;
    }

    /**
     * Obtains an active connection from the pool.
     */
    public static Connection getConnection() throws SQLException {
        if (customTestDataSource != null) {
            return customTestDataSource.getConnection();
        }
        return getDataSource().getConnection();
    }

    /**
     * Allows test suites to inject a custom mock data source.
     */
    public static void setCustomDataSource(DataSource ds) {
        customTestDataSource = ds;
    }

    /**
     * Resets custom data source to default production pool.
     */
    public static void resetToDefault() {
        customTestDataSource = null;
    }

    /**
     * Shuts down the connection pool cleanly.
     */
    public static void close() {
        synchronized (POOL_LOCK) {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                dataSource = null;
                LOGGER.info("[DBconn] HikariCP Connection Pool closed.");
            }
        }
    }

    public static String getActiveHost() {
        if (activeResolvedHost == null) {
            getDataSource();
        }
        return activeResolvedHost;
    }

    public static String getJdbcUrl() {
        if (activeJdbcUrl == null) {
            getDataSource();
        }
        return activeJdbcUrl;
    }
}
