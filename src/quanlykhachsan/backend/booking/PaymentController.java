package quanlykhachsan.backend.booking;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.booking.BookingService;
import quanlykhachsan.backend.booking.dto.PaymentCreateRequest;
import quanlykhachsan.backend.customer.LoyaltyService;
import quanlykhachsan.backend.utils.SecurityUtil;

public class PaymentController implements HttpHandler {

    private BookingService bookingService = new BookingService();
    private LoyaltyService loyaltyService = new LoyaltyService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        // Check if user is logged in (Role 1, 2, or 3)
        if (!SecurityUtil.hasPermission(exchange, 1, 2, 3)) return;

        try {
            // POST /api/payments
            if ("POST".equalsIgnoreCase(method) && "/api/payments".equals(path)) {
                InputStream is = exchange.getRequestBody();
                String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                Gson gson = JsonUtil.getGson();
                PaymentCreateRequest req = gson.fromJson(requestBody, PaymentCreateRequest.class);

                if (req.getBookingId() == null || req.getAmount() == null || req.getPaymentMethod() == null) {
                    ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Thiếu thông tin thanh toán!"));
                    return;
                }

                int bookingId = req.getBookingId();
                double amount = req.getAmount();
                boolean success = bookingService.processPayment(bookingId, amount, req.getPaymentMethod());

                if (success) {
                    // Award loyalty points
                    int earnedPoints = 0;
                    if (req.getCustomerId() != null) {
                        int customerId = req.getCustomerId();
                        loyaltyService.addPoints(customerId, amount, "Thanh toán đơn đặt phòng #" + bookingId);
                        earnedPoints = (int) (amount / 1000);
                    }

                    JsonObject resObj = new JsonObject();
                    resObj.addProperty("status", "success");
                    resObj.addProperty("message", "Thanh toán thành công!");
                    
                    JsonObject dataObj = new JsonObject();
                    dataObj.addProperty("bookingId", bookingId);
                    dataObj.addProperty("amount", amount);
                    dataObj.addProperty("earnedPoints", earnedPoints);
                    resObj.add("data", dataObj);

                    ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(resObj));
                } else {
                    ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Không tìm thấy đơn đặt phòng ID: " + bookingId));
                }

            } else {
                exchange.sendResponseHeaders(405, -1);
            }

        } catch (Exception e) {
            e.printStackTrace();
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Lỗi Server Payment: " + e.getMessage()));
        }
    }

}
