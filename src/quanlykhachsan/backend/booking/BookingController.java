package quanlykhachsan.backend.booking;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import quanlykhachsan.backend.customer.LoyaltyService;
import quanlykhachsan.backend.room.Room;
import quanlykhachsan.backend.room.RoomService;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.GlobalExceptionHandler;
import quanlykhachsan.backend.utils.JsonUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Booking Lifecycle & Optimistic Locking Integration Controller (TASK-A2.3).
 * <p>
 * Endpoints:
 * - GET /api/v1/bookings: List bookings
 * - GET /api/v1/bookings/{id}: Single booking details (404 if not found)
 * - POST /api/v1/bookings: Create booking under optimistic locking on rooms
 * - POST /api/v1/bookings/{id}/cancel: Cancel booking and release room to vacant_clean
 */
public class BookingController implements HttpHandler {

    private final BookingService bookingService = new BookingService();
    private final BookingDAO bookingDao = new BookingDAOImpl();
    private final RoomService roomService = new RoomService();
    private final LoyaltyService loyaltyService = new LoyaltyService();
    private final Gson gson = JsonUtil.getGson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String rawPath = exchange.getRequestURI().getPath();
        String path = (rawPath == null || rawPath.isEmpty()) ? "/" : rawPath;
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String method = exchange.getRequestMethod();

        try {
            // Collection endpoint: /api/v1/bookings or /api/bookings
            if ("/api/v1/bookings".equals(path) || "/api/bookings".equals(path)) {
                if ("GET".equalsIgnoreCase(method)) {
                    handleGetAll(exchange);
                    return;
                } else if ("POST".equalsIgnoreCase(method)) {
                    handleCreate(exchange);
                    return;
                } else {
                    ApiResponseUtil.sendError(exchange, 405, "ERR_405_METHOD_NOT_ALLOWED", "Method Not Allowed");
                    return;
                }
            }

            // POST /api/v1/bookings/{id}/cancel or /api/bookings/{id}/cancel
            if (path.matches("^/(api/v1/bookings|api/bookings)/\\d+/cancel$")) {
                if ("POST".equalsIgnoreCase(method)) {
                    String[] parts = path.split("/");
                    String id = parts[parts.length - 2];
                    handleCancel(exchange, Map.of("id", id));
                    return;
                } else {
                    ApiResponseUtil.sendError(exchange, 405, "ERR_405_METHOD_NOT_ALLOWED", "Method Not Allowed. Expected POST.");
                    return;
                }
            }

            // GET /api/v1/bookings/{id} or /api/bookings/{id}
            if (path.matches("^/(api/v1/bookings|api/bookings)/\\d+$")) {
                if ("GET".equalsIgnoreCase(method)) {
                    String[] parts = path.split("/");
                    String id = parts[parts.length - 1];
                    handleGetById(exchange, Map.of("id", id));
                    return;
                } else if ("PUT".equalsIgnoreCase(method)) {
                    String[] parts = path.split("/");
                    String id = parts[parts.length - 1];
                    handleUpdate(exchange, Map.of("id", id));
                    return;
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    String[] parts = path.split("/");
                    String id = parts[parts.length - 1];
                    handleCancel(exchange, Map.of("id", id));
                    return;
                }
            }

            // Legacy helper: GET /api/bookings/room/{roomId}
            if ("GET".equalsIgnoreCase(method) && path.contains("/bookings/room/")) {
                int roomId = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
                Booking activeBooking = null;
                long now = System.currentTimeMillis();
                for (Booking b : bookingService.getAllBookings()) {
                    String st = b.getStatus() != null ? b.getStatus().toLowerCase() : "";
                    if (b.getRoomId() == roomId && (st.equals("checked_in") || st.equals("confirmed") || st.equals("pending") || st.equals("booked"))) {
                        if (now >= b.getCheckInDate().getTime() && now <= b.getCheckOutDate().getTime()) {
                            activeBooking = b;
                            break;
                        }
                    }
                }
                ApiResponseUtil.sendSuccess(exchange, 200, activeBooking);
                return;
            }

            // Legacy helper: GET /api/bookings/customer/{id}
            if ("GET".equalsIgnoreCase(method) && path.contains("/bookings/customer/")) {
                int customerId = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
                List<Booking> list = bookingService.getBookingsByCustomer(customerId);
                ApiResponseUtil.sendSuccess(exchange, 200, list);
                return;
            }

            ApiResponseUtil.sendError(exchange, 404, "ERR_404_NOT_FOUND", "Endpoint not found: " + path);

        } catch (Exception e) {
            GlobalExceptionHandler.handle(exchange, e);
        }
    }

    /**
     * GET /api/v1/bookings - Returns list of bookings.
     */
    public void handleGetAll(HttpExchange exchange) throws IOException {
        try {
            List<Map<String, Object>> list = bookingDao.getAllBookings();
            ApiResponseUtil.sendSuccess(exchange, 200, list, Map.of("total", list.size()));
        } catch (Exception e) {
            GlobalExceptionHandler.handle(exchange, e);
        }
    }

    /**
     * GET /api/v1/bookings/{id} - Returns booking by ID (404 if not found).
     */
    public void handleGetById(HttpExchange exchange, Map<String, String> params) throws IOException {
        try {
            String idStr = (params != null && params.containsKey("id")) ? params.get("id") : null;
            if (idStr == null) {
                String path = exchange.getRequestURI().getPath();
                idStr = path.substring(path.lastIndexOf('/') + 1);
            }

            int id;
            try {
                id = Integer.parseInt(idStr);
            } catch (NumberFormatException e) {
                ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Invalid booking ID format: " + idStr);
                return;
            }

            Map<String, Object> booking = bookingDao.getBookingById(id);
            if (booking != null) {
                ApiResponseUtil.sendSuccess(exchange, 200, booking);
            } else {
                ApiResponseUtil.sendError(exchange, 404, "ERR_404_NOT_FOUND", "Booking not found with ID: " + id);
            }
        } catch (Exception e) {
            GlobalExceptionHandler.handle(exchange, e);
        }
    }

    /**
     * POST /api/v1/bookings - Atomically creates booking under optimistic locking on rooms.
     */
    public void handleCreate(HttpExchange exchange) throws IOException {
        try {
            String requestBody;
            try (InputStream is = exchange.getRequestBody()) {
                requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
            }

            if (requestBody.isEmpty()) {
                throw new IllegalArgumentException("Request body cannot be empty");
            }

            JsonObject obj;
            try {
                obj = JsonParser.parseString(requestBody).getAsJsonObject();
            } catch (Exception e) {
                throw new IllegalArgumentException("Malformed JSON request body");
            }

            int customerId = obj.has("customer_id") ? obj.get("customer_id").getAsInt()
                    : (obj.has("customerId") ? obj.get("customerId").getAsInt() : 0);

            int roomId = obj.has("room_id") ? obj.get("room_id").getAsInt()
                    : (obj.has("roomId") ? obj.get("roomId").getAsInt() : 0);

            int roomVersion = obj.has("room_version") ? obj.get("room_version").getAsInt()
                    : (obj.has("roomVersion") ? obj.get("roomVersion").getAsInt() : 0);

            String checkInDate = obj.has("check_in_date") && !obj.get("check_in_date").isJsonNull()
                    ? obj.get("check_in_date").getAsString()
                    : (obj.has("checkInDate") && !obj.get("checkInDate").isJsonNull() ? obj.get("checkInDate").getAsString() : null);

            String checkOutDate = obj.has("check_out_date") && !obj.get("check_out_date").isJsonNull()
                    ? obj.get("check_out_date").getAsString()
                    : (obj.has("checkOutDate") && !obj.get("checkOutDate").isJsonNull() ? obj.get("checkOutDate").getAsString() : null);

            double totalAmount = obj.has("total_amount") ? obj.get("total_amount").getAsDouble()
                    : (obj.has("totalAmount") ? obj.get("totalAmount").getAsDouble()
                    : (obj.has("total_price") ? obj.get("total_price").getAsDouble()
                    : (obj.has("totalPrice") ? obj.get("totalPrice").getAsDouble() : 0.0)));

            if (customerId <= 0) {
                throw new IllegalArgumentException("customer_id is required and must be positive");
            }
            if (roomId <= 0) {
                throw new IllegalArgumentException("room_id is required and must be positive");
            }
            if (checkInDate == null || checkInDate.isBlank() || checkOutDate == null || checkOutDate.isBlank()) {
                throw new IllegalArgumentException("check_in_date and check_out_date are required");
            }

            int bookingId = bookingDao.createBookingAtomic(customerId, roomId, roomVersion, checkInDate, checkOutDate, totalAmount);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", bookingId);
            data.put("booking_id", bookingId);
            data.put("customer_id", customerId);
            data.put("room_id", roomId);
            data.put("room_version", roomVersion + 1);
            data.put("master_folio_id", "FOLIO-MASTER-" + bookingId);
            data.put("status", "confirmed");
            data.put("total_amount", totalAmount);
            ApiResponseUtil.sendSuccess(exchange, 201, data);
        } catch (Exception e) {
            GlobalExceptionHandler.handle(exchange, e);
        }
    }

    /**
     * POST /api/v1/bookings/{id}/cancel - Cancels booking and releases room to vacant_clean.
     */
    public void handleCancel(HttpExchange exchange, Map<String, String> params) throws IOException {
        try {
            String idStr = (params != null && params.containsKey("id")) ? params.get("id") : null;
            if (idStr == null) {
                String path = exchange.getRequestURI().getPath();
                String[] parts = path.split("/");
                if (parts.length >= 3) {
                    idStr = parts[parts.length - 2];
                }
            }

            int id;
            try {
                id = Integer.parseInt(idStr);
            } catch (Exception e) {
                ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Invalid booking ID: " + idStr);
                return;
            }

            boolean ok = bookingDao.cancelBooking(id);
            if (ok) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("booking_id", id);
                data.put("status", "cancelled");
                data.put("message", "Booking cancelled successfully, room released to vacant_clean");
                ApiResponseUtil.sendSuccess(exchange, 200, data);
            } else {
                ApiResponseUtil.sendError(exchange, 404, "ERR_404_NOT_FOUND", "Booking not found with ID: " + id);
            }
        } catch (Exception e) {
            GlobalExceptionHandler.handle(exchange, e);
        }
    }

    /**
     * PUT /api/v1/bookings/{id} - Modifies stay dates under optimistic locking and prevents overbooking.
     */
    public void handleUpdate(HttpExchange exchange, Map<String, String> params) throws IOException {
        try {
            String idStr = (params != null && params.containsKey("id")) ? params.get("id") : null;
            if (idStr == null) {
                String path = exchange.getRequestURI().getPath();
                idStr = path.substring(path.lastIndexOf('/') + 1);
            }
            int bookingId;
            try {
                bookingId = Integer.parseInt(idStr);
            } catch (NumberFormatException e) {
                ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Invalid booking ID format: " + idStr);
                return;
            }

            String requestBody;
            try (InputStream is = exchange.getRequestBody()) {
                requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            if (requestBody.isEmpty()) {
                throw new IllegalArgumentException("Request body cannot be empty");
            }

            JsonObject obj;
            try {
                obj = JsonParser.parseString(requestBody).getAsJsonObject();
            } catch (Exception e) {
                throw new IllegalArgumentException("Malformed JSON request body");
            }

            int roomVersion = obj.has("room_version") ? obj.get("room_version").getAsInt()
                    : (obj.has("roomVersion") ? obj.get("roomVersion").getAsInt() : 0);

            String checkInDate = obj.has("check_in_date") && !obj.get("check_in_date").isJsonNull()
                    ? obj.get("check_in_date").getAsString()
                    : (obj.has("checkInDate") && !obj.get("checkInDate").isJsonNull() ? obj.get("checkInDate").getAsString() : null);

            String checkOutDate = obj.has("check_out_date") && !obj.get("check_out_date").isJsonNull()
                    ? obj.get("check_out_date").getAsString()
                    : (obj.has("checkOutDate") && !obj.get("checkOutDate").isJsonNull() ? obj.get("checkOutDate").getAsString() : null);

            Double totalAmount = obj.has("total_amount") && !obj.get("total_amount").isJsonNull()
                    ? obj.get("total_amount").getAsDouble()
                    : (obj.has("totalAmount") && !obj.get("totalAmount").isJsonNull()
                    ? obj.get("totalAmount").getAsDouble() : null);

            boolean ok = bookingDao.updateBookingAtomic(bookingId, roomVersion, checkInDate, checkOutDate, totalAmount);
            if (ok) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("id", bookingId);
                data.put("booking_id", bookingId);
                data.put("status", "updated");
                data.put("room_version", roomVersion + 1);
                data.put("check_in_date", checkInDate);
                data.put("check_out_date", checkOutDate);
                if (totalAmount != null) {
                    data.put("total_amount", totalAmount);
                }
                ApiResponseUtil.sendSuccess(exchange, 200, data);
            } else {
                ApiResponseUtil.sendError(exchange, 404, "ERR_404_NOT_FOUND", "Booking not found with ID: " + bookingId);
            }
        } catch (Exception e) {
            GlobalExceptionHandler.handle(exchange, e);
        }
    }
}
