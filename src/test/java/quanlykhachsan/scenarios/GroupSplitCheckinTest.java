package quanlykhachsan.scenarios;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import quanlykhachsan.BaseIntegrationTest;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Enterprise Automated Scenario 1 Test: Group Split Check-in (TASK-D3.1).
 * <p>
 * Scenario Definition & Context (docs/dev-spec/test-scenarios.md):
 * - A corporate group reserves 20 rooms (Rooms 401-420, IDs 7-26).
 * - Master folio covers room rates; individual guest folios cover incidentals.
 * - 5 early arrivals arrive at 10:00 AM; 15 arrive late.
 * - System enables independent check-in for the 5 early arrivals without checking in the entire group.
 * <p>
 * Definition of Done (DoD) Assertions:
 * 1. Room Status for the 5 early rooms changes to 'occupied_clean'.
 * 2. Remaining 15 rooms remain 'reserved'.
 * 3. Incidental charges on early rooms auto-route exclusively to 'FOLIO-GUEST'.
 * 4. Master Folio ('FOLIO-MASTER') contains room rates and blocks closure if guest incidentals remain unpaid.
 * 5. 100% of Scenario 1 assertions pass in &lt; 3 seconds.
 */
public class GroupSplitCheckinTest extends BaseIntegrationTest {

    private static final int GROUP_ROOM_COUNT = 20;
    private static final int EARLY_ARRIVAL_COUNT = 5;
    private static final int STARTING_ROOM_ID = 7; // Rooms 7 to 26 (Rooms 401 to 420)

    @Test
    @DisplayName("TASK-D3.1: Automated Scenario 1 Test — 20-Room Group Split Check-in and Multi-Folio Routing (< 3s)")
    public void testScenario1_GroupSplitCheckin_MultiFolioRouting() throws Exception {
        System.out.println("==================================================================");
        System.out.println("🏨 [SCENARIO 1] Executing Automated Group Split Check-in Test Suite...");
        System.out.println("==================================================================");

        // Standard Front Desk Bearer JWT Authentication
        String frontDeskToken = generateSignedJwt("FrontDesk", "staff_reception_01", 105);
        Map<String, String> authHeaders = Map.of(
                "Authorization", createAuthHeader(frontDeskToken),
                "Content-Type", "application/json"
        );

        // Warm up HTTP connection & JIT to eliminate cold-start socket allocation latency
        get("/api/v1/health");

        long scenarioStartTime = System.currentTimeMillis();

        // ---------------------------------------------------------------------
        // STEP 1: Verify Initial Room Baseline (20 vacant_clean rooms available)
        // ---------------------------------------------------------------------
        HttpResponse<String> roomsResp = get("/api/v1/rooms", authHeaders);
        Assertions.assertEquals(200, roomsResp.statusCode(), "GET /api/v1/rooms must return 200 OK");

        JsonObject roomsJson = parseJson(roomsResp.body());
        JsonArray allRooms = roomsJson.getAsJsonArray("data");

        List<Integer> targetRoomIds = new ArrayList<>();
        for (int i = 0; i < GROUP_ROOM_COUNT; i++) {
            int roomId = STARTING_ROOM_ID + i;
            targetRoomIds.add(roomId);

            JsonObject roomObj = findRoomById(allRooms, roomId);
            Assertions.assertNotNull(roomObj, "Target Room " + roomId + " must exist in inventory");
            Assertions.assertEquals("vacant_clean", roomObj.get("status").getAsString(),
                    "Room " + roomId + " must initially be in 'vacant_clean' state");
        }
        System.out.println("✔ [STEP 1] Confirmed 20 candidate rooms (IDs 7–26) in 'vacant_clean' baseline state.");

        // ---------------------------------------------------------------------
        // STEP 2: Group Reservation of 20 Rooms (POST /api/v1/bookings/group)
        // ---------------------------------------------------------------------
        JsonObject groupBookingReq = new JsonObject();
        groupBookingReq.addProperty("groupName", "Global Tech Summit 2026 Delegation");
        groupBookingReq.addProperty("customerId", 1);
        groupBookingReq.addProperty("checkInDate", "2026-10-25T14:00:00Z");
        groupBookingReq.addProperty("checkOutDate", "2026-10-28T12:00:00Z");
        groupBookingReq.addProperty("customerType", "Corporate");
        groupBookingReq.addProperty("splitBilling", true);

        JsonArray roomIdsArray = new JsonArray();
        for (int rId : targetRoomIds) {
            roomIdsArray.add(rId);
        }
        groupBookingReq.add("roomIds", roomIdsArray);

        HttpResponse<String> groupResp = post("/api/v1/bookings/group", groupBookingReq.toString(), authHeaders);
        Assertions.assertEquals(201, groupResp.statusCode(), "POST /api/v1/bookings/group must return 201 Created");

        JsonObject groupJson = parseJson(groupResp.body());
        Assertions.assertEquals("success", groupJson.get("status").getAsString());

        JsonObject groupData = groupJson.getAsJsonObject("data");
        String masterFolioId = groupData.get("masterFolioId").getAsString();
        Assertions.assertNotNull(masterFolioId, "Master Folio ID must be generated");
        Assertions.assertTrue(masterFolioId.startsWith("FOLIO-MASTER"), "Master Folio ID must follow standard naming");

        JsonArray bookings = groupData.getAsJsonArray("bookings");
        Assertions.assertEquals(GROUP_ROOM_COUNT, bookings.size(), "Must create exactly 20 sub-bookings");

        List<Integer> bookingIds = new ArrayList<>();
        for (JsonElement bElem : bookings) {
            JsonObject b = bElem.getAsJsonObject();
            bookingIds.add(b.get("id").getAsInt());
            Assertions.assertEquals("confirmed", b.get("status").getAsString());
        }

        // Verify all 20 rooms transitioned to 'reserved'
        HttpResponse<String> postBookingRoomsResp = get("/api/v1/rooms", authHeaders);
        JsonArray postBookingRooms = parseJson(postBookingRoomsResp.body()).getAsJsonArray("data");

        for (int rId : targetRoomIds) {
            JsonObject roomObj = findRoomById(postBookingRooms, rId);
            Assertions.assertEquals("reserved", roomObj.get("status").getAsString(),
                    "Room " + rId + " must transition to 'reserved' upon booking");
        }
        System.out.println("✔ [STEP 2] Group reservation created: 20 rooms booked, status='reserved', MasterFolio=" + masterFolioId);

        // ---------------------------------------------------------------------
        // STEP 3: Independent Early Check-in for 5 Guests (Rooms 7 to 11)
        // ---------------------------------------------------------------------
        List<Integer> earlyRoomIds = targetRoomIds.subList(0, EARLY_ARRIVAL_COUNT);
        List<Integer> lateRoomIds = targetRoomIds.subList(EARLY_ARRIVAL_COUNT, GROUP_ROOM_COUNT);

        System.out.println("⚡ [STEP 3] Performing independent check-in for 5 early arrivals at 10:00 AM...");
        for (int rId : earlyRoomIds) {
            HttpResponse<String> checkInResp = post("/api/v1/rooms/" + rId + "/check-in", "{}", authHeaders);
            Assertions.assertEquals(200, checkInResp.statusCode(),
                    "POST /api/v1/rooms/" + rId + "/check-in must return 200 OK");

            JsonObject checkInData = parseJson(checkInResp.body()).getAsJsonObject("data");
            Assertions.assertEquals("occupied_clean", checkInData.get("status").getAsString(),
                    "Early checked-in room must transition to 'occupied_clean'");
            System.out.println("   • Room " + rId + " (" + checkInData.get("roomNumber").getAsString()
                    + ") checked in -> status = occupied_clean");
        }

        // ---------------------------------------------------------------------
        // STEP 4: DoD Assertion 1 & 2 — Confirm 5 occupied_clean, 15 reserved
        // ---------------------------------------------------------------------
        HttpResponse<String> postCheckInRoomsResp = get("/api/v1/rooms", authHeaders);
        JsonArray postCheckInRooms = parseJson(postCheckInRoomsResp.body()).getAsJsonArray("data");

        // Assertion 1: Exactly the 5 early rooms are occupied_clean
        for (int rId : earlyRoomIds) {
            JsonObject roomObj = findRoomById(postCheckInRooms, rId);
            Assertions.assertEquals("occupied_clean", roomObj.get("status").getAsString(),
                    "DoD Assertion 1: Early arrival Room " + rId + " must be strictly 'occupied_clean'");
        }
        System.out.println("✔ [DoD ASSERTION 1] Verified all 5 early arrival rooms are 'occupied_clean'.");

        // Assertion 2: The remaining 15 rooms remain 'reserved'
        for (int rId : lateRoomIds) {
            JsonObject roomObj = findRoomById(postCheckInRooms, rId);
            Assertions.assertEquals("reserved", roomObj.get("status").getAsString(),
                    "DoD Assertion 2: Late arrival Room " + rId + " must remain strictly 'reserved'");
        }
        System.out.println("✔ [DoD ASSERTION 2] Verified all remaining 15 group rooms remain 'reserved'.");

        // ---------------------------------------------------------------------
        // STEP 5: Post Incidental Charges & Verify Auto-Routing to FOLIO-GUEST
        // ---------------------------------------------------------------------
        System.out.println("⚡ [STEP 5] Posting incidental charges to early arrival rooms & testing billing routing...");

        int earlyBookingId1 = bookingIds.get(0); // Booking for Room 7
        int earlyBookingId2 = bookingIds.get(1); // Booking for Room 8

        // Charge 1: Mini-bar on Room 7 (120,000 VND)
        JsonObject minibarCharge = new JsonObject();
        minibarCharge.addProperty("bookingId", earlyBookingId1);
        minibarCharge.addProperty("category", "MINI_BAR");
        minibarCharge.addProperty("description", "2x Evian Mineral Water & Toblerone");
        minibarCharge.addProperty("amount", 120000.0);

        HttpResponse<String> chargeResp1 = post("/api/v1/folios/" + earlyBookingId1 + "/charges",
                minibarCharge.toString(), authHeaders);
        Assertions.assertEquals(200, chargeResp1.statusCode());
        JsonObject chargeData1 = parseJson(chargeResp1.body()).getAsJsonObject("data");

        Assertions.assertEquals("FOLIO-GUEST-" + earlyBookingId1, chargeData1.get("routedTo").getAsString(),
                "Mini-bar charge must route exclusively to Guest Folio");
        Assertions.assertTrue(chargeData1.get("isIncidental").getAsBoolean(),
                "Mini-bar must be flagged as incidental");

        // Charge 2: Laundry Service on Room 7 (85,000 VND)
        JsonObject laundryCharge = new JsonObject();
        laundryCharge.addProperty("bookingId", earlyBookingId1);
        laundryCharge.addProperty("category", "LAUNDRY");
        laundryCharge.addProperty("description", "Express Dry Cleaning Suite");
        laundryCharge.addProperty("amount", 85000.0);

        HttpResponse<String> chargeResp2 = post("/api/v1/folios/" + earlyBookingId1 + "/charges",
                laundryCharge.toString(), authHeaders);
        Assertions.assertEquals(200, chargeResp2.statusCode());
        JsonObject chargeData2 = parseJson(chargeResp2.body()).getAsJsonObject("data");

        Assertions.assertEquals("FOLIO-GUEST-" + earlyBookingId1, chargeData2.get("routedTo").getAsString(),
                "Laundry charge must route exclusively to Guest Folio");

        // Charge 3: Room Service F&B on Room 8 (350,000 VND)
        JsonObject fnbCharge = new JsonObject();
        fnbCharge.addProperty("bookingId", earlyBookingId2);
        fnbCharge.addProperty("category", "ROOM_SERVICE");
        fnbCharge.addProperty("description", "Late Breakfast Continental Set");
        fnbCharge.addProperty("amount", 350000.0);

        HttpResponse<String> chargeResp3 = post("/api/v1/folios/" + earlyBookingId2 + "/charges",
                fnbCharge.toString(), authHeaders);
        Assertions.assertEquals(200, chargeResp3.statusCode());
        JsonObject chargeData3 = parseJson(chargeResp3.body()).getAsJsonObject("data");

        Assertions.assertEquals("FOLIO-GUEST-" + earlyBookingId2, chargeData3.get("routedTo").getAsString(),
                "Room Service charge must route exclusively to Guest Folio");

        // ---------------------------------------------------------------------
        // STEP 6: DoD Assertion 3 — Inspect Guest Folios & Master Folio Isolation
        // ---------------------------------------------------------------------
        // Inspect Guest Folio 1 (Room 7): expected balance = 120,000 + 85,000 = 205,000 VND
        HttpResponse<String> guestFolioResp1 = get("/api/v1/folios/FOLIO-GUEST-" + earlyBookingId1, authHeaders);
        Assertions.assertEquals(200, guestFolioResp1.statusCode());
        JsonObject guestFolioData1 = parseJson(guestFolioResp1.body()).getAsJsonObject("data");

        Assertions.assertEquals("GUEST", guestFolioData1.get("type").getAsString());
        Assertions.assertEquals(205000.0, guestFolioData1.get("balance").getAsDouble(), 0.001,
                "DoD Assertion 3: FOLIO-GUEST must reflect exact incidental balance (205,000 VND)");
        Assertions.assertEquals(2, guestFolioData1.getAsJsonArray("charges").size(),
                "FOLIO-GUEST must contain exactly 2 incidental line items");

        // Inspect Guest Folio 2 (Room 8): expected balance = 350,000 VND
        HttpResponse<String> guestFolioResp2 = get("/api/v1/folios/FOLIO-GUEST-" + earlyBookingId2, authHeaders);
        Assertions.assertEquals(200, guestFolioResp2.statusCode());
        JsonObject guestFolioData2 = parseJson(guestFolioResp2.body()).getAsJsonObject("data");
        Assertions.assertEquals(350000.0, guestFolioData2.get("balance").getAsDouble(), 0.001,
                "DoD Assertion 3: FOLIO-GUEST-2 must reflect exact incidental balance (350,000 VND)");

        // Inspect Master Folio for Room 7: must have zero incidental charges
        HttpResponse<String> masterFolioResp1 = get("/api/v1/folios/FOLIO-MASTER-" + earlyBookingId1, authHeaders);
        Assertions.assertEquals(200, masterFolioResp1.statusCode());
        JsonObject masterFolioData1 = parseJson(masterFolioResp1.body()).getAsJsonObject("data");

        Assertions.assertEquals("MASTER", masterFolioData1.get("type").getAsString());
        JsonArray masterCharges = masterFolioData1.getAsJsonArray("charges");
        for (JsonElement chgElem : masterCharges) {
            String cat = chgElem.getAsJsonObject().get("category").getAsString();
            Assertions.assertNotEquals("MINI_BAR", cat, "Master Folio must NEVER contain Mini-bar charges");
            Assertions.assertNotEquals("LAUNDRY", cat, "Master Folio must NEVER contain Laundry charges");
            Assertions.assertNotEquals("ROOM_SERVICE", cat, "Master Folio must NEVER contain Room Service charges");
        }
        System.out.println("✔ [DoD ASSERTION 3] Incidental charges successfully routed to FOLIO-GUEST (Balance = "
                + guestFolioData1.get("balance").getAsDouble() + " đ). Master Folio isolated from incidentals.");

        // ---------------------------------------------------------------------
        // STEP 7: DoD Invariant — Verify Master Folio Closure is BLOCKED when Guest Incidental is Unpaid
        // ---------------------------------------------------------------------
        System.out.println("🛡️ [STEP 7] Verifying Master Folio closure is BLOCKED when guest incidentals remain unpaid...");

        // 1. Attempt to close individual Booking Master Folio while Guest Folio has 205,000 VND unpaid
        HttpResponse<String> closeBookingMasterResp = post("/api/v1/folios/FOLIO-MASTER-" + earlyBookingId1 + "/close", "{}", authHeaders);
        Assertions.assertEquals(400, closeBookingMasterResp.statusCode(),
                "Settling/Closing Master Folio must be rejected (400 Bad Request) while Guest Folio balance > 0");
        JsonObject closeErr = parseJson(closeBookingMasterResp.body());
        Assertions.assertEquals("ERR_400_BAD_REQUEST", closeErr.get("error_code").getAsString());

        // 2. Attempt to close Group Master Folio while linked guest folios have unpaid incidentals
        HttpResponse<String> closeGroupMasterResp = post("/api/v1/folios/" + masterFolioId + "/close", "{}", authHeaders);
        Assertions.assertEquals(400, closeGroupMasterResp.statusCode(),
                "Settling/Closing Group Master Folio must be rejected while linked Guest Folios have unpaid incidentals");
        JsonObject groupCloseErr = parseJson(closeGroupMasterResp.body());
        Assertions.assertEquals("ERR_400_BAD_REQUEST", groupCloseErr.get("error_code").getAsString());

        System.out.println("✔ [DoD REVENUE GUARDRAIL] Master Folio closure strictly blocked while guest incidentals unpaid.");

        // ---------------------------------------------------------------------
        // STEP 8: Performance DoD Assertion — Execution Time < 3 Seconds
        // ---------------------------------------------------------------------
        long durationMs = System.currentTimeMillis() - scenarioStartTime;
        System.out.println("⏱ [DoD ASSERTION 4] Full Scenario 1 executed in " + durationMs + " ms (Requirement: < 3000 ms)");
        Assertions.assertTrue(durationMs < 3000,
                "DoD Assertion 4: Scenario 1 must complete in under 3 seconds (was " + durationMs + " ms)");

        System.out.println("==================================================================");
        System.out.println("🎉 [TASK-D3.1 VERIFIED] 100% of Scenario 1 Acceptance Criteria Passed Cleanly!");
        System.out.println("==================================================================");
    }

    private JsonObject findRoomById(JsonArray roomsArray, int targetId) {
        for (JsonElement el : roomsArray) {
            if (el.isJsonObject()) {
                JsonObject r = el.getAsJsonObject();
                if (r.has("id") && r.get("id").getAsInt() == targetId) {
                    return r;
                }
            }
        }
        return null;
    }
}
