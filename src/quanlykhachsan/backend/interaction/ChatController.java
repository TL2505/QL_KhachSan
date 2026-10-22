package quanlykhachsan.backend.interaction;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.utils.SecurityUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class ChatController implements HttpHandler {
    private ChatService chatService = new ChatService();
    private Gson gson = JsonUtil.getGson();

    public ChatController() {}
    ChatController(ChatService service) { this.chatService = service; }

    private int userId(HttpExchange exchange) {
        Object id = exchange.getAttribute("userId");
        return id == null ? 0 : Integer.parseInt(id.toString());
    }

    private boolean customer(HttpExchange exchange) {
        String role = String.valueOf(exchange.getAttribute("role"));
        return role.equalsIgnoreCase("customer") || role.equals("3");
    }

    private boolean deny(HttpExchange exchange) throws IOException {
        ApiResponseUtil.write(exchange, 403, ApiResponseUtil.error("Bạn chỉ được truy cập hội thoại hỗ trợ của mình."));
        return false;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if (!SecurityUtil.hasPermission(exchange, 1, 2, 3)) return;
        if (userId(exchange) <= 0) { deny(exchange); return; }

        try {
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/send")) {
                handleSendMessage(exchange);
            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/history")) {
                handleGetHistory(exchange);
            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/inbox")) {
                handleGetInbox(exchange);
            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/read")) {
                handleMarkRead(exchange);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        } catch (com.google.gson.JsonParseException | IllegalArgumentException e) {
            ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Dữ liệu tin nhắn không hợp lệ."));
        } catch (Exception e) {
            e.printStackTrace();
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Không thể xử lý tin nhắn. Vui lòng thử lại."));
        }
    }

    private void handleSendMessage(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        Message msg = gson.fromJson(body, Message.class);

        if (msg == null || msg.getContent() == null || msg.getContent().isBlank() || msg.getContent().length() > 2000)
            throw new IllegalArgumentException("Invalid message");
        if (msg.getSenderId() != userId(exchange)) { deny(exchange); return; }
        if (customer(exchange) ? msg.getReceiverId() != 0 : msg.getReceiverId() <= 0 || msg.getReceiverId() == userId(exchange)) {
            deny(exchange); return;
        }
        msg.setSenderId(userId(exchange));
        msg.setContent(msg.getContent().trim());

        chatService.sendMessage(msg);
        ApiResponseUtil.write(exchange, 200, ApiResponseUtil.success("Đã gửi tin nhắn."));
    }

    private void handleGetHistory(HttpExchange exchange) throws IOException {
        Map<String, String> params = getQueryParams(exchange.getRequestURI().getQuery());
        int u1 = Integer.parseInt(params.get("u1"));
        int u2 = Integer.parseInt(params.get("u2"));

        if (u1 != userId(exchange) || (customer(exchange) ? u2 != 0 : u2 <= 0 || u2 == u1)) { deny(exchange); return; }

        List<Message> history = chatService.getConversation(u1, u2);
        sendSuccess(exchange, history);
    }

    private void handleGetInbox(HttpExchange exchange) throws IOException {
        if (!SecurityUtil.hasPermission(exchange, 1, 2)) return;
        Map<String, String> params = getQueryParams(exchange.getRequestURI().getQuery());
        int staffId = Integer.parseInt(params.get("staffId"));
        if (staffId != userId(exchange)) { deny(exchange); return; }

        List<Message> inboxes = chatService.getInboxes(staffId);
        sendSuccess(exchange, inboxes);
    }

    private void handleMarkRead(HttpExchange exchange) throws IOException {
        if (!SecurityUtil.hasPermission(exchange, 1, 2)) return;
        InputStream is = exchange.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        JsonObject obj = gson.fromJson(body, JsonObject.class);
        int readerId = obj.get("readerId").getAsInt();
        if (readerId != userId(exchange)) { deny(exchange); return; }
        
        chatService.markAsRead(0, readerId); // 0 as placeholder for conversationId
        ApiResponseUtil.write(exchange, 200, ApiResponseUtil.success(null));
    }

    private Map<String, String> getQueryParams(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null) return result;
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) result.put(entry[0], entry[1]);
        }
        return result;
    }

    private void sendSuccess(HttpExchange exchange, Object data) throws IOException {
        ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(data));
    }
}
