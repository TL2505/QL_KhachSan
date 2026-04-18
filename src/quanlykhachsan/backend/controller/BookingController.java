package quanlykhachsan.backend.controller;

import quanlykhachsan.backend.model.Booking;
import quanlykhachsan.backend.service.BookingService;
import quanlykhachsan.backend.service.RoomService;
import quanlykhachsan.backend.service.LoyaltyService;
import quanlykhachsan.backend.utils.SecurityUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class BookingController implements HttpHandler {

    private BookingService bookingService = new BookingService();
    private RoomService roomService = new RoomService();
    private LoyaltyService loyaltyService = new LoyaltyService();
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        // Check if user is logged in (Role 1, 2, or 3)
        if (!SecurityUtil.hasPermission(exchange, 1, 2, 3)) return;

        try {
            // 0. GET /api/bookings (Lấy danh sách booking)
            if ("GET".equalsIgnoreCase(method) && "/api/bookings".equals(path)) {
                Gson gson = new Gson();
                JsonObject resObj = new JsonObject();
                resObj.addProperty("status", "success");
                resObj.add("data", gson.toJsonTree(bookingService.getAllBookings()));
                sendResponse(exchange, 200, gson.toJson(resObj));
                return;
            }

            // 1. POST /api/bookings (Đặt phòng)
            if ("POST".equalsIgnoreCase(method) && "/api/bookings".equals(path)) {
                InputStream is = exchange.getRequestBody();
                String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                Gson gson = new Gson();
                JsonObject reqObj = gson.fromJson(requestBody, JsonObject.class);

                String customerIdStr = reqObj.has("customerId") ? reqObj.get("customerId").getAsString() : null;
                String roomIdStr = reqObj.has("roomId") ? reqObj.get("roomId").getAsString() : null;
                String checkInStr = reqObj.has("checkInDate") ? reqObj.get("checkInDate").getAsString() : null;
                String checkOutStr = reqObj.has("checkOutDate") ? reqObj.get("checkOutDate").getAsString() : null;

                if (customerIdStr == null || roomIdStr == null || checkInStr == null || checkOutStr == null) {
                    sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Thiếu trường thông tin!\"}");
                    return;
                }

                int customerId = Integer.parseInt(customerIdStr);
                int roomId = Integer.parseInt(roomIdStr);
                Date checkIn = sdf.parse(checkInStr);
                Date checkOut = sdf.parse(checkOutStr);

                // Lấy thông tin phòng để kiểm tra trạng thái và tính giá tạm tính
                quanlykhachsan.backend.model.Room room = roomService.getRoomById(roomId);
                if (room == null || !"available".equals(room.getStatus())) {
                    sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Phòng không tồn tại hoặc đã được đặt!\"}");
                    return;
                }

                // Tính số ngày (rất đơn giản)
                long diff = checkOut.getTime() - checkIn.getTime();
                long days = diff / (1000 * 60 * 60 * 24);
                if (days < 1) days = 1;
                double totalPrice = days * room.getPrice();

                Booking newBooking = new Booking();
                newBooking.setCustomerId(customerId);
                newBooking.setRoomId(roomId);
                newBooking.setCheckInDate(checkIn);
                newBooking.setCheckOutDate(checkOut);
                newBooking.setTotalPrice(totalPrice);
                newBooking.setStatus("pending");

                int generatedId = bookingService.addBooking(newBooking);

                if (generatedId > 0) {
                    // Update room status -> booked
                    roomService.updateRoomStatus(roomId, "booked");
                    sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Đặt phòng thành công\", \"bookingId\": " + generatedId + "}");
                } else {
                    sendResponse(exchange, 500, "{\"status\": \"error\", \"message\": \"Lỗi tạo booking hoặc trùng lịch!\"}");
                }

            }
            // 2. PUT /api/bookings/checkin/{id}
            else if ("PUT".equalsIgnoreCase(method) && path.startsWith("/api/bookings/checkin/")) {
                int bookingId = Integer.parseInt(path.substring("/api/bookings/checkin/".length()));
                Booking b = bookingService.getBookingById(bookingId);
                
                if (b != null) {
                    bookingService.updateBookingStatus(bookingId, "checked_in");
                    roomService.updateRoomStatus(b.getRoomId(), "occupied");
                    sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Check-in thành công\"}");
                } else {
                    sendResponse(exchange, 404, "{\"status\": \"error\", \"message\": \"Không tìm thấy Booking ID\"}");
                }
            }
            // 3. PUT /api/bookings/checkout/{id}
            else if ("PUT".equalsIgnoreCase(method) && path.startsWith("/api/bookings/checkout/")) {
                int bookingId = Integer.parseInt(path.substring("/api/bookings/checkout/".length()));
                Booking b = bookingService.getBookingById(bookingId);
                
                if (b != null) {
                    bookingService.updateBookingStatus(bookingId, "checked_out");
                    roomService.updateRoomStatus(b.getRoomId(), "available");

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

                    sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Check-out thành công\"}");
                } else {
                    sendResponse(exchange, 404, "{\"status\": \"error\", \"message\": \"Không tìm thấy Booking ID\"}");
                }
            }
            // 4. GET /api/bookings/customer/{id}
            else if ("GET".equalsIgnoreCase(method) && path.startsWith("/api/bookings/customer/")) {
                int customerId = Integer.parseInt(path.substring("/api/bookings/customer/".length()));
                java.util.List<Booking> list = bookingService.getBookingsByCustomer(customerId);
                Gson gson = new Gson();
                JsonObject resObj = new JsonObject();
                resObj.addProperty("status", "success");
                resObj.add("data", gson.toJsonTree(list));
                sendResponse(exchange, 200, gson.toJson(resObj));
            }
            // 5. Các Method khác
            else {
                exchange.sendResponseHeaders(405, -1);
            }

        } catch (Exception e) {
            e.printStackTrace();
            JsonObject errObj = new JsonObject();
            errObj.addProperty("status", "error");
            errObj.addProperty("message", "Lỗi Server: " + e.getMessage());
            sendResponse(exchange, 500, new Gson().toJson(errObj));
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

}
