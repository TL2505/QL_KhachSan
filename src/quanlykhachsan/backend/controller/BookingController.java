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
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class BookingController implements HttpHandler {

    private BookingService bookingService = new BookingService();
    private RoomService roomService = new RoomService();
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        Gson gson = new Gson();

        try {
            // 1. GET /api/bookings (Lấy danh sách booking để làm search helper)
            if ("GET".equalsIgnoreCase(method) && "/api/bookings".equals(path)) {
                List<Booking> bookings = bookingService.getAllBookings();
                JsonObject resObj = new JsonObject();
                resObj.addProperty("status", "success");
                resObj.add("data", gson.toJsonTree(bookings));
                sendResponse(exchange, 200, gson.toJson(resObj));
            }
            // 2. POST /api/bookings (Đặt phòng)
            else if ("POST".equalsIgnoreCase(method) && "/api/bookings".equals(path)) {
                InputStream is = exchange.getRequestBody();
                String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject reqObj = gson.fromJson(requestBody, JsonObject.class);

                int customerId = reqObj.get("customerId").getAsInt();
                int roomId = reqObj.get("roomId").getAsInt();
                Date checkIn = sdf.parse(reqObj.get("checkInDate").getAsString());
                Date checkOut = sdf.parse(reqObj.get("checkOutDate").getAsString());

                quanlykhachsan.backend.model.Room room = roomService.getRoomById(roomId);
                if (room == null || (!"available".equals(room.getStatus()) && !"dirty".equals(room.getStatus()))) {
                    sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Phòng không khả dụng!\"}");
                    return;
                }

                long days = (checkOut.getTime() - checkIn.getTime()) / (1000 * 60 * 60 * 24);
                if (days < 1) days = 1;

                Booking b = new Booking();
                b.setCustomerId(customerId);
                b.setRoomId(roomId);
                b.setCheckInDate(checkIn);
                b.setCheckOutDate(checkOut);
                b.setTotalPrice(days * room.getPrice());
                b.setStatus("pending");

                int bookingId = bookingService.createBooking(b);
                if (bookingId != -1) {
                    roomService.updateRoomStatus(roomId, "booked");
                    JsonObject res = new JsonObject();
                    res.addProperty("status", "success");
                    res.addProperty("message", "Đặt phòng thành công");
                    res.addProperty("bookingId", bookingId);
                    sendResponse(exchange, 200, gson.toJson(res));
                } else {
                    sendResponse(exchange, 500, "{\"status\": \"error\", \"message\": \"Không thể đặt phòng (Trùng lịch)\"}");
                }
            }
            // 3. PUT /api/bookings/checkin/{id}
            else if ("PUT".equalsIgnoreCase(method) && path.startsWith("/api/bookings/checkin/")) {
                int bookingId = Integer.parseInt(path.substring("/api/bookings/checkin/".length()));
                Booking b = bookingService.getBookingById(bookingId);
                if (b != null) {
                    bookingService.updateBookingStatus(bookingId, "checked_in");
                    roomService.updateRoomStatus(b.getRoomId(), "occupied");
                    sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Check-in thành công\"}");
                } else {
                    sendResponse(exchange, 404, "{\"status\": \"error\", \"message\": \"Không tìm thấy Booking\"}");
                }
            }
            // 4. PUT /api/bookings/checkout/{id}
            else if ("PUT".equalsIgnoreCase(method) && path.startsWith("/api/bookings/checkout/")) {
                int bookingId = Integer.parseInt(path.substring("/api/bookings/checkout/".length()));
                Booking b = bookingService.getBookingById(bookingId);
                if (b != null) {
                    bookingService.updateBookingStatus(bookingId, "checked_out");
                    roomService.updateRoomStatus(b.getRoomId(), "dirty");
                    sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Check-out thành công\"}");
                } else {
                    sendResponse(exchange, 404, "{\"status\": \"error\", \"message\": \"Không tìm thấy Booking\"}");
                }
            }
            else {
                exchange.sendResponseHeaders(405, -1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"status\": \"error\", \"message\": \"Error: " + e.getMessage() + "\"}");
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
