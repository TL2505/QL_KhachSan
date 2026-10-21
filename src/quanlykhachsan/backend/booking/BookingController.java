package quanlykhachsan.backend.booking;

import quanlykhachsan.backend.room.RoomService;
import quanlykhachsan.backend.customer.LoyaltyService;
import quanlykhachsan.backend.utils.SecurityUtil;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
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

public class BookingController implements HttpHandler {

    private BookingService bookingService = new BookingService();
    private RoomService roomService = new RoomService();
    private LoyaltyService loyaltyService = new LoyaltyService();
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        // Check if user is logged in (Role 1, 2, or 3)
        String role=String.valueOf(exchange.getAttribute("role")).toLowerCase(java.util.Locale.ROOT);
        boolean customer=java.util.Set.of("customer","guest","3").contains(role);
        if (!role.equals("guest")&&!SecurityUtil.hasPermission(exchange, 1, 2, 3)) return;

        try {
            // 0. GET /api/bookings (Lấy danh sách booking)
            if ("GET".equalsIgnoreCase(method) && ("/api/bookings".equals(path) || "/api/v1/bookings".equals(path))) {
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(customer?bookingService.getBookingsByCustomer(quanlykhachsan.backend.hotelservice.ServiceOrderController.resolveCustomerId(exchange)):bookingService.getAllBookings()));
                return;
            }

            if ("GET".equalsIgnoreCase(method) && path.matches("/api/v1/bookings/\\d+")) {
                int bookingId = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
                Booking booking = bookingService.getBookingById(bookingId);
                if (booking == null) {
                    ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Booking not found"));
                    return;
                }
                if(customer&&booking.getCustomerId()!=quanlykhachsan.backend.hotelservice.ServiceOrderController.resolveCustomerId(exchange))throw new SecurityException();
                ApiResponseUtil.write(exchange, 200,
                    ApiResponseUtil.successWithData(bookingService.getBookingFolioDetails(booking)));
                return;
            }

            // Administrative decisions are intentionally limited to confirming
            // or cancelling a future reservation. Reception owns check-in,
            // check-out and housekeeping workflows.
            if ("PUT".equalsIgnoreCase(method) && path.matches("/api/v1/bookings/\\d+/admin-status")) {
                if (!SecurityUtil.checkAdmin(exchange)) return;
                int bookingId = Integer.parseInt(path.split("/")[4]);
                JsonObject request = JsonUtil.getGson().fromJson(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
                String requestedStatus = request != null && request.has("status") && !request.get("status").isJsonNull()
                    ? request.get("status").getAsString() : "";
                AdminBookingStatusService.Result result = new AdminBookingStatusService().decide(bookingId, requestedStatus);
                ApiResponseUtil.write(exchange, result.httpStatus(),
                    result.httpStatus() == 200 ? ApiResponseUtil.success(result.message()) : ApiResponseUtil.error(result.message()));
                return;
            }

            // 1. POST /api/bookings (Đặt phòng)
            if ("PUT".equalsIgnoreCase(method) && path.matches("/api/v1/bookings/\\d+/reception-status")) {
                if (!SecurityUtil.hasPermission(exchange, 2)) return;
                int bookingId = Integer.parseInt(path.split("/")[4]);
                JsonObject request = JsonUtil.getGson().fromJson(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
                String requestedStatus = request != null && request.has("status") && !request.get("status").isJsonNull()
                    ? request.get("status").getAsString() : "";
                AdminBookingStatusService.Result result = new AdminBookingStatusService().decideForReception(bookingId, requestedStatus);
                ApiResponseUtil.write(exchange, result.httpStatus(), result.httpStatus() == 200
                    ? ApiResponseUtil.success(result.message()) : ApiResponseUtil.error(result.message()));
                return;
            }

            if ("POST".equalsIgnoreCase(method) && ("/api/bookings".equals(path) || "/api/v1/bookings".equals(path))) {
                InputStream is = exchange.getRequestBody();
                String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                Gson gson = JsonUtil.getGson();
                BookingCreateRequest req = gson.fromJson(requestBody, BookingCreateRequest.class);

                if (req.getCustomerId() == null || req.getRoomId() == null || req.getCheckInDate() == null || req.getCheckOutDate() == null) {
                    ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Thiếu trường thông tin!"));
                    return;
                }

                int customerId=req.getCustomerId(),roomId=req.getRoomId();
                if(customer&&customerId!=quanlykhachsan.backend.hotelservice.ServiceOrderController.resolveCustomerId(exchange))throw new SecurityException();
                var dates=quanlykhachsan.backend.utils.PortalValidation.range(req.getCheckInDate(),req.getCheckOutDate());
                var room=RoomAllocationService.findRoom(roomId);if(room==null)throw new IllegalArgumentException("Unknown room");
                var request=new PublicBookingService.Request(null,null,null,roomId,room.version(),dates.checkIn(),dates.checkOut());
                var result=new PublicBookingService().reserve(exchange.getRequestHeaders().getFirst("Idempotency-Key"),request,customerId,"Web");
                ApiResponseUtil.write(exchange,result.status(),result.body());
            }
            // 2. PUT /api/bookings/checkin/{id}
            else if ("PUT".equalsIgnoreCase(method) && (path.startsWith("/api/bookings/checkin/") || path.startsWith("/api/v1/bookings/checkin/"))) {
                if (!SecurityUtil.hasPermission(exchange, 2)) return;
                int bookingId = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
                AdminBookingStatusService.Result result = new AdminBookingStatusService().checkIn(bookingId);
                ApiResponseUtil.write(exchange, result.httpStatus(), result.httpStatus() == 200
                    ? ApiResponseUtil.success(result.message()) : ApiResponseUtil.error(result.message()));
            }
            // 3. PUT /api/bookings/checkout/{id}
            else if ("PUT".equalsIgnoreCase(method) && (path.startsWith("/api/bookings/checkout/") || path.startsWith("/api/v1/bookings/checkout/"))) {
                if (!SecurityUtil.hasPermission(exchange, 2)) return;
                ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error("Use the folio payment checkout workflow"));
            }
            // 4. Cancellation: DELETE /api/bookings/{id} or PUT/POST /api/bookings/{id}/cancel
            else if (("DELETE".equalsIgnoreCase(method) && path.matches("/api/(v1/)?bookings/\\d+"))
                  || (("PUT".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method)) && path.matches("/api/(v1/)?bookings/\\d+/cancel"))) {
                int bookingId;
                if (path.endsWith("/cancel")) {
                    String cleanPath = path.substring(0, path.length() - "/cancel".length());
                    bookingId = Integer.parseInt(cleanPath.substring(cleanPath.lastIndexOf('/') + 1));
                } else {
                    bookingId = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
                }
                Booking b = bookingService.getBookingById(bookingId);
                if (b != null) {
                    if(customer&&b.getCustomerId()!=quanlykhachsan.backend.hotelservice.ServiceOrderController.resolveCustomerId(exchange))throw new SecurityException();
                    if ("checked_in".equalsIgnoreCase(b.getStatus()) || "checked_out".equalsIgnoreCase(b.getStatus())) {
                        ApiResponseUtil.sendError(exchange, 400, "ERR_400_INVALID_STATE", "Không thể hủy phòng đang ở hoặc đã thanh toán.");
                        return;
                    }
                    var cancelled=new AdminBookingStatusService().decide(bookingId,"cancelled");
                    ApiResponseUtil.write(exchange,cancelled.httpStatus(),cancelled.httpStatus()==200?ApiResponseUtil.success(cancelled.message()):ApiResponseUtil.error(cancelled.message()));
                } else {
                    ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Không tìm thấy Booking ID"));
                }
            }
            // 4. GET /api/bookings/room/{roomId} - Active booking for a room
            else if ("GET".equalsIgnoreCase(method) && (path.startsWith("/api/bookings/room/") || path.startsWith("/api/v1/bookings/room/"))) {
                if(customer)throw new SecurityException();
                int roomId = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
                Gson gson = JsonUtil.getGson();
                // Find the active booking for this room (checked_in, booked, or pending)
                quanlykhachsan.backend.booking.Booking activeBooking = null;
                long now = System.currentTimeMillis();
                for (quanlykhachsan.backend.booking.Booking b : bookingService.getAllBookings()) {
                    String st = b.getStatus() != null ? b.getStatus().toLowerCase() : "";
                    if (b.getRoomId() == roomId && (st.equals("checked_in") || st.equals("confirmed") || st.equals("pending") || st.equals("booked"))) {
                        if (now >= b.getCheckInDate().getTime() && now <= b.getCheckOutDate().getTime()) {
                            activeBooking = b;
                            break;
                        }
                    }
                }
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(activeBooking));
            }
            // 5. GET /api/bookings/customer/{id}
            else if ("GET".equalsIgnoreCase(method) && (path.startsWith("/api/bookings/customer/") || path.startsWith("/api/v1/bookings/customer/"))) {
                int customerId = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
                if(customer&&customerId!=quanlykhachsan.backend.hotelservice.ServiceOrderController.resolveCustomerId(exchange))throw new SecurityException();
                java.util.List<Booking> list = bookingService.getBookingsByCustomer(customerId);
                Gson gson = JsonUtil.getGson();
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(list));
            }
            // 5. Các Method khác
            else {
                exchange.sendResponseHeaders(405, -1);
            }

        } catch (SecurityException e) {
            ApiResponseUtil.sendError(exchange,403,"ERR_403_FORBIDDEN","Booking access denied");
        } catch (IllegalArgumentException e) {
            ApiResponseUtil.sendError(exchange,400,"ERR_400_BAD_REQUEST","Invalid booking request");
        } catch (IllegalStateException e) {
            ApiResponseUtil.sendError(exchange,409,"ERR_409_CONFLICT","Booking state changed");
        } catch (OptimisticLockException e) {
            JsonObject error = new JsonObject();
            error.addProperty("error_code", "ERR_409_CONFLICT");
            error.addProperty("message", "Room state changed by another concurrent transaction");
            error.addProperty("timestamp", java.time.Instant.now().toString());
            ApiResponseUtil.write(exchange, 409, JsonUtil.getGson().toJson(error));
        } catch (Exception e) {
            exchange.getResponseHeaders().set("X-Booking-Error-Id", BookingFailureLog.record("GET".equalsIgnoreCase(method) ? "booking-list" : "booking-write", e));
            ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("GET".equalsIgnoreCase(method)
                ? "Unable to load bookings; please retry or contact the Front Desk"
                : "Booking transaction was not confirmed; retry the same Idempotency-Key or contact the Front Desk"));
        }
    }
}
