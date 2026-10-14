package quanlykhachsan.backend.utils;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Standardized JSON Envelope Utility (RFC-7807) & Response Pipeline (TASK-A1.3).
 * <p>
 * Provides standard JSON envelopes for API responses:
 * <ul>
 *   <li>Success: {@code {"status": "SUCCESS", "data": {...}, "meta": {"timestamp": "...", ...}}}</li>
 *   <li>Error: {@code {"error_code": "ERR_...", "message": "...", "timestamp": "..."}}</li>
 * </ul>
 */
public class ApiResponseUtil {

    private static final Gson gson = JsonUtil.getGson();

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Sends a standardized Success response envelope:
     * <pre>
     * {
     *   "status": "SUCCESS",
     *   "data": { ... },
     *   "meta": { "timestamp": "...", ... }
     * }
     * </pre>
     */
    public static void sendSuccess(HttpExchange exchange, int statusCode, Object data, Object meta) throws IOException {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("status", "SUCCESS");
        envelope.put("data", data);

        Map<String, Object> metaMap = new LinkedHashMap<>();
        String now = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        metaMap.put("timestamp", now);

        if (meta instanceof Map<?, ?> customMeta) {
            for (Map.Entry<?, ?> entry : customMeta.entrySet()) {
                if (entry.getKey() != null) {
                    metaMap.put(entry.getKey().toString(), entry.getValue());
                }
            }
        } else if (meta != null) {
            metaMap.put("extra", meta);
        }
        envelope.put("meta", metaMap);

        sendJsonResponse(exchange, statusCode, gson.toJson(envelope));
    }

    /**
     * Convenience overload for sendSuccess with default meta containing timestamp.
     */
    public static void sendSuccess(HttpExchange exchange, int statusCode, Object data) throws IOException {
        sendSuccess(exchange, statusCode, data, null);
    }

    /**
     * Convenience overload for sendSuccess with 200 OK and default meta.
     */
    public static void sendSuccess(HttpExchange exchange, Object data) throws IOException {
        sendSuccess(exchange, 200, data, null);
    }

    /**
     * Sends a standardized Error response envelope (RFC-7807 standard):
     * <pre>
     * {
     *   "error_code": "ERR_...",
     *   "message": "...",
     *   "timestamp": "2026-10-14 14:00:00"
     * }
     * </pre>
     */
    public static void sendError(HttpExchange exchange, int statusCode, String errorCode, String message) throws IOException {
        Map<String, Object> errorEnvelope = new LinkedHashMap<>();
        errorEnvelope.put("error_code", errorCode != null ? errorCode : "ERR_" + statusCode);
        errorEnvelope.put("message", message != null ? message : "An error occurred");
        String now = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        errorEnvelope.put("timestamp", now);

        sendJsonResponse(exchange, statusCode, gson.toJson(errorEnvelope));
    }

    /**
     * Internal helper to write JSON byte payload with standard headers.
     */
    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        if (exchange.getResponseCode() != -1) {
            return; // Headers already sent
        }

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
            os.flush();
        }
    }

    // =========================================================================
    // Backward-Compatibility Helpers for Existing Controllers
    // =========================================================================

    public static <T> String success(String message, T data) {
        return gson.toJson(new ApiResponse<>("success", message, data));
    }

    public static String success(String message) {
        return gson.toJson(new ApiResponse<>("success", message));
    }

    public static <T> String successWithData(T data) {
        return gson.toJson(new ApiResponse<>("success", null, data));
    }

    public static String error(String message) {
        return gson.toJson(new ApiResponse<>("error", message, null));
    }

    public static void write(HttpExchange exchange, int statusCode, String json) throws IOException {
        sendJsonResponse(exchange, statusCode, json);
    }

    public static Gson getGson() {
        return gson;
    }
}
