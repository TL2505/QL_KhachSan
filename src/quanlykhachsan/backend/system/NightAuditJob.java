package quanlykhachsan.backend.system;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Automatically runs the audit at midnight for the just-closed business date. */
public final class NightAuditJob implements AutoCloseable {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "hotel-night-audit"); t.setDaemon(true); return t;
    });
    public void start() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDateTime next = now.toLocalDate().atTime(LocalTime.MIDNIGHT);
        if (!next.isAfter(now)) next = next.plusDays(1);
        long delay = Duration.between(now, next).toMillis();
        executor.scheduleAtFixedRate(this::runYesterday, delay, TimeUnit.DAYS.toMillis(1), TimeUnit.MILLISECONDS);
    }
    private void runYesterday() {
        try { new NightAuditEngine().run(currentBusinessDate()); }
        catch (Exception ex) { System.err.println("Night audit failed; checkpoint retained: " + ex.getClass().getSimpleName()); }
    }
    static LocalDate currentBusinessDate() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement q = c.prepareStatement("SELECT business_date FROM hotel_business_state WHERE id=1");
             ResultSet r = q.executeQuery()) {
            if (!r.next()) throw new SQLException("Hotel business date is not initialized");
            return r.getDate(1).toLocalDate();
        }
    }
    @Override public void close() { executor.shutdownNow(); }
}
