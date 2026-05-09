package quanlykhachsan.backend.booking;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.booking.BookingService;
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
                JsonObject reqObj = gson.fromJson(requestBody, JsonObject.class);

                String bookingIdStr = reqObj.has("bookingId") ? reqObj.get("bookingId").getAsString() : null;
                String amountStr = reqObj.has("amount") ? reqObj.get("amount").getAsString() : null;
                String paymentMethod = reqObj.has("paymentMethod") ? reqObj.get("paymentMethod").getAsString() : null;
                String customerIdStr = reqObj.has("customerId") ? reqObj.get("customerId").getAsString() : null;

                if (bookingIdStr == null || amountStr == null || paymentMethod == null) {
                    sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Thiếu thông tin thanh toán!\"}");
                    return;
                }

                int bookingId = Integer.parseInt(bookingIdStr);
                double amount = Double.parseDouble(amountStr);
                boolean success = bookingService.processPayment(bookingId, amount, paymentMethod);

                if (success) {
                    // Award loyalty points
                    int earnedPoints = 0;
                    if (customerIdStr != null) {
                        int customerId = Integer.parseInt(customerIdStr);
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

                    sendResponse(exchange, 200, gson.toJson(resObj));
                } else {
                    sendResponse(exchange, 404, "{\"status\": \"error\", \"message\": \"Không tìm thấy đơn đặt phòng ID: " + bookingId + "\"}");
                }

            } else {
                exchange.sendResponseHeaders(405, -1);
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"status\": \"error\", \"message\": \"Lỗi Server Payment: " + e.getMessage() + "\"}");
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
