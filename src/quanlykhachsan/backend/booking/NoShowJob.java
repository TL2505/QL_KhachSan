package quanlykhachsan.backend.booking;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Daily, bounded and restart-safe scheduler for overdue confirmed bookings. */
public final class NoShowJob implements AutoCloseable {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "hotel-no-show-job"); thread.setDaemon(true); return thread;
    });

    public void start() {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        LocalDateTime next = now.toLocalDate().atTime(LocalTime.of(2, 0));
        if (!next.isAfter(now)) next = next.plusDays(1);
        long initialDelay = Duration.between(now, next).toMillis();
        executor.scheduleAtFixedRate(this::runBatch, initialDelay, TimeUnit.DAYS.toMillis(1), TimeUnit.MILLISECONDS);
    }

    /** One transaction processes at most 50 bookings; repeat until the final partial batch. */
    public int runBatch() {
        int total = 0;
        try {
            NoShowEngine engine = new NoShowEngine();
            int count;
            do {
                count = engine.processBatch(LocalDateTime.now(BUSINESS_ZONE));
                total += count;
            } while (count == 50);
        } catch (Exception ex) {
            System.err.println("No-show processing failed: " + ex.getClass().getSimpleName());
        }
        return total;
    }

    @Override public void close() { executor.shutdownNow(); }
}
