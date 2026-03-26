package quanlykhachsan.backend.controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class PaymentController implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            // POST /api/payments
            if ("POST".equalsIgnoreCase(method) && "/api/payments".equals(path)) {
                InputStream is = exchange.getRequestBody();
                String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                String bookingIdStr = extractJsonValue(requestBody, "bookingId");
                String amountStr = extractJsonValue(requestBody, "amount");
                String paymentMethod = extractJsonValue(requestBody, "paymentMethod");

                if (bookingIdStr == null || amountStr == null || paymentMethod == null) {
                    sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Thiếu thông tin thanh toán!\"}");
                    return;
                }

                // Phase 1: Tạo Mock Transaction thay vì Record vào DB vì chưa có module Invoice
                String transactionId = "TXN-" + System.currentTimeMillis();
                
                String jsonResponse = "{\n" +
                        "  \"status\": \"success\",\n" +
                        "  \"message\": \"Thanh toán thành công\",\n" +
                        "  \"data\": {\n" +
                        "    \"transactionId\": \"" + transactionId + "\",\n" +
                        "    \"bookingId\": " + bookingIdStr + ",\n" +
                        "    \"amount\": " + amountStr + ",\n" +
                        "    \"paymentMethod\": \"" + paymentMethod + "\",\n" +
                        "    \"status\": \"paid\"\n" +
                        "  }\n" +
                        "}";

                sendResponse(exchange, 200, jsonResponse);

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

    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return null;
        int colonIndex = json.indexOf(":", keyIndex);
        if (colonIndex == -1) return null;
        
        int endIndex = json.indexOf(",", colonIndex);
        if (endIndex == -1) endIndex = json.indexOf("}", colonIndex);
        if (endIndex == -1) return null;

        String value = json.substring(colonIndex + 1, endIndex).trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }
}
