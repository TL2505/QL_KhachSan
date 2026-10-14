package quanlykhachsan.security;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import quanlykhachsan.backend.utils.*;
import static org.junit.jupiter.api.Assertions.*;

class CorsAndStaticServingTest {
    @TempDir Path root;

    @Test void corsRunsBeforeAuthenticationAndWhitelistHasExactBoundaries() throws Exception {
        if(System.getenv("JWT_SECRET")==null)System.setProperty("hotel.jwt.secret","synthetic-phase2-jwt-test-fixture-secret-only");
        Files.writeString(root.resolve("index.html"), "Booking");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var api = server.createContext("/api/", e -> {
            byte[] body = String.valueOf(e.getAttribute("user_id")).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            e.sendResponseHeaders(200, body.length); e.getResponseBody().write(body); e.close();
        });
        api.getFilters().add(new CorsFilter()); api.getFilters().add(new JwtFilter());
        server.createContext("/booking", new StaticFileHandler(root));
        server.start();
        try {
            var client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var preflight = send(client, base, "/api/v1/bookings/public", "OPTIONS", null);
            assertEquals(204, preflight.statusCode());
            assertEquals("", preflight.body());
            assertEquals("*", preflight.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
            String headers = preflight.headers().firstValue("Access-Control-Allow-Headers").orElseThrow();
            assertTrue(headers.contains("Idempotency-Key")); assertTrue(headers.contains("X-Signature"));
            for (String path : new String[]{"/api/v1/rooms/availability", "/api/v1/pricing/quote",
                    "/api/v1/auth/guest-otp/request", "/api/v1/auth/guest-otp/verify", "/api/v1/bookings/public"}) {
                assertEquals(200, send(client, base, path, "GET", null).statusCode());
                assertEquals(401, send(client, base, path, "GET", "Bearer invalid").statusCode());
            }
            for (String path : new String[]{"/api/v1/rooms", "/api/v1/rooms/availability/private",
                    "/api/v1/bookings/publicity", "/api/v1/auth/guest-otp/admin", "/api/v1/users"}) {
                assertEquals(401, send(client, base, path, "GET", null).statusCode(), path);
            }
            assertEquals(200, send(client, base, "/api/v1/rooms", "GET",
                "Bearer " + JwtTokenUtil.issueToken(1, "reception", "front_desk")).statusCode());
            assertEquals(403, send(client, base, "/api/v1/rooms", "GET",
                "Bearer " + JwtTokenUtil.issueToken(1, "invalid", "superuser")).statusCode());
            assertEquals("null", send(client, base, "/api/v1/rooms/availability", "GET", null).body(),
                "A public request must not inherit identity from a previous authenticated request");
            assertEquals(200, send(client, base, "/booking/index.html", "GET", null).statusCode());
            assertEquals(403, send(client, base, "/booking/../pom.xml", "GET", null).statusCode());
        } finally { server.stop(0); }
    }

    private static HttpResponse<String> send(HttpClient client, String base, String path, String method, String auth)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).method(method, HttpRequest.BodyPublishers.noBody());
        if (auth != null) request.header("Authorization", auth);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
