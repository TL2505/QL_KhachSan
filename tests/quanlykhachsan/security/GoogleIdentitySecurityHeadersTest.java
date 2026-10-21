package quanlykhachsan.security;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.SecurityHardeningFilter;
import static org.junit.jupiter.api.Assertions.*;

class GoogleIdentitySecurityHeadersTest {
    @Test void webAllowsOnlyRequiredGoogleIdentityPathsWhileApiRetainsStrictCsp() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        for (String path : new String[]{"/portal.html", "/api/v1/auth/google/config"}) {
            var context = server.createContext(path, exchange -> {
                byte[] body = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (var stream = exchange.getResponseBody()) { stream.write(body); }
            });
            context.getFilters().add(new SecurityHardeningFilter());
        }
        server.start();
        try {
            var client = HttpClient.newHttpClient();
            var base = "http://127.0.0.1:" + server.getAddress().getPort();
            var page = client.send(HttpRequest.newBuilder(URI.create(base + "/portal.html")).GET().build(), HttpResponse.BodyHandlers.ofString());
            String csp = page.headers().firstValue("Content-Security-Policy").orElseThrow();
            assertTrue(csp.contains("https://accounts.google.com/gsi/client;"));
            assertTrue(csp.contains("https://accounts.google.com/gsi/style;"));
            assertTrue(csp.contains("connect-src 'self' https://accounts.google.com/gsi/;"));
            assertTrue(csp.contains("frame-src 'self' https://accounts.google.com/gsi/;"));
            assertFalse(csp.contains("*.google.com"));
            assertFalse(csp.contains("https://accounts.google.com;"));
            assertEquals("DENY", page.headers().firstValue("X-Frame-Options").orElseThrow());
            assertEquals("nosniff", page.headers().firstValue("X-Content-Type-Options").orElseThrow());
            var api = client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/google/config")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals("default-src 'self'", api.headers().firstValue("Content-Security-Policy").orElseThrow());
        } finally { server.stop(0); }
    }
}
