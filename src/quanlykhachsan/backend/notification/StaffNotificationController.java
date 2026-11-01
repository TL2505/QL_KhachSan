package quanlykhachsan.backend.notification;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import quanlykhachsan.backend.payment.PaymentStatus;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.JsonUtil;

/** Staff/admin API for operational notifications and per-user read state. */
public final class StaffNotificationController implements HttpHandler {
    private static final Pattern READ_PATH = Pattern.compile(".*/(\\d+)/read/?$");
    private static final Set<String> STAFF_ROLES = Set.of("1", "2", "ADMIN", "STAFF", "FRONT_DESK",
            "HOUSEKEEPING", "MAINTENANCE", "F&B", "FB", "MANAGER", "GM", "REVENUE_MANAGER");

    @Override public void handle(HttpExchange exchange) throws IOException {
        Object rawRole = exchange.getAttribute("role");
        String role = rawRole == null ? "" : rawRole.toString().trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (!STAFF_ROLES.contains(role)) {
            ApiResponseUtil.sendError(exchange, 403, "ERR_403_FORBIDDEN", "Thông báo chỉ dành cho tài khoản nhân viên.");
            return;
        }
        int userId = userId(exchange);
        if (userId <= 0) {
            ApiResponseUtil.sendError(exchange, 401, "ERR_401_UNAUTHORIZED", "Staff account is required");
            return;
        }
        try {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) get(exchange, userId);
            else if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) markRead(exchange, userId);
            else ApiResponseUtil.sendError(exchange, 405, "ERR_405_METHOD_NOT_ALLOWED", "Method not allowed");
        } catch (Exception ex) {
            ApiResponseUtil.sendError(exchange, 503, "ERR_503_UNAVAILABLE", "Chưa tải được thông báo nhân viên; vui lòng thử lại.");
        }
    }

    private static void get(HttpExchange e, int userId) throws Exception {
        int limit = 20; boolean unreadOnly = false; String query = e.getRequestURI().getQuery();
        if (query != null) for (String part : query.split("&")) {
            if (part.startsWith("limit=")) try { limit = Integer.parseInt(part.substring(6)); } catch (NumberFormatException ignored) { }
            if (part.startsWith("unread_only=")) unreadOnly = Boolean.parseBoolean(part.substring(12));
        }
        limit = Math.max(1, Math.min(100, limit));
        try (Connection c = DatabaseConnection.getConnection()) {
            var data = new com.google.gson.JsonArray();
            String sql = "SELECT n.id,n.booking_id,n.title,n.message,n.action_url,n.created_at,"
                    + "NOT EXISTS(SELECT 1 FROM staff_notification_reads r WHERE r.notification_id=n.id AND r.user_id=?) AS is_read "
                    + "FROM staff_notifications n " + (unreadOnly ? "WHERE NOT EXISTS(SELECT 1 FROM staff_notification_reads r WHERE r.notification_id=n.id AND r.user_id=?) " : "")
                    + "ORDER BY n.created_at DESC,n.id DESC LIMIT ?";
            try (PreparedStatement q = c.prepareStatement(sql)) {
                q.setInt(1, userId); int p = 2;
                if (unreadOnly) q.setInt(p++, userId);
                q.setInt(p, limit);
                try (ResultSet r = q.executeQuery()) { data = PaymentStatus.rows(r); }
            }
            int unread;
            try (PreparedStatement q = c.prepareStatement("SELECT COUNT(*) FROM staff_notifications n WHERE NOT EXISTS(SELECT 1 FROM staff_notification_reads r WHERE r.notification_id=n.id AND r.user_id=?)")) {
                q.setInt(1, userId); try (ResultSet r = q.executeQuery()) { r.next(); unread = r.getInt(1); }
            }
            JsonObject result = new JsonObject(); result.addProperty("status", "success"); result.addProperty("unread_count", unread); result.add("data", data);
            ApiResponseUtil.write(e, 200, JsonUtil.getGson().toJson(result));
        }
    }

    private static void markRead(HttpExchange e, int userId) throws Exception {
        Matcher matcher = READ_PATH.matcher(e.getRequestURI().getPath());
        if (!matcher.matches()) { ApiResponseUtil.sendError(e, 400, "ERR_400_BAD_REQUEST", "Notification ID is invalid"); return; }
        long id;
        try { id = Long.parseLong(matcher.group(1)); } catch (NumberFormatException ex) { id = 0; }
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement q = c.prepareStatement(
                "INSERT INTO staff_notification_reads(notification_id,user_id) SELECT id,? FROM staff_notifications WHERE id=? "
              + "ON DUPLICATE KEY UPDATE read_at=CURRENT_TIMESTAMP")) {
            q.setInt(1, userId); q.setLong(2, id);
            if (q.executeUpdate() == 0) { ApiResponseUtil.sendError(e, 404, "ERR_404_NOT_FOUND", "Notification not found"); return; }
            ApiResponseUtil.write(e, 200, "{\"status\":\"success\",\"data\":{\"id\":" + id + ",\"is_read\":true}}");
        }
    }

    private static int userId(HttpExchange e) {
        Object value = e.getAttribute("userId");
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(value)); } catch (RuntimeException ex) { return 0; }
    }
}
