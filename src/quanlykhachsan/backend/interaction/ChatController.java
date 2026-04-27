package quanlykhachsan.backend.interaction;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.utils.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import quanlykhachsan.backend.interaction.Message;
import quanlykhachsan.backend.interaction.ChatService;
import quanlykhachsan.backend.utils.SecurityUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class ChatController implements HttpHandler {
    private ChatService chatService = new ChatService();
    private Gson gson = JsonUtil.getGson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

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
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 500, "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}");
        }
    }

    private void handleSendMessage(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        Message msg = gson.fromJson(body, Message.class);

        chatService.sendMessage(msg);
        sendJson(exchange, 200, "{\"status\": \"success\", \"message\": \"Zent!\"}");
    }

    private void handleGetHistory(HttpExchange exchange) throws IOException {
        Map<String, String> params = getQueryParams(exchange.getRequestURI().getQuery());
        int u1 = Integer.parseInt(params.get("u1"));
        int u2 = Integer.parseInt(params.get("u2"));

        List<Message> history = chatService.getConversation(u1, u2);
        sendSuccess(exchange, history);
    }

    private void handleGetInbox(HttpExchange exchange) throws IOException {
        Map<String, String> params = getQueryParams(exchange.getRequestURI().getQuery());
        int staffId = Integer.parseInt(params.get("staffId"));

        List<Message> inboxes = chatService.getInboxes(staffId);
        sendSuccess(exchange, inboxes);
    }

    private void handleMarkRead(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        JsonObject obj = gson.fromJson(body, JsonObject.class);
        int readerId = obj.get("readerId").getAsInt();
        
        chatService.markAsRead(0, readerId); // 0 as placeholder for conversationId
        sendJson(exchange, 200, "{\"status\": \"success\"}");
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
        JsonObject res = new JsonObject();
        res.addProperty("status", "success");
        res.add("data", gson.toJsonTree(data));
        sendJson(exchange, 200, res.toString());
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
