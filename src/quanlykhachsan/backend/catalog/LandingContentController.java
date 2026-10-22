package quanlykhachsan.backend.catalog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.SecurityUtil;

/** CMS content with a distinct draft/review/published lifecycle for legal and brand content. */
public final class LandingContentController implements HttpHandler {
    private static final String PUBLIC_PATH = "/api/v1/public/landing-content";
    private static final String ADMIN_PATH = "/api/v1/admin/catalog/content";
    private static final Map<String, Slot> SLOTS = Map.of(
            "home_hero", new Slot("brand", Map.of("headline", 120, "subtitle", 500, "cta_label", 60, "cta_url", 255, "image_url", 255)),
            "home_about", new Slot("brand", Map.of("headline", 120, "description", 1600)),
            "home_featured", new Slot("dynamic", Map.of("headline", 120, "description", 1000, "cta_label", 60, "cta_url", 255, "image_url", 255)),
            "home_legal_notice", new Slot("legal", Map.of("notice", 1200))
    );

    @Override public void handle(HttpExchange e) throws IOException {
        e.getResponseHeaders().set("Cache-Control", "no-store");
        String path = e.getRequestURI().getPath();
        try {
            if ("GET".equalsIgnoreCase(e.getRequestMethod()) && path.equals(PUBLIC_PATH)) {
                ApiResponseUtil.sendSuccess(e, 200, published()); return;
            }
            if (!SecurityUtil.hasPermission(e, 1, 2)) return;
            if ("GET".equalsIgnoreCase(e.getRequestMethod()) && path.equals(ADMIN_PATH)) {
                ApiResponseUtil.sendSuccess(e, 200, admin()); return;
            }
            String prefix = ADMIN_PATH + "/";
            if (path.startsWith(prefix)) {
                String rest = path.substring(prefix.length());
                String[] parts = rest.split("/");
                String key = parts[0]; Slot slot = SLOTS.get(key);
                if (slot == null) throw new IllegalArgumentException("Không tìm thấy mục nội dung.");
                if (parts.length == 1 && "PUT".equalsIgnoreCase(e.getRequestMethod())) {
                    saveDraft(e, key, slot, body(e)); return;
                }
                if (parts.length == 2 && "submit".equals(parts[1]) && "POST".equalsIgnoreCase(e.getRequestMethod())) {
                    submit(e, key); return;
                }
                if (parts.length == 2 && "approve".equals(parts[1]) && "POST".equalsIgnoreCase(e.getRequestMethod())) {
                    review(e, key, true, body(e)); return;
                }
                if (parts.length == 2 && "reject".equals(parts[1]) && "POST".equalsIgnoreCase(e.getRequestMethod())) {
                    review(e, key, false, body(e)); return;
                }
            }
            ApiResponseUtil.sendError(e, 404, "ERR_404_NOT_FOUND", "Không tìm thấy mục nội dung.");
        } catch (IllegalArgumentException | JsonSyntaxException ex) {
            ApiResponseUtil.sendError(e, 400, "LANDING_CONTENT_INVALID", ex.getMessage());
        } catch (SQLException ex) {
            ApiResponseUtil.sendError(e, 503, "LANDING_CONTENT_UNAVAILABLE", "Không thể lưu nội dung landing page lúc này.");
        } catch (Exception ex) {
            ApiResponseUtil.sendError(e, 503, "LANDING_CONTENT_UNAVAILABLE", "Không thể tải nội dung landing page lúc này.");
        }
    }

    private List<Map<String,Object>> admin() throws SQLException {
        Map<String,Map<String,Object>> rows = new LinkedHashMap<>();
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement q = c.prepareStatement(
                "SELECT content_key,classification,draft_json,published_json,workflow_status,submitted_by,submitted_at,approved_by,approved_at,review_note,updated_at FROM landing_content"); ResultSet rs = q.executeQuery()) {
            while (rs.next()) {
                Map<String,Object> row = new LinkedHashMap<>();
                String key = rs.getString("content_key"); row.put("key", key); row.put("classification", rs.getString("classification"));
                row.put("status", rs.getString("workflow_status")); row.put("draft", parseNullable(rs.getString("draft_json")));
                row.put("published", parseNullable(rs.getString("published_json"))); row.put("submitted_by", rs.getObject("submitted_by"));
                row.put("submitted_at", rs.getTimestamp("submitted_at")); row.put("approved_by", rs.getObject("approved_by"));
                row.put("approved_at", rs.getTimestamp("approved_at")); row.put("review_note", rs.getString("review_note"));
                row.put("updated_at", rs.getTimestamp("updated_at")); rows.put(key, row);
            }
        }
        List<Map<String,Object>> result = new ArrayList<>();
        for (var entry : SLOTS.entrySet()) {
            Map<String,Object> fallback = new LinkedHashMap<>();
            fallback.put("key", entry.getKey()); fallback.put("classification", entry.getValue().classification);
            fallback.put("status", "draft"); fallback.put("draft", null); fallback.put("published", null);
            result.add(rows.getOrDefault(entry.getKey(), fallback));
        }
        return result;
    }

    private List<Map<String,Object>> published() throws SQLException {
        List<Map<String,Object>> result = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement q = c.prepareStatement(
                "SELECT content_key,classification,published_json FROM landing_content WHERE published_json IS NOT NULL"); ResultSet rs = q.executeQuery()) {
            while (rs.next()) {
                String key = rs.getString("content_key");
                if (!SLOTS.containsKey(key)) continue;
                Map<String,Object> row = new LinkedHashMap<>(); row.put("key", key); row.put("classification", rs.getString("classification"));
                row.put("content", parseNullable(rs.getString("published_json"))); result.add(row);
            }
        }
        return result;
    }

    private void saveDraft(HttpExchange e, String key, Slot slot, JsonObject request) throws SQLException, IOException {
        JsonObject content = new JsonObject();
        for (var field : slot.fields.entrySet()) {
            String value = text(request, field.getKey(), field.getValue());
            content.addProperty(field.getKey(), value);
        }
        if (("home_hero".equals(key) || "home_about".equals(key)) && content.get("headline").getAsString().isBlank())
            throw new IllegalArgumentException("Cần nhập tiêu đề trước khi lưu nội dung thương hiệu.");
        validateUrls(content);
        int userId = userId(e);
        String classification = slot.classification;
        if ("dynamic".equals(classification)) {
            String sql = "INSERT INTO landing_content(content_key,classification,draft_json,published_json,workflow_status,updated_by) VALUES(?,?,?,?,'published',?) "
                    + "ON DUPLICATE KEY UPDATE draft_json=VALUES(draft_json),published_json=VALUES(published_json),workflow_status='published',submitted_by=NULL,submitted_at=NULL,approved_by=NULL,approved_at=NULL,review_note=NULL,updated_by=VALUES(updated_by)";
            upsert(sql, key, classification, content.toString(), content.toString(), userId);
        } else {
            String sql = "INSERT INTO landing_content(content_key,classification,draft_json,workflow_status,updated_by) VALUES(?,?,?,'draft',?) "
                    + "ON DUPLICATE KEY UPDATE draft_json=VALUES(draft_json),workflow_status='draft',submitted_by=NULL,submitted_at=NULL,approved_by=NULL,approved_at=NULL,review_note=NULL,updated_by=VALUES(updated_by)";
            upsert(sql, key, classification, content.toString(), null, userId);
        }
        ApiResponseUtil.sendSuccess(e, 200, Map.of("key", key, "status", "dynamic".equals(classification) ? "published" : "draft"));
    }

    private void upsert(String sql, String key, String classification, String draft, String published, int userId) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement q = c.prepareStatement(sql)) {
            q.setString(1, key); q.setString(2, classification); q.setString(3, draft);
            if (published != null) q.setString(4, published); q.setInt(published != null ? 5 : 4, userId); q.executeUpdate();
        }
    }

    private void submit(HttpExchange e, String key) throws SQLException, IOException {
        int user = userId(e);
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement q = c.prepareStatement(
                "UPDATE landing_content SET workflow_status='pending_review',submitted_by=?,submitted_at=CURRENT_TIMESTAMP,approved_by=NULL,approved_at=NULL,review_note=NULL,updated_by=? WHERE content_key=? AND classification IN ('brand','legal') AND draft_json IS NOT NULL")) {
            q.setInt(1, user); q.setInt(2, user); q.setString(3, key);
            if (q.executeUpdate() == 0) throw new IllegalArgumentException("Lưu bản nháp trước khi gửi duyệt.");
        }
        ApiResponseUtil.sendSuccess(e, 200, Map.of("key", key, "status", "pending_review"));
    }

    private void review(HttpExchange e, String key, boolean approve, JsonObject request) throws SQLException, IOException {
        if (!SecurityUtil.hasPermission(e, 1)) return;
        int reviewer = userId(e);
        String note = request.has("review_note") && !request.get("review_note").isJsonNull()
                ? text(request, "review_note", 1000) : "";
        if (!approve && note.isBlank()) throw new IllegalArgumentException("Cần ghi lý do từ chối để người biên tập cập nhật nội dung.");
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement q = c.prepareStatement(approve
                ? "UPDATE landing_content SET published_json=draft_json,workflow_status='published',approved_by=?,approved_at=CURRENT_TIMESTAMP,review_note=?,updated_by=? WHERE content_key=? AND workflow_status='pending_review' AND submitted_by<>?"
                : "UPDATE landing_content SET workflow_status='rejected',approved_by=?,approved_at=CURRENT_TIMESTAMP,review_note=?,updated_by=? WHERE content_key=? AND workflow_status='pending_review' AND submitted_by<>?")) {
            q.setInt(1, reviewer); q.setString(2, note); q.setInt(3, reviewer); q.setString(4, key); q.setInt(5, reviewer);
            if (q.executeUpdate() == 0) throw new IllegalArgumentException("Chỉ quản trị viên khác người gửi mới có thể duyệt bản đang chờ.");
        }
        ApiResponseUtil.sendSuccess(e, 200, Map.of("key", key, "status", approve ? "published" : "rejected"));
    }

    private JsonObject body(HttpExchange e) throws IOException {
        byte[] bytes = e.getRequestBody().readNBytes(20_001);
        if (bytes.length > 20_000) throw new IllegalArgumentException("Nội dung gửi quá dài.");
        try { return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Dữ liệu gửi không hợp lệ."); }
    }

    private static Object parseNullable(String value) { if (value == null || value.isBlank()) return null; return JsonParser.parseString(value); }
    private static int userId(HttpExchange e) { Object value = e.getAttribute("userId"); if (value instanceof Number n && n.intValue() > 0) return n.intValue(); throw new IllegalArgumentException("Phiên đăng nhập không hợp lệ."); }
    private static String text(JsonObject b, String key, int max) {
        JsonElement element = b.get(key); if (element == null || element.isJsonNull()) return "";
        if (!element.isJsonPrimitive()) throw new IllegalArgumentException("Thông tin " + key + " không hợp lệ.");
        String value = element.getAsString().trim();
        if (value.length() > max || value.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) throw new IllegalArgumentException("Thông tin " + key + " không hợp lệ.");
        return value;
    }
    private static void validateUrls(JsonObject content) {
        String image = content.has("image_url") ? content.get("image_url").getAsString() : "";
        if (!image.isBlank() && !(image.startsWith("/api/v1/media/") || image.startsWith("https://")))
            throw new IllegalArgumentException("Ảnh chỉ được dùng URL HTTPS hoặc ảnh đã tải lên hệ thống.");
        String link = content.has("cta_url") ? content.get("cta_url").getAsString() : "";
        boolean internalPath = link.startsWith("/") && !link.startsWith("//") && !link.contains("\\");
        if (!link.isBlank() && !(link.startsWith("#") || internalPath || link.startsWith("https://")))
            throw new IllegalArgumentException("Liên kết nút phải là đường dẫn nội bộ hoặc HTTPS.");
        if (image.startsWith("//") || link.startsWith("//") || image.startsWith("javascript:") || link.startsWith("javascript:")
                || image.startsWith("data:") || link.startsWith("data:")) throw new IllegalArgumentException("Đường dẫn nội dung không hợp lệ.");
    }
    private record Slot(String classification, Map<String,Integer> fields) { }
}
