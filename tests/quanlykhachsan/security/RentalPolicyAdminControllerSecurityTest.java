package quanlykhachsan.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.pricing.RentalPolicyAdminController;
import quanlykhachsan.backend.utils.JwtFilter;
import quanlykhachsan.backend.utils.JwtTokenUtil;

class RentalPolicyAdminControllerSecurityTest {
    @Test void rateConfigurationIsAdminOnly() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/admin/rental-policy", new RentalPolicyAdminController()).getFilters().add(new JwtFilter());
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/admin/rental-policy";
            HttpClient client = HttpClient.newHttpClient();
            assertEquals(403, send(client, url, "GET", JwtTokenUtil.issueToken(20, "desk", "front_desk")).statusCode());
            assertEquals(404, send(client, url, "DELETE", JwtTokenUtil.issueToken(21, "admin", "admin")).statusCode());
        } finally { server.stop(0); }
    }

    private static HttpResponse<String> send(HttpClient client, String url, String method, String token) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer " + token)
            .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }
}
