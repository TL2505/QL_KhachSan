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

    public SwingWorker<HttpResponse<String>, Void> request(String method, String path, String json,
                                                            Map<String, String> headers, ApiCallback callback) {
        Objects.requireNonNull(callback, "callback");
        String token = SessionManagerUtil.getAccessToken();
        HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(path))
            .timeout(Duration.ofMillis(5000))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json");
        if (token != null && !token.isBlank()) builder.header("Authorization", "Bearer " + token);
        if (headers != null) headers.forEach(builder::header);
        HttpRequest.BodyPublisher body = json == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json);
        builder.method(method, body);
        HttpRequest request = builder.build();
        SwingWorker<HttpResponse<String>, Void> worker = new SwingWorker<>() {
            @Override protected HttpResponse<String> doInBackground() throws IOException, InterruptedException {
                return client.send(request, HttpResponse.BodyHandlers.ofString());
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
        String token = SessionManagerUtil.getAccessToken();
        HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(path))
            .timeout(Duration.ofMillis(5000)).header("Accept", "application/json")
            .header("Content-Type", "application/json");
        if (token != null && !token.isBlank()) builder.header("Authorization", "Bearer " + token);
        if (headers != null) headers.forEach(builder::header);
        HttpRequest.BodyPublisher body = json == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json);
        builder.method(method, body);
        try {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted", ex);
        }
    }

    private URI resolve(String path) {
        Objects.requireNonNull(path, "path");
        String relative = path.startsWith("/") ? path.substring(1) : path;
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
