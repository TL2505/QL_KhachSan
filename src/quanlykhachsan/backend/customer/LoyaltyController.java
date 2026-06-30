package quanlykhachsan.backend.customer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.customer.dto.LoyaltyRedeemRequest;
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
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(customers));

            // GET /api/loyalty/history/{customerId}
            } else if ("GET".equalsIgnoreCase(method) && path.startsWith("/api/loyalty/history/")) {
                String[] parts = path.split("/");
                int customerId = Integer.parseInt(parts[parts.length - 1]);
                List<LoyaltyHistory> history = loyaltyService.getHistory(customerId);
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(history));

            // POST /api/loyalty/redeem  → redeem points
            } else if ("POST".equalsIgnoreCase(method) && path.equals("/api/loyalty/redeem")) {
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                LoyaltyRedeemRequest req = gson.fromJson(body, LoyaltyRedeemRequest.class);

                int customerId = req.getCustomerId();
                int pointsToRedeem = req.getPointsToRedeem();
                double discountAmount = req.getDiscountAmount();

                // Validate redemption package
                if (!((pointsToRedeem == 100 && discountAmount == 50000)
                     || (pointsToRedeem == 500 && discountAmount == 300000))) {
                    ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Gói đổi điểm không hợp lệ!"));
                    return;
                }

                boolean success = loyaltyService.redeemPoints(customerId, pointsToRedeem, discountAmount, "Đổi điểm lấy ưu đãi");
                if (success) {
                    ApiResponseUtil.write(exchange, 200, ApiResponseUtil.success("Đổi điểm thành công! Giảm " + (long) discountAmount + " VNĐ."));
                } else {
                    ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Không đủ điểm để đổi!"));
                }

            } else {
                exchange.sendResponseHeaders(405, -1);
            }

        } catch (Exception e) {
            e.printStackTrace();
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Lỗi Server: " + e.getMessage()));
        }
    }
}
