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