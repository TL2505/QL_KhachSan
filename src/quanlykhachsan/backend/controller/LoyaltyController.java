package quanlykhachsan.backend.controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.model.Customer;
import quanlykhachsan.backend.model.LoyaltyHistory;
import quanlykhachsan.backend.service.CustomerService;
import quanlykhachsan.backend.service.LoyaltyService;
import quanlykhachsan.backend.utils.SecurityUtil;

public class LoyaltyController implements HttpHandler {

    private LoyaltyService loyaltyService = new LoyaltyService();
    private CustomerService customerService = new CustomerService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        Gson gson = JsonUtil.getGson();

        if (!SecurityUtil.hasPermission(exchange, 1, 2)) return;

        try {
            // GET /api/loyalty/customers  → all customers with loyalty info
            if ("GET".equalsIgnoreCase(method) && path.equals("/api/loyalty/customers")) {
                List<Customer> customers = customerService.getAllCustomers();
                JsonObject resObj = new JsonObject();
                resObj.addProperty("status", "success");
                resObj.add("data", gson.toJsonTree(customers));
                sendResponse(exchange, 200, gson.toJson(resObj));

            // GET /api/loyalty/history/{customerId}
            } else if ("GET".equalsIgnoreCase(method) && path.startsWith("/api/loyalty/history/")) {
                String[] parts = path.split("/");
                int customerId = Integer.parseInt(parts[parts.length - 1]);
                List<LoyaltyHistory> history = loyaltyService.getHistory(customerId);
                JsonObject resObj = new JsonObject();
                resObj.addProperty("status", "success");
                resObj.add("data", gson.toJsonTree(history));
                sendResponse(exchange, 200, gson.toJson(resObj));

            // POST /api/loyalty/redeem  → redeem points
            } else if ("POST".equalsIgnoreCase(method) && path.equals("/api/loyalty/redeem")) {
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject req = gson.fromJson(body, JsonObject.class);

                int customerId = req.get("customerId").getAsInt();
                int pointsToRedeem = req.get("pointsToRedeem").getAsInt();
                double discountAmount = req.get("discountAmount").getAsDouble();

                // Validate redemption package
                if (!((pointsToRedeem == 100 && discountAmount == 50000)
                     || (pointsToRedeem == 500 && discountAmount == 300000))) {
                    sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Gói đổi điểm không hợp lệ!\"}");
                    return;
                }

                boolean success = loyaltyService.redeemPoints(customerId, pointsToRedeem, discountAmount, "Đổi điểm lấy ưu đãi");
                if (success) {
                    sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Đổi điểm thành công! Giảm " + (long) discountAmount + " VNĐ.\"}");
                } else {
                    sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Không đủ điểm để đổi!\"}");
                }

            } else {
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
}
