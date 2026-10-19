package quanlykhachsan.frontend.api;

import java.io.File;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Properties;

/**
 * Centralized Connection and Environment Configuration for Desktop App (Java Swing).
 * Explicit environment or reviewed file configuration takes priority.
 * An unavailable configured host fails clearly; financial requests never switch ledgers.
 */
public final class ApiConfig {
    public static final String DEFAULT_VM_IP = "192.168.56.102";
    public static final int DEFAULT_BACKEND_PORT = 8081;
    public static final int DEFAULT_DB_PORT = 3306;

    public static final String DEFAULT_API_BASE_URL = "http://localhost:8081/api/v1";
    public static final String LOCALHOST_IP = "127.0.0.1";
    public static final String LOCALHOST_API_BASE_URL = "http://localhost:8081/api/v1";
    public static final String DEFAULT_JDBC_URL = "jdbc:mysql://localhost:3306/hotel_prod_db?useSSL=false&serverTimezone=UTC";
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
     * Compatibility accessor; returns the configured origin without cross-host fallback.
     */
    public static String getFallbackUrl() { return activeBaseUrl; }

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
    public static synchronized String trySmartFallback() { return null; }

    public static String getDbHost() {
        return getPropertyOrEnv("db.host", "DB_HOST", "localhost");
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
        if(System.getenv("DB_PASSWORD_FILE")!=null)return quanlykhachsan.backend.utils.SecretConfiguration.required("DB_PASSWORD");
        return getPropertyOrEnv("db.password", "DB_PASSWORD", "");
    }

    /**
     * Resolves API Base URL with priority:
     * 1. Explicit PMS URL or API environment configuration.
     * 2. Environment variables: API_BASE_URL, BACKEND_URL, SERVER_URL.
     * 3. Localhost default when no server is configured.
     */
    private static String resolveBaseUrl() {
        String explicit=System.getProperty("pms.backend.url",System.getenv("PMS_BACKEND_URL"));
        if(explicit!=null&&!explicit.isBlank())return normalizeApiUrl(explicit);
        String env=getEnvUrl();if(env!=null)return normalizeApiUrl(env);
        return normalizeApiUrl(PROPS.getProperty("server.url",LOCALHOST_API_BASE_URL));
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
     * 1. Explicit JDBC URL or DB_HOST environment configuration.
     * 2. Environment variables: DB_URL, JDBC_URL.
     * 3. Reviewed file configuration or localhost default.
     */
    private static String resolveJdbcUrl() {
        String env=getEnvJdbc();if(env!=null)return env;
        String configured=PROPS.getProperty("db.url");
        if(configured!=null&&!configured.isBlank())return configured.trim();
        return "jdbc:mysql://"+getDbHost()+":"+getDbPort()+"/"+getDbName()+"?useSSL=false&serverTimezone=UTC";
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
        String val = System.getenv(envKey);
        if (val != null && !val.trim().isEmpty()) return val.trim();
        val = PROPS.getProperty(propKey);
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
