package quanlykhachsan.frontend.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import javax.swing.SwingWorker;
import javax.swing.SwingUtilities;
import quanlykhachsan.frontend.utils.HttpUtil;
import quanlykhachsan.frontend.utils.SessionManagerUtil;

/** Central, bounded-time HTTP client. Requests are always executed by SwingWorker. */
public final class ApiClient {
    private static final ApiClient INSTANCE = new ApiClient(HttpUtil.getBaseUrl(), HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(3000)).build());
    private final URI baseUri;
    private final HttpClient client;

    public ApiClient(URI baseUri, HttpClient client) {
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.client = Objects.requireNonNull(client, "client");
    }

    private ApiClient(String baseUrl, HttpClient client) {
        this(URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/"), client);
    }

    public static ApiClient getInstance() { return INSTANCE; }

    public SwingWorker<HttpResponse<String>, Void> get(String path, ApiCallback callback) {
        return request("GET", path, null, Map.of(), callback);
    }

    public SwingWorker<HttpResponse<String>, Void> post(String path, String json, ApiCallback callback) {
        return request("POST", path, json, Map.of(), callback);
    }

    public SwingWorker<HttpResponse<String>, Void> put(String path, String json, ApiCallback callback) {
        return request("PUT", path, json, Map.of(), callback);
    }

    public SwingWorker<HttpResponse<String>, Void> delete(String path, ApiCallback callback) {
        return request("DELETE", path, null, Map.of(), callback);
    }

    public static boolean isPublicEndpoint(String path) {
        if (path == null || path.isBlank()) return false;
        String normalized = path.trim().toLowerCase(java.util.Locale.ROOT);
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (normalized.startsWith("api/")) normalized = normalized.substring(4);
        if (normalized.startsWith("v1/")) normalized = normalized.substring(3);
        if (!normalized.startsWith("/")) normalized = "/" + normalized;
        return normalized.startsWith("/auth/login")
            || normalized.startsWith("/auth/register")
            || normalized.startsWith("/health")
            || normalized.startsWith("/auth/guest-otp");
    }

    /**
     * Creates a JwtParser configured with 120 seconds of allowed clock skew
     * to tolerate time drift between Windows host and Linux VM / Docker containers.
     */
    public static io.jsonwebtoken.JwtParser createJwtParser(javax.crypto.SecretKey key) {
        return io.jsonwebtoken.Jwts.parser()
            .setSigningKey(key)
            .setAllowedClockSkewSeconds(120)
            .build();
    }

    public static io.jsonwebtoken.Claims parseAndValidateToken(String token, javax.crypto.SecretKey key) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("JWT token cannot be null or blank");
        }
        return createJwtParser(key).parseSignedClaims(token.trim()).getPayload();
    }

    public SwingWorker<HttpResponse<String>, Void> request(String method, String path, String json,
                                                            Map<String, String> headers, ApiCallback callback) {
        Objects.requireNonNull(callback, "callback");
        final boolean isPublic = isPublicEndpoint(path);
        final String token = isPublic ? null : SessionManagerUtil.getAccessToken();
        HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(path))
            .timeout(Duration.ofMillis(5000))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json");
        if (!isPublic && token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (headers != null) {
            headers.forEach((k, v) -> {
                if (!isPublic || !"Authorization".equalsIgnoreCase(k)) {
                    builder.header(k, v);
                }
            });
        }
        HttpRequest.BodyPublisher body = json == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json);
        builder.method(method, body);
        HttpRequest request = builder.build();
        SwingWorker<HttpResponse<String>, Void> worker = new SwingWorker<>() {
            @Override protected HttpResponse<String> doInBackground() throws IOException, InterruptedException {
                HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (!isPublic && (resp.statusCode() == 401 || resp.statusCode() == 403) && token != null) {
                    SessionManagerUtil.setAccessToken(null);
                    SessionManagerUtil.ensureAccessToken();
                    String refreshed = SessionManagerUtil.getAccessToken();
                    if (refreshed != null && !refreshed.equals(token)) {
                        HttpRequest.Builder retry = HttpRequest.newBuilder(resolve(path))
                            .timeout(Duration.ofMillis(5000))
                            .header("Accept", "application/json")
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + refreshed);
                        if (headers != null) headers.forEach((k, v) -> {
                            if (!"Authorization".equalsIgnoreCase(k)) retry.header(k, v);
                        });
                        retry.method(method, body);
                        return client.send(retry.build(), HttpResponse.BodyHandlers.ofString());
                    }
                }
                return resp;
            }
            @Override protected void done() {
                try {
                    HttpResponse<String> response = get();
                    if (response.statusCode() >= 200 && response.statusCode() < 300) callback.onSuccess(response);
                    else callback.onError(new ApiException(response.statusCode(), response.body()));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    callback.onError(ex);
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    callback.onError(cause instanceof Exception ? (Exception) cause : new IOException(cause));
                }
            }
        };
        worker.execute();
        return worker;
    }

    /** Synchronous compatibility bridge for API facades already running on worker threads. */
    public HttpResponse<String> sendSync(String method, String path, String json,
                                         Map<String, String> headers) throws IOException {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Synchronous HTTP requests are forbidden on the Swing EDT");
        }
        final boolean isPublic = isPublicEndpoint(path);
        final String token = isPublic ? null : SessionManagerUtil.getAccessToken();
        HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(path))
            .timeout(Duration.ofMillis(5000)).header("Accept", "application/json")
            .header("Content-Type", "application/json");
        if (!isPublic && token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (headers != null) {
            headers.forEach((k, v) -> {
                if (!isPublic || !"Authorization".equalsIgnoreCase(k)) {
                    builder.header(k, v);
                }
            });
        }
        HttpRequest.BodyPublisher body = json == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json);
        builder.method(method, body);
        try {
            HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (!isPublic && (resp.statusCode() == 401 || resp.statusCode() == 403) && token != null) {
                SessionManagerUtil.setAccessToken(null);
                SessionManagerUtil.ensureAccessToken();
                String refreshed = SessionManagerUtil.getAccessToken();
                if (refreshed != null && !refreshed.equals(token)) {
                    HttpRequest.Builder retryBuilder = HttpRequest.newBuilder(resolve(path))
                        .timeout(Duration.ofMillis(5000)).header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + refreshed);
                    if (headers != null) {
                        headers.forEach((k, v) -> {
                            if (!"Authorization".equalsIgnoreCase(k)) retryBuilder.header(k, v);
                        });
                    }
                    retryBuilder.method(method, body);
                    return client.send(retryBuilder.build(), HttpResponse.BodyHandlers.ofString());
                }
            }
            return resp;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted", ex);
        }
    }

    private URI resolve(String path) {
        Objects.requireNonNull(path, "path");
        String relative = path.startsWith("/") ? path.substring(1) : path;
        String basePath = baseUri.getPath();
        if (basePath != null && (basePath.endsWith("/v1/") || basePath.endsWith("/v1"))) {
            if (relative.startsWith("v1/")) {
                relative = relative.substring(3);
            } else if (relative.startsWith("api/v1/")) {
                relative = relative.substring(7);
            }
        }
        return baseUri.resolve(relative);
    }

    public static final class ApiException extends IOException {
        private final int statusCode;
        private final String responseBody;
        public ApiException(int statusCode, String responseBody) {
            super("API request failed with HTTP " + statusCode);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }
        public int getStatusCode() { return statusCode; }
        public String getResponseBody() { return responseBody; }
    }
}
