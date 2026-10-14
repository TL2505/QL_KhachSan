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

import java.text.SimpleDateFormat;
import java.util.Date;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.booking.dto.BookingCreateRequest;
import quanlykhachsan.backend.room.OptimisticLockException;
import quanlykhachsan.backend.room.RoomAllocationService;


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

            // 0. GET /api/bookings (Lấy danh sách booking)
            if ("GET".equalsIgnoreCase(method) && ("/api/bookings".equals(path) || "/api/v1/bookings".equals(path))) {
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(bookingService.getAllBookings()));
                return;
            }

            if ("GET".equalsIgnoreCase(method) && path.matches("/api/v1/bookings/\\d+")) {
                int bookingId = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
                Booking booking = bookingService.getBookingById(bookingId);
                if (booking == null) {
                    ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Booking not found"));
                    return;
                }
                ApiResponseUtil.write(exchange, 200,
                    ApiResponseUtil.successWithData(bookingService.getBookingFolioDetails(booking)));
                return;
            }

            // 1. POST /api/bookings (Đặt phòng)
            if ("POST".equalsIgnoreCase(method) && ("/api/bookings".equals(path) || "/api/v1/bookings".equals(path))) {
                InputStream is = exchange.getRequestBody();
                String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                Gson gson = JsonUtil.getGson();
                BookingCreateRequest req = gson.fromJson(requestBody, BookingCreateRequest.class);

                if (req.getCustomerId() == null || req.getRoomId() == null || req.getCheckInDate() == null || req.getCheckOutDate() == null) {
                    ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Thiếu trường thông tin!"));
                    return;
                }

                int customerId = req.getCustomerId();
                int roomId = req.getRoomId();
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                Date checkIn = sdf.parse(req.getCheckInDate());
                Date checkOut = sdf.parse(req.getCheckOutDate());

                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTime(checkIn);
                cal.set(java.util.Calendar.HOUR_OF_DAY, 14);
                checkIn = cal.getTime();

                cal.setTime(checkOut);
                cal.set(java.util.Calendar.HOUR_OF_DAY, 12);
                checkOut = cal.getTime();

                // Lấy thông tin phòng để kiểm tra trạng thái và tính giá tạm tính
                RoomAllocationService.RoomSnapshot room = RoomAllocationService.findRoom(roomId);
                if (room == null) {
                    ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Phòng không tồn tại!"));
                    return;
                }

                String st = room.status() != null ? room.status().toLowerCase() : "";
                if (!"vacant_clean".equals(st)) {
                    ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error("Room is not available for reservation"));
                    return;
                }

                // Tính số ngày (rất đơn giản)
                long diff = checkOut.getTime() - checkIn.getTime();
                long days = diff / (1000 * 60 * 60 * 24);
                if (days < 1) days = 1;
                java.math.BigDecimal roomFee = room.price().multiply(java.math.BigDecimal.valueOf(days));
                FinancialCalculator.Calculation pricing = FinancialCalculator.calculate(
                    roomFee, java.math.BigDecimal.ZERO, TaxConfig.fromRuntimeConfiguration());
                double totalPrice = pricing.getTotalAmount().doubleValue();

                Booking newBooking = new Booking();
                newBooking.setCustomerId(customerId);
                newBooking.setRoomId(roomId);
                newBooking.setCheckInDate(checkIn);
                newBooking.setCheckOutDate(checkOut);
                newBooking.setTotalPrice(totalPrice);
                newBooking.setStatus("pending");

                int expectedRoomVersion = room.version();
                int generatedId = bookingService.addBooking(newBooking, expectedRoomVersion);

                if (generatedId > 0) {
                    JsonObject details = new JsonObject();
                    details.addProperty("bookingId", generatedId);
                    ApiResponseUtil.write(exchange, 201, ApiResponseUtil.successWithData(details));
                } else {
                    ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Lỗi tạo booking hoặc trùng lịch!"));
                }

            }
            // 2. PUT /api/bookings/checkin/{id}
            else if ("PUT".equalsIgnoreCase(method) && (path.startsWith("/api/bookings/checkin/") || path.startsWith("/api/v1/bookings/checkin/"))) {
                int bookingId = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
                Booking b = bookingService.getBookingById(bookingId);
                
                if (b != null) {
                    bookingService.updateBookingStatus(bookingId, "checked_in");
                    roomService.updateRoomStatus(b.getRoomId(), "occupied_clean");
                    ApiResponseUtil.write(exchange, 200, ApiResponseUtil.success("Check-in thành công"));
                } else {
                    ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Không tìm thấy Booking ID"));
                }
            }
            // 3. PUT /api/bookings/checkout/{id}
            else if ("PUT".equalsIgnoreCase(method) && (path.startsWith("/api/bookings/checkout/") || path.startsWith("/api/v1/bookings/checkout/"))) {
                int bookingId = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
                Booking b = bookingService.getBookingById(bookingId);
                
                if (b != null) {
                    bookingService.updateBookingStatus(bookingId, "checked_out");

                    // Cộng điểm tích lũy cho khách hàng sau khi check-out
                    try {
                        if (b.getCustomerId() > 0 && b.getTotalPrice() > 0) {
                            loyaltyService.addPoints(
                                b.getCustomerId(),
                                b.getTotalPrice(),
                                "Thanh toán phòng #" + b.getRoomId() + " (Booking #" + bookingId + ")"
                            );
                        }
                    } catch (Exception loyaltyEx) {
                        // Không để lỗi điểm thưởng ảnh hưởng đến việc check-out
                        System.err.println("[WARN] Không thể cộng điểm thành viên: " + loyaltyEx.getMessage());
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

            // 4. GET /api/bookings/room/{roomId} - Active booking for a room
            else if ("GET".equalsIgnoreCase(method) && (path.startsWith("/api/bookings/room/") || path.startsWith("/api/v1/bookings/room/"))) {
                int roomId = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
                Gson gson = JsonUtil.getGson();
                // Find the active booking for this room (checked_in, booked, or pending)
                quanlykhachsan.backend.booking.Booking activeBooking = null;

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

            // 5. GET /api/bookings/customer/{id}
            else if ("GET".equalsIgnoreCase(method) && (path.startsWith("/api/bookings/customer/") || path.startsWith("/api/v1/bookings/customer/"))) {
                int customerId = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
                java.util.List<Booking> list = bookingService.getBookingsByCustomer(customerId);
                Gson gson = JsonUtil.getGson();
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(list));

            }

            JsonObject obj;
            try {
                obj = JsonParser.parseString(requestBody).getAsJsonObject();
            } catch (Exception e) {
                throw new IllegalArgumentException("Malformed JSON request body");
            }


        } catch (OptimisticLockException e) {
            JsonObject error = new JsonObject();
            error.addProperty("error_code", "ERR_409_CONFLICT");
            error.addProperty("message", "Room state changed by another concurrent transaction");
            error.addProperty("timestamp", java.time.Instant.now().toString());
            ApiResponseUtil.write(exchange, 409, JsonUtil.getGson().toJson(error));

        } catch (Exception e) {
            GlobalExceptionHandler.handle(exchange, e);
        }
    }
}
