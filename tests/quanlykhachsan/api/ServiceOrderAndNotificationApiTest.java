package quanlykhachsan.api;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.Main;
import quanlykhachsan.backend.hotelservice.ServiceOrder;
import quanlykhachsan.backend.hotelservice.ServiceOrderDAO;
import quanlykhachsan.backend.customer.Notification;
import quanlykhachsan.backend.customer.NotificationDAO;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.JwtTokenUtil;
import quanlykhachsan.backend.utils.UpdateDbUtility;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;

public class ServiceOrderAndNotificationApiTest {

    private static HttpServer testServer;
    private static int port = 8092;
    private static String baseUrl = "http://localhost:" + port;
    private static HttpClient client;
    private static boolean dbAvailable = false;

    private static int testCustomerId = 901;
    private static int testOtherCustomerId = 902;
    private static int testRoomId = 901;
    private static int testCheckedInBookingId = 901;
    private static int testConfirmedBookingId = 902;

    @BeforeAll
    public static void setUp() throws Exception {
        quanlykhachsan.support.Phase2TestDatabase.require();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

        try (Connection conn = DatabaseConnection.getConnection()) {
            dbAvailable = true;
            UpdateDbUtility.migratePhase2_1(conn);

            try (Statement s = conn.createStatement()) {
                // Ensure room types & room
                s.executeUpdate("INSERT IGNORE INTO room_types (id, name, base_price, capacity) VALUES (901, 'Phase2 Test Suite', 1500000.00, 2)");
                s.executeUpdate("INSERT IGNORE INTO rooms (id, room_number, room_type_id, price, status) VALUES (" + testRoomId + ", '901', 901, 1500000.00, 'occupied_clean')");

                // Ensure customers
                s.executeUpdate("INSERT IGNORE INTO customers (id, full_name, identity_card, phone, email) VALUES (" + testCustomerId + ", 'Test Guest One', 'ID-901', '0901000901', 'guest901@test.com')");
                s.executeUpdate("INSERT IGNORE INTO customers (id, full_name, identity_card, phone, email) VALUES (" + testOtherCustomerId + ", 'Test Guest Two', 'ID-902', '0902000902', 'guest902@test.com')");

                // Ensure bookings: one checked_in, one confirmed
                s.executeUpdate("REPLACE INTO bookings (id, customer_id, room_id, check_in_date, check_out_date, total_price, status, customer_type) " +
                                "VALUES (" + testCheckedInBookingId + ", " + testCustomerId + ", " + testRoomId + ", NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), 3000000.00, 'checked_in', 'Web')");
                s.executeUpdate("REPLACE INTO bookings (id, customer_id, room_id, check_in_date, check_out_date, total_price, status, customer_type) " +
                                "VALUES (" + testConfirmedBookingId + ", " + testCustomerId + ", " + testRoomId + ", DATE_ADD(NOW(), INTERVAL 5 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), 3000000.00, 'confirmed', 'Web')");

                // Ensure folio for checked-in booking
                s.executeUpdate("INSERT IGNORE INTO folios (id, booking_id, folio_type, balance, status, code) " +
                                "VALUES (901, " + testCheckedInBookingId + ", 'guest', 0.00, 'open', 'FOLIO-GUEST-B901')");

                // Ensure F&B service item
                s.executeUpdate("INSERT IGNORE INTO services (id, name, description, price, status, category) VALUES (901, 'Test Wagyu Beef', 'Wagyu Beef for Testing', 500000.00, 'active','FNB'),(902,'Synthetic Spa','Synthetic Spa',350000.00,'active','SPA')");

                // Ensure customer role and user for resolveCustomerId test
                s.executeUpdate("INSERT IGNORE INTO roles (id, name, description) VALUES (3, 'customer', 'Customer role')");
                s.executeUpdate("REPLACE INTO users (id, username, password, role_id, customer_id) VALUES (955, 'guest_user_955', 'hashed_pwd', 3, " + testCustomerId + ")");
                s.executeUpdate("INSERT IGNORE INTO users(id,username,password,role_id,customer_id) VALUES(901,'synthetic_901','not-a-password',3,901),(902,'synthetic_902','not-a-password',3,902)");
                s.executeUpdate("UPDATE customers SET user_id = 955 WHERE id = " + testCustomerId);
            }
        }

        testServer = Main.createServer(port);
        testServer.start();
    }

    @AfterAll
    public static void tearDown() {
        if (testServer != null) {
            testServer.stop(0);
        }
    }

    @Test
    @DisplayName("TASK-4: POST /api/v1/service-orders rejects non-checked-in booking with HTTP 403")
    public void testRejectNonCheckedInBooking() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(dbAvailable, "Disposable database fixture required");

        String token = JwtTokenUtil.issueToken(testCustomerId, "testguest901", "customer");

        JsonObject payload = new JsonObject();
        payload.addProperty("booking_id", testConfirmedBookingId); // status = 'confirmed', not checked_in
        payload.addProperty("category", "FNB");
        payload.addProperty("item_name", "Test Breakfast");
        payload.addProperty("quantity", 1);
        payload.addProperty("unit_price", 250000.00);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/service-orders"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(403, resp.statusCode());
        Assertions.assertTrue(resp.body().contains("checked_in") || resp.body().contains("ERR_403"));
    }

    @Test
    @DisplayName("TASK-4: POST /api/v1/service-orders enforces IDOR protection (customer A cannot order for customer B)")
    public void testIdorProtection() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(dbAvailable, "Disposable database fixture required");

        // Token belongs to testOtherCustomerId (902), but booking belongs to testCustomerId (901)
        String token = JwtTokenUtil.issueToken(testOtherCustomerId, "testguest902", "customer");

        JsonObject payload = new JsonObject();
        payload.addProperty("booking_id", testCheckedInBookingId);
        payload.addProperty("category", "FNB");
        payload.addProperty("item_name", "Test Burger");
        payload.addProperty("quantity", 1);
        payload.addProperty("unit_price", 150000.00);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/service-orders"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(403, resp.statusCode());
        Assertions.assertTrue(resp.body().contains("IDOR") || resp.body().contains("ERR_403"));
    }

    @Test
    @DisplayName("TASK-5: POST /api/v1/service-orders atomically debits room Folio and returns HTTP 201")
    public void testAtomicServiceOrderFolioDebit() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(dbAvailable, "Disposable database fixture required");

        String token = JwtTokenUtil.issueToken(testCustomerId, "testguest901", "customer");

        JsonObject payload = new JsonObject();
        payload.addProperty("booking_id", testCheckedInBookingId);
        payload.addProperty("category", "FNB");
        payload.addProperty("service_id", 901);
        payload.addProperty("item_name", "Test Wagyu Beef");
        payload.addProperty("quantity", 2);
        payload.addProperty("unit_price", 500000.00);
        payload.addProperty("special_instructions", "Well done");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/service-orders"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(201, resp.statusCode());
        Assertions.assertTrue(resp.body().contains("folio_transaction_id"));

        // Verify Folio balance increased by 1,000,000
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement q = conn.prepareStatement("SELECT balance FROM folios WHERE booking_id = ? AND status = 'open'")) {
            q.setInt(1, testCheckedInBookingId);
            try (ResultSet r = q.executeQuery()) {
                Assertions.assertTrue(r.next());
                BigDecimal balance = r.getBigDecimal(1);
                Assertions.assertTrue(balance.compareTo(new BigDecimal("1000000.00")) >= 0);
            }
        }

        // Verify folio_transaction_id is stored in service_orders (JDBC Generated Keys Fix)
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement q = conn.prepareStatement("SELECT folio_transaction_id FROM service_orders WHERE booking_id = ? ORDER BY id DESC LIMIT 1")) {
            q.setInt(1, testCheckedInBookingId);
            try (ResultSet r = q.executeQuery()) {
                Assertions.assertTrue(r.next());
                long fTransId = r.getLong(1);
                Assertions.assertFalse(r.wasNull());
                Assertions.assertTrue(fTransId > 0, "folio_transaction_id must not be null or 0");
            }
        }
    }

    @Test
    @DisplayName("TASK-6: GET /api/v1/service-orders/active returns pending orders for staff")
    public void testActiveServiceOrdersEndpoint() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(dbAvailable, "Disposable database fixture required");

        String staffToken = JwtTokenUtil.issueToken(1, "admin_user", "admin");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/service-orders/active"))
                .header("Authorization", "Bearer " + staffToken)
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, resp.statusCode());
        Assertions.assertTrue(resp.body().contains("pending_count"));
        Assertions.assertTrue(resp.body().contains("status"));
    }

    @Test
    @DisplayName("TASK-6: GET & PUT /api/v1/notifications handles customer notifications lifecycle")
    public void testNotificationsLifecycle() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(dbAvailable, "Disposable database fixture required");

        // Seed a test notification
        NotificationDAO nDao = new NotificationDAO();
        Notification n = new Notification();
        n.setCustomerId(testCustomerId);
        n.setBookingId(testCheckedInBookingId);
        n.setNotificationType("service_update");
        n.setTitle("Đơn phục vụ phòng đang chuẩn bị");
        n.setMessage("Món ăn đang được bếp trưởng chuẩn bị");
        long notifId = nDao.createNotification(n);
        Assertions.assertTrue(notifId > 0);

        String customerToken = JwtTokenUtil.issueToken(testCustomerId, "testguest901", "customer");

        // GET notifications
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/notifications"))
                .header("Authorization", "Bearer " + customerToken)
                .GET()
                .build();

        HttpResponse<String> getResp = client.send(getReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, getResp.statusCode());
        Assertions.assertTrue(getResp.body().contains("unread_count"));

        // PUT mark as read
        HttpRequest putReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/notifications/" + notifId + "/read"))
                .header("Authorization", "Bearer " + customerToken)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .PUT(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> putResp = client.send(putReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, putResp.statusCode());
        Assertions.assertTrue(putResp.body().contains("is_read"));
    }

    @Test
    @DisplayName("TASK-7: OPTIONS Preflight request to /api/v1/service-orders without Auth header passes with HTTP 204/200")
    public void testOptionsPreflightBypassWithoutAuth() throws Exception {
        HttpRequest optionsReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/service-orders"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> resp = client.send(optionsReq, HttpResponse.BodyHandlers.ofString());
        // Must NOT return 401 Unauthorized during preflight check
        Assertions.assertNotEquals(401, resp.statusCode(), "OPTIONS request must never return 401 Unauthorized");
        Assertions.assertTrue(resp.statusCode() == 204 || resp.statusCode() == 200, "OPTIONS preflight should return 204 or 200");
        Assertions.assertTrue(resp.headers().firstValue("Access-Control-Allow-Origin").isPresent());

        // Test notifications endpoint OPTIONS as well
        HttpRequest notifOptionsReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/notifications"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> notifOptionsResp = client.send(notifOptionsReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertTrue(notifOptionsResp.statusCode() == 204 || notifOptionsResp.statusCode() == 200);
    }

    @Test
    @DisplayName("TASK-8: PUT /api/v1/service-orders/{id}/status extracts ID via regex without ArrayIndexOutOfBoundsException")
    public void testSafePathVariableServiceOrderStatus() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(dbAvailable, "Disposable database fixture required");

        String staffToken = JwtTokenUtil.issueToken(1, "admin_user", "admin");

        JsonObject body = new JsonObject();
        body.addProperty("order_status", "ACCEPTED");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/service-orders/305/status"))
                .header("Authorization", "Bearer " + staffToken)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .PUT(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        // Since order 305 doesn't exist, it should return 404 NOT_FOUND, NOT 500 ArrayIndexOutOfBoundsException
        Assertions.assertNotEquals(500, resp.statusCode(), "Should not throw 500 ArrayIndexOutOfBoundsException");
        Assertions.assertTrue(resp.statusCode() == 404 || resp.statusCode() == 200);
    }

    @Test
    @DisplayName("TASK-9: PUT /api/v1/notifications/{id}/read extracts ID via regex safely")
    public void testSafePathVariableNotificationRead() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(dbAvailable, "Disposable database fixture required");

        String customerToken = JwtTokenUtil.issueToken(testCustomerId, "testguest901", "customer");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/notifications/88888/read"))
                .header("Authorization", "Bearer " + customerToken)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .PUT(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        // Non-existent notification should return 404 NOT_FOUND, NOT 500 ArrayIndexOutOfBoundsException
        Assertions.assertNotEquals(500, resp.statusCode(), "Should not throw 500 ArrayIndexOutOfBoundsException");
        Assertions.assertTrue(resp.statusCode() == 404 || resp.statusCode() == 200);
    }

    @Test
    @DisplayName("TASK-10: Pure unit test verifying regex path variable extraction patterns")
    public void testRegexPathVariableExtractionLogic() {
        java.util.regex.Pattern orderPattern = java.util.regex.Pattern.compile(".*/service-orders/(\\d+)(?:/status)?/?$");
        java.util.regex.Pattern notifPattern = java.util.regex.Pattern.compile(".*/notifications/(\\d+)(?:/read)?/?$");

        // Valid service order status paths
        java.util.regex.Matcher m1 = orderPattern.matcher("/api/v1/service-orders/305/status");
        Assertions.assertTrue(m1.matches());
        Assertions.assertEquals("305", m1.group(1));

        java.util.regex.Matcher m2 = orderPattern.matcher("/api/v1/service-orders/42");
        Assertions.assertTrue(m2.matches());
        Assertions.assertEquals("42", m2.group(1));

        java.util.regex.Matcher m3 = orderPattern.matcher("/api/v1/service-orders/42/");
        Assertions.assertTrue(m3.matches());
        Assertions.assertEquals("42", m3.group(1));

        // Invalid service order paths
        Assertions.assertFalse(orderPattern.matcher("/api/v1/service-orders").matches());
        Assertions.assertFalse(orderPattern.matcher("/api/v1/service-orders/active").matches());
        Assertions.assertFalse(orderPattern.matcher("/api/v1/service-orders/abc/status").matches());

        // Valid notification mark-as-read paths
        java.util.regex.Matcher n1 = notifPattern.matcher("/api/v1/notifications/789/read");
        Assertions.assertTrue(n1.matches());
        Assertions.assertEquals("789", n1.group(1));

        java.util.regex.Matcher n2 = notifPattern.matcher("/api/v1/notifications/789");
        Assertions.assertTrue(n2.matches());
        Assertions.assertEquals("789", n2.group(1));

        // Invalid notification paths
        Assertions.assertFalse(notifPattern.matcher("/api/v1/notifications").matches());
        Assertions.assertFalse(notifPattern.matcher("/api/v1/notifications/unread").matches());
    }

    @Test
    @DisplayName("BUG-1 FIX: Price tampering is prevented - database price overrides client unit_price")
    public void testPriceTamperingPreventedAuthoritativeDbPrice() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(dbAvailable, "Disposable database fixture required");

        String token = JwtTokenUtil.issueToken(testCustomerId, "testguest901", "customer");

        JsonObject payload = new JsonObject();
        payload.addProperty("booking_id", testCheckedInBookingId);
        payload.addProperty("category", "FNB");
        payload.addProperty("service_id", 901); // Wagyu Beef listed at 500,000 VND
        payload.addProperty("item_name", "Test Wagyu Beef");
        payload.addProperty("quantity", 1);
        payload.addProperty("unit_price", 1000.00); // Malicious attempt to tamper price to 1,000 VND

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/service-orders"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(201, resp.statusCode());
        // Verify response has unit_price 500000.00 and final_price 500000.00, NOT 1000.00!
        Assertions.assertTrue(resp.body().contains("500000"), "Authoritative DB price 500,000 VND must override client price 1,000 VND");
        Assertions.assertFalse(resp.body().contains("\"unit_price\":1000"), "Client tampered price must be rejected");
    }

    @Test
    @DisplayName("BUG-2 FIX: resolveCustomerId maps user_id to actual customer_id and avoids false 403 IDOR")
    public void testResolveCustomerIdWithUserIdToCustomerIdMapping() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(dbAvailable, "Disposable database fixture required");

        // JWT contains userId 955 (from users table), while booking has customer_id 901
        String token = JwtTokenUtil.issueToken(955, "guest_user_955", "customer");

        JsonObject payload = new JsonObject();
        payload.addProperty("booking_id", testCheckedInBookingId); // customer_id = 901
        payload.addProperty("category", "FNB");
        payload.addProperty("service_id", 901);
        payload.addProperty("item_name", "Test Wagyu Beef");
        payload.addProperty("quantity", 1);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/service-orders"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        // Must succeed with 201 Created and NOT 403 Forbidden!
        Assertions.assertEquals(201, resp.statusCode(), "resolveCustomerId must look up customer_id from user_id without 403 IDOR false positive");
    }

    @Test
    @DisplayName("BUG-5 FIX: ISO-8601 scheduled_time string parsed properly without Timestamp.valueOf exception")
    public void testScheduledTimeIso8601Parsing() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(dbAvailable, "Disposable database fixture required");

        String token = JwtTokenUtil.issueToken(testCustomerId, "testguest901", "customer");

        JsonObject payload = new JsonObject();
        payload.addProperty("booking_id", testCheckedInBookingId);
        payload.addProperty("category", "SPA");
        payload.addProperty("service_id",902);
        payload.addProperty("item_name", "Aromatherapy Massage");
        payload.addProperty("quantity", 1);
        payload.addProperty("unit_price", 350000.00);
        payload.addProperty("scheduled_time", java.time.Instant.now().plusSeconds(3600).toString()); // ISO-8601 with Z and milliseconds

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/service-orders"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(201, resp.statusCode());

        // Check scheduled_time in DB is non-null
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement q = conn.prepareStatement("SELECT scheduled_time FROM service_orders WHERE booking_id = ? AND category = 'SPA' ORDER BY id DESC LIMIT 1")) {
            q.setInt(1, testCheckedInBookingId);
            try (ResultSet r = q.executeQuery()) {
                Assertions.assertTrue(r.next());
                java.sql.Timestamp st = r.getTimestamp(1);
                Assertions.assertNotNull(st, "scheduled_time must be successfully parsed and stored in database");
                Assertions.assertTrue(st.toString().contains("2026-10-19 14:30:00"));
            }
        }
    }

    @Test
    @DisplayName("BUG-5 UNIT TEST: ISO-8601 String sanitization and parsing unit test")
    public void testIso8601TimestampSanitization() {
        String inputIsoZ = "2026-10-19T14:30:00.000Z";
        String sanitized = inputIsoZ.trim().replace("Z", "").replace("T", " ");
        if (sanitized.length() > 19) {
            sanitized = sanitized.substring(0, 19);
        }
        if (sanitized.length() == 16) {
            sanitized += ":00";
        }
        java.sql.Timestamp ts = java.sql.Timestamp.valueOf(sanitized);
        Assertions.assertEquals("2026-10-19 14:30:00.0", ts.toString());

        String inputShort = "2026-10-19T14:30";
        String sanitizedShort = inputShort.trim().replace("Z", "").replace("T", " ");
        if (sanitizedShort.length() > 19) {
            sanitizedShort = sanitizedShort.substring(0, 19);
        }
        if (sanitizedShort.length() == 16) {
            sanitizedShort += ":00";
        }
        java.sql.Timestamp tsShort = java.sql.Timestamp.valueOf(sanitizedShort);
        Assertions.assertEquals("2026-10-19 14:30:00.0", tsShort.toString());
    }
}
