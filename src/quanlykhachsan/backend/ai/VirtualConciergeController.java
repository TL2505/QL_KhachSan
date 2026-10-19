package quanlykhachsan.backend.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.utils.PortalValidation;

public final class VirtualConciergeController implements HttpHandler {
    private static final long RATE_WINDOW_MILLIS = 60_000L;
    private static final int MAX_TRACKED_CUSTOMERS = 10_000;
    private static final int MAX_REQUESTS_PER_WINDOW = 10;
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile(
            "(?i)[a-z0-9][a-z0-9._:-]{0,63}");
    private static final ConcurrentHashMap<Integer, ArrayDeque<Long>> REQUESTS = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(500))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final PromptGuardrailFilter GUARDRAIL = new PromptGuardrailFilter();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"CUSTOMER".equalsIgnoreCase(String.valueOf(exchange.getAttribute("role")))) {
            ApiResponseUtil.sendError(exchange, 403, "ERR_403_FORBIDDEN", "Customer access required");
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            ApiResponseUtil.sendError(exchange, 405, "ERR_405_METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        if (path.endsWith("/chat")) {
            handleChat(exchange);
        } else if (path.endsWith("/escalate")) {
            handleEscalation(exchange);
        } else {
            ApiResponseUtil.sendError(exchange, 404, "ERR_404_NOT_FOUND", "Not found");
        }
    }

    private static void handleChat(HttpExchange exchange) throws IOException {
        Integer userId = authenticatedUserId(exchange);
        if (userId == null) {
            ApiResponseUtil.sendError(exchange, 401, "ERR_401_UNAUTHORIZED", "Invalid customer session");
            return;
        }
        try {
            JsonObject body = PortalValidation.body(exchange);
            String clientSession = PortalValidation.text(body, "session_id", 64, true);
            String message = PortalValidation.text(body, "message", 2000, true);
            if (!SESSION_ID_PATTERN.matcher(clientSession).matches()) {
                throw new IllegalArgumentException("Invalid session_id");
            }
            if (GUARDRAIL.isUnsafe(message)) {
                System.err.println("[AI concierge] Blocked unsafe prompt for customer userId=" + userId);
                ApiResponseUtil.sendError(exchange, 400, "PROMPT_INJECTION_DETECTED",
                        "Yêu cầu không hợp lệ do vi phạm chính sách bảo mật.");
                return;
            }
            if (!allowRequest(userId)) {
                exchange.getResponseHeaders().set("Retry-After", "60");
                ApiResponseUtil.sendError(exchange, 429, "ERR_429_RATE_LIMITED",
                        "Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau.");
                return;
            }

            JsonObject query = new JsonObject();
            query.addProperty("session_id", "customer-" + userId + "-" + clientSession);
            query.addProperty("message", message);
            HttpRequest request = HttpRequest.newBuilder(resolveRagUri())
                    .timeout(Duration.ofMillis(1800))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.getGson().toJson(query)))
                    .build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                System.err.println("[AI concierge] RAG upstream returned HTTP " + response.statusCode());
                ApiResponseUtil.sendError(exchange, 503, "ERR_503_AI_UNAVAILABLE",
                        "Trợ lý đang tạm thời không khả dụng. Bạn có thể chuyển yêu cầu tới Lễ tân.");
                return;
            }
            String upstreamBody;
            try (InputStream stream = response.body()) {
                byte[] bytes = stream.readNBytes(65_537);
                if (bytes.length > 65_536) {
                    ApiResponseUtil.sendError(exchange, 503, "ERR_503_AI_UNAVAILABLE",
                            "Trợ lý đang tạm thời không khả dụng. Bạn có thể chuyển yêu cầu tới Lễ tân.");
                    return;
                }
                upstreamBody = new String(bytes, StandardCharsets.UTF_8);
            }
            JsonObject data = extractResponseData(upstreamBody);
            if (data == null || !data.has("reply") || !data.get("reply").isJsonPrimitive()
                    || !data.getAsJsonPrimitive("reply").isString()
                    || data.get("reply").getAsString().isBlank()
                    || !data.has("confidence_score") || !data.get("confidence_score").isJsonPrimitive()
                    || !data.getAsJsonPrimitive("confidence_score").isNumber()) {
                System.err.println("[AI concierge] RAG upstream returned an invalid response");
                ApiResponseUtil.sendError(exchange, 503, "ERR_503_AI_UNAVAILABLE",
                        "Trợ lý đang tạm thời không khả dụng. Bạn có thể chuyển yêu cầu tới Lễ tân.");
                return;
            }
            double confidence = data.get("confidence_score").getAsDouble();
            if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
                ApiResponseUtil.sendError(exchange, 503, "ERR_503_AI_UNAVAILABLE",
                        "Trợ lý đang tạm thời không khả dụng. Bạn có thể chuyển yêu cầu tới Lễ tân.");
                return;
            }
            if (confidence < 0.80) {
                int ticketId = createTicket(userId, "customer-" + userId + "-" + clientSession,
                        "Cần Lễ tân xác minh câu hỏi của khách: " + message);
                JsonObject escalation = new JsonObject();
                escalation.addProperty("reply", "Để tôi kiểm tra lại thông tin này với đội ngũ Lễ tân.");
                escalation.addProperty("confidence_score", confidence);
                escalation.addProperty("escalated", true);
                escalation.addProperty("ticket_id", ticketId);
                ApiResponseUtil.sendSuccess(exchange, 200, escalation);
                return;
            }
            ApiResponseUtil.sendSuccess(exchange, 200, sanitizeResponse(data));
        } catch (IllegalArgumentException ex) {
            ApiResponseUtil.sendError(exchange, 400, "ERR_400_INVALID_REQUEST", ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            ApiResponseUtil.sendError(exchange, 503, "ERR_503_AI_UNAVAILABLE",
                    "Trợ lý đang tạm thời không khả dụng. Bạn có thể chuyển yêu cầu tới Lễ tân.");
        } catch (Exception ex) {
            if (isTimeout(ex)) {
                ApiResponseUtil.sendError(exchange, 504, "ERR_504_AI_TIMEOUT",
                        "Trợ lý phản hồi chậm. Bạn có thể chuyển yêu cầu tới Lễ tân.");
            } else {
                System.err.println("[AI concierge] Request failed: " + ex.getClass().getSimpleName());
                ApiResponseUtil.sendError(exchange, 503, "ERR_503_AI_UNAVAILABLE",
                        "Trợ lý đang tạm thời không khả dụng. Bạn có thể chuyển yêu cầu tới Lễ tân.");
            }
        }
    }

    private static void handleEscalation(HttpExchange exchange) throws IOException {
        Integer userId = authenticatedUserId(exchange);
        if (userId == null) {
            ApiResponseUtil.sendError(exchange, 401, "ERR_401_UNAUTHORIZED", "Invalid customer session");
            return;
        }
        try {
            JsonObject body = PortalValidation.body(exchange);
            String sessionId = PortalValidation.text(body, "session_id", 64, true);
            String reason = PortalValidation.text(body, "reason", 2000, true);
            if (!SESSION_ID_PATTERN.matcher(sessionId).matches()) {
                throw new IllegalArgumentException("Invalid session_id");
            }
            if (GUARDRAIL.isUnsafe(reason)) {
                System.err.println("[AI concierge] Blocked unsafe escalation for customer userId=" + userId);
                ApiResponseUtil.sendError(exchange, 400, "PROMPT_INJECTION_DETECTED",
                        "Yêu cầu không hợp lệ do vi phạm chính sách bảo mật.");
                return;
            }
            int ticketId = createTicket(userId, "customer-" + userId + "-" + sessionId, reason);
            JsonObject ticket = new JsonObject();
            ticket.addProperty("ticket_id", ticketId);
            ticket.addProperty("status", "OPEN");
            ticket.addProperty("message", "Yêu cầu đã được ghi nhận cho Lễ tân. Mã tiếp nhận: " + ticketId);
            ApiResponseUtil.sendSuccess(exchange, 200, ticket);
        } catch (IllegalArgumentException ex) {
            ApiResponseUtil.sendError(exchange, 400, "ERR_400_INVALID_REQUEST", ex.getMessage());
        } catch (Exception ex) {
            System.err.println("[AI concierge] Escalation failed: " + ex.getClass().getSimpleName());
            ApiResponseUtil.sendError(exchange, 503, "ERR_503_ESCALATION_UNAVAILABLE",
                    "Chưa thể chuyển yêu cầu. Vui lòng thử lại hoặc liên hệ trực tiếp với Lễ tân.");
        }
    }

    private static Integer authenticatedUserId(HttpExchange exchange) {
        Object value = exchange.getAttribute("userId");
        if (!(value instanceof Number number) || number.intValue() < 1) return null;
        return number.intValue();
    }

    private static synchronized boolean allowRequest(int userId) {
        long now = System.currentTimeMillis();
        REQUESTS.entrySet().removeIf(entry -> {
            ArrayDeque<Long> timestamps = entry.getValue();
            synchronized (timestamps) {
                while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= RATE_WINDOW_MILLIS) {
                    timestamps.removeFirst();
                }
                return timestamps.isEmpty();
            }
        });
        if (!REQUESTS.containsKey(userId) && REQUESTS.size() >= MAX_TRACKED_CUSTOMERS) return false;
        ArrayDeque<Long> timestamps = REQUESTS.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= RATE_WINDOW_MILLIS) {
            timestamps.removeFirst();
        }
        if (timestamps.size() >= MAX_REQUESTS_PER_WINDOW) return false;
        timestamps.addLast(now);
        return true;
    }

    private static int createTicket(int userId, String sessionId, String reason) throws Exception {
        try (Connection connection = DatabaseConnection.getConnection()) {
            Integer customerId = null;
            try (PreparedStatement lookup = connection.prepareStatement(
                    "SELECT customer_id FROM users WHERE id=?")) {
                lookup.setInt(1, userId);
                try (ResultSet result = lookup.executeQuery()) {
                    if (result.next()) {
                        int value = result.getInt(1);
                        if (!result.wasNull()) customerId = value;
                    }
                }
            }
            if (customerId == null) throw new IllegalStateException("Customer profile is not linked");
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO ai_escalation_tickets (customer_id, session_id, reason) VALUES (?,?,?)",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                insert.setInt(1, customerId);
                insert.setString(2, sessionId);
                insert.setString(3, reason);
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (!keys.next()) throw new IllegalStateException("Escalation ticket ID was not generated");
                    return keys.getInt(1);
                }
            }
        }
    }

    private static JsonObject extractResponseData(String json) {
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) return null;
        JsonObject object = parsed.getAsJsonObject();
        if (object.has("data") && object.get("data").isJsonObject()) return object.getAsJsonObject("data");
        return object;
    }

    private static JsonObject sanitizeResponse(JsonObject upstream) {
        JsonObject result = new JsonObject();
        result.add("reply", upstream.get("reply"));
        result.add("citations", JsonUtil.getGson().toJsonTree(cleanObjects(upstream.get("citations"), "doc", "section")));
        result.add("suggested_actions", JsonUtil.getGson().toJsonTree(cleanStrings(upstream.get("suggested_actions"))));
        if (upstream.has("confidence_score") && upstream.get("confidence_score").isJsonPrimitive()
                && upstream.getAsJsonPrimitive("confidence_score").isNumber()) {
            double confidence = upstream.get("confidence_score").getAsDouble();
            if (Double.isFinite(confidence) && confidence >= 0 && confidence <= 1) {
                result.addProperty("confidence_score", confidence);
            }
        }
        return result;
    }

    private static List<JsonObject> cleanObjects(JsonElement value, String... fields) {
        List<JsonObject> result = new ArrayList<>();
        if (value == null || !value.isJsonArray()) return result;
        for (JsonElement item : value.getAsJsonArray()) {
            if (!item.isJsonObject()) continue;
            JsonObject clean = new JsonObject();
            for (String field : fields) {
                JsonElement fieldValue = item.getAsJsonObject().get(field);
                if (fieldValue != null && fieldValue.isJsonPrimitive()
                        && fieldValue.getAsJsonPrimitive().isString()) {
                    String text = fieldValue.getAsString().trim();
                    if (!text.isEmpty() && text.length() <= 160) clean.addProperty(field, text);
                }
            }
            if (!clean.isEmpty()) result.add(clean);
        }
        return result;
    }

    private static List<String> cleanStrings(JsonElement value) {
        List<String> result = new ArrayList<>();
        if (value == null || !value.isJsonArray()) return result;
        for (JsonElement item : value.getAsJsonArray()) {
            if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                String text = item.getAsString().trim();
                if (!text.isEmpty() && text.length() <= 120 && result.size() < 5) result.add(text);
            }
        }
        return result;
    }

    private static boolean isTimeout(Exception ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException || cause instanceof HttpTimeoutException) return true;
        }
        return false;
    }

    private static URI resolveRagUri() {
        String configured = System.getProperty("hotel.ai.rag.url");
        if (configured == null || configured.isBlank()) configured = System.getenv("AI_RAG_URL");
        if (configured == null || configured.isBlank()) configured = "http://127.0.0.1:8082/rag/query";
        try {
            URI uri = URI.create(configured);
            String host = uri.getHost();
            if (!"http".equalsIgnoreCase(uri.getScheme()) || host == null
                    || !(host.equals("127.0.0.1") || host.equalsIgnoreCase("localhost") || host.equals("::1"))
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("AI_RAG_URL must target the local AI service over HTTP");
            }
            return uri;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Invalid AI RAG endpoint configuration");
        }
    }
}
