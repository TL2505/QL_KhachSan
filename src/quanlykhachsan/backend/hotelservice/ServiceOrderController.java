package quanlykhachsan.backend.hotelservice;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.utils.SecurityUtil;

/**
 * Controller handling in-stay service orders (F&B, Spa, Housekeeping).
 * Enforces In-House status validation and IDOR protection.
 */
public class ServiceOrderController implements HttpHandler {

    private static final Pattern ORDER_PATH_PATTERN = Pattern.compile(".*/service-orders/(\\d+)(?:/status)?/?$");

    private final ServiceOrderDAO serviceOrderDAO = new ServiceOrderDAO();
    private final Gson gson = JsonUtil.getGson();

    private static final Set<String> STAFF_ROLES = Set.of(
        "admin", "staff", "front_desk", "housekeeping", "f&b", "fb", "manager", "gm",
        "ADMIN", "STAFF", "1", "2"
    );

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            if ("POST".equalsIgnoreCase(method)) {
                handleCreateOrder(exchange);
            } else if ("GET".equalsIgnoreCase(method)) {
                if (path.endsWith("/active")) {
                    handleGetActiveOrders(exchange);
                } else {
                    handleGetOrders(exchange);
                }
            } else if ("PUT".equalsIgnoreCase(method)) {
                handleUpdateStatus(exchange, path);
            } else if ("OPTIONS".equalsIgnoreCase(method)) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            } else {
                ApiResponseUtil.sendError(exchange, 405, "ERR_405_METHOD_NOT_ALLOWED", "Method not allowed");
            }
        } catch (IllegalArgumentException e) {
            ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", e.getMessage());
        } catch (IllegalStateException e) {
            ApiResponseUtil.sendError(exchange, 403, "ERR_403_FORBIDDEN", e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            ApiResponseUtil.sendError(exchange, 500, "ERR_500_INTERNAL", "Lỗi xử lý đơn dịch vụ: " + e.getMessage());
        }
    }

    private void handleCreateOrder(HttpExchange exchange) throws Exception {
        JsonObject req = gson.fromJson(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8), JsonObject.class);
        if (req == null || !req.has("booking_id")) {
            ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Thiếu thông tin bắt buộc: booking_id");
            return;
        }

        int bookingId = req.get("booking_id").getAsInt();
        String category = req.has("category") ? req.get("category").getAsString().trim().toUpperCase() : "FNB";
        String itemName = req.has("item_name") ? req.get("item_name").getAsString().trim() : "";
        int quantity = req.has("quantity") ? req.get("quantity").getAsInt() : 1;
        Integer serviceId = req.has("service_id") && !req.get("service_id").isJsonNull() ? req.get("service_id").getAsInt() : null;
        String voucherCode = req.has("voucher_code") && !req.get("voucher_code").isJsonNull() ? req.get("voucher_code").getAsString().trim() : null;
        String specialInstructions = req.has("special_instructions") && !req.get("special_instructions").isJsonNull() ? req.get("special_instructions").getAsString().trim() : null;

        if (itemName.isEmpty() && (serviceId == null || serviceId <= 0)) {
            ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Vui lòng nhập tên món/dịch vụ hoặc chọn service_id hợp lệ");
            return;
        }

        // Check IDOR and In-House status
        int jwtCustomerId = resolveCustomerId(exchange);
        String role = String.valueOf(exchange.getAttribute("role"));
        boolean isStaffOrAdmin = STAFF_ROLES.contains(role != null ? role.trim() : "");

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement q = c.prepareStatement("SELECT customer_id, status FROM bookings WHERE id = ?")) {
            q.setInt(1, bookingId);
            try (ResultSet r = q.executeQuery()) {
                if (!r.next()) {
                    ApiResponseUtil.sendError(exchange, 404, "ERR_404_NOT_FOUND", "Không tìm thấy mã đặt phòng #" + bookingId);
                    return;
                }
                int bookingOwnerId = r.getInt(1);
                String bookingStatus = r.getString(2);

                // IDOR Validation: Customer can only order for their own booking
                if (!isStaffOrAdmin && (jwtCustomerId <= 0 || bookingOwnerId != jwtCustomerId)) {
                    ApiResponseUtil.sendError(exchange, 403, "ERR_403_FORBIDDEN",
                            "Từ chối quyền truy cập: Bạn không có quyền đặt dịch vụ cho phòng này (IDOR Violation).");
                    return;
                }

                // In-House Validation: Only checked_in status is allowed
                if (!"checked_in".equalsIgnoreCase(bookingStatus)) {
                    ApiResponseUtil.sendError(exchange, 403, "ERR_403_NOT_CHECKED_IN",
                            "Dịch vụ phòng trực tuyến chỉ áp dụng cho Quý khách đang lưu trú tại khách sạn (Status: In-House checked_in).");
                    return;
                }
            }
        }

        ServiceOrder order = new ServiceOrder();
        order.setBookingId(bookingId);
        order.setCategory(category);
        order.setServiceId(serviceId);
        order.setItemName(itemName);
        order.setQuantity(quantity);
        order.setVoucherCode(voucherCode);
        order.setSpecialInstructions(specialInstructions);

        if ((serviceId == null || serviceId <= 0) && req.has("unit_price") && !req.get("unit_price").isJsonNull()) {
            order.setUnitPrice(req.get("unit_price").getAsBigDecimal());
        }

        if (req.has("scheduled_time") && !req.get("scheduled_time").isJsonNull()) {
            try {
                String stStr = req.get("scheduled_time").getAsString().trim();
                stStr = stStr.replace("Z", "").replace("T", " ");
                if (stStr.length() > 19) {
                    stStr = stStr.substring(0, 19);
                }
                if (stStr.length() == 16) {
                    stStr += ":00";
                }
                order.setScheduledTime(Timestamp.valueOf(stStr));
            } catch (Exception ignored) {}
        }

        // Execute atomic creation & folio debit
        ServiceOrder created = serviceOrderDAO.createOrderWithFolioDebit(order);

        JsonObject data = new JsonObject();
        data.addProperty("order_id", created.getId());
        data.addProperty("booking_id", created.getBookingId());
        data.addProperty("room_number", created.getRoomNumber());
        data.addProperty("category", created.getCategory());
        data.addProperty("item_name", created.getItemName());
        data.addProperty("quantity", created.getQuantity());
        data.addProperty("unit_price", created.getUnitPrice());
        data.addProperty("subtotal", created.getSubtotal());
        data.addProperty("discount_amount", created.getDiscountAmount());
        data.addProperty("final_price", created.getFinalPrice());
        data.addProperty("order_status", created.getOrderStatus());
        data.addProperty("folio_id", created.getFolioId());
        data.addProperty("folio_transaction_id", created.getFolioTransactionId());
        data.addProperty("created_at", System.currentTimeMillis());

        JsonObject envelope = new JsonObject();
        envelope.addProperty("status", "success");
        envelope.addProperty("message", "Yêu cầu dịch vụ phòng đã được ghi nhận và hạch toán vào hóa đơn phòng!");
        envelope.add("data", data);

        ApiResponseUtil.write(exchange, 201, gson.toJson(envelope));
    }

    private void handleGetActiveOrders(HttpExchange exchange) throws Exception {
        String role = String.valueOf(exchange.getAttribute("role"));
        if (!STAFF_ROLES.contains(role != null ? role.trim() : "")) {
            ApiResponseUtil.sendError(exchange, 403, "ERR_403_FORBIDDEN", "Chức năng chỉ dành cho nhân viên khách sạn");
            return;
        }

        List<ServiceOrder> activeOrders = serviceOrderDAO.getActiveOrders();
        JsonObject envelope = new JsonObject();
        envelope.addProperty("status", "success");
        envelope.addProperty("pending_count", activeOrders.size());
        envelope.add("data", gson.toJsonTree(activeOrders));

        ApiResponseUtil.write(exchange, 200, gson.toJson(envelope));
    }

    private void handleGetOrders(HttpExchange exchange) throws Exception {
        String query = exchange.getRequestURI().getQuery();
        int bookingId = 0;
        if (query != null && query.contains("booking_id=")) {
            for (String param : query.split("&")) {
                if (param.startsWith("booking_id=")) {
                    bookingId = Integer.parseInt(param.substring("booking_id=".length()));
                }
            }
        }

        if (bookingId > 0) {
            int jwtCustomerId = resolveCustomerId(exchange);
            String role = String.valueOf(exchange.getAttribute("role"));
            boolean isStaffOrAdmin = STAFF_ROLES.contains(role != null ? role.trim() : "");

            if (!isStaffOrAdmin) {
                try (Connection c = DatabaseConnection.getConnection();
                     PreparedStatement q = c.prepareStatement("SELECT customer_id FROM bookings WHERE id = ?")) {
                    q.setInt(1, bookingId);
                    try (ResultSet r = q.executeQuery()) {
                        if (!r.next() || r.getInt(1) != jwtCustomerId) {
                            ApiResponseUtil.sendError(exchange, 403, "ERR_403_FORBIDDEN", "Không có quyền xem đơn phòng này");
                            return;
                        }
                    }
                }
            }

            List<ServiceOrder> orders = serviceOrderDAO.getOrdersByBookingId(bookingId);
            JsonObject envelope = new JsonObject();
            envelope.addProperty("status", "success");
            envelope.add("data", gson.toJsonTree(orders));
            ApiResponseUtil.write(exchange, 200, gson.toJson(envelope));
        } else {
            handleGetActiveOrders(exchange);
        }
    }

    private void handleUpdateStatus(HttpExchange exchange, String path) throws Exception {
        String role = String.valueOf(exchange.getAttribute("role"));
        if (!STAFF_ROLES.contains(role != null ? role.trim() : "")) {
            ApiResponseUtil.sendError(exchange, 403, "ERR_403_FORBIDDEN", "Chức năng chỉ dành cho nhân viên khách sạn");
            return;
        }

        JsonObject req = gson.fromJson(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8), JsonObject.class);
        if (req == null || !req.has("order_status")) {
            ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Thiếu trạng thái cập nhật: order_status");
            return;
        }

        long orderId = 0;
        if (path != null) {
            Matcher m = ORDER_PATH_PATTERN.matcher(path);
            if (m.matches()) {
                try {
                    orderId = Long.parseLong(m.group(1));
                } catch (NumberFormatException ignored) {}
            }
        }
        if (orderId == 0 && req != null && req.has("order_id") && !req.get("order_id").isJsonNull()) {
            try {
                orderId = req.get("order_id").getAsLong();
            } catch (Exception ignored) {}
        }

        if (orderId <= 0) {
            ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Mã đơn dịch vụ không hợp lệ");
            return;
        }

        String newStatus = req.get("order_status").getAsString().trim().toUpperCase();
        boolean ok = serviceOrderDAO.updateOrderStatus(orderId, newStatus);
        if (ok) {
            JsonObject envelope = new JsonObject();
            envelope.addProperty("status", "success");
            envelope.addProperty("message", "Đã cập nhật trạng thái đơn dịch vụ #" + orderId + " sang " + newStatus);
            ApiResponseUtil.write(exchange, 200, gson.toJson(envelope));
        } else {
            ApiResponseUtil.sendError(exchange, 404, "ERR_404_NOT_FOUND", "Không tìm thấy đơn dịch vụ #" + orderId);
        }
    }

    public static int resolveCustomerId(HttpExchange exchange) {
        Object cidObj = exchange.getAttribute("customer_id");
        if (cidObj instanceof Number num && num.intValue() > 0) {
            return num.intValue();
        }
        if (cidObj != null) {
            try {
                int cid = Integer.parseInt(cidObj.toString().trim());
                if (cid > 0) return cid;
            } catch (NumberFormatException ignored) {}
        }

        Object roleObj = exchange.getAttribute("role");
        String role = roleObj != null ? roleObj.toString().trim() : "";
        Object uidObj = exchange.getAttribute("userId");
        if (uidObj == null) uidObj = exchange.getAttribute("user_id");

        if (uidObj != null) {
            try {
                int uid = Integer.parseInt(uidObj.toString().trim());
                if (uid > 0) {
                    // Always retrieve the actual customer_id from user_id (via customers table)
                    try (Connection c = DatabaseConnection.getConnection();
                         PreparedStatement q = c.prepareStatement("SELECT id FROM customers WHERE user_id = ?")) {
                        q.setInt(1, uid);
                        try (ResultSet r = q.executeQuery()) {
                            if (r.next()) return r.getInt(1);
                        }
                    } catch (Exception ignored) {}

                    // Also check users table if user account references customer_id
                    try (Connection c = DatabaseConnection.getConnection();
                         PreparedStatement q = c.prepareStatement("SELECT customer_id FROM users WHERE id = ?")) {
                        q.setInt(1, uid);
                        try (ResultSet r = q.executeQuery()) {
                            if (r.next()) {
                                int cid = r.getInt(1);
                                if (!r.wasNull() && cid > 0) return cid;
                            }
                        }
                    } catch (Exception ignored) {}

                    // Fallback to uid only if not linked to a customer profile
                    return uid;
                }
            } catch (Exception ignored) {}
        }
        return 0;
    }
}
