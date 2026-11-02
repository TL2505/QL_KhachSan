package quanlykhachsan.frontend.api;

import java.io.File;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;
import java.util.function.BiPredicate;

/**
 * Centralized Connection and Environment Configuration for Desktop App (Java Swing).
 * Explicit environment or reviewed file configuration takes priority. Cross-host
 * fallback is disabled unless explicitly enabled for a legacy desktop environment.
 */
public final class ApiConfig {
    public static final String DEFAULT_SERVER_HOST = "localhost";
    public static final int DEFAULT_BACKEND_PORT = 8081;
    public static final int DEFAULT_DB_PORT = 3306;

    public static final String DEFAULT_API_BASE_URL = "http://localhost:8081/api/v1";
    public static final String LOCALHOST_IP = "127.0.0.1";
    public static final String LOCALHOST_API_BASE_URL = "http://localhost:8081/api/v1";
    public static final String DEFAULT_JDBC_URL = "jdbc:mysql://localhost:3306/hotel_prod_db?useSSL=false&serverTimezone=UTC";
    public static final String LOCALHOST_JDBC_URL = DEFAULT_JDBC_URL;

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
        return configured("DB_HOST", "db.host", "localhost");
    }

    public static int getDbPort() {
        String p = configured("DB_PORT", "db.port", String.valueOf(DEFAULT_DB_PORT));
        try {
            return Integer.parseInt(p);
        } catch (Exception e) {
            return DEFAULT_DB_PORT;
        }
    }

    public static String getDbName() {
        return configured("DB_NAME", "db.name", "hotel_prod_db");
    }

    public static String getDbUser() {
        return configured("DB_USER", "db.user", "root");
    }

    public static String getDbPassword() {
        String file = System.getenv("DB_PASSWORD_FILE");
        if(file != null && !file.isBlank())return quanlykhachsan.backend.utils.SecretConfiguration.required("DB_PASSWORD");
        String password = System.getenv("DB_PASSWORD");
        if (password != null) return password; // Empty local passwords and whitespace are explicit values.
        password = quanlykhachsan.backend.utils.SecretConfiguration.optional("DB_PASSWORD");
        return password != null ? password : PROPS.getProperty("db.password", "");
    }

    /**
     * Resolves API Base URL with priority:
     * 1. Explicit PMS URL or API environment configuration.
     * 2. Environment variables: API_BASE_URL, BACKEND_URL, SERVER_URL.
     * 3. Localhost default when no server is configured.
     */
    private static String resolveBaseUrl() {
        String explicit=System.getProperty("pms.backend.url",configured("PMS_BACKEND_URL","pms.backend.url",null));
        if(explicit!=null&&!explicit.isBlank())return normalizeApiUrl(explicit);
        String env=getEnvUrl();if(env!=null)return normalizeApiUrl(env);
        return normalizeApiUrl(PROPS.getProperty("server.url",LOCALHOST_API_BASE_URL));
    }

    private static String getEnvUrl() {
        for (String k : new String[]{"API_BASE_URL", "BACKEND_URL", "SERVER_URL"}) {
            String val = quanlykhachsan.backend.utils.SecretConfiguration.optional(k);
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
        String jdbcUrl = getEnvJdbc();
        if (jdbcUrl == null) {
            jdbcUrl = configured("DB_URL", "db.url", null);
        }
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            jdbcUrl = "jdbc:mysql://" + getDbHost() + ":" + getDbPort() + "/" + getDbName()
                + "?useSSL=false&serverTimezone=UTC&createDatabaseIfNotExist=true";
        } else if (!jdbcUrl.contains("createDatabaseIfNotExist=")) {
            jdbcUrl = jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "createDatabaseIfNotExist=true";
        }
        // A real database must never silently switch to another server unless unreachable.
        return resolveWindowsDbFallback(jdbcUrl.trim(),
            true,
            (host, port) -> isTcpReachable(host, port, 1000));
    }

    static String resolveWindowsDbFallback(String jdbcUrl, boolean allowFallback,
                                           BiPredicate<String, Integer> tcpProbe) {
        if (!allowFallback) return jdbcUrl;

        URI jdbcUri = URI.create(jdbcUrl.substring("jdbc:".length()));
        if (!"mysql".equalsIgnoreCase(jdbcUri.getScheme())) return jdbcUrl;

        String host = jdbcUri.getHost();
        if (host == null || isLocalHost(host)) return jdbcUrl;

        int port = jdbcUri.getPort() > 0 ? jdbcUri.getPort() : DEFAULT_DB_PORT;
        if (tcpProbe.test(host, port)) return jdbcUrl;

        if (tcpProbe.test("localhost", port)) {
            System.err.printf(
                "[ApiConfig] DB host %s:%d is unreachable; using localhost:%d fallback.%n",
                host, port, port);
            return jdbcUrlWithHost(jdbcUri, "localhost");
        }

        System.err.printf(
            "[ApiConfig] DB host %s:%d and localhost:%d are unreachable; keeping configured DB URL.%n",
            host, port, port);
        return jdbcUrl;
    }

    private static String jdbcUrlWithHost(URI jdbcUri, String host) {
        try {
            URI localUri = new URI(jdbcUri.getScheme(), jdbcUri.getUserInfo(), host,
                jdbcUri.getPort(), jdbcUri.getPath(), jdbcUri.getQuery(), jdbcUri.getFragment());
            return "jdbc:" + localUri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Could not build localhost JDBC fallback URL", e);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
    }

    private static boolean isLocalHost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
            || "::1".equals(host) || "[::1]".equals(host);
    }

    private static String getEnvJdbc() {
        for (String k : new String[]{"DB_URL", "JDBC_URL"}) {
            String val = "DB_URL".equals(k)
                ? quanlykhachsan.backend.utils.SecretConfiguration.optional(k)
                : System.getenv(k);
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        }
        String host = configured("DB_HOST", "db.host", null);
        if (host != null && !host.trim().isEmpty()) {
            String port = configured("DB_PORT", "db.port", "3306");
            String name = configured("DB_NAME", "db.name", "hotel_prod_db");
            return "jdbc:mysql://" + host + ":" + port + "/" + name + "?useSSL=false&serverTimezone=UTC&createDatabaseIfNotExist=true";
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

    private static String configured(String envKey, String propKey, String fallback) {
        String value = quanlykhachsan.backend.utils.SecretConfiguration.optional(envKey);
        if (value != null && !value.isBlank()) return value;
        value = PROPS.getProperty(propKey);
        return value != null && !value.isBlank() ? value.trim() : fallback;
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
