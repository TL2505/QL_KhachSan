package quanlykhachsan.backend.booking;

import quanlykhachsan.backend.booking.Booking;
import quanlykhachsan.backend.booking.BookingService;
import quanlykhachsan.backend.room.RoomService;
import quanlykhachsan.backend.customer.LoyaltyService;
import quanlykhachsan.backend.utils.SecurityUtil;
import quanlykhachsan.backend.utils.ApiResponseUtil;
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
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.booking.dto.BookingCreateRequest;

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
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(bookingService.getAllBookings()));
                return;
            }

            // 1. POST /api/bookings (Đặt phòng)
            if ("POST".equalsIgnoreCase(method) && "/api/bookings".equals(path)) {
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
                Date checkIn = sdf.parse(req.getCheckInDate());
                Date checkOut = sdf.parse(req.getCheckOutDate());

                // Lấy thông tin phòng để kiểm tra trạng thái và tính giá tạm tính
                quanlykhachsan.backend.room.Room room = roomService.getRoomById(roomId);
                if (room == null || !"available".equals(room.getStatus())) {
                    ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Phòng không tồn tại hoặc đã được đặt!"));
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
                    JsonObject details = new JsonObject();
                    details.addProperty("bookingId", generatedId);
                    ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(details));
                } else {
                    ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Lỗi tạo booking hoặc trùng lịch!"));
                }

            }
            // 2. PUT /api/bookings/checkin/{id}
            else if ("PUT".equalsIgnoreCase(method) && path.startsWith("/api/bookings/checkin/")) {
                int bookingId = Integer.parseInt(path.substring("/api/bookings/checkin/".length()));
                Booking b = bookingService.getBookingById(bookingId);
                
                if (b != null) {
                    bookingService.updateBookingStatus(bookingId, "checked_in");
                    roomService.updateRoomStatus(b.getRoomId(), "occupied");
                    ApiResponseUtil.write(exchange, 200, ApiResponseUtil.success("Check-in thành công"));
                } else {
                    ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Không tìm thấy Booking ID"));
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

                    ApiResponseUtil.write(exchange, 200, ApiResponseUtil.success("Check-out thành công"));
                } else {
                    ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Không tìm thấy Booking ID"));
                }
            }
            // 4. GET /api/bookings/room/{roomId} - Active booking for a room
            else if ("GET".equalsIgnoreCase(method) && path.startsWith("/api/bookings/room/")) {
                int roomId = Integer.parseInt(path.substring("/api/bookings/room/".length()));
                Gson gson = JsonUtil.getGson();
                // Find the active booking for this room (checked_in, booked, or pending)
                quanlykhachsan.backend.booking.Booking activeBooking = null;
                for (quanlykhachsan.backend.booking.Booking b : bookingService.getAllBookings()) {
                    if (b.getRoomId() == roomId &&
                        (b.getStatus().equals("checked_in") || b.getStatus().equals("booked") || b.getStatus().equals("pending"))) {
                        activeBooking = b;
                        break;
                    }
                }
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(activeBooking));
            }
            // 5. GET /api/bookings/customer/{id}
            else if ("GET".equalsIgnoreCase(method) && path.startsWith("/api/bookings/customer/")) {
                int customerId = Integer.parseInt(path.substring("/api/bookings/customer/".length()));
                java.util.List<Booking> list = bookingService.getBookingsByCustomer(customerId);
                Gson gson = JsonUtil.getGson();
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(list));
            }
            // 5. Các Method khác
            else {
                exchange.sendResponseHeaders(405, -1);
            }

        } catch (Exception e) {
            e.printStackTrace();
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Lỗi Server: " + e.getMessage()));
        }
    }
}
