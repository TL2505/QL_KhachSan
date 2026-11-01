package quanlykhachsan.backend.catalog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

/** Staff-editable room type catalogue. Hidden types remain available to the PMS but not the landing page. */
public final class RoomCatalogController implements HttpHandler {
    private static final String PUBLIC_PATH = "/api/v1/public/room-types";
    private static final String ADMIN_PATH = "/api/v1/admin/catalog/room-types";

    @Override public void handle(HttpExchange e) throws IOException {
        e.getResponseHeaders().set("Cache-Control", "no-store");
        String path = e.getRequestURI().getPath();
        try {
            if ("GET".equalsIgnoreCase(e.getRequestMethod()) && path.equals(PUBLIC_PATH)) {
                ApiResponseUtil.sendSuccess(e, 200, list(false)); return;
            }
            if (!SecurityUtil.hasPermission(e, 1, 2)) return;
            if ("GET".equalsIgnoreCase(e.getRequestMethod()) && path.equals(ADMIN_PATH)) {
                ApiResponseUtil.sendSuccess(e, 200, list(true)); return;
            }
            if ("POST".equalsIgnoreCase(e.getRequestMethod()) && path.equals(ADMIN_PATH)) {
                ApiResponseUtil.sendSuccess(e, 201, create(readBody(e))); return;
            }
            if (path.matches("/api/v1/admin/catalog/room-types/[0-9]+") && "PUT".equalsIgnoreCase(e.getRequestMethod())) {
                int id = idAtEnd(path);
                update(id, readBody(e)); ApiResponseUtil.sendSuccess(e, 200, find(id)); return;
            }
            if (path.matches("/api/v1/admin/catalog/room-types/[0-9]+/images") && "POST".equalsIgnoreCase(e.getRequestMethod())) {
                long imageId = attachImage(Integer.parseInt(path.split("/")[6]), readBody(e));
                ApiResponseUtil.sendSuccess(e, 201, Map.of("id", imageId)); return;
            }
            if (path.matches("/api/v1/admin/catalog/room-types/[0-9]+/images/[0-9]+") && "PUT".equalsIgnoreCase(e.getRequestMethod())) {
                String[] parts = path.split("/");
                updateImage(Integer.parseInt(parts[6]), Long.parseLong(parts[8]), readBody(e));
                ApiResponseUtil.sendSuccess(e, 200, find(Integer.parseInt(parts[6]))); return;
            }
            ApiResponseUtil.sendError(e, 404, "ERR_404_NOT_FOUND", "Không tìm thấy cấu hình hạng phòng.");
        } catch (IllegalArgumentException ex) {
            ApiResponseUtil.sendError(e, 400, "ROOM_CATALOG_INVALID", ex.getMessage());
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1062) ApiResponseUtil.sendError(e, 409, "ROOM_TYPE_DUPLICATE", "Tên hạng phòng đã tồn tại.");
            else ApiResponseUtil.sendError(e, 503, "ROOM_CATALOG_UNAVAILABLE", "Không thể lưu cấu hình hạng phòng lúc này.");
        } catch (Exception ex) {
            ApiResponseUtil.sendError(e, 503, "ROOM_CATALOG_UNAVAILABLE", "Không thể tải cấu hình hạng phòng lúc này.");
        }
    }

    private List<Map<String,Object>> list(boolean includeHidden) throws SQLException {
        String sql = "SELECT id,name,description,base_price,capacity,image_url,image_alt,amenities,display_order,is_visible "
                + "FROM room_types " + (includeHidden ? "" : "WHERE is_visible=1 ") + "ORDER BY display_order,id";
        List<Map<String,Object>> result = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection()) {
            try (PreparedStatement q = c.prepareStatement(sql); ResultSet rs = q.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    Map<String,Object> row = new LinkedHashMap<>();
                    row.put("id", id); row.put("name", rs.getString("name")); row.put("description", rs.getString("description"));
                    row.put("base_price", rs.getBigDecimal("base_price")); row.put("capacity", rs.getInt("capacity"));
                    row.put("image_url", rs.getString("image_url")); row.put("image_alt", rs.getString("image_alt"));
                    row.put("amenities", rs.getString("amenities")); row.put("display_order", rs.getInt("display_order"));
                    row.put("is_visible", rs.getBoolean("is_visible"));
                    result.add(row);
                }
            }
            // Close the room_types result set before querying image metadata on the same connection.
            for (Map<String,Object> row : result) row.put("images", images(c, ((Number) row.get("id")).intValue(), includeHidden));
        }
        return result;
    }

    private Map<String,Object> find(int id) throws SQLException {
        List<Map<String,Object>> rows = list(true);
        return rows.stream().filter(row -> ((Number) row.get("id")).intValue() == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hạng phòng."));
    }

    private List<Map<String,Object>> images(Connection c, int roomTypeId, boolean includeHidden) throws SQLException {
        String sql = "SELECT i.id,i.asset_id,COALESCE(m.public_url,i.external_url) image_url,i.alt_text,i.display_order,"
                + "i.is_primary,i.is_visible,m.mime_type,m.size_bytes,m.width,m.height,m.original_name "
                + "FROM room_type_images i LEFT JOIN media_assets m ON m.id=i.asset_id WHERE i.room_type_id=? "
                + (includeHidden ? "" : "AND i.is_visible=1 ") + "ORDER BY i.display_order,i.id";
        List<Map<String,Object>> result = new ArrayList<>();
        try (PreparedStatement q = c.prepareStatement(sql)) {
            q.setInt(1, roomTypeId);
            try (ResultSet rs = q.executeQuery()) {
                while (rs.next()) {
                    Map<String,Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getLong("id"));
                    item.put("url", rs.getString("image_url")); item.put("alt_text", rs.getString("alt_text"));
                    item.put("display_order", rs.getInt("display_order")); item.put("is_primary", rs.getBoolean("is_primary"));
                    if (includeHidden) {
                        item.put("asset_id", rs.getObject("asset_id")); item.put("is_visible", rs.getBoolean("is_visible"));
                        item.put("mime_type", rs.getString("mime_type")); item.put("size_bytes", rs.getObject("size_bytes"));
                        item.put("width", rs.getObject("width")); item.put("height", rs.getObject("height"));
                        item.put("original_name", rs.getString("original_name"));
                    }
                    result.add(item);
                }
            }
        }
        return result;
    }

    private Map<String,Object> create(JsonObject b) throws SQLException {
        String sql = "INSERT INTO room_types(name,description,base_price,capacity,amenities,display_order,is_visible) VALUES(?,?,?,?,?,?,?)";
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement q = c.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            bindType(q, b, 1);
            q.executeUpdate();
            try (ResultSet rs = q.getGeneratedKeys()) { if (!rs.next()) throw new SQLException("Room type id missing"); return find(rs.getInt(1)); }
        }
    }

    private void update(int id, JsonObject b) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                int primaryId = b.has("primary_image_id") && !b.get("primary_image_id").isJsonNull() ? integer(b, "primary_image_id", 1) : -1;
                try (PreparedStatement q = c.prepareStatement("UPDATE room_types SET name=?,description=?,base_price=?,capacity=?,amenities=?,display_order=?,is_visible=? WHERE id=?")) {
                    bindType(q, b, 1); q.setInt(8, id);
                    if (q.executeUpdate() == 0) throw new IllegalArgumentException("Không tìm thấy hạng phòng.");
                }
                if (b.has("primary_image_id")) setPrimary(c, id, primaryId);
                c.commit();
            } catch (Exception ex) { c.rollback(); if (ex instanceof SQLException sql) throw sql; if (ex instanceof IllegalArgumentException arg) throw arg; throw new SQLException(ex); }
            finally { c.setAutoCommit(true); }
        }
    }

    private void bindType(PreparedStatement q, JsonObject b, int start) throws SQLException {
        String name = text(b, "name", 100, true), description = text(b, "description", 4000, false);
        String amenities = text(b, "amenities", 500, false);
        java.math.BigDecimal price;
        try { price = new java.math.BigDecimal(requiredPrimitive(b, "base_price")).setScale(2, java.math.RoundingMode.UNNECESSARY); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Giá tham chiếu phải lớn hơn 0 và tối đa 2 chữ số thập phân."); }
        int capacity = integer(b, "capacity", 1), order = integer(b, "display_order", 0);
        if (price.signum() <= 0 || price.compareTo(new java.math.BigDecimal("99999999.99")) > 0) throw new IllegalArgumentException("Giá tham chiếu phải lớn hơn 0.");
        q.setString(start, name); q.setString(start + 1, description); q.setBigDecimal(start + 2, price);
        q.setInt(start + 3, capacity); q.setString(start + 4, amenities); q.setInt(start + 5, order);
        q.setBoolean(start + 6, bool(b, "is_visible", true));
    }

    private long attachImage(int roomTypeId, JsonObject b) throws SQLException {
        long assetId = longValue(b, "asset_id", 1);
        String alt = text(b, "alt_text", 255, false);
        int order = integer(b, "display_order", 0);
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                ensureType(c, roomTypeId);
                try (PreparedStatement q = c.prepareStatement("INSERT INTO room_type_images(room_type_id,asset_id,alt_text,display_order,is_primary,is_visible) VALUES(?,?,?,?,0,1)", PreparedStatement.RETURN_GENERATED_KEYS)) {
                    q.setInt(1, roomTypeId); q.setLong(2, assetId); q.setString(3, alt); q.setInt(4, order); q.executeUpdate();
                    long id; try (ResultSet rs = q.getGeneratedKeys()) { if (!rs.next()) throw new SQLException("Image id missing"); id = rs.getLong(1); }
                    try (PreparedStatement q2 = c.prepareStatement("SELECT COUNT(*) FROM room_type_images WHERE room_type_id=? AND is_primary=1 AND is_visible=1")) {
                        q2.setInt(1, roomTypeId); try (ResultSet rs = q2.executeQuery()) { if (rs.next() && rs.getInt(1) == 0) setPrimary(c, roomTypeId, id); }
                    }
                    c.commit(); return id;
                }
            } catch (Exception ex) { c.rollback(); if (ex instanceof SQLException sql) throw sql; if (ex instanceof IllegalArgumentException arg) throw arg; throw new SQLException(ex); }
            finally { c.setAutoCommit(true); }
        }
    }

    private void updateImage(int roomTypeId, long imageId, JsonObject b) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                String alt = text(b, "alt_text", 255, false);
                int order = integer(b, "display_order", 0);
                boolean visible = bool(b, "is_visible", true), primary = bool(b, "is_primary", false);
                boolean replaceAsset = b.has("asset_id") && !b.get("asset_id").isJsonNull();
                long assetId = replaceAsset ? longValue(b, "asset_id", 1) : 0;
                String updateSql = replaceAsset
                        ? "UPDATE room_type_images SET asset_id=?,external_url=NULL,alt_text=?,display_order=?,is_visible=? WHERE id=? AND room_type_id=?"
                        : "UPDATE room_type_images SET alt_text=?,display_order=?,is_visible=? WHERE id=? AND room_type_id=?";
                try (PreparedStatement q = c.prepareStatement(updateSql)) {
                    int parameter = 1;
                    if (replaceAsset) q.setLong(parameter++, assetId);
                    q.setString(parameter++, alt); q.setInt(parameter++, order); q.setBoolean(parameter++, visible);
                    q.setLong(parameter++, imageId); q.setInt(parameter, roomTypeId);
                    if (q.executeUpdate() == 0) throw new IllegalArgumentException("Không tìm thấy ảnh của hạng phòng.");
                }
                if (primary && visible) setPrimary(c, roomTypeId, imageId);
                else if (!visible) {
                    boolean wasPrimary = false;
                    try (PreparedStatement q = c.prepareStatement("SELECT is_primary FROM room_type_images WHERE id=?")) {
                        q.setLong(1, imageId);
                        try (ResultSet rs = q.executeQuery()) { wasPrimary = rs.next() && rs.getBoolean(1); }
                    }
                    if (wasPrimary) setPrimary(c, roomTypeId, firstVisibleImageId(c, roomTypeId, imageId));
                }
                c.commit();
            } catch (Exception ex) { c.rollback(); if (ex instanceof SQLException sql) throw sql; if (ex instanceof IllegalArgumentException arg) throw arg; throw new SQLException(ex); }
            finally { c.setAutoCommit(true); }
        }
    }

    private long firstVisibleImageId(Connection c, int roomTypeId, long excludedImageId) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT id FROM room_type_images WHERE room_type_id=? AND is_visible=1 AND id<>? ORDER BY display_order,id LIMIT 1")) {
            q.setInt(1, roomTypeId); q.setLong(2, excludedImageId);
            try (ResultSet rs = q.executeQuery()) { return rs.next() ? rs.getLong(1) : -1; }
        }
    }

    private void setPrimary(Connection c, int roomTypeId, long imageId) throws SQLException {
        if (imageId > 0) {
            try (PreparedStatement check = c.prepareStatement("SELECT 1 FROM room_type_images WHERE id=? AND room_type_id=? AND is_visible=1")) {
                check.setLong(1, imageId); check.setInt(2, roomTypeId); try (ResultSet rs = check.executeQuery()) { if (!rs.next()) throw new IllegalArgumentException("Ảnh chính phải đang hiển thị trong hạng phòng này."); }
            }
        }
        try (PreparedStatement q = c.prepareStatement("UPDATE room_type_images SET is_primary=(id=?) WHERE room_type_id=?")) {
            q.setLong(1, imageId); q.setInt(2, roomTypeId); q.executeUpdate();
        }
        String url = null, alt = null;
        if (imageId > 0) try (PreparedStatement q = c.prepareStatement("SELECT COALESCE(m.public_url,i.external_url),i.alt_text FROM room_type_images i LEFT JOIN media_assets m ON m.id=i.asset_id WHERE i.id=? AND i.room_type_id=?")) {
            q.setLong(1, imageId); q.setInt(2, roomTypeId); try (ResultSet rs = q.executeQuery()) { if (rs.next()) { url = rs.getString(1); alt = rs.getString(2); } }
        }
        try (PreparedStatement q = c.prepareStatement("UPDATE room_types SET image_url=?,image_alt=? WHERE id=?")) {
            q.setString(1, url); q.setString(2, alt); q.setInt(3, roomTypeId); q.executeUpdate();
        }
    }

    private void ensureType(Connection c, int id) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT id FROM room_types WHERE id=?")) { q.setInt(1, id); try (ResultSet rs = q.executeQuery()) { if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy hạng phòng."); } }
    }

    private JsonObject readBody(HttpExchange e) throws IOException {
        byte[] bytes = e.getRequestBody().readNBytes(20_001);
        if (bytes.length > 20_000) throw new IllegalArgumentException("Nội dung gửi quá dài.");
        try { return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Dữ liệu gửi không hợp lệ."); }
    }

    private static int idAtEnd(String path) { return Integer.parseInt(path.substring(path.lastIndexOf('/') + 1)); }
    private static String requiredPrimitive(JsonObject b, String key) { if (!b.has(key) || b.get(key).isJsonNull() || !b.get(key).isJsonPrimitive()) throw new IllegalArgumentException("Thiếu thông tin " + key + "."); return b.get(key).getAsString(); }
    private static String text(JsonObject b, String key, int max, boolean required) {
        if (!b.has(key) || b.get(key).isJsonNull()) { if (required) throw new IllegalArgumentException("Thiếu thông tin " + key + "."); return ""; }
        if (!b.get(key).isJsonPrimitive()) throw new IllegalArgumentException("Thông tin " + key + " không hợp lệ.");
        String value = b.get(key).getAsString().trim();
        if ((required && value.isEmpty()) || value.length() > max || value.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) throw new IllegalArgumentException("Thông tin " + key + " không hợp lệ.");
        return value;
    }
    private static int integer(JsonObject b, String key, int min) { try { int n = b.get(key).getAsBigDecimal().intValueExact(); if (n < min) throw new NumberFormatException(); return n; } catch (RuntimeException ex) { throw new IllegalArgumentException("Thông tin " + key + " không hợp lệ."); } }
    private static long longValue(JsonObject b, String key, long min) { try { long n = b.get(key).getAsBigDecimal().longValueExact(); if (n < min) throw new NumberFormatException(); return n; } catch (RuntimeException ex) { throw new IllegalArgumentException("Thông tin " + key + " không hợp lệ."); } }
    private static boolean bool(JsonObject b, String key, boolean fallback) { if (!b.has(key) || b.get(key).isJsonNull()) return fallback; try { return b.get(key).getAsBoolean(); } catch (RuntimeException ex) { throw new IllegalArgumentException("Thông tin " + key + " không hợp lệ."); } }
}
