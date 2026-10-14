package quanlykhachsan.backend.booking;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import quanlykhachsan.backend.system.SystemStateHolder;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.GlobalExceptionHandler;
import quanlykhachsan.backend.utils.HttpExchangeHelper;
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.utils.SystemStatusFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Store-and-Forward POS Integration Sync Controller (TASK-A3.2).
 * <p>
 * Implements {@link HttpHandler} for handling {@code POST /api/v1/pos/sync}:
 * <ul>
 *   <li>Receives batches of offline POS charges stored during freeze window (00:00 - 00:05).</li>
 *   <li>Enforces transaction idempotency to eliminate duplicate billing.</li>
 *   <li>Atomically accounts for charges against the NEW active business date (post-audit).</li>
 *   <li>Preserves original offline transaction timestamps for audit integrity.</li>
 * </ul>
 */
public class PosSyncController implements HttpHandler {

    private final PosSyncDao posSyncDao = PosSyncDao.getInstance();
    private final Gson gson = JsonUtil.getGson();

    private static final PosSyncController INSTANCE = new PosSyncController();

    public static PosSyncController getInstance() {
        return INSTANCE;
    }

    /**
     * DTO representing the result of processing a POS sync batch.
     */
    public static class SyncResult {
        public final boolean success;
        public final int statusCode;
        public final String responseJson;

        public SyncResult(boolean success, int statusCode, String responseJson) {
            this.success = success;
            this.statusCode = statusCode;
            this.responseJson = responseJson;
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"POST".equalsIgnoreCase(method)) {
            ApiResponseUtil.sendError(exchange, 405, "ERR_405_METHOD_NOT_ALLOWED", "Method Not Allowed. Expected POST.");
            return;
        }
        handleSync(exchange, Map.of());
    }

    /**
     * Handles batch sync request from POS terminals.
     */
    public void handleSync(HttpExchange exchange, Map<String, String> params) throws IOException {
        try {
            // 1. Guardrail: If system is currently frozen, reject with 503
            if (SystemStateHolder.isSystemFrozen()) {
                SystemStatusFilter.sendServiceUnavailable(exchange);
                return;
            }

            // 2. Read request body and headers
            String body = HttpExchangeHelper.readRequestBody(exchange);
            if (body == null || body.isBlank()) {
                ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Request body cannot be empty");
                return;
            }

            JsonObject payload;
            try {
                payload = JsonParser.parseString(body).getAsJsonObject();
            } catch (Exception e) {
                ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Malformed JSON payload: " + e.getMessage());
                return;
            }

            String headerIdempotencyKey = HttpExchangeHelper.getHeader(exchange, "Idempotency-Key");
            SyncResult result = processSync(payload, headerIdempotencyKey);

            if (!result.success) {
                byte[] bytes = result.responseJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                if (result.statusCode == 503) {
                    exchange.getResponseHeaders().set("Retry-After", "300");
                }
                exchange.sendResponseHeaders(result.statusCode, bytes.length);
                try (var os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }

            // Deliver standard SUCCESS envelope according to Dev A standard
            JsonObject respObj = JsonParser.parseString(result.responseJson).getAsJsonObject();
            JsonObject dataObj = respObj.getAsJsonObject("data");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("batch_id", dataObj.get("batch_id").getAsString());
            data.put("applied_business_date", dataObj.get("applied_business_date").getAsString());
            data.put("business_date", dataObj.get("business_date").getAsString());
            data.put("synced_count", dataObj.get("synced_count").getAsInt());
            data.put("duplicate_skipped", dataObj.get("duplicate_skipped").getAsInt());

            Map<String, Object> meta = Map.of(
                    "timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );
            ApiResponseUtil.sendSuccess(exchange, 200, data, meta);

        } catch (Exception e) {
            GlobalExceptionHandler.handle(exchange, e);
        }
    }

    /**
     * Core business processing for POS batch sync (compatible with MockApiServer and Production).
     *
     * @param payload JsonObject containing batch/transactions
     * @param headerIdempotencyKey optional Idempotency-Key header value
     * @return SyncResult containing HTTP status code, success flag, and JSON response
     */
    public SyncResult processSync(JsonObject payload, String headerIdempotencyKey) {
        if (SystemStateHolder.isSystemFrozen()) {
            JsonObject err = new JsonObject();
            err.addProperty("status", "ERROR");
            err.addProperty("error_code", "ERR_503_SERVICE_UNAVAILABLE");
            JsonObject errDetail = new JsonObject();
            errDetail.addProperty("code", "ERR_503_SYSTEM_FROZEN");
            errDetail.addProperty("message", "The system is currently in the Night Audit closing window (00:00 - 00:05). All write operations are temporarily locked. Read-only mode is active.");
            err.add("error", errDetail);
            JsonObject meta = new JsonObject();
            meta.addProperty("retry_after_seconds", 300);
            err.add("meta", meta);
            return new SyncResult(false, 503, gson.toJson(err));
        }

        if (payload == null) {
            JsonObject err = new JsonObject();
            err.addProperty("status", "ERROR");
            err.addProperty("error_code", "ERR_400_BAD_REQUEST");
            err.addProperty("message", "Request body cannot be empty");
            return new SyncResult(false, 400, gson.toJson(err));
        }

        String batchId = payload.has("batch_id")
                ? payload.get("batch_id").getAsString()
                : (payload.has("batchId") ? payload.get("batchId").getAsString() : "BATCH-" + System.currentTimeMillis());

        JsonArray itemsArray = new JsonArray();
        if (payload.has("items") && payload.get("items").isJsonArray()) {
            itemsArray = payload.getAsJsonArray("items");
        } else if (payload.has("transactions") && payload.get("transactions").isJsonArray()) {
            itemsArray = payload.getAsJsonArray("transactions");
        } else if (payload.has("charges") && payload.get("charges").isJsonArray()) {
            itemsArray = payload.getAsJsonArray("charges");
        } else {
            itemsArray.add(payload);
        }

        LocalDate appliedBusinessDate = SystemStateHolder.getCurrentBusinessDate();
        int syncedCount = 0;
        int duplicateSkipped = 0;

        Connection conn = null;
        try {
            if (DatabaseConnection.isDatabaseAvailable()) {
                try {
                    conn = DatabaseConnection.getConnection();
                    posSyncDao.ensureTableExists(conn);
                } catch (Exception ignored) {
                    conn = null;
                }
            }

            for (JsonElement el : itemsArray) {
                if (!el.isJsonObject()) continue;
                JsonObject item = el.getAsJsonObject();

                String itemKey = item.has("idempotency_key")
                        ? item.get("idempotency_key").getAsString()
                        : (item.has("idempotencyKey")
                        ? item.get("idempotencyKey").getAsString()
                        : headerIdempotencyKey);

                int bookingId = 1;
                if (item.has("booking_id")) {
                    bookingId = item.get("booking_id").getAsInt();
                } else if (item.has("bookingId")) {
                    bookingId = item.get("bookingId").getAsInt();
                } else if (item.has("folio_id")) {
                    String fStr = item.get("folio_id").getAsString();
                    String digits = fStr.replaceAll("\\D+", "");
                    if (!digits.isEmpty()) {
                        try { bookingId = Integer.parseInt(digits); } catch (Exception ignored) {}
                    }
                } else if (item.has("folioId")) {
                    String fStr = item.get("folioId").getAsString();
                    String digits = fStr.replaceAll("\\D+", "");
                    if (!digits.isEmpty()) {
                        try { bookingId = Integer.parseInt(digits); } catch (Exception ignored) {}
                    }
                }

                String chargeType = item.has("charge_type")
                        ? item.get("charge_type").getAsString()
                        : (item.has("category") ? item.get("category").getAsString() : "ROOM_SERVICE");

                double amount = item.has("amount") ? item.get("amount").getAsDouble() : 0.0;
                String desc = item.has("description") ? item.get("description").getAsString() : "Offline POS Charge";

                String offlineCreatedAt = item.has("offline_created_at")
                        ? item.get("offline_created_at").getAsString()
                        : (item.has("offline_timestamp")
                        ? item.get("offline_timestamp").getAsString()
                        : (item.has("offlineTimestamp") ? item.get("offlineTimestamp").getAsString() : java.time.Instant.now().toString()));

                Map<String, Object> syncRes = posSyncDao.syncOfflineCharge(
                        conn,
                        itemKey,
                        batchId,
                        bookingId,
                        chargeType,
                        amount,
                        desc,
                        offlineCreatedAt
                );

                boolean isDup = Boolean.TRUE.equals(syncRes.get("duplicate"));
                if (isDup) {
                    duplicateSkipped++;
                } else {
                    syncedCount++;
                }
            }
        } catch (Exception e) {
            JsonObject err = new JsonObject();
            err.addProperty("status", "ERROR");
            err.addProperty("error_code", "ERR_500_INTERNAL_ERROR");
            err.addProperty("message", e.getMessage());
            return new SyncResult(false, 500, gson.toJson(err));
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }

        JsonObject data = new JsonObject();
        data.addProperty("batch_id", batchId);
        data.addProperty("applied_business_date", appliedBusinessDate.toString());
        data.addProperty("business_date", appliedBusinessDate.toString());
        data.addProperty("synced_count", syncedCount);
        data.addProperty("duplicate_skipped", duplicateSkipped);

        JsonObject resp = new JsonObject();
        resp.addProperty("status", "success");
        resp.add("data", data);

        JsonObject meta = new JsonObject();
        meta.addProperty("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        resp.add("meta", meta);

        return new SyncResult(true, 200, gson.toJson(resp));
    }
}
