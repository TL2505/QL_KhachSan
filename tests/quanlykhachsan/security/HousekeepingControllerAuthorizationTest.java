package quanlykhachsan.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.room.HousekeepingController;
import quanlykhachsan.backend.utils.JwtFilter;
import quanlykhachsan.backend.utils.JwtTokenUtil;

class HousekeepingControllerAuthorizationTest {
    @Test void onlyHousekeepingRolesReachTheTaskHandler() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/housekeeping/tasks", new HousekeepingController()).getFilters().add(new JwtFilter());
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/housekeeping/tasks";
            HttpClient client = HttpClient.newHttpClient();
            String deskToken = JwtTokenUtil.issueToken(10, "desk", "front_desk");
            String housekeepingToken = JwtTokenUtil.issueToken(11, "cleaner", "housekeeping");
            assertEquals(403, send(client, url, "GET", deskToken).statusCode());
            assertEquals(405, send(client, url, "DELETE", housekeepingToken).statusCode());
        } finally { server.stop(0); }
    }

    private static HttpResponse<String> send(HttpClient client, String url, String method, String token) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer " + token)
            .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }
}
