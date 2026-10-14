package quanlykhachsan.backend.utils;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StaticFileHandlerTest {
    @TempDir Path directory;

    @Test void servesAssetsAndRejectsUnsafeRequests() throws Exception {
        Path publicRoot = Files.createDirectory(directory.resolve("public"));
        Files.writeString(publicRoot.resolve("index.html"), "<html>Booking</html>");
        Files.writeString(directory.resolve("secret.txt"), "private");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new StaticFileHandler(publicRoot));
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            for (String path : new String[]{"/", "/booking", "/booking/", "/booking/index.html"}) {
                var response = request(client, base, path, "GET");
                assertEquals(200, response.statusCode());
                assertEquals("<html>Booking</html>", response.body());
                assertEquals("text/html; charset=UTF-8", response.headers().firstValue("Content-Type").orElseThrow());
            }
            String[][] types = {{"css", "text/css"}, {"js", "application/javascript"},
                    {"png", "image/png"}, {"svg", "image/svg+xml"}};
            for (String[] type : types) {
                Files.writeString(publicRoot.resolve("asset." + type[0]), "asset");
                var response = request(client, base, "/booking/asset." + type[0], "GET");
                assertEquals(200, response.statusCode());
                assertEquals(type[1], response.headers().firstValue("Content-Type").orElseThrow());
            }
            var head = request(client, base, "/booking/index.html", "HEAD");
            assertEquals(200, head.statusCode());
            assertEquals("", head.body());
            assertEquals("20", head.headers().firstValue("Content-Length").orElseThrow());
            for (String path : new String[]{"/booking/../secret.txt", "/booking/%2e%2e/secret.txt",
                    "/booking/..%5csecret.txt", "/booking/C:/secret.txt", "/booking/%00"}) {
                assertEquals(403, request(client, base, path, "GET").statusCode(), path);
            }
            var missing = request(client, base, "/booking/missing.html", "GET");
            assertEquals(404, missing.statusCode());
            assertEquals("Not found", missing.body());
            assertEquals(404, request(client, base, "/api/unknown", "GET").statusCode());
            assertEquals(405, request(client, base, "/booking/index.html", "POST").statusCode());
        } finally {
            server.stop(0);
        }
    }

    private static HttpResponse<String> request(HttpClient client, String base, String path, String method)
            throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path))
                .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }
}
