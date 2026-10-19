package quanlykhachsan.frontend.utils;

/**
 * Active Auto-Discovery and Failover Server Configuration for Desktop App (Java Swing).
 * Implements Zero-Hardcoded Host - Dual-Environment Parity.
 * Automatically discovers active backend between Localhost and VM (192.168.56.102).
 */
public class ServerConfig {
    private static final String PROP_BACKEND_URL = System.getProperty("pms.backend.url", System.getenv("PMS_BACKEND_URL"));
    private static final String[] CANDIDATES = {
        "http://localhost:8081",
        "http://192.168.56.102:8081"
    };
    private static volatile String activeBaseUrl = null;

    public static String getBaseUrl() {
        if (PROP_BACKEND_URL != null && !PROP_BACKEND_URL.trim().isEmpty()) {
            String url = PROP_BACKEND_URL.trim();
            if (url.endsWith("/api/v1")) {
                url = url.substring(0, url.length() - "/api/v1".length());
            } else if (url.endsWith("/api/v1/")) {
                url = url.substring(0, url.length() - "/api/v1/".length());
            }
            while (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            return url;
        }
        if (activeBaseUrl != null) {
            return activeBaseUrl;
        }
        synchronized (ServerConfig.class) {
            if (activeBaseUrl != null) return activeBaseUrl;
            // Quick detection: Try pinging /api/v1/health with 600ms timeout
            for (String candidate : CANDIDATES) {
                if (probeHealth(candidate + "/api/v1/health")) {
                    activeBaseUrl = candidate;
                    System.out.println("[ServerConfig] Auto-discovered active backend: " + activeBaseUrl);
                    return activeBaseUrl;
                }
            }
            // Default fallback
            activeBaseUrl = "http://localhost:8081";
            return activeBaseUrl;
        }
    }

    public static String getApiBaseUrl() {
        return getBaseUrl() + "/api/v1";
    }

    private static boolean probeHealth(String healthUrl) {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(healthUrl).openConnection();
            conn.setConnectTimeout(600);
            conn.setReadTimeout(600);
            conn.setRequestMethod("GET");
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public static synchronized void resetCache() {
        activeBaseUrl = null;
    }
}
