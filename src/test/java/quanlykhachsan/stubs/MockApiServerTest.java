package quanlykhachsan.stubs;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Self-verification test suite for MockApiServer.
 * Tests all 10 Core Endpoints and DoD requirements.
 */
public class MockApiServerTest {

    public static void main(String[] args) {
        MockApiServer server = new MockApiServer(8089);
        try {
            server.start();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();

            String baseUrl = "http://localhost:8089";

            System.out.println("🧪 [TEST START] Running Verification Suite for MockApiServer (TASK-D1.2)...");

            // 0. Health
            testEndpoint(client, "GET Health",
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/v1/health")).GET().build(),
                    200, "OK");

            // 1. POST /api/v1/auth/login
            testEndpoint(client, "1. POST /auth/login (Success)",
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/v1/auth/login"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"admin_main\",\"password\":\"secret\"}"))
                            .build(),
                    200, "token");

            testEndpoint(client, "1. POST /auth/login (Invalid User)",
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/v1/auth/login"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"invalid_user\",\"password\":\"secret\"}"))
                            .build(),
                    401, "ERR_401_UNAUTHORIZED");

            // 2. GET /api/v1/rooms
            testEndpoint(client, "2. GET /rooms",
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/v1/rooms")).GET().build(),
                    200, "vacant_clean");

            // 3. GET /api/v1/rooms/availability
            testEndpoint(client, "3. GET /rooms/availability",
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/v1/rooms/availability")).GET().build(),
                    200, "availableCount");

            // 4. POST /api/v1/customers
            testEndpoint(client, "4. POST /customers",
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/v1/customers"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"fullName\":\"Test Guest\",\"phone\":\"0912345678\"}"))
                            .build(),
                    201, "Test Guest");

            // 5. GET /api/v1/bookings
            testEndpoint(client, "5. GET /bookings",
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/v1/bookings")).GET().build(),
                    200, "confirmed");

            // 6. GET /api/v1/bookings/{id}
            testEndpoint(client, "6. GET /bookings/1",
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/v1/bookings/1")).GET().build(),
                    200, "Nguyen Van A");

            testEndpoint(client, "6. GET /bookings/999 (Not Found)",
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/v1/bookings/999")).GET().build(),
                    404, "ERR_404_NOT_FOUND");

            // 7. POST /api/v1/bookings (Without Idempotency-Key -> 400)
            testEndpoint(client, "7. POST /bookings (Missing Idempotency-Key)",
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/v1/bookings"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"customerId\":1,\"roomId\":1}"))
                            .build(),
                    400, "ERR_400_BAD_REQUEST");

            // 7. POST /api/v1/bookings (With Idempotency-Key -> 201)
            String idemKey = "test-idem-key-" + System.currentTimeMillis();
            testEndpoint(client, "7. POST /bookings (Valid Key)",
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/v1/bookings"))
                            .header("Content-Type", "application/json")
                            .header("Idempotency-Key", idemKey)
                            .POST(HttpRequest.BodyPublishers.ofString("{\"customerId\":1,\"roomId\":1,\"totalPrice\":1000000.0}"))
                            .build(),
                    201, "confirmed");

            // Idempotent replay
            testEndpoint(client, "7. POST /bookings (Replay Same Key)",
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/v1/bookings"))
                            .header("Content-Type", "application/json")
                            .header("Idempotency-Key", idemKey)
                            .POST(HttpRequest.BodyPublishers.ofString("{\"customerId\":1,\"roomId\":1,\"totalPrice\":1000000.0}"))
                            .build(),
                    201, "confirmed");

            // 8. PUT /api/v1/bookings/{id}
            testEndpoint(client, "8. PUT /bookings/1",
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/v1/bookings/1"))
                            .header("Content-Type", "application/json")
                            .PUT(HttpRequest.BodyPublishers.ofString("{\"totalPrice\":1800000.0}"))
                            .build(),
                    200, "1800000");

            // 9. POST /api/v1/bookings/{id}/cancel
            testEndpoint(client, "9. POST /bookings/1/cancel",
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/v1/bookings/1/cancel"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build(),
                    200, "cancelled");

            // 10. POST /api/v1/folios/{id}/pay (Without Idempotency-Key -> 400)
            testEndpoint(client, "10. POST /folios/1/pay (Missing Key)",
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/v1/folios/1/pay"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"amount\":500000.0}"))
                            .build(),
                    400, "ERR_400_BAD_REQUEST");

            // 10. POST /api/v1/folios/{id}/pay (With Idempotency-Key -> 200)
            testEndpoint(client, "10. POST /folios/1/pay (Valid Key)",
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/v1/folios/1/pay"))
                            .header("Content-Type", "application/json")
                            .header("Idempotency-Key", "pay-key-" + System.currentTimeMillis())
                            .POST(HttpRequest.BodyPublishers.ofString("{\"amount\":500000.0,\"paymentMethod\":\"cash\"}"))
                            .build(),
                    200, "paid");

            System.out.println("\n🎉 ALL 14 TEST CASES PASSED WITH 100% SUCCESS!");

        } catch (Exception e) {
            System.err.println("❌ TEST SUITE FAILED: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            server.stop();
        }
    }

    private static void testEndpoint(HttpClient client, String testName, HttpRequest request, int expectedStatus, String expectedSnippet) throws Exception {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != expectedStatus) {
            throw new AssertionError(testName + " FAILED: Expected HTTP " + expectedStatus + " but got " + response.statusCode() + ". Body: " + response.body());
        }
        if (!response.body().contains(expectedSnippet)) {
            throw new AssertionError(testName + " FAILED: Body does not contain snippet '" + expectedSnippet + "'. Body: " + response.body());
        }
        System.out.println("  ✔ [PASS] " + testName + " -> HTTP " + response.statusCode());
    }
}
