package quanlykhachsan.backend.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CorsFilterTest {
    private HttpServer server;
    private String baseUrl;
    private final AtomicBoolean handlerInvoked = new AtomicBoolean(false);

    @BeforeEach
    void setUp() throws IOException {
        handlerInvoked.set(false);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var context = server.createContext("/api/v1/test", exchange -> {
            handlerInvoked.set(true);
            byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        context.getFilters().add(new CorsFilter());
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void preflightOptionsReturns204WithAllowHeadersAndSkipsHandler() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/test"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Idempotency-Key, Content-Type, X-User-Role")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(204, response.statusCode());
        assertTrue(!handlerInvoked.get(), "Handler should not be reached for OPTIONS preflight");
        assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
        String allowedHeaders = response.headers().firstValue("Access-Control-Allow-Headers").orElse("");
        assertTrue(allowedHeaders.contains("Idempotency-Key"), "Must allow Idempotency-Key header");
        assertTrue(allowedHeaders.contains("X-User-Role"), "Must allow X-User-Role header");
        assertTrue(allowedHeaders.contains("Authorization"), "Must allow Authorization header");
    }

    @Test
    void standardGetRequestIncludesCorsHeadersAndExecutesHandler() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/test"))
                .GET()
                .header("Origin", "http://localhost:3000")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(handlerInvoked.get(), "Handler must be executed for normal GET request");
        assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
    }
}
