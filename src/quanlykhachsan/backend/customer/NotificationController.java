package quanlykhachsan.backend.customer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import quanlykhachsan.backend.hotelservice.ServiceOrderController;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.JsonUtil;

/**
 * Controller handling customer lifecycle notifications and in-app alerts.
 */
public class NotificationController implements HttpHandler {

    private static final Pattern NOTIFICATION_PATH_PATTERN = Pattern.compile(".*/notifications/(\\d+)(?:/read)?/?$");

    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final Gson gson = JsonUtil.getGson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            if ("GET".equalsIgnoreCase(method)) {
                handleGetNotifications(exchange);
            } else if ("PUT".equalsIgnoreCase(method)) {
                handleMarkRead(exchange, path);
            } else if ("OPTIONS".equalsIgnoreCase(method)) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, PUT, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            } else {
                ApiResponseUtil.sendError(exchange, 405, "ERR_405_METHOD_NOT_ALLOWED", "Method not allowed");
            }
        } catch (Exception e) {
            e.printStackTrace();
            ApiResponseUtil.sendError(exchange, 500, "ERR_500_INTERNAL", "Lỗi xử lý thông báo: " + e.getMessage());
        }
    }

    private void handleGetNotifications(HttpExchange exchange) throws Exception {
        int customerId = ServiceOrderController.resolveCustomerId(exchange);
        if (customerId <= 0) {
            ApiResponseUtil.sendError(exchange, 401, "ERR_401_UNAUTHORIZED", "Vui lòng đăng nhập để xem thông báo");
            return;
        }

        String query = exchange.getRequestURI().getQuery();
        boolean unreadOnly = false;
        int limit = 20;

        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("unread_only=")) {
                    unreadOnly = Boolean.parseBoolean(param.substring("unread_only=".length()));
                } else if (param.startsWith("limit=")) {
                    try {
                        limit = Integer.parseInt(param.substring("limit=".length()));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        List<Notification> list = notificationDAO.getNotificationsByCustomer(customerId, unreadOnly, limit);
        int unreadCount = notificationDAO.getUnreadCount(customerId);

        JsonObject envelope = new JsonObject();
        envelope.addProperty("status", "success");
        envelope.addProperty("unread_count", unreadCount);
        envelope.add("data", gson.toJsonTree(list));

        ApiResponseUtil.write(exchange, 200, gson.toJson(envelope));
    }

    private void handleMarkRead(HttpExchange exchange, String path) throws Exception {
        int customerId = ServiceOrderController.resolveCustomerId(exchange);
        if (customerId <= 0) {
            ApiResponseUtil.sendError(exchange, 401, "ERR_401_UNAUTHORIZED", "Vui lòng đăng nhập");
            return;
        }

        long notificationId = 0;
        if (path != null) {
            Matcher m = NOTIFICATION_PATH_PATTERN.matcher(path);
            if (m.matches()) {
                try {
                    notificationId = Long.parseLong(m.group(1));
                } catch (NumberFormatException ignored) {}
            }
        }

        if (notificationId == 0) {
            try {
                JsonObject req = gson.fromJson(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8), JsonObject.class);
                if (req != null && req.has("id") && !req.get("id").isJsonNull()) {
                    notificationId = req.get("id").getAsLong();
                }
            } catch (Exception ignored) {}
        }

        if (notificationId <= 0) {
            ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Mã thông báo không hợp lệ");
            return;
        }

        boolean ok = notificationDAO.markAsRead(notificationId, customerId);
        if (ok) {
            JsonObject envelope = new JsonObject();
            envelope.addProperty("status", "success");
            envelope.addProperty("message", "Đã đánh dấu thông báo là đã đọc");
            JsonObject data = new JsonObject();
            data.addProperty("id", notificationId);
            data.addProperty("is_read", true);
            data.addProperty("read_at", System.currentTimeMillis());
            envelope.add("data", data);
            ApiResponseUtil.write(exchange, 200, gson.toJson(envelope));
        } else {
            ApiResponseUtil.sendError(exchange, 404, "ERR_404_NOT_FOUND", "Không tìm thấy thông báo hoặc bạn không có quyền sửa đổi");
        }
    }
}
