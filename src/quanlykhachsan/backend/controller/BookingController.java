package quanlykhachsan.backend.controller;

import quanlykhachsan.backend.model.Booking;
import quanlykhachsan.backend.service.BookingService;
import quanlykhachsan.backend.service.RoomService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BookingController implements HttpHandler {

    private BookingService bookingService = new BookingService();
    private RoomService roomService = new RoomService();
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            // 1. POST /api/bookings (Đặt phòng)
            if ("POST".equalsIgnoreCase(method) && "/api/bookings".equals(path)) {
                InputStream is = exchange.getRequestBody();
                String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                String customerIdStr = extractJsonValue(requestBody, "customerId");
                String roomIdStr = extractJsonValue(requestBody, "roomId");
                String checkInStr = extractJsonValue(requestBody, "checkInDate");
                String checkOutStr = extractJsonValue(requestBody, "checkOutDate");

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

                boolean success = bookingService.createBooking(newBooking);

                if (success) {
                    // Update room status -> booked
                    roomService.updateRoomStatus(roomId, "booked");
                    sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Đặt phòng thành công\", \"data\": null}");
                } else {
                    sendResponse(exchange, 500, "{\"status\": \"error\", \"message\": \"Trùng lịch trùng phòng! Không thể đặt.\"}");
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
                    sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Check-out thành công\"}");
                } else {
                    sendResponse(exchange, 404, "{\"status\": \"error\", \"message\": \"Không tìm thấy Booking ID\"}");
                }
            }
            // 4. Các Method khác
            else {
                exchange.sendResponseHeaders(405, -1);
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"status\": \"error\", \"message\": \"Lỗi Server: " + e.getMessage() + "\"}");
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

    // Hàm lấy giá trị số hoặc chuỗi từ JSON đơn giản (bỏ qua dấu ngoặc kép nếu có)
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return null;
        int colonIndex = json.indexOf(":", keyIndex);
        if (colonIndex == -1) return null;
        
        // Cắt từ vị trí dấu phẩy hoặc ngoặc nhọn kết thúc
        int endIndex = json.indexOf(",", colonIndex);
        if (endIndex == -1) endIndex = json.indexOf("}", colonIndex);
        if (endIndex == -1) return null;

        String value = json.substring(colonIndex + 1, endIndex).trim();
        // Xóa dấu nháy kép nếu có
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }
}
