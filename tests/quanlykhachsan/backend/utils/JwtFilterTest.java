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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtFilterTest {
    private HttpServer server;
    private String baseUrl;
    private final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    private final AtomicReference<String> capturedRole = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        handlerInvoked.set(false);
        capturedRole.set(null);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        var protectedContext = server.createContext("/api/v1/bookings", exchange -> {
            handlerInvoked.set(true);
            Object role = exchange.getAttribute("role");
            if (role != null) {
                capturedRole.set(role.toString());
            }
            byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        protectedContext.getFilters().add(new JwtFilter());

        var publicContext = server.createContext("/api/v1/rooms", exchange -> {
            handlerInvoked.set(true);
            byte[] response = "{\"rooms\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        publicContext.getFilters().add(new JwtFilter());

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
    void optionsPreflightBypassesAuthentication() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/bookings"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(handlerInvoked.get(), "Handler should be reached because OPTIONS bypasses JWT auth");
    }

    @Test
    void publicRoomsEndpointBypassesAuthentication() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/rooms"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(handlerInvoked.get(), "Public room endpoint should bypass authentication");
    }

    @Test
    void guestXUserRoleHeaderBypassesAuthentication() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/bookings"))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .header("X-User-Role", "guest")
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(handlerInvoked.get(), "Requests with X-User-Role should bypass authentication");
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/bookings"))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
        assertTrue(!handlerInvoked.get(), "Handler should not be invoked when unauthenticated");
        assertTrue(response.body().contains("ERR_401_UNAUTHORIZED"));
    }

    @Test
    void protectedEndpointWithValidJwtTokenPassesAndInjectsRole() throws Exception {
        String token = JwtTokenUtil.issueToken(42, "admin_user", "manager");
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/bookings"))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(handlerInvoked.get(), "Handler must be invoked when valid token provided");
        assertEquals("manager", capturedRole.get());
    }
}
