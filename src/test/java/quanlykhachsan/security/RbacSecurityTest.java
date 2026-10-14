package quanlykhachsan.security;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import quanlykhachsan.BaseIntegrationTest;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Automated RBAC Matrix Security Verification Test Suite (TASK-D2.2).
 * <p>
 * Exercises all critical endpoints using role-scoped JWT tokens across the 4 key roles:
 * - Admin
 * - FrontDesk
 * - Housekeeping
 * - F&B
 * <p>
 * Definition of Done (DoD) Criteria:
 * 1. Housekeeping accessing payment endpoint receives 403 Forbidden (ERR_403_FORBIDDEN).
 * 2. Front Desk accessing user admin endpoint receives 403 Forbidden (ERR_403_FORBIDDEN).
 * 3. Admin accesses all endpoints successfully (200/201 OK).
 * 4. Comprehensive systematic matrix verification with zero 500 error leakages.
 */
public class RbacSecurityTest extends BaseIntegrationTest {

    public static final String ROLE_ADMIN = "Admin";
    public static final String ROLE_FRONT_DESK = "FrontDesk";
    public static final String ROLE_HOUSEKEEPING = "Housekeeping";
    public static final String ROLE_FNB = "F&B";

    /**
     * Generates a cryptographically valid HMAC-SHA256 signed JWT Bearer token with the given role.
     */
    public static String generateJwt(String role, String username, int userId) {
        return generateSignedJwt(role, username, userId);
    }

    private Map<String, String> authHeaders(String role) {
        String token = generateJwt(role, "user_" + role.toLowerCase(), 100);
        return Map.of("Authorization", "Bearer " + token);
    }

    private Map<String, String> authAndIdemHeaders(String role) {
        String token = generateJwt(role, "user_" + role.toLowerCase(), 100);
        return Map.of(
                "Authorization", "Bearer " + token,
                "Idempotency-Key", "idem-" + UUID.randomUUID()
        );
    }

    @Test
    @DisplayName("DoD 1: Housekeeping accessing payment endpoint receives 403 Forbidden")
    public void testHousekeeping_AccessingPaymentEndpoint_Receives403Forbidden() throws Exception {
        System.out.println("🛡️ [RBAC DoD 1] Verifying Housekeeping blocked from payment endpoint...");

        Map<String, String> headers = authAndIdemHeaders(ROLE_HOUSEKEEPING);
        JsonObject body = new JsonObject();
        body.addProperty("amount", 500000.0);
        body.addProperty("paymentMethod", "credit_card");

        HttpResponse<String> resp = post("/api/v1/folios/1/pay", body.toString(), headers);

        System.out.println("   • Housekeeping -> POST /api/v1/folios/1/pay: Status = " + resp.statusCode());
        Assertions.assertEquals(403, resp.statusCode(),
                "DoD INVARIANT: Housekeeping must receive 403 Forbidden on payment endpoint");

        JsonObject err = parseJson(resp.body());
        Assertions.assertEquals("ERR_403_FORBIDDEN", err.get("error_code").getAsString(),
                "Response must contain standard error code ERR_403_FORBIDDEN");
        Assertions.assertTrue(err.get("message").getAsString().contains("Housekeeping"),
                "Error message should explain Housekeeping is not permitted");
    }

    @Test
    @DisplayName("DoD 2: Front Desk accessing user admin endpoint receives 403 Forbidden")
    public void testFrontDesk_AccessingUserAdminEndpoint_Receives403Forbidden() throws Exception {
        System.out.println("🛡️ [RBAC DoD 2] Verifying Front Desk blocked from user administration...");

        Map<String, String> headers = authHeaders(ROLE_FRONT_DESK);

        HttpResponse<String> resp = get("/api/v1/users", headers);

        System.out.println("   • FrontDesk -> GET /api/v1/users: Status = " + resp.statusCode());
        Assertions.assertEquals(403, resp.statusCode(),
                "DoD INVARIANT: Front Desk must receive 403 Forbidden on user admin endpoint");

        JsonObject err = parseJson(resp.body());
        Assertions.assertEquals("ERR_403_FORBIDDEN", err.get("error_code").getAsString(),
                "Response must contain standard error code ERR_403_FORBIDDEN");
        Assertions.assertTrue(err.get("message").getAsString().contains("FrontDesk"),
                "Error message should explain FrontDesk is not permitted");
    }

    @Test
    @DisplayName("DoD 3: Admin role accesses all endpoints successfully (HTTP 200/201 OK)")
    public void testAdmin_AccessesAllEndpoints_ReceivesSuccess() throws Exception {
        System.out.println("🛡️ [RBAC DoD 3] Verifying Admin role can access all endpoints...");

        // 1. User Admin endpoint
        HttpResponse<String> userResp = get("/api/v1/users", authHeaders(ROLE_ADMIN));
        System.out.println("   • Admin -> GET /api/v1/users: Status = " + userResp.statusCode());
        Assertions.assertEquals(200, userResp.statusCode(), "Admin must access /api/v1/users with 200 OK");
        Assertions.assertEquals("success", parseJson(userResp.body()).get("status").getAsString());

        // 2. Payment endpoint
        JsonObject payBody = new JsonObject();
        payBody.addProperty("amount", 750000.0);
        payBody.addProperty("paymentMethod", "credit_card");
        HttpResponse<String> payResp = post("/api/v1/folios/1/pay", payBody.toString(), authAndIdemHeaders(ROLE_ADMIN));
        System.out.println("   • Admin -> POST /api/v1/folios/1/pay: Status = " + payResp.statusCode());
        Assertions.assertEquals(200, payResp.statusCode(), "Admin must access payment endpoint with 200 OK");
        Assertions.assertEquals("success", parseJson(payResp.body()).get("status").getAsString());

        // 3. Room Management
        HttpResponse<String> roomsResp = get("/api/v1/rooms", authHeaders(ROLE_ADMIN));
        System.out.println("   • Admin -> GET /api/v1/rooms: Status = " + roomsResp.statusCode());
        Assertions.assertEquals(200, roomsResp.statusCode(), "Admin must access /api/v1/rooms with 200 OK");

        // 4. Booking Management
        HttpResponse<String> bookingsResp = get("/api/v1/bookings", authHeaders(ROLE_ADMIN));
        System.out.println("   • Admin -> GET /api/v1/bookings: Status = " + bookingsResp.statusCode());
        Assertions.assertEquals(200, bookingsResp.statusCode(), "Admin must access /api/v1/bookings with 200 OK");
    }

    @Test
    @DisplayName("Front Desk role successfully processes payments and accesses bookings")
    public void testFrontDesk_CanAccessPaymentAndBookings() throws Exception {
        System.out.println("🛡️ Verifying Front Desk access to operations & payments...");

        // Front Desk processes payment -> 200 OK
        JsonObject payBody = new JsonObject();
        payBody.addProperty("amount", 200000.0);
        HttpResponse<String> payResp = post("/api/v1/folios/1/pay", payBody.toString(), authAndIdemHeaders(ROLE_FRONT_DESK));
        Assertions.assertEquals(200, payResp.statusCode(), "Front Desk is authorized to process payments");

        // Front Desk lists bookings -> 200 OK
        HttpResponse<String> bookResp = get("/api/v1/bookings", authHeaders(ROLE_FRONT_DESK));
        Assertions.assertEquals(200, bookResp.statusCode(), "Front Desk is authorized to view bookings");

        // Front Desk lists rooms -> 200 OK
        HttpResponse<String> roomsResp = get("/api/v1/rooms", authHeaders(ROLE_FRONT_DESK));
        Assertions.assertEquals(200, roomsResp.statusCode(), "Front Desk is authorized to view rooms");
    }

    @Test
    @DisplayName("Housekeeping can inspect rooms but cannot modify bookings or access admin")
    public void testHousekeeping_CanAccessRooms_CannotModifyBookings() throws Exception {
        System.out.println("🛡️ Verifying Housekeeping boundary guardrails...");

        // Rooms access -> 200 OK
        HttpResponse<String> roomsResp = get("/api/v1/rooms", authHeaders(ROLE_HOUSEKEEPING));
        Assertions.assertEquals(200, roomsResp.statusCode(), "Housekeeping is authorized to view rooms");

        // Availability access -> 200 OK
        HttpResponse<String> availResp = get("/api/v1/rooms/availability", authHeaders(ROLE_HOUSEKEEPING));
        Assertions.assertEquals(200, availResp.statusCode(), "Housekeeping is authorized to view room availability");

        // Booking create -> 403 Forbidden
        JsonObject bkgReq = new JsonObject();
        bkgReq.addProperty("customerId", 1);
        bkgReq.addProperty("roomId", 1);
        HttpResponse<String> bkgResp = post("/api/v1/bookings", bkgReq.toString(), authAndIdemHeaders(ROLE_HOUSEKEEPING));
        Assertions.assertEquals(403, bkgResp.statusCode(), "Housekeeping cannot create bookings");
        Assertions.assertEquals("ERR_403_FORBIDDEN", parseJson(bkgResp.body()).get("error_code").getAsString());

        // User Admin -> 403 Forbidden
        HttpResponse<String> adminResp = get("/api/v1/users", authHeaders(ROLE_HOUSEKEEPING));
        Assertions.assertEquals(403, adminResp.statusCode(), "Housekeeping cannot access user admin");
        Assertions.assertEquals("ERR_403_FORBIDDEN", parseJson(adminResp.body()).get("error_code").getAsString());
    }

    @Test
    @DisplayName("F&B role is blocked from payment, user admin, and booking creation")
    public void testFnB_CanAccessRooms_CannotAccessPaymentOrUserAdmin() throws Exception {
        System.out.println("🛡️ Verifying F&B role operational boundaries...");

        // Rooms view -> 200 OK
        HttpResponse<String> roomsResp = get("/api/v1/rooms", authHeaders(ROLE_FNB));
        Assertions.assertEquals(200, roomsResp.statusCode(), "F&B is authorized to view rooms");

        // Payment -> 403 Forbidden
        JsonObject payBody = new JsonObject();
        payBody.addProperty("amount", 100000.0);
        HttpResponse<String> payResp = post("/api/v1/folios/1/pay", payBody.toString(), authAndIdemHeaders(ROLE_FNB));
        Assertions.assertEquals(403, payResp.statusCode(), "F&B cannot access payment endpoint directly");
        Assertions.assertEquals("ERR_403_FORBIDDEN", parseJson(payResp.body()).get("error_code").getAsString());

        // User Admin -> 403 Forbidden
        HttpResponse<String> adminResp = get("/api/v1/users", authHeaders(ROLE_FNB));
        Assertions.assertEquals(403, adminResp.statusCode(), "F&B cannot access user admin");
        Assertions.assertEquals("ERR_403_FORBIDDEN", parseJson(adminResp.body()).get("error_code").getAsString());

        // Booking create -> 403 Forbidden
        JsonObject bkgReq = new JsonObject();
        bkgReq.addProperty("customerId", 1);
        bkgReq.addProperty("roomId", 1);
        HttpResponse<String> bkgResp = post("/api/v1/bookings", bkgReq.toString(), authAndIdemHeaders(ROLE_FNB));
        Assertions.assertEquals(403, bkgResp.statusCode(), "F&B cannot create bookings");
        Assertions.assertEquals("ERR_403_FORBIDDEN", parseJson(bkgResp.body()).get("error_code").getAsString());
    }

    @Test
    @DisplayName("Unauthenticated and malformed tokens on protected endpoints receive 401 Unauthorized")
    public void testUnauthenticatedAndInvalidToken_Returns401Unauthorized() throws Exception {
        System.out.println("🛡️ Verifying 401 Unauthorized on unauthenticated and malformed requests...");

        // 1. Missing token on user admin endpoint -> 401 Unauthorized
        HttpResponse<String> noTokenResp = get("/api/v1/users");
        Assertions.assertEquals(401, noTokenResp.statusCode(), "Missing token must receive 401 Unauthorized");
        Assertions.assertEquals("ERR_401_UNAUTHORIZED", parseJson(noTokenResp.body()).get("error_code").getAsString());

        // 2. Malformed token on user admin endpoint -> 401 Unauthorized
        Map<String, String> malformedHeaders = Map.of("Authorization", "Bearer invalid-malformed-token-12345");
        HttpResponse<String> malformedResp = get("/api/v1/users", malformedHeaders);
        Assertions.assertEquals(401, malformedResp.statusCode(), "Malformed token must receive 401 Unauthorized");
        Assertions.assertEquals("ERR_401_UNAUTHORIZED", parseJson(malformedResp.body()).get("error_code").getAsString());

        // 3. Malformed token on payment endpoint -> 401 Unauthorized
        JsonObject payBody = new JsonObject();
        payBody.addProperty("amount", 100000.0);
        Map<String, String> malformedPayHeaders = Map.of(
                "Authorization", "Bearer invalid-malformed-token-12345",
                "Idempotency-Key", "idem-" + UUID.randomUUID()
        );
        HttpResponse<String> malformedPayResp = post("/api/v1/folios/1/pay", payBody.toString(), malformedPayHeaders);
        Assertions.assertEquals(401, malformedPayResp.statusCode(), "Malformed token on payment must receive 401 Unauthorized");
        Assertions.assertEquals("ERR_401_UNAUTHORIZED", parseJson(malformedPayResp.body()).get("error_code").getAsString());

        // 4. Missing token on booking endpoint -> 401 Unauthorized (Critical security guardrail)
        JsonObject bkgReq = new JsonObject();
        bkgReq.addProperty("customerId", 1);
        bkgReq.addProperty("roomId", 1);
        Map<String, String> noAuthBkgHeaders = Map.of("Idempotency-Key", "idem-" + UUID.randomUUID());
        HttpResponse<String> noAuthBkgResp = post("/api/v1/bookings", bkgReq.toString(), noAuthBkgHeaders);
        Assertions.assertEquals(401, noAuthBkgResp.statusCode(), "Unauthenticated request to /bookings must receive 401 Unauthorized");
        Assertions.assertEquals("ERR_401_UNAUTHORIZED", parseJson(noAuthBkgResp.body()).get("error_code").getAsString());
    }

    @ParameterizedTest(name = "Role {0} accessing {1} ({2}) -> Expected Status {3}")
    @CsvSource({
            "Admin,       /api/v1/users,         GET,  200",
            "FrontDesk,   /api/v1/users,         GET,  403",
            "Housekeeping,/api/v1/users,         GET,  403",
            "F&B,         /api/v1/users,         GET,  403",
            "Admin,       /api/v1/folios/1/pay,  POST, 200",
            "FrontDesk,   /api/v1/folios/1/pay,  POST, 200",
            "Housekeeping,/api/v1/folios/1/pay,  POST, 403",
            "F&B,         /api/v1/folios/1/pay,  POST, 403",
            "Admin,       /api/v1/rooms,         GET,  200",
            "FrontDesk,   /api/v1/rooms,         GET,  200",
            "Housekeeping,/api/v1/rooms,         GET,  200",
            "F&B,         /api/v1/rooms,         GET,  200",
            "Admin,       /api/v1/bookings,      POST, 201",
            "FrontDesk,   /api/v1/bookings,      POST, 201",
            "Housekeeping,/api/v1/bookings,      POST, 403",
            "F&B,         /api/v1/bookings,      POST, 403"
    })
    @DisplayName("Exhaustive verification of 4 roles against key endpoints across RBAC matrix (16 matrix cases)")
    public void testRbacMatrix_SystematicVerification(String role, String endpoint, String method, int expectedStatus) throws Exception {
        Map<String, String> headers = "POST".equals(method) ? authAndIdemHeaders(role) : authHeaders(role);
        HttpResponse<String> resp;
        if ("POST".equals(method)) {
            String payload;
            if (endpoint.contains("bookings")) {
                JsonObject b = new JsonObject();
                b.addProperty("customerId", 1);
                b.addProperty("roomId", 1);
                b.addProperty("totalPrice", 1000000.0);
                payload = b.toString();
            } else {
                payload = "{\"amount\":100000.0}";
            }
            resp = post(endpoint, payload, headers);
        } else {
            resp = get(endpoint, headers);
        }

        Assertions.assertEquals(expectedStatus, resp.statusCode(),
                String.format("CRITICAL RBAC MATRIX: Role '%s' accessing %s via %s expected %d but received %d",
                        role, endpoint, method, expectedStatus, resp.statusCode()));
    }
}
