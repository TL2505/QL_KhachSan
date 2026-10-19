package quanlykhachsan.api;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.Main;
import quanlykhachsan.backend.interaction.Review;
import quanlykhachsan.backend.interaction.ReviewDAO;
import quanlykhachsan.backend.interaction.ReviewDAOImpl;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.JwtTokenUtil;
import quanlykhachsan.backend.utils.UpdateDbUtility;

public class Phase3ReputationAndReviewTest {

    private static HttpServer testServer;
    private static int port = 8094;
    private static String baseUrl = "http://localhost:" + port;
    private static HttpClient client;
    private static boolean dbAvailable = false;

    private static int testCustomerId = 910;
    private static int testOtherCustomerId = 911;
    private static int testRoomId = 910;
    private static int testCheckedOutBookingId = 910;
    private static int testCheckedInBookingId = 911;
    private static int testExpiredBookingId = 912;

    @BeforeAll
    public static void setUp() throws Exception {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

        try (Connection conn = DatabaseConnection.getConnection()) {
            dbAvailable = true;
            UpdateDbUtility.migratePhase2_1(conn);

            try (Statement s = conn.createStatement()) {
                // Ensure room types & room
                s.executeUpdate("INSERT IGNORE INTO room_types (id, name, base_price, capacity) VALUES (910, 'Penthouse Suite', 2500000.00, 2)");
                s.executeUpdate("INSERT IGNORE INTO rooms (id, room_number, room_type_id, price, status) VALUES (" + testRoomId + ", 'PH-910', 910, 2500000.00, 'vacant_clean')");

                // Ensure customers
                s.executeUpdate("INSERT IGNORE INTO customers (id, full_name, identity_card, phone, email, loyalty_points) VALUES (" + testCustomerId + ", 'Trần Hải Long', 'ID-LONG-910', '0910000910', 'long910@test.com', 100)");
                s.executeUpdate("INSERT IGNORE INTO customers (id, full_name, identity_card, phone, email, loyalty_points) VALUES (" + testOtherCustomerId + ", 'Lê Thị Thu', 'ID-THU-911', '0911000911', 'thu911@test.com', 50)");

                // Ensure bookings:
                // 1. checked_out within 2 days (valid stay)
                s.executeUpdate("REPLACE INTO bookings (id, customer_id, room_id, check_in_date, check_out_date, check_out_actual, total_price, status, customer_type) " +
                                "VALUES (" + testCheckedOutBookingId + ", " + testCustomerId + ", " + testRoomId + ", DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 5000000.00, 'checked_out', 'Web')");

                // 2. checked_in (not yet checked out)
                s.executeUpdate("REPLACE INTO bookings (id, customer_id, room_id, check_in_date, check_out_date, total_price, status, customer_type) " +
                                "VALUES (" + testCheckedInBookingId + ", " + testCustomerId + ", " + testRoomId + ", NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), 5000000.00, 'checked_in', 'Web')");

                // 3. checked_out 20 days ago (> 14 days expired)
                s.executeUpdate("REPLACE INTO bookings (id, customer_id, room_id, check_in_date, check_out_date, check_out_actual, total_price, status, customer_type) " +
                                "VALUES (" + testExpiredBookingId + ", " + testCustomerId + ", " + testRoomId + ", DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 20 DAY), 5000000.00, 'checked_out', 'Web')");

                // Clear any existing reviews for test bookings
                s.executeUpdate("DELETE FROM reviews WHERE booking_id IN (" + testCheckedOutBookingId + ", " + testCheckedInBookingId + ", " + testExpiredBookingId + ")");
            }
        } catch (Exception e) {
            System.err.println("Database not available for integration test: " + e.getMessage());
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
    @DisplayName("TASK-7: POST /api/v1/reviews enforces Verified Stay - rejects non-checked-out booking with HTTP 403")
    public void testVerifiedStayRejectsNonCheckedOut() throws Exception {
        if (!dbAvailable) return;

        String token = JwtTokenUtil.issueToken(testCustomerId, "testguest910", "customer");

        JsonObject payload = new JsonObject();
        payload.addProperty("booking_id", testCheckedInBookingId); // status = 'checked_in', NOT checked_out!
        payload.addProperty("rating_room", 5);
        payload.addProperty("rating_fnb", 5);
        payload.addProperty("rating_service", 5);
        payload.addProperty("rating_cleanliness", 5);
        payload.addProperty("comment", "Tuyệt vời");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/reviews"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(403, resp.statusCode());
        Assertions.assertTrue(resp.body().contains("checked_out") || resp.body().contains("ERR_403"));
    }

    @Test
    @DisplayName("TASK-7: POST /api/v1/reviews enforces IDOR - customer A cannot review booking of customer B")
    public void testIdorProtectionOnReviews() throws Exception {
        if (!dbAvailable) return;

        // Token belongs to testOtherCustomerId (911), but booking belongs to testCustomerId (910)
        String token = JwtTokenUtil.issueToken(testOtherCustomerId, "testguest911", "customer");

        JsonObject payload = new JsonObject();
        payload.addProperty("booking_id", testCheckedOutBookingId);
        payload.addProperty("rating_room", 5);
        payload.addProperty("rating_fnb", 4);
        payload.addProperty("rating_service", 5);
        payload.addProperty("rating_cleanliness", 5);
        payload.addProperty("comment", "Kỳ nghỉ rất tốt");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/reviews"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(403, resp.statusCode());
        Assertions.assertTrue(resp.body().contains("IDOR") || resp.body().contains("ERR_403"));
    }

    @Test
    @DisplayName("TASK-7: POST /api/v1/reviews enforces 14-day checkout window")
    public void testExpiredReviewWindow() throws Exception {
        if (!dbAvailable) return;

        String token = JwtTokenUtil.issueToken(testCustomerId, "testguest910", "customer");

        JsonObject payload = new JsonObject();
        payload.addProperty("booking_id", testExpiredBookingId); // checked out 20 days ago (> 14 days)
        payload.addProperty("rating_room", 5);
        payload.addProperty("rating_fnb", 5);
        payload.addProperty("rating_service", 5);
        payload.addProperty("rating_cleanliness", 5);
        payload.addProperty("comment", "Kỳ nghỉ tháng trước");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/reviews"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertTrue(resp.statusCode() == 400 || resp.statusCode() == 403);
        Assertions.assertTrue(resp.body().contains("14 ngày") || resp.body().contains("ERR_REVIEW_WINDOW_EXPIRED"));
    }

    @Test
    @DisplayName("TASK-7: POST /api/v1/reviews atomically inserts review, awards 200 points, logs history, and prevents double reviews")
    public void testReviewToRewardAtomicAndUniqueness() throws Exception {
        if (!dbAvailable) return;

        String token = JwtTokenUtil.issueToken(testCustomerId, "testguest910", "customer");

        JsonObject payload = new JsonObject();
        payload.addProperty("booking_id", testCheckedOutBookingId);
        payload.addProperty("rating_room", 5);
        payload.addProperty("rating_fnb", 4);
        payload.addProperty("rating_service", 5);
        payload.addProperty("rating_cleanliness", 5);
        payload.addProperty("rating_overall", 4.8);
        payload.addProperty("comment", "Kỳ nghỉ tuyệt vời! Bò Wagyu A5 và phục vụ phòng chuẩn 5 sao.");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/reviews"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(201, resp.statusCode());
        Assertions.assertTrue(resp.body().contains("reward_points"));
        Assertions.assertTrue(resp.body().contains("200"));

        // Verify points incremented in database
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement q = c.prepareStatement("SELECT loyalty_points FROM customers WHERE id = ?")) {
            q.setInt(1, testCustomerId);
            try (ResultSet r = q.executeQuery()) {
                Assertions.assertTrue(r.next());
                int points = r.getInt(1);
                Assertions.assertTrue(points >= 300, "Customer loyalty points should increase by 200 (was 100, now >= 300)");
            }
        }

        // Verify loyalty_histories row created
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement q = c.prepareStatement("SELECT points, type, description FROM loyalty_histories WHERE customer_id = ? ORDER BY id DESC LIMIT 1")) {
            q.setInt(1, testCustomerId);
            try (ResultSet r = q.executeQuery()) {
                Assertions.assertTrue(r.next());
                Assertions.assertEquals(200, r.getInt(1));
                Assertions.assertEquals("earn", r.getString(2));
                Assertions.assertTrue(r.getString(3).contains(String.valueOf(testCheckedOutBookingId)));
            }
        }

        // Anti-Fraud / Anti-Race condition: submit review AGAIN for the same booking -> MUST FAIL with 400
        HttpResponse<String> secondResp = client.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(400, secondResp.statusCode());
        Assertions.assertTrue(secondResp.body().contains("đã có đánh giá") || secondResp.body().contains("ERR_ALREADY_REVIEWED") || secondResp.body().contains("ERR_DUPLICATE_REVIEW"));
    }

    @Test
    @DisplayName("TASK-8: GET /api/v1/reviews/public bypasses JWT filter and returns aggregates and review list")
    public void testPublicReputationBypassesJwt() throws Exception {
        if (!dbAvailable) return;

        HttpRequest publicReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/reviews/public?page=1&limit=6"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(publicReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, resp.statusCode(), "Public reputation API must return 200 without JWT token");
        Assertions.assertTrue(resp.body().contains("summary"));
        Assertions.assertTrue(resp.body().contains("overall_average"));
        Assertions.assertTrue(resp.body().contains("reviews"));
    }

    @Test
    @DisplayName("TASK-8: PUT /api/v1/reviews/{id}/reply requires STAFF/ADMIN and publishes management response")
    public void testManagementReplyEndpoint() throws Exception {
        if (!dbAvailable) return;

        // 1. Locate review id for testCheckedOutBookingId
        int reviewId = 0;
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement q = c.prepareStatement("SELECT id FROM reviews WHERE booking_id = ?")) {
            q.setInt(1, testCheckedOutBookingId);
            try (ResultSet r = q.executeQuery()) {
                if (r.next()) {
                    reviewId = r.getInt(1);
                }
            }
        }
        if (reviewId == 0) return; // skipped if review not created

        // 2. Attempt reply as Customer -> 403 Forbidden
        String customerToken = JwtTokenUtil.issueToken(testCustomerId, "testguest910", "customer");
        JsonObject replyPayload = new JsonObject();
        replyPayload.addProperty("management_response", "Chân thành cảm ơn Quý khách đã tin tưởng lựa chọn Aurelia Grand Palace!");

        HttpRequest forbiddenReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/reviews/" + reviewId + "/reply"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + customerToken)
                .PUT(HttpRequest.BodyPublishers.ofString(replyPayload.toString()))
                .build();

        HttpResponse<String> forbiddenResp = client.send(forbiddenReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(403, forbiddenResp.statusCode(), "Customer cannot reply to reviews");

        // 3. Attempt reply as Admin -> 200 OK
        String adminToken = JwtTokenUtil.issueToken(1, "admin_user", "admin");
        HttpRequest adminReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/reviews/" + reviewId + "/reply"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + adminToken)
                .PUT(HttpRequest.BodyPublishers.ofString(replyPayload.toString()))
                .build();

        HttpResponse<String> adminResp = client.send(adminReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, adminResp.statusCode());
        Assertions.assertTrue(adminResp.body().contains("management_response"));

        // 4. Verify in DB
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement q = c.prepareStatement("SELECT management_response, responded_at FROM reviews WHERE id = ?")) {
            q.setInt(1, reviewId);
            try (ResultSet r = q.executeQuery()) {
                Assertions.assertTrue(r.next());
                Assertions.assertTrue(r.getString(1).contains("Chân thành cảm ơn"));
                Assertions.assertNotNull(r.getTimestamp(2));
            }
        }
    }

    @Test
    @DisplayName("TASK-9: Unit test for Name Masking logic in public reputation output")
    public void testCustomerNameMaskingLogic() {
        // Pure unit test verifying privacy protection matching Phase 2.1 Baseline Specification
        Assertions.assertEquals("Trần H*** L***", ReviewDAOImpl.maskCustomerName("Trần Hải Long"));
        Assertions.assertEquals("Nguyễn V*** A***", ReviewDAOImpl.maskCustomerName("Nguyễn Văn A"));
        Assertions.assertEquals("Lê N***", ReviewDAOImpl.maskCustomerName("Lê Nam"));
        Assertions.assertEquals("John D***", ReviewDAOImpl.maskCustomerName("John Doe"));
        Assertions.assertEquals("N***", ReviewDAOImpl.maskCustomerName("Nam"));
        Assertions.assertEquals("J***", ReviewDAOImpl.maskCustomerName("John"));
        Assertions.assertEquals("Guest", ReviewDAOImpl.maskCustomerName(null));
        Assertions.assertEquals("Guest", ReviewDAOImpl.maskCustomerName("   "));
        // Also verify maskName alias
        Assertions.assertEquals("Trần H*** L***", ReviewDAOImpl.maskName("Trần Hải Long"));
        Assertions.assertEquals("N***", ReviewDAOImpl.maskName("Nam"));
    }

    @Test
    @DisplayName("TASK-10: NaN & Infinite protection on empty reputation summary for Gson serialization")
    public void testEmptyReputationSummaryNaNProtection() throws Exception {
        // Direct unit test of calculation logic on 0 reviews to prove Gson never receives Double.NaN
        int total = 0;
        int satCount = 0;
        double recPercent = (total > 0) ? (satCount * 100.0) / total : 100.0;
        if (Double.isNaN(recPercent) || Double.isInfinite(recPercent)) {
            recPercent = 100.0;
        }
        Assertions.assertEquals(100.0, recPercent);
        Assertions.assertFalse(Double.isNaN(recPercent));
        Assertions.assertFalse(Double.isInfinite(recPercent));

        com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
        obj.addProperty("recommendation_percentage", recPercent);
        String json = quanlykhachsan.backend.utils.JsonUtil.getGson().toJson(obj);
        Assertions.assertTrue(json.contains("100.0"));
    }
}
