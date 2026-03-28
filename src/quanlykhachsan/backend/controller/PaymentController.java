package quanlykhachsan.backend.controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.dao.PaymentDAO;
import quanlykhachsan.backend.daoimpl.PaymentDAOImpl;
import quanlykhachsan.backend.model.Payment;

public class PaymentController implements HttpHandler {

    private PaymentDAO paymentDAO = new PaymentDAOImpl();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            // POST /api/payments
            if ("POST".equalsIgnoreCase(method) && "/api/payments".equals(path)) {
                InputStream is = exchange.getRequestBody();
                String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                Gson gson = new Gson();
                JsonObject reqObj = gson.fromJson(requestBody, JsonObject.class);

                // Lấy thông tin từ Request
                Integer bookingId = reqObj.has("bookingId") ? reqObj.get("bookingId").getAsInt() : null;
                Double amount = reqObj.has("amount") ? reqObj.get("amount").getAsDouble() : null;
                String paymentMethod = reqObj.has("paymentMethod") ? reqObj.get("paymentMethod").getAsString() : null;

                if (bookingId == null || amount == null || paymentMethod == null) {
                    sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Thiếu thông tin thanh toán!\"}");
                    return;
                }

                // --- LƯU THÔNG TIN THANH TOÁN VÀO CƠ SỞ DỮ LIỆU ---
                Payment payment = new Payment();
                payment.setInvoiceId(bookingId); // Dùng Booking ID làm Invoice ID
                payment.setAmount(amount);
                payment.setPaymentMethod(paymentMethod);
                payment.setPaymentDate(new Date());

                paymentDAO.addPayment(payment);

                // Phản hồi thành công
                JsonObject resObj = new JsonObject();
                resObj.addProperty("status", "success");
                resObj.addProperty("message", "Thanh toán thành công và đã lưu vào hệ thống");
                
                JsonObject dataObj = new JsonObject();
                dataObj.addProperty("bookingId", bookingId);
                dataObj.addProperty("amount", amount);
                dataObj.addProperty("paymentMethod", paymentMethod);
                dataObj.addProperty("status", "paid");
                
                resObj.add("data", dataObj);

                sendResponse(exchange, 200, gson.toJson(resObj));

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
