package quanlykhachsan.backend.pricing;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Admin-only editor for the explicitly configured hourly and overnight rates. */
public final class RentalPolicyAdminController implements HttpHandler {
    private static final String PATH = "/api/v1/admin/rental-policy";

    @Override public void handle(HttpExchange e) throws IOException {
        e.getResponseHeaders().set("Cache-Control", "no-store");
        if (e.getAttribute("userId") == null || !"admin".equalsIgnoreCase(String.valueOf(e.getAttribute("role")))) {
            ApiResponseUtil.sendError(e, 403, "ERR_403_FORBIDDEN", "Chỉ quản trị viên được cấu hình giá lưu trú.");
            return;
        }
        String path = e.getRequestURI().getPath();
        try {
            if (path.equals(PATH) && e.getRequestMethod().equals("GET")) {
                List<JsonObject> rows = new ArrayList<>();
                try (Connection c = DatabaseConnection.getConnection(); PreparedStatement q = c.prepareStatement(
                        "SELECT id,name,base_price,hourly_base_price,hourly_extra_hour_price,overnight_price FROM room_types ORDER BY name,id")) {
                    try (ResultSet r = q.executeQuery()) { while (r.next()) {
                        JsonObject row = new JsonObject(); row.addProperty("id", r.getInt("id"));
                        row.addProperty("name", r.getString("name")); row.addProperty("daily_base_price", r.getBigDecimal("base_price"));
                        addNullable(row, "hourly_base_price", r.getBigDecimal("hourly_base_price"));
                        addNullable(row, "hourly_extra_hour_price", r.getBigDecimal("hourly_extra_hour_price"));
                        addNullable(row, "overnight_price", r.getBigDecimal("overnight_price")); rows.add(row);
                    }}
                }
                ApiResponseUtil.sendSuccess(e, rows); return;
            }
            if (e.getRequestMethod().equals("PUT") && path.matches(PATH + "/[0-9]+")) {
                int id = Integer.parseInt(path.substring(PATH.length() + 1));
                byte[] bytes = e.getRequestBody().readNBytes(4097);
                if (bytes.length > 4096) { ApiResponseUtil.sendError(e, 413, "ERR_413_PAYLOAD_TOO_LARGE", "Yêu cầu quá dài."); return; }
                JsonObject body = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                BigDecimal base = rate(body, "hourly_base_price"), extra = rate(body, "hourly_extra_hour_price"), overnight = rate(body, "overnight_price");
                try (Connection c = DatabaseConnection.getConnection(); PreparedStatement q = c.prepareStatement(
                        "UPDATE room_types SET hourly_base_price=?,hourly_extra_hour_price=?,overnight_price=? WHERE id=?")) {
                    q.setBigDecimal(1, base); q.setBigDecimal(2, extra); q.setBigDecimal(3, overnight); q.setInt(4, id);
                    if (q.executeUpdate() != 1) { ApiResponseUtil.sendError(e, 404, "ERR_404_NOT_FOUND", "Không tìm thấy hạng phòng."); return; }
                }
                ApiResponseUtil.sendSuccess(e, java.util.Map.of("room_type_id", id, "saved", true)); return;
            }
            ApiResponseUtil.sendError(e, 404, "ERR_404_NOT_FOUND", "Route not found");
        } catch (IllegalArgumentException ex) {
            ApiResponseUtil.sendError(e, 400, "ERR_400_BAD_REQUEST", "Giá phải lớn hơn 0 và có tối đa 2 chữ số thập phân.");
        } catch (Exception ex) {
            ApiResponseUtil.sendError(e, 503, "ERR_503_UNAVAILABLE", "Chưa thể tải hoặc lưu cấu hình giá.");
        }
    }

    private static BigDecimal rate(JsonObject body, String key) {
        if (!body.has(key) || body.get(key).isJsonNull()) throw new IllegalArgumentException();
        BigDecimal value = body.get(key).getAsBigDecimal();
        if (value.signum() <= 0 || value.scale() > 2 || value.compareTo(new BigDecimal("99999999.99")) > 0) throw new IllegalArgumentException();
        return value.setScale(2);
    }

    private static void addNullable(JsonObject target, String key, BigDecimal value) {
        if (value == null) target.add(key, com.google.gson.JsonNull.INSTANCE); else target.addProperty(key, value);
    }
}
