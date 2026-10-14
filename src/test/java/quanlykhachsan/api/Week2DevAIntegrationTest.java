package quanlykhachsan.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import quanlykhachsan.backend.Main;
import quanlykhachsan.backend.booking.FolioRoutingEngine;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.SecurityUtil;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Automated Integration Test Suite for Week 2 Dev A Tasks (TASK-A2.1, TASK-A2.2, TASK-A2.3).
 * <p>
 * DoD Criteria Verified:
 * 1. testRoomAvailability_ReturnsOnlyVacantCleanRooms (TASK-A2.1)
 * 2. testCustomerCreation_DuplicateIdentityCard_Returns409Conflict (TASK-A2.2)
 * 3. testCustomerSearch_Keyword_ReturnsMatchingProfile (TASK-A2.2)
 * 4. testBookingCreation_InvalidDateRange_Returns400BadRequest (TASK-A2.3)
 * 5. testBookingCreation_OutdatedVersion_Returns409Conflict (TASK-A2.3)
 * 6. testBookingCreation_Success_IncrementsVersionAndProvisionsFolio (TASK-A2.3)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Week2DevAIntegrationTest {

    private HttpServer testServer;
    private String serverBaseUrl;
    private HttpClient httpClient;
    private HikariDataSource mockDataSource;
    private String jwtToken;

    // In-Memory Test State
    private static final Map<Integer, MockRoom> mockRooms = new ConcurrentHashMap<>();
    private static final Map<Integer, MockCustomer> mockCustomers = new ConcurrentHashMap<>();
    private static final Map<Integer, MockBooking> mockBookings = new ConcurrentHashMap<>();
    private static final AtomicInteger customerIdGen = new AtomicInteger(100);
    private static final AtomicInteger bookingIdGen = new AtomicInteger(500);

    static class MockRoom {
        int id;
        String roomNumber;
        String status;
        int version;
        int roomTypeId;
        String roomTypeName;
        double basePrice;
        int capacity;

        MockRoom(int id, String roomNumber, String status, int version, int roomTypeId, String roomTypeName, double basePrice, int capacity) {
            this.id = id;
            this.roomNumber = roomNumber;
            this.status = status;
            this.version = version;
            this.roomTypeId = roomTypeId;
            this.roomTypeName = roomTypeName;
            this.basePrice = basePrice;
            this.capacity = capacity;
        }
    }

    static class MockCustomer {
        int id;
        String fullName;
        String phone;
        String identityCard;
        String email;
        String loyaltyLevel;

        MockCustomer(int id, String fullName, String phone, String identityCard, String email, String loyaltyLevel) {
            this.id = id;
            this.fullName = fullName;
            this.phone = phone;
            this.identityCard = identityCard;
            this.email = email;
            this.loyaltyLevel = loyaltyLevel;
        }
    }

    static class MockBooking {
        int id;
        int customerId;
        int roomId;
        String checkInDate;
        String checkOutDate;
        double totalPrice;
        String status;

        MockBooking(int id, int customerId, int roomId, String checkInDate, String checkOutDate, double totalPrice, String status) {
            this.id = id;
            this.customerId = customerId;
            this.roomId = roomId;
            this.checkInDate = checkInDate;
            this.checkOutDate = checkOutDate;
            this.totalPrice = totalPrice;
            this.status = status;
        }
    }

    @BeforeAll
    public void setUp() throws Exception {
        // Initialize Baseline In-Memory State
        mockRooms.clear();
        mockCustomers.clear();
        mockBookings.clear();

        // Seed 3 rooms: Room 1 (vacant_clean), Room 2 (vacant_dirty), Room 3 (vacant_clean)
        mockRooms.put(1, new MockRoom(1, "101", "vacant_clean", 0, 1, "Deluxe Ocean", 1500000.0, 2));
        mockRooms.put(2, new MockRoom(2, "102", "vacant_dirty", 0, 1, "Deluxe Ocean", 1500000.0, 2));
        mockRooms.put(3, new MockRoom(3, "103", "vacant_clean", 0, 2, "Suite Presidential", 3500000.0, 4));

        // Seed Initial Customer
        mockCustomers.put(1, new MockCustomer(1, "Nguyen Van A", "0901234567", "012345678912", "nva@hotel.com", "Gold"));

        // Generate Valid JWT Bearer Token for Authenticated Calls
        jwtToken = SecurityUtil.generateToken(1, "admin", "admin");

        // Initialize Mock DataSource
        mockDataSource = createMockWeek2DataSource();
        DatabaseConnection.setCustomDataSource(mockDataSource);

        // Reset SystemStateHolder to current operational business date matching LocalDate.now()
        quanlykhachsan.backend.system.SystemStateHolder.resetToDefault();
        quanlykhachsan.backend.system.SystemStateHolder.setBusinessDate(java.time.LocalDate.now());
        quanlykhachsan.backend.system.SystemStateHolder.setFrozen(false);

        // Start Server on Ephemeral Port
        testServer = Main.createServer(0);
        testServer.start();

        int port = testServer.getAddress().getPort();
        serverBaseUrl = "http://localhost:" + port;

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        System.out.println("🚀 [Week2DevAIntegrationTest] Server listening at " + serverBaseUrl);
    }

    @AfterAll
    public void tearDown() {
        try {
            if (testServer != null) {
                testServer.stop(0);
                System.out.println("🛑 [Week2DevAIntegrationTest] Server stopped.");
            }
        } finally {
            DatabaseConnection.resetToDefault();
            quanlykhachsan.backend.system.SystemStateHolder.resetToDefault();
            System.out.println("✔ [Week2DevAIntegrationTest] DatabaseConnection reset to default.");
        }
    }

    // =========================================================================
    // 1. TASK-A2.1: ROOM AVAILABILITY TEST
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("TASK-A2.1: GET /api/v1/rooms/availability returns only 'vacant_clean' rooms for same-day check-in")
    public void testRoomAvailability_ReturnsOnlyVacantCleanRooms() throws Exception {
        java.time.LocalDate today = java.time.LocalDate.now();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/rooms/availability?check_in=" + today + "&check_out=" + today.plusDays(3)))
                .header("Authorization", "Bearer " + jwtToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, response.statusCode(), "Expected HTTP 200 OK for room availability");

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("SUCCESS", json.get("status").getAsString());

        JsonArray rooms = json.getAsJsonArray("data");
        Assertions.assertTrue(rooms.size() >= 2, "Should return at least 2 vacant_clean rooms");

        for (JsonElement el : rooms) {
            JsonObject room = el.getAsJsonObject();
            Assertions.assertEquals("vacant_clean", room.get("status").getAsString(),
                    "Same-day availability engine must strictly return vacant_clean rooms");
            Assertions.assertTrue(room.has("version"), "Room must contain version column for optimistic locking");
        }

        System.out.println("✔ [TASK-A2.1 Passed] testRoomAvailability_ReturnsOnlyVacantCleanRooms verified.");
    }

    @Test
    @Order(2)
    @DisplayName("TASK-A2.1 Future Availability: GET /api/v1/rooms/availability for future dates includes dirty/occupied rooms")
    public void testRoomAvailability_FutureDate_AllowsOccupiedOrDirtyRooms() throws Exception {
        java.time.LocalDate futureCi = java.time.LocalDate.now().plusMonths(2);
        java.time.LocalDate futureCo = futureCi.plusDays(4);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/rooms/availability?check_in=" + futureCi + "&check_out=" + futureCo))
                .header("Authorization", "Bearer " + jwtToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, response.statusCode(), "Expected HTTP 200 OK for future room availability");

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("SUCCESS", json.get("status").getAsString());

        JsonArray rooms = json.getAsJsonArray("data");
        Assertions.assertEquals(3, rooms.size(), "Future availability must return all 3 non-broken rooms (including Room 102 which is currently vacant_dirty)");

        System.out.println("✔ [TASK-A2.1 Bugfix Verified] Future room availability correctly includes currently dirty/occupied rooms.");
    }

    // =========================================================================
    // 2. TASK-A2.2: CUSTOMER DUPLICATE IDENTITY TEST
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("TASK-A2.2: POST /api/v1/customers with duplicate identity card returns 409 Conflict")
    public void testCustomerCreation_DuplicateIdentityCard_Returns409Conflict() throws Exception {
        String testIdentity = "CCCD-99887766";

        // Step 1: Create customer 1 successfully
        JsonObject req1 = new JsonObject();
        req1.addProperty("full_name", "Tran Van First");
        req1.addProperty("phone", "0911000001");
        req1.addProperty("identity_card", testIdentity);
        req1.addProperty("email", "first@hotel.com");

        HttpRequest post1 = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/customers"))
                .header("Authorization", "Bearer " + jwtToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(req1.toString()))
                .build();

        HttpResponse<String> resp1 = httpClient.send(post1, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(201, resp1.statusCode(), "First customer creation must succeed with 201 Created");

        // Step 2: Attempt to create customer 2 with identical identity card number
        JsonObject req2 = new JsonObject();
        req2.addProperty("full_name", "Le Thi Duplicate");
        req2.addProperty("phone", "0911000002");
        req2.addProperty("identity_card", testIdentity);
        req2.addProperty("email", "duplicate@hotel.com");

        HttpRequest post2 = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/customers"))
                .header("Authorization", "Bearer " + jwtToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(req2.toString()))
                .build();

        HttpResponse<String> resp2 = httpClient.send(post2, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(409, resp2.statusCode(), "Duplicate identity card must return HTTP 409 Conflict");

        JsonObject errJson = JsonParser.parseString(resp2.body()).getAsJsonObject();
        Assertions.assertEquals("ERR_409_CONFLICT", errJson.get("error_code").getAsString());

        System.out.println("✔ [TASK-A2.2 Passed] testCustomerCreation_DuplicateIdentityCard_Returns409Conflict verified.");
    }

    // =========================================================================
    // 3. TASK-A2.2: CUSTOMER FAST SEARCH TEST
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("TASK-A2.2: GET /api/v1/customers?search=... returns matching customer profile")
    public void testCustomerSearch_Keyword_ReturnsMatchingProfile() throws Exception {
        String searchKey = "CCCD-99887766";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/customers?search=" + searchKey))
                .header("Authorization", "Bearer " + jwtToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, response.statusCode());

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("SUCCESS", json.get("status").getAsString());

        JsonArray data = json.getAsJsonArray("data");
        Assertions.assertFalse(data.isEmpty(), "Search must return matching profile");

        JsonObject matched = data.get(0).getAsJsonObject();
        Assertions.assertEquals(searchKey, matched.get("identity_card").getAsString());
        Assertions.assertEquals("Tran Van First", matched.get("full_name").getAsString());

        System.out.println("✔ [TASK-A2.2 Passed] testCustomerSearch_Keyword_ReturnsMatchingProfile verified.");
    }

    // =========================================================================
    // 4. TASK-A2.3: INVALID DATE RANGE TEST
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("TASK-A2.3: POST /api/v1/bookings with check_in >= check_out returns 400 Bad Request")
    public void testBookingCreation_InvalidDateRange_Returns400BadRequest() throws Exception {
        JsonObject bkgReq = new JsonObject();
        bkgReq.addProperty("customer_id", 1);
        bkgReq.addProperty("room_id", 1);
        bkgReq.addProperty("room_version", 0);
        bkgReq.addProperty("check_in_date", "2026-11-10");
        bkgReq.addProperty("check_out_date", "2026-11-05"); // Invalid: check_in >= check_out
        bkgReq.addProperty("total_amount", 2000000.0);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/bookings"))
                .header("Authorization", "Bearer " + jwtToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bkgReq.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(400, response.statusCode(), "Inverted date range must return HTTP 400 Bad Request");

        JsonObject errJson = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("ERR_400_BAD_REQUEST", errJson.get("error_code").getAsString());

        System.out.println("✔ [TASK-A2.3 Passed] testBookingCreation_InvalidDateRange_Returns400BadRequest verified.");
    }

    // =========================================================================
    // 5. TASK-A2.3: OPTIMISTIC LOCKING OUTDATED VERSION TEST
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("TASK-A2.3: POST /api/v1/bookings with outdated room_version returns 409 Conflict")
    public void testBookingCreation_OutdatedVersion_Returns409Conflict() throws Exception {
        java.time.LocalDate today = java.time.LocalDate.now();
        JsonObject bkgReq = new JsonObject();
        bkgReq.addProperty("customer_id", 1);
        bkgReq.addProperty("room_id", 1);
        bkgReq.addProperty("room_version", 999); // Outdated version mismatch (current version is 0)
        bkgReq.addProperty("check_in_date", today.toString());
        bkgReq.addProperty("check_out_date", today.plusDays(4).toString());
        bkgReq.addProperty("total_amount", 6000000.0);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/bookings"))
                .header("Authorization", "Bearer " + jwtToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bkgReq.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(409, response.statusCode(), "Version mismatch must return HTTP 409 Conflict");

        JsonObject errJson = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("ERR_409_CONFLICT", errJson.get("error_code").getAsString());

        System.out.println("✔ [TASK-A2.3 Passed] testBookingCreation_OutdatedVersion_Returns409Conflict verified.");
    }

    // =========================================================================
    // 6. TASK-A2.3: ATOMIC BOOKING SUCCESS TEST (VERSION & FOLIO)
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("TASK-A2.3: Successful booking increments room version and provisions Master Folio")
    public void testBookingCreation_Success_IncrementsVersionAndProvisionsFolio() throws Exception {
        int targetRoomId = 1;
        MockRoom roomBefore = mockRooms.get(targetRoomId);
        int initialVersion = roomBefore.version;

        java.time.LocalDate today = java.time.LocalDate.now();
        JsonObject bkgReq = new JsonObject();
        bkgReq.addProperty("customer_id", 1);
        bkgReq.addProperty("room_id", targetRoomId);
        bkgReq.addProperty("room_version", initialVersion); // Matching current version (0)
        bkgReq.addProperty("check_in_date", today.toString());
        bkgReq.addProperty("check_out_date", today.plusDays(4).toString());
        bkgReq.addProperty("total_amount", 6000000.0);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/bookings"))
                .header("Authorization", "Bearer " + jwtToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bkgReq.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(201, response.statusCode(), "Expected HTTP 201 Created for successful booking");

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("SUCCESS", json.get("status").getAsString());

        JsonObject data = json.getAsJsonObject("data");
        int bookingId = data.get("booking_id").getAsInt();
        int newVersion = data.get("room_version").getAsInt();

        // Assert room version incremented by exactly 1
        Assertions.assertEquals(initialVersion + 1, newVersion, "Room version must increment by exactly 1");

        // Assert Master Folio is provisioned
        String masterFolioId = data.get("master_folio_id").getAsString();
        Assertions.assertEquals("FOLIO-MASTER-" + bookingId, masterFolioId);

        FolioRoutingEngine.Folio masterFolio = FolioRoutingEngine.getInstance().getFolio(masterFolioId);
        Assertions.assertNotNull(masterFolio, "Master Folio must exist in FolioRoutingEngine");
        Assertions.assertEquals(6000000.0, masterFolio.getBalance(), 0.01, "Master Folio balance must reflect room rate");

        // Assert room status changed to 'reserved'
        MockRoom roomAfter = mockRooms.get(targetRoomId);
        Assertions.assertEquals("reserved", roomAfter.status, "Room status must transition to 'reserved'");
        Assertions.assertEquals(initialVersion + 1, roomAfter.version);

        System.out.println("✔ [TASK-A2.3 Passed] testBookingCreation_Success_IncrementsVersionAndProvisionsFolio verified.");
    }

    // =========================================================================
    // 7. TASK-A2.3: CAS STATUS GUARDRAIL TEST (VACANT_CLEAN CONSTRAINT)
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("TASK-A2.3: POST /api/v1/bookings on non-vacant_clean room (e.g. vacant_dirty) returns 409 Conflict")
    public void testBookingCreation_NonVacantCleanRoom_Returns409Conflict() throws Exception {
        int dirtyRoomId = 2; // Room 102: status 'vacant_dirty', version 0
        MockRoom room = mockRooms.get(dirtyRoomId);
        Assertions.assertEquals("vacant_dirty", room.status);

        java.time.LocalDate today = java.time.LocalDate.now();
        JsonObject bkgReq = new JsonObject();
        bkgReq.addProperty("customer_id", 1);
        bkgReq.addProperty("room_id", dirtyRoomId);
        bkgReq.addProperty("room_version", room.version); // Correct version, but invalid room status for same-day
        bkgReq.addProperty("check_in_date", today.toString());
        bkgReq.addProperty("check_out_date", today.plusDays(4).toString());
        bkgReq.addProperty("total_amount", 6000000.0);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/bookings"))
                .header("Authorization", "Bearer " + jwtToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bkgReq.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(409, response.statusCode(), "Booking a non-vacant_clean room must return HTTP 409 Conflict");

        JsonObject errJson = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("ERR_409_CONFLICT", errJson.get("error_code").getAsString());

        System.out.println("✔ [TASK-A2.3 Passed] testBookingCreation_NonVacantCleanRoom_Returns409Conflict verified.");
    }

    @Test
    @Order(8)
    @DisplayName("TASK-A2.3 & TASK-A2.6 Overbooking Guardrail: PUT /api/v1/bookings/{id} overbooking conflict returns 409 Conflict")
    public void testBookingUpdate_OverbookingConflict_Returns409Conflict() throws Exception {
        // Step 1: Create an existing booking for the same room (Room 1)
        int bookingId = 501; // Created in testBookingCreation_Success for Room 1
        mockBookings.put(502, new MockBooking(502, 1, 1, "2026-12-10 14:00:00", "2026-12-15 12:00:00", 17500000.0, "confirmed"));

        // Step 2: Attempt to reschedule booking 501 into Room 3's existing stay dates (2026-12-12 to 2026-12-14)
        JsonObject updateReq = new JsonObject();
        updateReq.addProperty("room_version", 1);
        updateReq.addProperty("check_in_date", "2026-12-12 14:00:00");
        updateReq.addProperty("check_out_date", "2026-12-14 12:00:00");
        updateReq.addProperty("total_amount", 7000000.0);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/bookings/" + bookingId))
                .header("Authorization", "Bearer " + jwtToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(updateReq.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(409, response.statusCode(), "Overlapping dates on booking update must return 409 Conflict");

        JsonObject errJson = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("ERR_409_CONFLICT", errJson.get("error_code").getAsString());
        Assertions.assertTrue(errJson.get("message").getAsString().toLowerCase().contains("schedule conflict"),
                "Error message should mention schedule conflict / overbooking");

        System.out.println("✔ [Overbooking Guardrail Passed] PUT /api/v1/bookings/{id} double-booking correctly blocked with 409 Conflict.");
    }

    @Test
    @Order(9)
    @DisplayName("TASK-A2.3 & TASK-A2.6: PUT /api/v1/bookings/{id} successfully amends stay dates when no conflict exists")
    public void testBookingUpdate_Success_NonConflictingDates() throws Exception {
        int bookingId = 501;
        // Reschedule booking 501 to non-conflicting future dates (2026-12-20 to 2026-12-22)
        JsonObject updateReq = new JsonObject();
        updateReq.addProperty("room_version", 1);
        updateReq.addProperty("check_in_date", "2026-12-20 14:00:00");
        updateReq.addProperty("check_out_date", "2026-12-22 12:00:00");
        updateReq.addProperty("total_amount", 7000000.0);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/bookings/" + bookingId))
                .header("Authorization", "Bearer " + jwtToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(updateReq.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, response.statusCode(), "Non-conflicting stay date amendment must return 200 OK");

        JsonObject respJson = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("SUCCESS", respJson.get("status").getAsString());

        JsonObject data = respJson.getAsJsonObject("data");
        Assertions.assertEquals("updated", data.get("status").getAsString());
        Assertions.assertEquals(2, data.get("room_version").getAsInt(), "Room version should increment upon modification");

        System.out.println("✔ [Booking Amendment Passed] PUT /api/v1/bookings/{id} successful stay amendment verified.");
    }

    @Test
    @Order(10)
    @DisplayName("TASK-A2.1 & TASK-A2.3: Future booking on currently uncleaned/occupied room succeeds and preserves physical status")
    public void testFutureBooking_OnCurrentlyDirtyRoom_SucceedsAndPreservesPhysicalStatus() throws Exception {
        int dirtyRoomId = 2; // Room 102: status 'vacant_dirty', version 0
        MockRoom roomBefore = mockRooms.get(dirtyRoomId);
        Assertions.assertEquals("vacant_dirty", roomBefore.status);
        int initialVersion = roomBefore.version;

        // Book Room 2 for future dates (e.g. 2 months from now)
        java.time.LocalDate futureCi = java.time.LocalDate.now().plusMonths(2);
        java.time.LocalDate futureCo = futureCi.plusDays(3);

        JsonObject bkgReq = new JsonObject();
        bkgReq.addProperty("customer_id", 1);
        bkgReq.addProperty("room_id", dirtyRoomId);
        bkgReq.addProperty("room_version", initialVersion);
        bkgReq.addProperty("check_in_date", futureCi.toString());
        bkgReq.addProperty("check_out_date", futureCo.toString());
        bkgReq.addProperty("total_amount", 4500000.0);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/bookings"))
                .header("Authorization", "Bearer " + jwtToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bkgReq.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(201, response.statusCode(), "Booking future stay on dirty/occupied room must succeed with HTTP 201 Created");

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Assertions.assertEquals("SUCCESS", json.get("status").getAsString());

        // Assert physical status today is STILL 'vacant_dirty' (NOT prematurely changed to 'reserved')
        MockRoom roomAfter = mockRooms.get(dirtyRoomId);
        Assertions.assertEquals("vacant_dirty", roomAfter.status, "Room physical status today must NOT be changed to reserved for future bookings");
        Assertions.assertEquals(initialVersion + 1, roomAfter.version, "Room version must still increment for optimistic concurrency control");

        System.out.println("✔ [Future Booking CAS Passed] Future booking on vacant_dirty room succeeded, preserving physical room status.");
    }

    @Test
    @Order(11)
    @DisplayName("ADR-06 & TASK-A2.3: Booking cancellation voids Folio and eliminates phantom debt")
    public void testBookingCancellation_VoidsFolioAndEliminatesPhantomDebt() throws Exception {
        int bookingId = 501; // Booking 501 from test 6

        // Before cancellation, Master Folio has a balance
        String masterFolioId = "FOLIO-MASTER-" + bookingId;
        FolioRoutingEngine.Folio masterFolio = FolioRoutingEngine.getInstance().getFolio(masterFolioId);
        Assertions.assertNotNull(masterFolio, "Master Folio must exist prior to cancellation");

        // Cancel the booking via POST /api/v1/bookings/{id}/cancel
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/bookings/" + bookingId + "/cancel"))
                .header("Authorization", "Bearer " + jwtToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, response.statusCode(), "Expected HTTP 200 OK for successful booking cancellation");

        // Assert ADR-06 Ledger Invariant:
        // Folio is CANCELLED, balance is zeroed out (0.00 VND phantom debt eliminated)
        Assertions.assertEquals(FolioRoutingEngine.FolioStatus.CANCELLED, masterFolio.getStatus(), "Master Folio status must transition to CANCELLED");
        Assertions.assertEquals(0.0, masterFolio.getBalance(), 0.001, "Cancelled folio balance must be 0.00 to prevent phantom debt in accounts receivable");

        // Assert folio can now be closed without IllegalStateException
        Assertions.assertDoesNotThrow(() -> masterFolio.closeFolio(), "Closing a voided/cancelled folio must not throw an exception");

        System.out.println("✔ [ADR-06 Cancellation Passed] Booking cancellation successfully voided Folio and eliminated phantom debt.");
    }

    @Test
    @Order(12)
    @DisplayName("TASK-A2.3 Issue 1 Fix: Future bookings in distinct months avoid false version contention")
    public void testBookingCreation_FutureDates_DifferentMonths_NoFalseVersionContention() throws Exception {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate novCi = today.plusMonths(1);
        java.time.LocalDate novCo = novCi.plusDays(5);
        java.time.LocalDate decCi = today.plusMonths(2);
        java.time.LocalDate decCo = decCi.plusDays(5);

        // Guest A books Room 3 for November (retrieving current room version 0)
        JsonObject bkgNov = new JsonObject();
        bkgNov.addProperty("customer_id", 1);
        bkgNov.addProperty("room_id", 3);
        bkgNov.addProperty("room_version", 0);
        bkgNov.addProperty("check_in_date", novCi.toString());
        bkgNov.addProperty("check_out_date", novCo.toString());
        bkgNov.addProperty("total_amount", 5000000.0);

        HttpRequest reqNov = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/bookings"))
                .header("Authorization", "Bearer " + jwtToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bkgNov.toString()))
                .build();

        HttpResponse<String> respNov = httpClient.send(reqNov, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(201, respNov.statusCode(), "Guest A November booking must succeed (201)");

        // Guest B also viewed Room 3 before Guest A's booking (expected version 0) and books for December
        // Prior to bugfix: WHERE version = 0 failed because Guest A incremented version to 1 -> False 409 Conflict!
        // With Issue 1 fix: Schedule integrity is enforced by overlapSql; future stays avoid false version contention!
        JsonObject bkgDec = new JsonObject();
        bkgDec.addProperty("customer_id", 1);
        bkgDec.addProperty("room_id", 3);
        bkgDec.addProperty("room_version", 0); // Stale version from before Guest A booked
        bkgDec.addProperty("check_in_date", decCi.toString());
        bkgDec.addProperty("check_out_date", decCo.toString());
        bkgDec.addProperty("total_amount", 6000000.0);

        HttpRequest reqDec = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/bookings"))
                .header("Authorization", "Bearer " + jwtToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bkgDec.toString()))
                .build();

        HttpResponse<String> respDec = httpClient.send(reqDec, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(201, respDec.statusCode(), "Guest B December booking must succeed without false 409 version conflict!");

        // Guest C attempts to book overlapping dates in November (novCi.plusDays(1) to novCo.plusDays(1))
        JsonObject bkgOverlap = new JsonObject();
        bkgOverlap.addProperty("customer_id", 1);
        bkgOverlap.addProperty("room_id", 3);
        bkgOverlap.addProperty("room_version", 0);
        bkgOverlap.addProperty("check_in_date", novCi.plusDays(1).toString());
        bkgOverlap.addProperty("check_out_date", novCo.plusDays(1).toString());
        bkgOverlap.addProperty("total_amount", 5000000.0);

        HttpRequest reqOverlap = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/bookings"))
                .header("Authorization", "Bearer " + jwtToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bkgOverlap.toString()))
                .build();

        HttpResponse<String> respOverlap = httpClient.send(reqOverlap, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(409, respOverlap.statusCode(), "Overlapping stay window must be blocked with HTTP 409 Conflict by overlapSql");
    }

    // =========================================================================
    // IN-MEMORY MOCK JDBC INFRASTRUCTURE
    // =========================================================================

    private static HikariDataSource createMockWeek2DataSource() {
        InvocationHandler dsHandler = (proxy, method, args) -> {
            String name = method.getName();
            if ("getConnection".equals(name)) {
                return createMockConnection();
            } else if ("isWrapperFor".equals(name)) {
                return false;
            } else if ("unwrap".equals(name)) {
                throw new SQLException("Mock DataSource is not a wrapper");
            } else if ("getLoginTimeout".equals(name)) {
                return 0;
            }
            if (method.getReturnType().equals(int.class)) return 0;
            if (method.getReturnType().equals(boolean.class)) return false;
            return null;
        };

        DataSource underlyingDs = (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                dsHandler
        );

        HikariConfig config = new HikariConfig();
        config.setDataSource(underlyingDs);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setPoolName("Test-Week2MockPool");
        config.setConnectionTimeout(1000);
        config.setValidationTimeout(1000);

        return new HikariDataSource(config);
    }

    private static Connection createMockConnection() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("prepareStatement".equals(name)) {
                        String sql = (String) args[0];
                        return createMockPreparedStatement(sql);
                    } else if ("createStatement".equals(name)) {
                        return createMockStatement();
                    } else if ("isClosed".equals(name)) {
                        return false;
                    } else if ("isValid".equals(name)) {
                        return true;
                    } else if ("getAutoCommit".equals(name)) {
                        return true;
                    } else if ("setAutoCommit".equals(name)) {
                        return null;
                    } else if ("commit".equals(name) || "rollback".equals(name)) {
                        return null;
                    } else if ("getTransactionIsolation".equals(name)) {
                        return Connection.TRANSACTION_READ_COMMITTED;
                    } else if ("close".equals(name)) {
                        return null;
                    }
                    if (method.getReturnType().equals(int.class)) return 0;
                    if (method.getReturnType().equals(boolean.class)) return false;
                    return null;
                }
        );
    }

    private static Statement createMockStatement() {
        return (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("executeQuery".equals(name)) {
                        return createEmptyResultSet();
                    } else if ("close".equals(name)) {
                        return null;
                    }
                    if (method.getReturnType().equals(int.class)) return 0;
                    if (method.getReturnType().equals(boolean.class)) return false;
                    return null;
                }
        );
    }

    private static PreparedStatement createMockPreparedStatement(String sql) {
        final Map<Integer, Object> params = new ConcurrentHashMap<>();
        final int[] lastGeneratedId = new int[]{-1};

        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    String name = method.getName();

                    if (name.startsWith("set") && args != null && args.length >= 2) {
                        int idx = (Integer) args[0];
                        params.put(idx, args[1]);
                        return null;
                    }

                    if ("executeUpdate".equals(name)) {
                        return executeMockUpdate(sql, params, lastGeneratedId);
                    }

                    if ("executeQuery".equals(name)) {
                        return executeMockQuery(sql, params);
                    }

                    if ("getGeneratedKeys".equals(name)) {
                        return createGeneratedKeyResultSet(lastGeneratedId[0]);
                    }

                    if ("close".equals(name)) return null;
                    if ("isClosed".equals(name)) return false;
                    if (method.getReturnType().equals(int.class)) return 0;
                    if (method.getReturnType().equals(boolean.class)) return false;
                    return null;
                }
        );
    }

    private static int executeMockUpdate(String sql, Map<Integer, Object> params, int[] lastGeneratedId) throws SQLException {
        String lower = sql.toLowerCase();

        // 1. Optimistic Locking Room Update (Same-Day)
        if (lower.contains("update rooms set status = 'reserved'")) {
            int roomId = (Integer) params.get(1);
            int expectedVersion = (Integer) params.get(2);

            MockRoom room = mockRooms.get(roomId);
            if (room != null && "vacant_clean".equalsIgnoreCase(room.status) && room.version == expectedVersion) {
                room.status = "reserved";
                room.version += 1;
                return 1; // Exactly 1 row updated
            }
            return 0; // Optimistic lock conflict!
        }

        // 1b. Future Booking Room Update (status != 'out_of_order', physical status preserved, no cross-month version contention)
        if (lower.contains("update rooms set version = version + 1 where id = ? and status != 'out_of_order'")
                || lower.contains("update rooms set version = version + 1 where id = ? and version = ? and status != 'out_of_order'")) {
            int roomId = (Integer) params.get(1);
            MockRoom room = mockRooms.get(roomId);
            if (room != null && !"out_of_order".equalsIgnoreCase(room.status)) {
                room.version += 1;
                return 1;
            }
            return 0;
        }

        // 2. Room Release Update on Cancel (Preserve status if room is occupied/dirty today)
        if (lower.contains("update rooms set status = 'vacant_clean'") || lower.contains("update rooms set status = case when status = 'reserved'")) {
            int roomId = (Integer) params.get(1);
            MockRoom room = mockRooms.get(roomId);
            if (room != null) {
                if ("reserved".equalsIgnoreCase(room.status)) {
                    room.status = "vacant_clean";
                }
                room.version += 1;
                return 1;
            }
            return 0;
        }

        // 3. Customer Insert (with simulated unique constraint error 1062 on duplicate)
        if (lower.contains("insert into customers")) {
            String phone = (String) params.get(2);
            String idCard = (String) params.get(3);
            for (MockCustomer c : mockCustomers.values()) {
                if (c.identityCard.equalsIgnoreCase(idCard) || c.phone.equals(phone)) {
                    throw new SQLException("Duplicate entry '" + idCard + "' for key 'identity_card'", "23000", 1062);
                }
            }

            int id = customerIdGen.incrementAndGet();
            String fullName = (String) params.get(1);
            String email = (String) params.get(4);
            String loyalty = (String) params.get(5);

            mockCustomers.put(id, new MockCustomer(id, fullName, phone, idCard, email, loyalty));
            lastGeneratedId[0] = id;
            return 1;
        }

        // 3b. Room version increment on updateBookingAtomic (PUT /api/v1/bookings/{id})
        if (lower.contains("update rooms set version = version + 1 where id = ? and version = ?")) {
            int roomId = (Integer) params.get(1);
            int expectedVersion = (Integer) params.get(2);
            MockRoom r = mockRooms.get(roomId);
            if (r != null && r.version == expectedVersion) {
                r.version += 1;
                return 1;
            }
            return 0;
        }

        // 3c. Booking update on updateBookingAtomic (PUT /api/v1/bookings/{id})
        if (lower.contains("update bookings set check_in_date = ?")) {
            int bkgId = (Integer) params.get(4);
            MockBooking b = mockBookings.get(bkgId);
            if (b != null) {
                b.checkInDate = (String) params.get(1);
                b.checkOutDate = (String) params.get(2);
                if (params.get(3) != null) {
                    b.totalPrice = ((Number) params.get(3)).doubleValue();
                }
                return 1;
            }
            return 0;
        }

        // 4. Booking Insert
        if (lower.contains("insert into bookings")) {
            int id = bookingIdGen.incrementAndGet();
            int custId = (Integer) params.get(1);
            int roomId = (Integer) params.get(2);
            String checkIn = (String) params.get(3);
            String checkOut = (String) params.get(4);
            double total = ((Number) params.get(5)).doubleValue();

            mockBookings.put(id, new MockBooking(id, custId, roomId, checkIn, checkOut, total, "confirmed"));
            lastGeneratedId[0] = id;
            return 1;
        }

        // 5. Booking Cancel
        if (lower.contains("update bookings set status = 'cancelled'")) {
            int bkgId = (Integer) params.get(1);
            MockBooking b = mockBookings.get(bkgId);
            if (b != null) {
                b.status = "cancelled";
                return 1;
            }
            return 0;
        }

        // 6. Invoices insert
        if (lower.contains("insert into invoices")) {
            return 1;
        }

        return 1;
    }

    private static ResultSet executeMockQuery(String sql, Map<Integer, Object> params) {
        String lower = sql.toLowerCase();

        // 1. Room Availability or All Rooms Query
        if (lower.contains("from rooms r")) {
            List<Map<String, Object>> rows = new ArrayList<>();
            boolean isAvailability = lower.contains("where r.status = 'vacant_clean'")
                    || lower.contains("r.status not in")
                    || lower.contains("from bookings b");

            boolean sameDayVacantCleanOnly = lower.contains("where r.status = 'vacant_clean'");

            for (MockRoom r : mockRooms.values()) {
                if (isAvailability) {
                    if (sameDayVacantCleanOnly && !"vacant_clean".equalsIgnoreCase(r.status)) {
                        continue; // Skip non-vacant_clean rooms in same-day availability query
                    }
                    if (!sameDayVacantCleanOnly && ("out_of_order".equalsIgnoreCase(r.status) || "maintenance".equalsIgnoreCase(r.status) || "out_of_service".equalsIgnoreCase(r.status))) {
                        continue; // Exclude broken rooms for future availability
                    }
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", r.id);
                row.put("room_number", r.roomNumber);
                row.put("status", r.status);
                row.put("version", r.version);
                row.put("room_type_id", r.roomTypeId);
                row.put("room_type_name", r.roomTypeName);
                row.put("base_price", r.basePrice);
                row.put("capacity", r.capacity);
                rows.add(row);
            }
            return createMapResultSet(rows);
        }

        // 1b. Overlap query check for bookings (POST /api/v1/bookings and PUT /api/v1/bookings/{id})
        if (lower.contains("select count(*) from bookings") && lower.contains("not (check_out_date <= ? or check_in_date >= ?)")) {
            int roomId = (Integer) params.get(1);
            int excludeBkgId = -1;
            String newCi;
            String newCo;
            if (lower.contains("id != ?")) {
                excludeBkgId = (Integer) params.get(2);
                newCi = (String) params.get(3);
                newCo = (String) params.get(4);
            } else {
                newCi = (String) params.get(2);
                newCo = (String) params.get(3);
            }
            int count = 0;
            for (MockBooking b : mockBookings.values()) {
                if (b.roomId == roomId && b.id != excludeBkgId && !"cancelled".equalsIgnoreCase(b.status)) {
                    if (!(b.checkOutDate.compareTo(newCi) <= 0 || b.checkInDate.compareTo(newCo) >= 0)) {
                        count++;
                    }
                }
            }
            Map<String, Object> countRow = new LinkedHashMap<>();
            countRow.put("1", count);
            countRow.put("count", count);
            return createMapResultSet(List.of(countRow));
        }

        // 2. Customer duplicate identity or phone check
        if (lower.contains("from customers where identity_card = ? or phone = ?")) {
            String idCard = (String) params.get(1);
            String phone = (String) params.get(2);
            List<Map<String, Object>> rows = new ArrayList<>();

            for (MockCustomer c : mockCustomers.values()) {
                if (c.identityCard.equalsIgnoreCase(idCard) || c.phone.equals(phone)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", c.id);
                    row.put("identity_card", c.identityCard);
                    row.put("phone", c.phone);
                    rows.add(row);
                    break;
                }
            }
            return createMapResultSet(rows);
        }

        // 3. Customer search query
        if (lower.contains("from customers where full_name like ?") || lower.contains("from customers order by id desc")) {
            List<Map<String, Object>> rows = new ArrayList<>();
            String pattern = params.containsKey(1) ? ((String) params.get(1)).replace("%", "").toLowerCase() : null;

            for (MockCustomer c : mockCustomers.values()) {
                if (pattern == null || c.fullName.toLowerCase().contains(pattern)
                        || c.phone.contains(pattern) || c.identityCard.toLowerCase().contains(pattern)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", c.id);
                    row.put("full_name", c.fullName);
                    row.put("phone", c.phone);
                    row.put("identity_card", c.identityCard);
                    row.put("loyalty_level", c.loyaltyLevel);
                    row.put("created_at", "2026-10-14 17:00:00");
                    rows.add(row);
                }
            }
            return createMapResultSet(rows);
        }

        // 4. Booking query by ID or cancel check
        if (lower.contains("from bookings where id = ?") || lower.contains("where b.id = ?")) {
            int bkgId = (Integer) params.get(1);
            MockBooking b = mockBookings.get(bkgId);
            List<Map<String, Object>> rows = new ArrayList<>();
            if (b != null) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", b.id);
                row.put("customer_id", b.customerId);
                row.put("customer_name", "Nguyen Van A");
                row.put("room_id", b.roomId);
                row.put("room_number", "101");
                row.put("check_in_date", b.checkInDate);
                row.put("check_out_date", b.checkOutDate);
                row.put("total_amount", b.totalPrice);
                row.put("status", b.status);
                rows.add(row);
            }
            return createMapResultSet(rows);
        }

        // 5. All bookings query
        if (lower.contains("from bookings b")) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (MockBooking b : mockBookings.values()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", b.id);
                row.put("customer_id", b.customerId);
                row.put("customer_name", "Nguyen Van A");
                row.put("room_id", b.roomId);
                row.put("room_number", "101");
                row.put("check_in_date", b.checkInDate);
                row.put("check_out_date", b.checkOutDate);
                row.put("total_amount", b.totalPrice);
                row.put("status", b.status);
                rows.add(row);
            }
            return createMapResultSet(rows);
        }

        return createEmptyResultSet();
    }

    private static ResultSet createEmptyResultSet() {
        return createMapResultSet(Collections.emptyList());
    }

    private static ResultSet createGeneratedKeyResultSet(int genId) {
        if (genId <= 0) return createEmptyResultSet();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("1", genId);
        return createMapResultSet(List.of(row));
    }

    private static ResultSet createMapResultSet(List<Map<String, Object>> rows) {
        final int[] cursor = new int[]{-1};

        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    String name = method.getName();

                    if ("next".equals(name)) {
                        cursor[0]++;
                        return cursor[0] < rows.size();
                    }

                    if (cursor[0] < 0 || cursor[0] >= rows.size()) {
                        if (method.getReturnType().equals(boolean.class)) return false;
                        if (method.getReturnType().equals(int.class)) return 0;
                        if (method.getReturnType().equals(double.class)) return 0.0;
                        return null;
                    }

                    Map<String, Object> cur = rows.get(cursor[0]);

                    if ("getInt".equals(name)) {
                        Object key = args[0];
                        Object val = key instanceof Integer i ? cur.values().toArray()[(Integer) key - 1] : cur.get(key);
                        return val instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(val));
                    }

                    if ("getDouble".equals(name)) {
                        Object key = args[0];
                        Object val = key instanceof Integer i ? cur.values().toArray()[(Integer) key - 1] : cur.get(key);
                        return val instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(val));
                    }

                    if ("getString".equals(name)) {
                        Object key = args[0];
                        Object val = key instanceof Integer i ? cur.values().toArray()[(Integer) key - 1] : cur.get(key);
                        return val != null ? String.valueOf(val) : null;
                    }

                    if ("getObject".equals(name)) {
                        Object key = args[0];
                        return key instanceof Integer i ? cur.values().toArray()[(Integer) key - 1] : cur.get(key);
                    }

                    if ("close".equals(name)) return null;
                    if ("isClosed".equals(name)) return false;
                    if (method.getReturnType().equals(boolean.class)) return false;
                    if (method.getReturnType().equals(int.class)) return 0;
                    return null;
                }
        );
    }
}
