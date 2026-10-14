package quanlykhachsan.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quanlykhachsan.backend.utils.JsonUtil;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * High-Throughput & JVM Memory Stability Microbenchmark (TASK-A4.2).
 * <p>
 * Validates streaming Gson TypeAdapter performance:
 * <ul>
 *   <li>Single-thread serialization throughput exceeding 5,000 ops/s over 20,000 iterations.</li>
 *   <li>Elimination of scientific notation in financial fields (e.g. 1800000.00 vs 1.8E6).</li>
 *   <li>Stable JVM Heap footprint without memory leakage or excessive GC pressure.</li>
 *   <li>Full bidirectional roundtrip fidelity for Java 8+ time and high-precision numbers.</li>
 * </ul>
 */
@DisplayName("TASK-A4.2: Gson Streaming TypeAdapter Microbenchmark & Memory Profiling")
public class JsonThroughputMemoryTest {

    private static final Logger logger = LoggerFactory.getLogger(JsonThroughputMemoryTest.class);

    public static class ComplexRoomBooking {
        private Integer id;
        private String room_number;
        private BigDecimal price;
        private LocalDateTime check_in_date;
        private String status;

        public ComplexRoomBooking() {
        }

        public ComplexRoomBooking(Integer id, String room_number, BigDecimal price, LocalDateTime check_in_date, String status) {
            this.id = id;
            this.room_number = room_number;
            this.price = price;
            this.check_in_date = check_in_date;
            this.status = status;
        }

        public Integer getId() {
            return id;
        }

        public String getRoom_number() {
            return room_number;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public LocalDateTime getCheck_in_date() {
            return check_in_date;
        }

        public String getStatus() {
            return status;
        }
    }

    @Test
    @DisplayName("Verify serialization correctness, elimination of scientific notation and round-trip fidelity")
    void testSerializationCorrectnessAndFinancialPrecision() {
        LocalDateTime checkIn = LocalDateTime.of(2026, 10, 14, 14, 30, 0);
        BigDecimal price = new BigDecimal("1800000.00");
        ComplexRoomBooking sample = new ComplexRoomBooking(101, "VIP-101", price, checkIn, "confirmed");

        String json = JsonUtil.toJson(sample);
        assertNotNull(json, "Serialized JSON should not be null");

        // Verify elimination of scientific notation (e.g. 1.8E6)
        assertFalse(json.contains("1.8E"), "JSON must not contain scientific notation (1.8E)");
        assertFalse(json.contains("1.8e"), "JSON must not contain scientific notation (1.8e)");
        assertTrue(json.contains("1800000.00"), "JSON must serialize unshortened plain decimal representation");

        // Verify date formatting (yyyy-MM-dd HH:mm:ss)
        assertTrue(json.contains("2026-10-14 14:30:00"), "JSON must serialize date in yyyy-MM-dd HH:mm:ss format");

        // Verify bidirectional roundtrip
        ComplexRoomBooking deserialized = JsonUtil.fromJson(json, ComplexRoomBooking.class);
        assertNotNull(deserialized);
        assertEquals(101, deserialized.getId());
        assertEquals("VIP-101", deserialized.getRoom_number());
        assertEquals(new BigDecimal("1800000.00"), deserialized.getPrice());
        assertEquals(checkIn, deserialized.getCheck_in_date());
        assertEquals("confirmed", deserialized.getStatus());
    }

    @Test
    @DisplayName("Measure serialization throughput (Target: > 5,000 ops/s over 20,000 iterations)")
    void testSerializationThroughput() {
        LocalDateTime checkIn = LocalDateTime.of(2026, 10, 14, 14, 0, 0);
        BigDecimal price = new BigDecimal("1800000.00");
        ComplexRoomBooking payload = new ComplexRoomBooking(202, "PRESIDENTIAL-202", price, checkIn, "reserved");

        // 1. Warm-up loop (5,000 iterations) to trigger JIT compilation
        for (int i = 0; i < 5_000; i++) {
            String warmJson = JsonUtil.toJson(payload);
            if (warmJson.isEmpty()) {
                throw new IllegalStateException("Warmup failed");
            }
        }

        // 2. Timed measurement loop (20,000 iterations)
        final int iterations = 20_000;
        long startNanos = System.nanoTime();
        int sink = 0;

        for (int i = 0; i < iterations; i++) {
            String json = JsonUtil.toJson(payload);
            sink += json.length();
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        double throughput = iterations / elapsedSeconds;

        logger.info(String.format("TASK-A4.2 Microbenchmark Results: %d ops in %.4fs => Throughput = %.2f ops/s (sink=%d)",
                iterations, elapsedSeconds, throughput, sink));

        // 3. Assert minimum throughput threshold
        assertTrue(throughput > 5000.0,
                String.format("Throughput %.2f ops/s must exceed threshold 5000.0 ops/s", throughput));
    }

    @Test
    @DisplayName("JVM Heap memory stability profiling under high serialization volume")
    void testMemoryStabilityAndHeapRetention() throws InterruptedException {
        // Collect pre-test heap state
        System.gc();
        Thread.sleep(100);
        long initialUsedHeap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        LocalDateTime checkIn = LocalDateTime.of(2026, 10, 14, 15, 0, 0);
        BigDecimal price = new BigDecimal("2500000.00");
        ComplexRoomBooking payload = new ComplexRoomBooking(303, "SUITE-303", price, checkIn, "occupied");

        // Execute batch serialization
        final int batchSize = 50_000;
        int dummyLength = 0;
        for (int i = 0; i < batchSize; i++) {
            String json = JsonUtil.toJson(payload);
            dummyLength += json.length();
        }
        assertTrue(dummyLength > 0);

        // Collect post-test heap state after GC
        System.gc();
        Thread.sleep(150);
        long postGcUsedHeap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        long heapDeltaBytes = postGcUsedHeap - initialUsedHeap;
        double heapDeltaMb = heapDeltaBytes / (1024.0 * 1024.0);

        logger.info(String.format("TASK-A4.2 Heap Profiling: Initial = %.2fMB, Post-GC = %.2fMB, Delta = %.2fMB",
                initialUsedHeap / (1024.0 * 1024.0),
                postGcUsedHeap / (1024.0 * 1024.0),
                heapDeltaMb));

        // Assert no persistent memory leak (retained delta should be well under 10MB for 50k ephemeral objects)
        assertTrue(heapDeltaMb < 15.0,
                String.format("Retained heap delta (%.2f MB) indicates potential memory leakage (>15MB)", heapDeltaMb));
    }
}
