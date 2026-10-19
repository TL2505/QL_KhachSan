package quanlykhachsan.frontend.worker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import quanlykhachsan.frontend.utils.HttpUtil;
import quanlykhachsan.frontend.utils.ServerConfig;
import quanlykhachsan.frontend.utils.SessionManagerUtil;

/**
 * Background polling worker for PMS staff to receive real-time room service order alerts.
 * Polling interval: 15 seconds.
 * Zero-compromise constraints:
 * 1. Background polling via ScheduledExecutorService.
 * 2. UI thread safety ensured with SwingUtilities.invokeLater().
 * 3. Fails silently (logs warning) during network outages - never triggers error pop-ups.
 * 4. Automatically terminates when form is closed.
 */
public class ServiceOrderAlertWorker {

    @FunctionalInterface
    public interface OrderAlertListener {
        void onOrdersUpdated(int pendingCount, List<JsonObject> pendingOrders);
    }

    private final ScheduledExecutorService scheduler;
    private final OrderAlertListener listener;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final java.util.Set<Long> notifiedOrderIds = Collections.synchronizedSet(new java.util.HashSet<>());
    private int lastPendingCount = 0;

    public ServiceOrderAlertWorker(OrderAlertListener listener) {
        this.listener = listener;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ServiceOrderAlertWorker-Thread");
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized void start() {
        if (isRunning.compareAndSet(false, true)) {
            // Initial poll after 1s, then every 15s
            scheduler.scheduleWithFixedDelay(this::pollOrders, 1, 15, TimeUnit.SECONDS);
        }
    }

    public void pollOrders() {
        if (!isRunning.get() || Thread.currentThread().isInterrupted()) {
            return;
        }

        try {
            String token = SessionManagerUtil.getAccessToken();
            if (token == null || token.isBlank()) {
                return;
            }

            // Call GET /api/v1/service-orders/active
            String jsonStr = HttpUtil.sendGet(ServerConfig.getBaseUrl() + "/api/v1/service-orders/active");
            if (jsonStr == null || jsonStr.isBlank()) {
                return;
            }

            JsonObject resp = JsonParser.parseString(jsonStr).getAsJsonObject();

            // Edge case 1 fix: Prevent accidental cache deletion during network/server instability
            if (resp.has("success") && !resp.get("success").getAsBoolean()) {
                return; // Server error envelope (e.g. temporary DB outage), keep notifiedOrderIds intact!
            }
            if (resp.has("status") && "error".equalsIgnoreCase(resp.get("status").getAsString())) {
                return; // Server error status, keep notifiedOrderIds intact!
            }
            if (!resp.has("data") || !resp.get("data").isJsonArray()) {
                return; // Malformed payload, keep notifiedOrderIds intact!
            }

            JsonArray dataArr = resp.getAsJsonArray("data");

            List<JsonObject> pendingList = new ArrayList<>();
            for (JsonElement el : dataArr) {
                if (el != null && el.isJsonObject()) {
                    JsonObject order = el.getAsJsonObject();
                    String status = order.has("order_status") && !order.get("order_status").isJsonNull()
                            ? order.get("order_status").getAsString() : "";
                    if ("PENDING".equalsIgnoreCase(status)) {
                        pendingList.add(order);
                    }
                }
            }

            final int currentPendingCount = pendingList.size();

            // Operational fix: Only beep when a brand-new order ID (one that hasn't triggered an alert yet) is detected
            java.util.Set<Long> currentPendingIds = new java.util.HashSet<>();
            boolean hasBrandNewOrder = false;
            for (JsonObject order : pendingList) {
                long orderId = order.has("id") && !order.get("id").isJsonNull() ? order.get("id").getAsLong() : 0L;
                if (orderId > 0) {
                    currentPendingIds.add(orderId);
                    if (!notifiedOrderIds.contains(orderId)) {
                        notifiedOrderIds.add(orderId);
                        hasBrandNewOrder = true;
                    }
                }
            }
            // Prune orders that have been accepted or completed so the memory footprint is bounded
            // Only invoked when HTTP response is verified to be 200 OK and valid JSON
            notifiedOrderIds.retainAll(currentPendingIds);

            if (hasBrandNewOrder) {
                try {
                    Toolkit.getDefaultToolkit().beep();
                } catch (Exception ignored) {}
            }
            lastPendingCount = currentPendingCount;

            // Thread-safe UI update
            if (listener != null) {
                final List<JsonObject> immutableList = Collections.unmodifiableList(pendingList);
                SwingUtilities.invokeLater(() -> {
                    try {
                        listener.onOrdersUpdated(currentPendingCount, immutableList);
                    } catch (Exception ex) {
                        System.err.println("[ServiceOrderAlertWorker] Listener callback warning: " + ex.getMessage());
                    }
                });
            }

        } catch (Exception ex) {
            // Must fail silently (or log warning) during network outages - do not trigger error pop-ups
            System.err.println("[ServiceOrderAlertWorker] Polling silent warning: " + ex.getMessage());
        }
    }

    /**
     * Edge case 2 fix: Triggers an immediate background poll off-EDT,
     * avoiding up to 15s latency on MainUI badge after order acceptance.
     */
    public void triggerImmediateCheck() {
        if (isRunning.get() && !scheduler.isShutdown()) {
            scheduler.execute(this::pollOrders);
        }
    }

    public synchronized void stop() {
        if (isRunning.compareAndSet(true, false)) {
            try {
                scheduler.shutdownNow();
            } catch (Exception ignored) {}
        }
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public int getLastPendingCount() {
        return lastPendingCount;
    }
}
