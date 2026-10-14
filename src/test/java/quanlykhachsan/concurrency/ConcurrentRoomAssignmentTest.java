package quanlykhachsan.concurrency;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import quanlykhachsan.BaseIntegrationTest;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Automated Concurrency Test for Scenario 5 (TASK-D2.1).
 * <p>
 * Simulates high-contention race condition: 50 concurrent threads submitting
 * POST /api/v1/bookings targeting the same vacant room at the exact same millisecond.
 * <p>
 * Definition of Done (DoD):
 * 1. Exactly 1 request receives 200/201 OK.
 * 2. Exactly 49 requests receive 409 Conflict (ERR_409_CONFLICT).
 * 3. Room status transitions to 'reserved' and room version increments by exactly 1.
 * 4. Zero double-booking occurs (exactly 1 booking record created for the room).
 */
public class ConcurrentRoomAssignmentTest extends BaseIntegrationTest {

    private static final int CONCURRENT_THREADS = 50;
    private static final int TARGET_ROOM_ID = 1; // Room 101: initial status 'vacant_clean', initial version 1

    @Test
    @DisplayName("TASK-D2.1: 50 concurrent threads competing for the same vacant room enforce atomic optimistic locking")
    public void testConcurrentRoomAssignment_Scenario5() throws Exception {
        System.out.println("==================================================================");
        System.out.println("⚡ [SCENARIO 5] Running 50-Thread Concurrent Room Allocation Test...");
        System.out.println("==================================================================");

        // 1. Verify Target Room Initial State
        HttpResponse<String> initialRoomResp = get("/api/v1/rooms");
        Assertions.assertEquals(200, initialRoomResp.statusCode());
        JsonObject targetRoomInitial = findRoomById(initialRoomResp.body(), TARGET_ROOM_ID);
        Assertions.assertNotNull(targetRoomInitial, "Target Room " + TARGET_ROOM_ID + " must exist");
        Assertions.assertEquals("vacant_clean", targetRoomInitial.get("status").getAsString(),
                "Target Room must start as 'vacant_clean'");
        int initialVersion = targetRoomInitial.has("version") ? targetRoomInitial.get("version").getAsInt() : 1;
        System.out.println("✔ Initial Room State: ID=" + TARGET_ROOM_ID + ", Status=vacant_clean, Version=" + initialVersion);

        int initialConfirmedBookings = countConfirmedBookingsForRoom(TARGET_ROOM_ID);
        Assertions.assertEquals(0, initialConfirmedBookings,
                "Target Room must have 0 active/confirmed bookings before test begins");

        // 2. Synchronization Primitives
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_THREADS);

        ExecutorService threadPool = Executors.newFixedThreadPool(CONCURRENT_THREADS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger otherCount = new AtomicInteger(0);

        Map<Integer, Integer> responseCodes = new ConcurrentHashMap<>();
        Map<Integer, String> responseBodies = new ConcurrentHashMap<>();

        // 3. Dispatch 50 Concurrent Threads
        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            final int threadIndex = i;
            threadPool.submit(() -> {
                try {
                    // Unique Idempotency-Key per thread to evaluate distinct concurrent reservation attempts
                    String idempotencyKey = "concurrent-key-" + UUID.randomUUID();

                    // Cryptographically valid HMAC-SHA256 Bearer JWT token (FrontDesk role) matching JwtFilter
                    String token = generateSignedJwt("FrontDesk", "agent_" + threadIndex, 100 + threadIndex);

                    JsonObject bookingReq = new JsonObject();
                    bookingReq.addProperty("customerId", (threadIndex % 3) + 1);
                    bookingReq.addProperty("roomId", TARGET_ROOM_ID);
                    bookingReq.addProperty("roomVersion", initialVersion);
                    bookingReq.addProperty("checkInDate", "2026-10-25T14:00:00Z");
                    bookingReq.addProperty("checkOutDate", "2026-10-28T12:00:00Z");
                    bookingReq.addProperty("totalPrice", 1500000.0);
                    bookingReq.addProperty("customerType", "Walk-in");

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/api/v1/bookings"))
                            .timeout(Duration.ofSeconds(15))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", idempotencyKey)
                            .POST(HttpRequest.BodyPublishers.ofString(bookingReq.toString(), StandardCharsets.UTF_8))
                            .build();

                    // Signal ready and await coordinated simultaneous blast
                    readyLatch.countDown();
                    startLatch.await();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                    int status = response.statusCode();
                    responseCodes.put(threadIndex, status);
                    responseBodies.put(threadIndex, response.body());

                    if (status == 200 || status == 201) {
                        successCount.incrementAndGet();
                    } else if (status == 409) {
                        conflictCount.incrementAndGet();
                    } else {
                        otherCount.incrementAndGet();
                    }

                } catch (Exception e) {
                    otherCount.incrementAndGet();
                    responseBodies.put(threadIndex, "Exception: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Wait for all 50 threads to line up at the gate
        boolean ready = readyLatch.await(10, TimeUnit.SECONDS);
        Assertions.assertTrue(ready, "All 50 threads must be ready within 10 seconds");

        // Fire all 50 requests at the exact same millisecond
        long startTime = System.currentTimeMillis();
        startLatch.countDown();

        // Wait for all 50 requests to complete
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;
        threadPool.shutdown();

        Assertions.assertTrue(completed, "All 50 threads must complete within 30 seconds");

        System.out.println("⏱ 50 Concurrent Requests completed in " + duration + " ms");
        System.out.println("📊 Results Breakdown:");
        System.out.println("   • HTTP 200/201 (Success) : " + successCount.get());
        System.out.println("   • HTTP 409     (Conflict): " + conflictCount.get());
        System.out.println("   • Other Statuses         : " + otherCount.get());

        // 4. Assert Exact DoD Concurrency Invariants
        Assertions.assertEquals(1, successCount.get(),
                "CRITICAL INVARIANT: Exactly 1 request must receive 200/201 OK");
        Assertions.assertEquals(49, conflictCount.get(),
                "CRITICAL INVARIANT: Exactly 49 requests must receive 409 Conflict");
        Assertions.assertEquals(0, otherCount.get(),
                "CRITICAL INVARIANT: Zero requests should encounter unexpected error codes");

        // 5. Verify Room Status & Optimistic Locking Version Increment
        HttpResponse<String> postRoomResp = get("/api/v1/rooms");
        JsonObject targetRoomFinal = findRoomById(postRoomResp.body(), TARGET_ROOM_ID);
        Assertions.assertNotNull(targetRoomFinal, "Target Room must exist post-test");

        String finalStatus = targetRoomFinal.get("status").getAsString();
        int finalVersion = targetRoomFinal.get("version").getAsInt();

        System.out.println("✔ Final Room State: ID=" + TARGET_ROOM_ID
                + ", Status=" + finalStatus + ", Version=" + finalVersion);

        Assertions.assertEquals("reserved", finalStatus,
                "Room status must transition to 'reserved'");
        Assertions.assertEquals(initialVersion + 1, finalVersion,
                "Room version must increment by exactly 1 (initial: " + initialVersion + ", final: " + finalVersion + ")");

        // 6. Verify Zero Double-Booking in Database/Registry
        int finalConfirmedBookings = countConfirmedBookingsForRoom(TARGET_ROOM_ID);
        System.out.println("✔ Confirmed Bookings for Room " + TARGET_ROOM_ID + ": " + finalConfirmedBookings);
        Assertions.assertEquals(1, finalConfirmedBookings,
                "ZERO DOUBLE-BOOKING: Exactly 1 booking record must exist for Room " + TARGET_ROOM_ID);
        Assertions.assertEquals(initialConfirmedBookings + 1, finalConfirmedBookings,
                "ZERO DOUBLE-BOOKING: Exactly 1 new booking record was added during concurrent execution");

        System.out.println("🎉 [SCENARIO 5 VERIFIED] Exactly 1 winner, 49 conflicts, version incremented by 1, zero double-booking.");
    }

    private int countConfirmedBookingsForRoom(int roomId) throws Exception {
        HttpResponse<String> bookingsResp = get("/api/v1/bookings");
        JsonArray bookings = parseJson(bookingsResp.body()).getAsJsonArray("data");
        int count = 0;
        for (JsonElement el : bookings) {
            JsonObject b = el.getAsJsonObject();
            if (b.get("roomId").getAsInt() == roomId && "confirmed".equalsIgnoreCase(b.get("status").getAsString())) {
                count++;
            }
        }
        return count;
    }

    private JsonObject findRoomById(String roomsJson, int roomId) {
        JsonObject parsed = parseJson(roomsJson);
        JsonArray data = parsed.getAsJsonArray("data");
        for (JsonElement el : data) {
            JsonObject r = el.getAsJsonObject();
            if (r.get("id").getAsInt() == roomId) {
                return r;
            }
        }
        return null;
    }
}
