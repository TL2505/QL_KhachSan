package quanlykhachsan.backend.customer;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.JwtFilter;
import quanlykhachsan.backend.utils.JwtTokenUtil;
import static org.junit.jupiter.api.Assertions.*;

class LoyaltyControllerTest {
    @Test void verifiedRolesAndAdjustmentValidationAreEnforced() throws Exception {
        var service = new LoyaltyService() {
            @Override public Customer adjustPoints(int id, int change, String reason, String actor) {
                assertEquals("41", actor);
                if (change < -100) throw new IllegalStateException("Không đủ điểm");
                var c = new Customer(); c.setId(id); c.setLoyaltyPoints(100 + change);
                c.setTotalLoyaltyPoints(100 + Math.max(0, change)); c.setLoyaltyLevel("Member"); return c;
            }
        };
        var customers = new CustomerService() {
            @Override public List<Customer> getAllCustomers() { return List.of(new Customer()); }
        };
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/loyalty", new LoyaltyController(service, customers)).getFilters().add(new JwtFilter());
        server.start();
        try {
            var client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/loyalty";
            String admin = JwtTokenUtil.issueToken(41, "admin", "ADMIN");
            String staff = JwtTokenUtil.issueToken(42, "staff", "STAFF");
            String customer = JwtTokenUtil.issueToken(43, "customer", "CUSTOMER");
            String valid = "{\"customerId\":7,\"pointsChange\":25,\"reason\":\"Bổ sung điểm\"}";
            for (String token : List.of(staff, customer)) {
                assertEquals(403, send(client, base + "/adjust", token, valid).statusCode(), "Spoofed admin header must not allow adjustments");
            }
            assertEquals(401, send(client, base + "/adjust", null, valid).statusCode());
            var result = send(client, base + "/adjust", admin, valid);
            assertEquals(200, result.statusCode());
            assertTrue(result.body().contains("\"loyaltyPoints\":125"));
            assertEquals(409, send(client, base + "/adjust", admin, valid.replace(":25", ":-101")).statusCode());
            for (String invalid : List.of("null", "[]", "{}", "{", valid.replace(":25", ":1.5"), valid.replace(":25", ":2147483648"), valid.replace(":25", ":null"), valid.replace(":25", ":\"25\""))) {
                assertEquals(400, send(client, base + "/adjust", admin, invalid).statusCode(), invalid);
            }
            var get = HttpRequest.newBuilder(URI.create(base + "/customers")).header("Authorization", "Bearer " + admin).GET().build();
            assertEquals(200, client.send(get, HttpResponse.BodyHandlers.ofString()).statusCode(), "Admin does not need a role header to list members");
        } finally { server.stop(0); }
    }

    @Test void invalidAdjustmentsAreRejectedBeforeOpeningDatabase() {
        var service = new LoyaltyService();
        for (int change : new int[]{0, 1}) {
            assertThrows(IllegalArgumentException.class, () -> service.adjustPoints(0, change, "Test", "41"));
        }
        assertThrows(IllegalArgumentException.class, () -> service.adjustPoints(1, 0, "Test", "41"));
        assertThrows(IllegalArgumentException.class, () -> service.adjustPoints(1, 1, " ", "41"));
        assertThrows(IllegalArgumentException.class, () -> service.adjustPoints(1, 1, "x".repeat(201), "41"));
    }

    private HttpResponse<String> send(HttpClient client, String url, String token, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json").header("X-User-Role", "ADMIN");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
