package quanlykhachsan.backend;

import java.net.URI;
import java.net.http.*;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StaticAssetRateLimitTest {
    @Test void pageAssetsDoNotConsumeTheUnauthenticatedApiBudget() throws Exception {
        var server = Main.createServer(0);
        server.start();
        try {
            var client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            for (int i = 0; i < 65; i++) {
                String asset = i % 2 == 0 ? "/portal-app.js" : "/booking/portal.css";
                var response = get(client, base + asset);
                assertEquals(200, response.statusCode(), asset);
                assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElseThrow());
            }
            assertEquals(401, get(client, base + "/api/v1/services").statusCode());
            boolean limited = false;
            for (int i = 0; i < 65; i++) {
                int status = get(client, base + "/api/v1/services").statusCode();
                assertTrue(status == 401 || status == 429);
                limited |= status == 429;
            }
            assertTrue(limited, "Unauthenticated APIs must retain their rate limit");
            assertEquals(200, get(client, base + "/portal.html").statusCode());
        } finally {
            Main.closeBackgroundServices(server);
            server.stop(0);
            ((ExecutorService) server.getExecutor()).shutdownNow();
        }
    }

    private HttpResponse<Void> get(HttpClient client, String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.discarding());
    }
}
