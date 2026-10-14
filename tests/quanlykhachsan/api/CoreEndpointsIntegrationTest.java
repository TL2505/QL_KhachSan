package quanlykhachsan.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import quanlykhachsan.BaseIntegrationTest;

import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Core Endpoints Integration Test Suite (TASK-D1.3).
 * <p>
 * Exercises the 10 Core Endpoints matching docs/dev-spec/api-contracts.md:
 * 1. POST /api/v1/auth/login
 * 2. GET  /api/v1/rooms
 * 3. GET  /api/v1/rooms/availability
 * 4. POST /api/v1/customers
 * 5. GET  /api/v1/bookings
 * 6. GET  /api/v1/bookings/{id}
 * 7. POST /api/v1/bookings
 * 8. PUT  /api/v1/bookings/{id}
 * 9. POST /api/v1/bookings/{id}/cancel
 * 10. POST /api/v1/folios/{id}/pay
 */
public class CoreEndpointsIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("0. Health Endpoint responds with 200 OK")
    public void testHealthCheck() throws Exception {
        HttpResponse<String> resp = get("/api/v1/health");
        Assertions.assertEquals(200, resp.statusCode());
        JsonObject json = parseJson(resp.body());
        Assertions.assertEquals("success", json.get("status").getAsString());
        Assertions.assertEquals("OK", json.get("data").getAsString());
    }

    @Test
    @DisplayName("1. POST /auth/login returns JWT token and user claims")
    public void testAuthLogin_Success() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("username", "admin_main");
        req.addProperty("password", "secure_password");

        HttpResponse<String> resp = post("/api/v1/auth/login", req.toString());
        Assertions.assertEquals(200, resp.statusCode());

        JsonObject json = parseJson(resp.body());
        Assertions.assertEquals("success", json.get("status").getAsString());

        JsonObject data = json.getAsJsonObject("data");
        Assertions.assertNotNull(data.get("token"));
        Assertions.assertTrue(data.get("token").getAsString().startsWith("eyJ"), "Token must be a standard RFC 7519 JWT");
        Assertions.assertTrue(data.get("token").getAsString().contains("."), "Token must contain standard JWT dot separators");

        JsonObject user = data.getAsJsonObject("user");
        Assertions.assertEquals("admin_main", user.get("username").getAsString());
        Assertions.assertEquals("admin", user.get("role").getAsString());
    }

    @Test
    @DisplayName("1. POST /auth/login with invalid credentials returns 401 ERR_401_UNAUTHORIZED")
    public void testAuthLogin_InvalidCredentials() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("username", "invalid_user");
        req.addProperty("password", "bad_pass");

        HttpResponse<String> resp = post("/api/v1/auth/login", req.toString());
        Assertions.assertEquals(401, resp.statusCode());

        JsonObject json = parseJson(resp.body());
        Assertions.assertEquals("ERR_401_UNAUTHORIZED", json.get("error_code").getAsString());
    }

    @Test
    @DisplayName("2. GET /rooms enforces strictly the 6 allowed domain room statuses")
    public void testRoomsList_CompliantStatuses() throws Exception {
        HttpResponse<String> resp = get("/api/v1/rooms");
        Assertions.assertEquals(200, resp.statusCode());

        JsonObject json = parseJson(resp.body());
        Assertions.assertEquals("success", json.get("status").getAsString());

        JsonArray rooms = json.getAsJsonArray("data");
        Assertions.assertTrue(rooms.size() >= 6, "Expected at least 6 standard test rooms");

        for (JsonElement el : rooms) {
            JsonObject room = el.getAsJsonObject();
            String status = room.get("status").getAsString();
            Assertions.assertTrue(ALLOWED_ROOM_STATUSES.contains(status),
                    "Room " + room.get("roomNumber").getAsString() + " has invalid status: " + status);
        }
    }

    @Test
    @DisplayName("3. GET /rooms/availability returns available room counts")
    public void testRoomsAvailability() throws Exception {
        HttpResponse<String> resp = get("/api/v1/rooms/availability?checkInDate=2026-10-20&checkOutDate=2026-10-22");
        Assertions.assertEquals(200, resp.statusCode());

        JsonObject json = parseJson(resp.body());
        Assertions.assertEquals("success", json.get("status").getAsString());
        JsonArray data = json.getAsJsonArray("data");
        Assertions.assertFalse(data.isEmpty());
    }

    @Test
    @DisplayName("4. POST /customers creates guest with 201 Created")
    public void testCustomerCreation() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("fullName", "Le Thi Test");
        req.addProperty("identityCard", "012999888777");
        req.addProperty("phone", "0933888777");
        req.addProperty("email", "test@hotel.com");

        HttpResponse<String> resp = post("/api/v1/customers", req.toString());
        Assertions.assertEquals(201, resp.statusCode());

        JsonObject json = parseJson(resp.body());
        Assertions.assertEquals("success", json.get("status").getAsString());
        JsonObject data = json.getAsJsonObject("data");
        Assertions.assertEquals("Le Thi Test", data.get("fullName").getAsString());
        Assertions.assertEquals(0, data.get("loyaltyPoints").getAsInt());
    }

    @Test
    @DisplayName("5. GET /bookings returns active booking records")
    public void testBookingsList() throws Exception {
        HttpResponse<String> resp = get("/api/v1/bookings");
        Assertions.assertEquals(200, resp.statusCode());

        JsonObject json = parseJson(resp.body());
        Assertions.assertEquals("success", json.get("status").getAsString());
        JsonArray data = json.getAsJsonArray("data");
        Assertions.assertFalse(data.isEmpty());
    }

    @Test
    @DisplayName("6. GET /bookings/{id} returns single booking detail")
    public void testBookingGetById() throws Exception {
        HttpResponse<String> resp = get("/api/v1/bookings/1");
        Assertions.assertEquals(200, resp.statusCode());

        JsonObject json = parseJson(resp.body());
        Assertions.assertEquals("success", json.get("status").getAsString());
        JsonObject data = json.getAsJsonObject("data");
        Assertions.assertEquals(1, data.get("id").getAsInt());
    }

    @Test
    @DisplayName("6. GET /bookings/{id} with non-existent ID returns 404 ERR_404_NOT_FOUND")
    public void testBookingGetById_NotFound() throws Exception {
        HttpResponse<String> resp = get("/api/v1/bookings/9999");
        Assertions.assertEquals(404, resp.statusCode());

        JsonObject json = parseJson(resp.body());
        Assertions.assertEquals("ERR_404_NOT_FOUND", json.get("error_code").getAsString());
    }

    @Test
    @DisplayName("7. POST /bookings without Idempotency-Key returns 400 ERR_400_BAD_REQUEST")
    public void testBookingCreate_MissingIdempotencyKey() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("customerId", 1);
        req.addProperty("roomId", 1);

        Map<String, String> headers = Map.of(
                "Authorization", "Bearer " + generateSignedJwt("FrontDesk", "staff_test", 100)
        );
        HttpResponse<String> resp = post("/api/v1/bookings", req.toString(), headers);
        Assertions.assertEquals(400, resp.statusCode());

        JsonObject json = parseJson(resp.body());
        Assertions.assertEquals("ERR_400_BAD_REQUEST", json.get("error_code").getAsString());
    }

    @Test
    @DisplayName("7. POST /bookings with Idempotency-Key creates booking and handles replay")
    public void testBookingCreate_WithIdempotencyKeyAndReplay() throws Exception {
        String key = "test-idem-key-" + System.currentTimeMillis();
        JsonObject req = new JsonObject();
        req.addProperty("customerId", 1);
        req.addProperty("roomId", 1);
        req.addProperty("totalPrice", 1200000.0);

        Map<String, String> headers = Map.of(
                "Idempotency-Key", key,
                "Authorization", "Bearer " + generateSignedJwt("FrontDesk", "staff_test", 100)
        );

        // 1st request -> 201 Created
        HttpResponse<String> resp1 = post("/api/v1/bookings", req.toString(), headers);
        Assertions.assertEquals(201, resp1.statusCode());
        JsonObject json1 = parseJson(resp1.body());
        int createdId = json1.getAsJsonObject("data").get("id").getAsInt();

        // 2nd request (duplicate key) -> Idempotent replay with same status & body
        HttpResponse<String> resp2 = post("/api/v1/bookings", req.toString(), headers);
        Assertions.assertEquals(201, resp2.statusCode());
        JsonObject json2 = parseJson(resp2.body());
        Assertions.assertEquals(createdId, json2.getAsJsonObject("data").get("id").getAsInt());
    }

    @Test
    @DisplayName("8. PUT /bookings/{id} updates booking and increments version")
    public void testBookingUpdate() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("totalPrice", 1850000.0);

        HttpResponse<String> resp = put("/api/v1/bookings/1", req.toString());
        Assertions.assertEquals(200, resp.statusCode());

        JsonObject json = parseJson(resp.body());
        JsonObject data = json.getAsJsonObject("data");
        Assertions.assertEquals(1850000.0, data.get("totalPrice").getAsDouble());
        Assertions.assertTrue(data.get("version").getAsInt() >= 2);
    }

    @Test
    @DisplayName("9. POST /bookings/{id}/cancel marks booking cancelled and frees room")
    public void testBookingCancel() throws Exception {
        HttpResponse<String> resp = post("/api/v1/bookings/1/cancel", "{}");
        Assertions.assertEquals(200, resp.statusCode());

        JsonObject json = parseJson(resp.body());
        JsonObject data = json.getAsJsonObject("data");
        Assertions.assertEquals("cancelled", data.get("status").getAsString());
    }

    @Test
    @DisplayName("10. POST /folios/{id}/pay without Idempotency-Key returns 400 ERR_400_BAD_REQUEST")
    public void testFolioPay_MissingIdempotencyKey() throws Exception {
        HttpResponse<String> resp = post("/api/v1/folios/1/pay", "{\"amount\":500000.0}");
        Assertions.assertEquals(400, resp.statusCode());

        JsonObject json = parseJson(resp.body());
        Assertions.assertEquals("ERR_400_BAD_REQUEST", json.get("error_code").getAsString());
    }

    @Test
    @DisplayName("10. POST /folios/{id}/pay with Idempotency-Key returns 200 OK with receipt")
    public void testFolioPay_WithIdempotencyKey() throws Exception {
        String key = "pay-key-" + System.currentTimeMillis();
        Map<String, String> headers = Map.of("Idempotency-Key", key);

        HttpResponse<String> resp = post("/api/v1/folios/1/pay", "{\"amount\":500000.0,\"paymentMethod\":\"credit_card\"}", headers);
        Assertions.assertEquals(200, resp.statusCode());

        JsonObject json = parseJson(resp.body());
        JsonObject data = json.getAsJsonObject("data");
        Assertions.assertEquals("paid", data.get("status").getAsString());
        Assertions.assertNotNull(data.get("transactionId"));
    }
}
