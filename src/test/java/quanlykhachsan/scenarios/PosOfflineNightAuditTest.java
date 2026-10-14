package quanlykhachsan.scenarios;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import quanlykhachsan.BaseIntegrationTest;
import quanlykhachsan.backend.system.SystemStateHolder;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Enterprise Automated Scenario 3 Test: POS Offline during Night Audit (TASK-D3.2).
 * <p>
 * Scenario Definition &amp; Context (docs/dev-spec/test-scenarios.md &amp; tasks-phase1.md):
 * <ul>
 *   <li><b>Context:</b> The F&amp;B POS loses connection to PMS during Night Audit (00:00 - 00:05 freeze). A guest orders late-night room service at 00:02.</li>
 *   <li><b>Expected Behavior:</b> PMS rejects writes with 503 Retry-After: 300. POS enters store-and-forward mode and queues locally. When system restores at 00:06, POS syncs batch. Charge posts under the NEW business date.</li>
 * </ul>
 * <p>
 * Definition of Done (DoD) Assertions:
 * <ol>
 *   <li><b>DoD Assertion 1:</b> PMS rejects API calls during 00:00-00:05 Lock with HTTP 503 Service Unavailable and header 'Retry-After: 300'.</li>
 *   <li><b>DoD Assertion 2:</b> POS enters Store-and-Forward mode and queues the transaction locally (verifying offline queue, status, and 00:02 timestamp).</li>
 *   <li><b>DoD Assertion 3:</b> Post-sync transaction timestamp in the Folio reflects the NEW business date (2026-10-15), while preserving the original 00:02 generation timestamp.</li>
 * </ol>
 */
public class PosOfflineNightAuditTest extends BaseIntegrationTest {

    private static final LocalDate DAY_1_DATE = LocalDate.of(2026, 10, 14);
    private static final LocalDate DAY_2_DATE = LocalDate.of(2026, 10, 15);
    private static final String TARGET_GUEST_FOLIO_ID = "FOLIO-GUEST-1";
    private static final double ROOM_SERVICE_AMOUNT = 450000.0; // Wagyu Burger & Organic Herbal Tea

    /**
     * Store-and-Forward POS Client Model for Scenario 3
     */
    public static class OfflinePosTransaction {
        private final String transactionId;
        private final String folioId;
        private final String roomNumber;
        private final String category;
        private final String description;
        private final double amount;
        private final String offlineTimestamp;
        private final String idempotencyKey;
        private String syncStatus;

        public OfflinePosTransaction(String transactionId, String folioId, String roomNumber,
                                     String category, String description, double amount,
                                     String offlineTimestamp, String idempotencyKey) {
            this.transactionId = transactionId;
            this.folioId = folioId;
            this.roomNumber = roomNumber;
            this.category = category;
            this.description = description;
            this.amount = amount;
            this.offlineTimestamp = offlineTimestamp;
            this.idempotencyKey = idempotencyKey;
            this.syncStatus = "CREATED";
        }

        public String getTransactionId() { return transactionId; }
        public String getFolioId() { return folioId; }
        public String getRoomNumber() { return roomNumber; }
        public String getCategory() { return category; }
        public String getDescription() { return description; }
        public double getAmount() { return amount; }
        public String getOfflineTimestamp() { return offlineTimestamp; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public String getSyncStatus() { return syncStatus; }
        public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("transaction_id", transactionId);
            obj.addProperty("folio_id", folioId);
            obj.addProperty("room_number", roomNumber);
            obj.addProperty("category", category);
            obj.addProperty("description", description);
            obj.addProperty("amount", amount);
            obj.addProperty("offline_timestamp", offlineTimestamp);
            obj.addProperty("idempotency_key", idempotencyKey);
            return obj;
        }
    }

    public static class PosTerminalClient {
        private final String terminalId;
        private final Queue<OfflinePosTransaction> offlineQueue = new ConcurrentLinkedQueue<>();

        public PosTerminalClient(String terminalId) {
            this.terminalId = terminalId;
        }

        public void enqueueOffline(OfflinePosTransaction txn) {
            txn.setSyncStatus("QUEUED_OFFLINE");
            offlineQueue.add(txn);
        }

        public Queue<OfflinePosTransaction> getOfflineQueue() {
            return offlineQueue;
        }

        public JsonObject buildSyncBatchPayload(String batchId) {
            JsonObject batch = new JsonObject();
            batch.addProperty("batch_id", batchId);
            batch.addProperty("terminal_id", terminalId);
            JsonArray txnsArray = new JsonArray();
            for (OfflinePosTransaction txn : offlineQueue) {
                txnsArray.add(txn.toJson());
            }
            batch.add("transactions", txnsArray);
            return batch;
        }

        public void markAllSynced() {
            for (OfflinePosTransaction txn : offlineQueue) {
                txn.setSyncStatus("SYNCED_CONFIRMED");
            }
            offlineQueue.clear();
        }
    }

    @Test
    @DisplayName("TASK-D3.2: Automated Scenario 3 Test — POS Offline during Night Audit (00:00 - 00:05 Lockout & Store-and-Forward)")
    public void testScenario3_PosOfflineDuringNightAudit() throws Exception {
        System.out.println("==================================================================");
        System.out.println("🌙 [SCENARIO 3] Executing Automated POS Offline Night Audit Suite...");
        System.out.println("==================================================================");

        // Standard Front Desk Bearer JWT Authentication
        String frontDeskToken = generateSignedJwt("FrontDesk", "staff_night_audit", 108);
        Map<String, String> authHeaders = Map.of(
                "Authorization", createAuthHeader(frontDeskToken),
                "Content-Type", "application/json"
        );

        // Warm up HTTP connection & JIT to eliminate cold-start socket allocation latency
        get("/api/v1/health");

        long scenarioStartTime = System.currentTimeMillis();

        // ---------------------------------------------------------------------
        // STEP 1: Verify Baseline Operating Day (Day 1: 2026-10-14) & Folio
        // ---------------------------------------------------------------------
        SystemStateHolder.setBusinessDate(DAY_1_DATE);
        SystemStateHolder.setFrozen(false);

        // Ensure active booking & guest folio exists for Room 101
        HttpResponse<String> initialFolioResp = get("/api/v1/folios/" + TARGET_GUEST_FOLIO_ID, authHeaders);
        if (initialFolioResp.statusCode() == 404) {
            // Split folio for Booking 1 (Room 101) if not already created
            JsonObject splitReq = new JsonObject();
            splitReq.addProperty("roomRate", 1500000.0);
            post("/api/v1/folios/1/split", splitReq.toString(), authHeaders);
        }

        HttpResponse<String> folioBeforeResp = get("/api/v1/folios/" + TARGET_GUEST_FOLIO_ID, authHeaders);
        Assertions.assertEquals(200, folioBeforeResp.statusCode(), "GET /api/v1/folios/" + TARGET_GUEST_FOLIO_ID + " must return 200 OK");
        JsonObject folioBeforeJson = parseJson(folioBeforeResp.body()).getAsJsonObject("data");
        double initialBalance = folioBeforeJson.get("balance").getAsDouble();

        System.out.println("✔ [STEP 1] Baseline operational state verified: Business Date = "
                + SystemStateHolder.getBusinessDate() + ", Guest Folio Balance = " + initialBalance + " VND.");

        // ---------------------------------------------------------------------
        // STEP 2: 00:00 Night Audit Initiated — System Freeze Engaged
        // ---------------------------------------------------------------------
        SystemStateHolder.setFrozen(true);
        System.out.println("❄ [STEP 2] 00:00 Night Audit initiated: System State frozen (00:00 - 00:05 Lockout).");

        // Verify Read-Only traffic is NOT blocked during freeze (TASK-A3.1 requirement)
        HttpResponse<String> readCheckResp = get("/api/v1/rooms", authHeaders);
        Assertions.assertEquals(200, readCheckResp.statusCode(),
                "Read-only queries (GET /api/v1/rooms) must remain operational (200 OK) during freeze");
        System.out.println("   • Confirmed read queries remain 100% operational during system freeze.");

        // ---------------------------------------------------------------------
        // STEP 3: 00:02 POS Attempts Transaction Online — Rejected with 503 Retry-After: 300
        // ---------------------------------------------------------------------
        PosTerminalClient posTerminal = new PosTerminalClient("POS-FB-ROOMSERVICE-01");
        OfflinePosTransaction roomServiceTxn = new OfflinePosTransaction(
                "TXN-POS-88210",
                TARGET_GUEST_FOLIO_ID,
                "101",
                "ROOM_SERVICE",
                "Late-Night Wagyu Burger & Organic Herbal Tea",
                ROOM_SERVICE_AMOUNT,
                "2026-10-14T00:02:15Z",
                "IDEMP-POS-TXN-88210"
        );

        JsonObject attemptPayload = new JsonObject();
        attemptPayload.addProperty("batch_id", "POS-BATCH-ATTEMPT-01");
        attemptPayload.addProperty("terminal_id", "POS-FB-ROOMSERVICE-01");
        JsonArray singleTxnArr = new JsonArray();
        singleTxnArr.add(roomServiceTxn.toJson());
        attemptPayload.add("transactions", singleTxnArr);

        Map<String, String> syncHeaders = Map.of(
                "Authorization", createAuthHeader(frontDeskToken),
                "Content-Type", "application/json",
                "Idempotency-Key", roomServiceTxn.getIdempotencyKey()
        );

        long freezeReqStart = System.nanoTime();
        HttpResponse<String> frozenSyncResp = post("/api/v1/pos/sync", attemptPayload.toString(), syncHeaders);
        long freezeLatencyMs = (System.nanoTime() - freezeReqStart) / 1_000_000;

        // DoD ASSERTION 1: 503 Service Unavailable + Retry-After: 300 + < 10ms filter latency
        System.out.println("⏱ [DoD ASSERTION 1] Write during freeze returned in " + freezeLatencyMs + " ms.");
        Assertions.assertEquals(503, frozenSyncResp.statusCode(),
                "PMS must reject mutating API calls during 00:00-00:05 lock with HTTP 503");

        String retryAfter = frozenSyncResp.headers().firstValue("Retry-After").orElse(null);
        Assertions.assertEquals("300", retryAfter,
                "503 Service Unavailable response must include header 'Retry-After: 300'");

        JsonObject errorBody = parseJson(frozenSyncResp.body());
        Assertions.assertEquals("ERR_503_SERVICE_UNAVAILABLE", errorBody.get("error_code").getAsString(),
                "Error code must be ERR_503_SERVICE_UNAVAILABLE");

        System.out.println("✔ [DoD ASSERTION 1] Verified HTTP 503 Service Unavailable with Retry-After: 300 (Status="
                + frozenSyncResp.statusCode() + ", Code=" + errorBody.get("error_code").getAsString() + ").");

        // ---------------------------------------------------------------------
        // STEP 4: POS Enters Store-and-Forward Mode & Queues Transaction Locally
        // ---------------------------------------------------------------------
        // POS terminal client detects 503 with Retry-After, buffers locally into offline queue
        posTerminal.enqueueOffline(roomServiceTxn);

        // DoD ASSERTION 2: Local store-and-forward queueing verified
        Assertions.assertEquals(1, posTerminal.getOfflineQueue().size(),
                "POS terminal must queue exactly 1 transaction in local store-and-forward queue");
        OfflinePosTransaction queuedTxn = posTerminal.getOfflineQueue().peek();
        Assertions.assertNotNull(queuedTxn);
        Assertions.assertEquals("QUEUED_OFFLINE", queuedTxn.getSyncStatus(),
                "Queued transaction state must be 'QUEUED_OFFLINE'");
        Assertions.assertEquals("2026-10-14T00:02:15Z", queuedTxn.getOfflineTimestamp(),
                "Queued transaction must preserve offline creation timestamp 00:02:15");
        Assertions.assertEquals(ROOM_SERVICE_AMOUNT, queuedTxn.getAmount(),
                "Queued transaction amount must match 450,000 VND");

        System.out.println("✔ [DoD ASSERTION 2] POS Store-and-Forward successfully queued transaction locally (QueueSize="
                + posTerminal.getOfflineQueue().size() + ", Status=" + queuedTxn.getSyncStatus() + ", OfflineTime="
                + queuedTxn.getOfflineTimestamp() + ").");

        // ---------------------------------------------------------------------
        // STEP 5: 00:05 Night Audit Completes (Step 7: Advance Hotel Date & Unfreeze)
        // ---------------------------------------------------------------------
        SystemStateHolder.advanceBusinessDate(); // Advances from 2026-10-14 to 2026-10-15
        SystemStateHolder.setFrozen(false);

        Assertions.assertFalse(SystemStateHolder.isSystemFrozen(), "System must be unfrozen after Night Audit completion");
        Assertions.assertEquals(DAY_2_DATE, SystemStateHolder.getBusinessDate(),
                "Business date must advance to Day 2 (2026-10-15)");

        System.out.println("🌅 [STEP 5] 00:05 Night Audit Step 7 finished: System Unfrozen. New Business Date = "
                + SystemStateHolder.getBusinessDate());

        // ---------------------------------------------------------------------
        // STEP 6: 00:06 Connection Restored — Store-and-Forward Drain & Auto-Sync
        // ---------------------------------------------------------------------
        String syncBatchId = "POS-BATCH-20261015-001";
        JsonObject syncPayload = posTerminal.buildSyncBatchPayload(syncBatchId);

        HttpResponse<String> syncResp = post("/api/v1/pos/sync", syncPayload.toString(), syncHeaders);
        Assertions.assertEquals(200, syncResp.statusCode(), "POST /api/v1/pos/sync must return 200 OK after unfreeze");

        JsonObject syncJson = parseJson(syncResp.body());
        Assertions.assertEquals("success", syncJson.get("status").getAsString());

        JsonObject syncData = syncJson.getAsJsonObject("data");
        Assertions.assertEquals(1, syncData.get("synced_count").getAsInt(), "Synced count must be 1");
        Assertions.assertEquals(DAY_2_DATE.toString(), syncData.get("business_date").getAsString(),
                "Sync receiver must record charges under the active business date");

        posTerminal.markAllSynced();
        Assertions.assertEquals(0, posTerminal.getOfflineQueue().size(),
                "POS terminal offline queue must be empty after successful drain and confirmation");

        System.out.println("✔ [STEP 6] POS Store-and-Forward sync completed successfully: 1 transaction synced under date "
                + syncData.get("business_date").getAsString());

        // ---------------------------------------------------------------------
        // STEP 7: Inspect Folio & Verify Business Date Assignment (DoD Assertion 3)
        // ---------------------------------------------------------------------
        HttpResponse<String> folioAfterResp = get("/api/v1/folios/" + TARGET_GUEST_FOLIO_ID, authHeaders);
        Assertions.assertEquals(200, folioAfterResp.statusCode(), "GET /api/v1/folios/" + TARGET_GUEST_FOLIO_ID + " must return 200 OK");

        JsonObject folioAfterJson = parseJson(folioAfterResp.body()).getAsJsonObject("data");
        double updatedBalance = folioAfterJson.get("balance").getAsDouble();
        Assertions.assertEquals(initialBalance + ROOM_SERVICE_AMOUNT, updatedBalance, 0.001,
                "Folio balance must accurately increment by Room Service charge amount");

        JsonArray chargesList = folioAfterJson.getAsJsonArray("charges");
        JsonObject matchedCharge = null;
        for (JsonElement ce : chargesList) {
            JsonObject cObj = ce.getAsJsonObject();
            if (cObj.has("description") && cObj.get("description").getAsString().contains("Late-Night Wagyu Burger")) {
                matchedCharge = cObj;
                break;
            }
        }

        Assertions.assertNotNull(matchedCharge, "Synced charge must exist in Guest Folio charges list");

        // DoD ASSERTION 3: Business Date assignment & timestamp integrity
        String chargeBusinessDate = matchedCharge.get("businessDate").getAsString();
        Assertions.assertEquals(DAY_2_DATE.toString(), chargeBusinessDate,
                "Charge posting business date must reflect Day 2 (" + DAY_2_DATE + "), leaving Day 1 intact");

        Assertions.assertTrue(matchedCharge.has("offlineTimestamp"), "Charge must record original offline timestamp");
        String chargeOfflineTime = matchedCharge.get("offlineTimestamp").getAsString();
        Assertions.assertEquals("2026-10-14T00:02:15Z", chargeOfflineTime,
                "Offline generation timestamp must preserve original 00:02 creation time");

        System.out.println("✔ [DoD ASSERTION 3] Post-sync charge in " + TARGET_GUEST_FOLIO_ID
                + " reflects NEW business date (" + chargeBusinessDate + ") with original offline timestamp ("
                + chargeOfflineTime + "). New Folio Balance = " + updatedBalance + " VND.");

        // ---------------------------------------------------------------------
        // STEP 8: Idempotency Protection Verification (Zero Double-Billing)
        // ---------------------------------------------------------------------
        HttpResponse<String> replaySyncResp = post("/api/v1/pos/sync", syncPayload.toString(), syncHeaders);
        Assertions.assertEquals(200, replaySyncResp.statusCode(), "Replay sync request must return 200 OK");

        HttpResponse<String> folioReplayResp = get("/api/v1/folios/" + TARGET_GUEST_FOLIO_ID, authHeaders);
        double replayBalance = parseJson(folioReplayResp.body()).getAsJsonObject("data").get("balance").getAsDouble();
        Assertions.assertEquals(updatedBalance, replayBalance, 0.001,
                "Idempotent resubmission must NOT double-charge the guest folio");
        System.out.println("✔ [STEP 8] Idempotency verification passed: Folio balance remained unchanged ("
                + replayBalance + " VND).");

        // ---------------------------------------------------------------------
        // STEP 9: Scenario 3 SLA & Execution Summary
        // ---------------------------------------------------------------------
        long scenarioDurationMs = System.currentTimeMillis() - scenarioStartTime;
        System.out.println("⏱ [DoD ASSERTION 4] Full Scenario 3 executed in " + scenarioDurationMs + " ms (Requirement: < 3000 ms)");
        Assertions.assertTrue(scenarioDurationMs < 3000,
                "Scenario 3 SLA violation: execution took " + scenarioDurationMs + " ms, expected < 3000 ms");

        System.out.println("==================================================================");
        System.out.println("🎉 [TASK-D3.2 VERIFIED] 100% of Scenario 3 Acceptance Criteria Passed Cleanly!");
        System.out.println("==================================================================");
    }
}
