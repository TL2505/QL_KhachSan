package quanlykhachsan.backend.booking;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PublicBookingAccessTest {
    @Test
    void guestAndCustomerRolesReachRequestValidationWhileStaffRolesAreDenied() throws Exception {
        for (String role : new String[]{"guest", "customer", "CUSTOMER", "3", "admin"}) {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            PublicBookingController controller = new PublicBookingController();
            server.createContext("/api/v1/bookings/public", exchange -> {
                exchange.setAttribute("role", role);
                controller.handle(exchange);
            });
            server.start();
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/bookings/public"))
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .header("Content-Type", "application/json")
                        .build();
                HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
                assertEquals(java.util.Set.of("guest", "customer", "CUSTOMER", "3").contains(role) ? 400 : 403,
                        response.statusCode(), role);
            } finally {
                server.stop(0);
            }
        }
    }
}
