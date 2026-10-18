package quanlykhachsan.frontend.utils;

public class HttpUtil {

    private static volatile java.util.function.IntConsumer serviceUnavailableHandler;

    private static final String DEFAULT_URL = quanlykhachsan.frontend.api.ApiConfig.DEFAULT_API_BASE_URL;
    private static final String BASE_URL;

    static {
        String configuredUrl = quanlykhachsan.frontend.api.ApiConfig.getBaseUrl();
        if (configuredUrl == null || configuredUrl.trim().isEmpty()) {
            configuredUrl = DEFAULT_URL;
        }
        BASE_URL = configuredUrl;
    }

    /** Configured API base URL. The returned value is available without network I/O. */
    public static String getBaseUrl() { return BASE_URL; }

    public static String sendGet(String endpoint) throws Exception {
        java.net.http.HttpResponse<String> response = send("GET", endpoint, null, null);
        notifyServiceUnavailable(response);
        if (response.statusCode() != 200) throw new java.io.IOException("Failed : HTTP error code : " + response.statusCode());
        return response.body();
    }

    public static String sendPost(String endpoint, String jsonBody) throws Exception {
        return sendWithBody("POST", endpoint, jsonBody);
    }

    public static String sendPut(String endpoint, String jsonBody) throws Exception {
        return sendWithBody("PUT", endpoint, jsonBody);
    }

    public static String sendDelete(String endpoint) throws Exception {
        java.net.http.HttpResponse<String> response = send("DELETE", endpoint, null, null);
        notifyServiceUnavailable(response);
        return response.body();
    }

    private static String sendWithBody(String method, String endpoint, String jsonBody) throws Exception {
        java.net.http.HttpResponse<String> response = send(method, endpoint, jsonBody, null);
        notifyServiceUnavailable(response);
        return response.body();
    }

    /** Sends a POST with caller-supplied headers while retaining configured API URL and session auth. */
    public static HttpResult sendPostWithHeaders(String endpoint, String jsonBody,
                                                  java.util.Map<String, String> headers) throws Exception {
        java.net.http.HttpResponse<String> response = send("POST", endpoint, jsonBody, headers);
        notifyServiceUnavailable(response);
        return new HttpResult(response.statusCode(), response.body(),
            response.headers().firstValue("Retry-After").orElse(null));
    }

    private static java.net.http.HttpResponse<String> send(String method, String endpoint, String body,
            java.util.Map<String, String> additionalHeaders) throws Exception {
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("ngrok-skip-browser-warning", "true");
        if (additionalHeaders != null) headers.putAll(additionalHeaders);
        return quanlykhachsan.frontend.api.ApiClient.getInstance().sendSync(method, endpoint, body, headers);
    }

    public static void setServiceUnavailableHandler(java.util.function.IntConsumer handler) {
        serviceUnavailableHandler = handler;
    }

    private static void notifyServiceUnavailable(java.net.http.HttpResponse<String> response) {
        int status = response.statusCode();
        if (status != 503) return;
        int retryAfter = 300;
        try {
            String header = response.headers().firstValue("Retry-After").orElse(null);
            if (header != null) retryAfter = Math.max(1, Integer.parseInt(header.trim()));
        } catch (NumberFormatException ignored) {
            // The banner uses the contract's default five-minute retry window.
        }
        java.util.function.IntConsumer handler = serviceUnavailableHandler;
        if (handler != null) handler.accept(retryAfter);
    }

    public static final class HttpResult {
        private final int statusCode;
        private final String body;
        private final String retryAfter;

        private HttpResult(int statusCode, String body, String retryAfter) {
            this.statusCode = statusCode;
            this.body = body;
            this.retryAfter = retryAfter;
        }

        public int getStatusCode() { return statusCode; }
        public String getBody() { return body; }
        public String getRetryAfter() { return retryAfter; }
    }
}
