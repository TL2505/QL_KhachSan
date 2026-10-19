package quanlykhachsan.frontend.api;

import java.io.File;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Properties;

/**
 * Centralized Connection and Environment Configuration for Desktop App (Java Swing).
 * Default absolute priority: 192.168.56.102 (PMS VM).
 * Provides flexible fallback mechanism to environment variables or localhost if VM is unreachable.
 */
public final class ApiConfig {
    public static final String DEFAULT_VM_IP = "192.168.56.102";
    public static final int DEFAULT_BACKEND_PORT = 8081;
    public static final int DEFAULT_DB_PORT = 3306;

    public static final String DEFAULT_API_BASE_URL = "http://192.168.56.102:8081/api/v1";
    public static final String LOCALHOST_IP = "127.0.0.1";
    public static final String LOCALHOST_API_BASE_URL = "http://localhost:8081/api/v1";
    public static final String DEFAULT_JDBC_URL = "jdbc:mysql://192.168.56.102:3306/hotel_prod_db?useSSL=false&serverTimezone=UTC";
    public static final String LOCALHOST_JDBC_URL = "jdbc:mysql://localhost:3306/hotel_prod_db?useSSL=false&serverTimezone=UTC";

    private static final Properties PROPS = loadProperties();

    private static volatile String activeBaseUrl = resolveBaseUrl();
    private static volatile String activeJdbcUrl = resolveJdbcUrl();

    private ApiConfig() {}

    private static Properties loadProperties() {
        Properties p = new Properties();
        File file = new File(System.getProperty("hotel.config.file", "config.properties"));
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                p.load(fis);
            } catch (Exception ignored) {}
        }
        return p;
    }

    public static String getBaseUrl() {
        return activeBaseUrl;
    }

    public static synchronized void setBaseUrl(String url) {
        if (url != null && !url.isBlank()) {
            activeBaseUrl = normalizeApiUrl(url);
        }
    }

    public static String getJdbcUrl() {
        return activeJdbcUrl;
    }

    public static synchronized void setJdbcUrl(String url) {
        if (url != null && !url.isBlank()) {
            activeJdbcUrl = url.trim();
        }
    }

    /**
     * Returns the alternate fallback URL between VM (192.168.56.102) and Localhost.
     */
    public static String getFallbackUrl() {
        String current = activeBaseUrl;
        if (current == null) return LOCALHOST_API_BASE_URL;
        if (current.contains("localhost") || current.contains("127.0.0.1")) {
            return DEFAULT_API_BASE_URL;
        } else {
            return LOCALHOST_API_BASE_URL;
        }
    }

    /**
     * Probes an API host & port via fast TCP connect.
     */
    public static boolean probeHealth(String baseUrl, int timeoutMs) {
        if (baseUrl == null || baseUrl.isBlank()) return false;
        try {
            java.net.URI uri = java.net.URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : DEFAULT_BACKEND_PORT;
            return isTcpReachable(host, port, timeoutMs);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Attempts to switch to fallback URL if reachable.
     */
    public static synchronized String trySmartFallback() {
        String fallback = getFallbackUrl();
        if (probeHealth(fallback, 800)) {
            System.out.println("[ApiConfig] Smart URL resolution switched base URL to: " + fallback);
            setBaseUrl(fallback);
            if (fallback.contains("localhost") || fallback.contains("127.0.0.1")) {
                setJdbcUrl(LOCALHOST_JDBC_URL);
            } else {
                setJdbcUrl(DEFAULT_JDBC_URL);
            }
            return fallback;
        }
        return null;
    }

    public static String getDbHost() {
        return getPropertyOrEnv("db.host", "DB_HOST", DEFAULT_VM_IP);
    }

    public static int getDbPort() {
        String p = getPropertyOrEnv("db.port", "DB_PORT", String.valueOf(DEFAULT_DB_PORT));
        try {
            return Integer.parseInt(p);
        } catch (Exception e) {
            return DEFAULT_DB_PORT;
        }
    }

    public static String getDbName() {
        return getPropertyOrEnv("db.name", "DB_NAME", "hotel_prod_db");
    }

    public static String getDbUser() {
        return getPropertyOrEnv("db.user", "DB_USER", "root");
    }

    public static String getDbPassword() {
        return getPropertyOrEnv("db.password", "DB_PASSWORD", "");
    }

    /**
     * Resolves API Base URL with priority:
     * 1. 192.168.56.102:8081 if reachable or specified in config.properties / default.
     * 2. Environment variables: API_BASE_URL, BACKEND_URL, SERVER_URL.
     * 3. Fallback to localhost:8081 if 192.168.56.102 is completely unreachable.
     */
    private static String resolveBaseUrl() {
        String pmsUrl = System.getProperty("pms.backend.url", System.getenv("PMS_BACKEND_URL"));
        if (pmsUrl != null && !pmsUrl.trim().isEmpty()) {
            return normalizeApiUrl(pmsUrl.trim());
        }

        // 1. Check config.properties first
        String configured = PROPS.getProperty("server.url");
        if (configured != null && !configured.trim().isEmpty()) {
            configured = configured.trim();
            if (configured.contains(DEFAULT_VM_IP)) {
                if (isTcpReachable(DEFAULT_VM_IP, DEFAULT_BACKEND_PORT, 600)) {
                    return normalizeApiUrl(configured);
                }
                String envUrl = getEnvUrl();
                if (envUrl != null) return normalizeApiUrl(envUrl);
                return ServerConfig.getBaseUrl() + "/api/v1";
            }
            return normalizeApiUrl(configured);
        }

        // 2. Active auto-discovery via ServerConfig
        return ServerConfig.getBaseUrl() + "/api/v1";
    }

    private static String getEnvUrl() {
        for (String k : new String[]{"API_BASE_URL", "BACKEND_URL", "SERVER_URL"}) {
            String val = System.getenv(k);
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        }
        return null;
    }

    private static String normalizeApiUrl(String url) {
        if (url == null || url.trim().isEmpty()) return DEFAULT_API_BASE_URL;
        String clean = url.trim();
        if (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        if (clean.endsWith("/api")) clean = clean + "/v1";
        return clean;
    }

    /**
     * Resolves JDBC URL with priority:
     * 1. 192.168.56.102:3306 if reachable or specified in config.properties / default.
     * 2. Environment variables: DB_URL, JDBC_URL.
     * 3. Fallback to localhost if 192.168.56.102 is unreachable.
     */
    private static String resolveJdbcUrl() {
        String configured = PROPS.getProperty("db.url");
        if (configured != null && !configured.trim().isEmpty()) {
            if (configured.contains(DEFAULT_VM_IP)) {
                if (isTcpReachable(DEFAULT_VM_IP, DEFAULT_DB_PORT, 600)) {
                    return configured.trim();
                }
                String envUrl = getEnvJdbc();
                if (envUrl != null) return envUrl;
                return "jdbc:mysql://localhost:3306/hotel_prod_db?useSSL=false&serverTimezone=UTC";
            }
            return configured.trim();
        }

        if (isTcpReachable(DEFAULT_VM_IP, DEFAULT_DB_PORT, 600)) {
            return DEFAULT_JDBC_URL;
        }

        String envUrl = getEnvJdbc();
        if (envUrl != null) return envUrl;

        return DEFAULT_JDBC_URL;
    }

    private static String getEnvJdbc() {
        for (String k : new String[]{"DB_URL", "JDBC_URL"}) {
            String val = System.getenv(k);
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        }
        String host = System.getenv("DB_HOST");
        if (host != null && !host.trim().isEmpty()) {
            String port = System.getenv("DB_PORT") != null ? System.getenv("DB_PORT") : "3306";
            String name = System.getenv("DB_NAME") != null ? System.getenv("DB_NAME") : "hotel_prod_db";
            return "jdbc:mysql://" + host + ":" + port + "/" + name + "?useSSL=false&serverTimezone=UTC";
        }
        return null;
    }

    private static String getPropertyOrEnv(String propKey, String envKey, String fallback) {
        String val = PROPS.getProperty(propKey);
        if (val != null && !val.trim().isEmpty()) return val.trim();
        val = System.getenv(envKey);
        if (val != null && !val.trim().isEmpty()) return val.trim();
        return fallback;
    }

    public static boolean isTcpReachable(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
