package quanlykhachsan.backend.customer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import java.util.NoSuchElementException;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.customer.dto.LoyaltyRedeemRequest;
import quanlykhachsan.backend.utils.SecurityUtil;

public class LoyaltyController implements HttpHandler {

    private LoyaltyService loyaltyService = new LoyaltyService();
    private CustomerService customerService = new CustomerService();

    public LoyaltyController() { }

    LoyaltyController(LoyaltyService loyaltyService, CustomerService customerService) {
        this.loyaltyService = loyaltyService;
        this.customerService = customerService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath().replaceFirst("^/api/v1/loyalty", "/api/loyalty");
        Gson gson = JsonUtil.getGson();

        // Kiểm tra quyền cơ bản: phải đăng nhập (role 1, 2, hoặc 3)
        if (!SecurityUtil.hasPermission(exchange, 1, 2, 3)) return;

        try {
            // GET /api/loyalty/customers  → Chỉ ADMIN và STAFF được xem danh sách
            if ("GET".equalsIgnoreCase(method) && path.equals("/api/loyalty/customers")) {
                if (!SecurityUtil.hasPermission(exchange, 1, 2)) return;
                List<Customer> customers = customerService.getAllCustomers();
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(customers));

            } else if ("POST".equalsIgnoreCase(method) && path.equals("/api/loyalty/adjust")) {
                if (!SecurityUtil.checkAdmin(exchange)) return;
                var json = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                if (!json.isJsonObject()) throw new IllegalArgumentException("Dữ liệu điều chỉnh điểm không hợp lệ.");
                JsonObject request = json.getAsJsonObject();
                if (!request.has("customerId") || !request.has("pointsChange") || !request.has("reason")
                        || !request.get("customerId").isJsonPrimitive() || !request.getAsJsonPrimitive("customerId").isNumber()
                        || !request.get("pointsChange").isJsonPrimitive() || !request.getAsJsonPrimitive("pointsChange").isNumber()
                        || !request.get("reason").isJsonPrimitive() || !request.getAsJsonPrimitive("reason").isString()) {
                    throw new IllegalArgumentException("Dữ liệu điều chỉnh điểm không hợp lệ.");
                }
                int customerId = request.get("customerId").getAsBigDecimal().intValueExact();
                int pointsChange = request.get("pointsChange").getAsBigDecimal().intValueExact();
                Customer updated = loyaltyService.adjustPoints(customerId, pointsChange, request.get("reason").getAsString(), String.valueOf(exchange.getAttribute("userId")));
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(updated));

            // GET /api/loyalty/history/{customerId} → CUSTOMER chỉ xem lịch sử của mình
            } else if ("GET".equalsIgnoreCase(method) && path.startsWith("/api/loyalty/history/")) {
                String[] parts = path.split("/");
                int customerId = Integer.parseInt(parts[parts.length - 1]);
                List<LoyaltyHistory> history = loyaltyService.getHistory(customerId);
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(history));

            // POST /api/loyalty/redeem → CUSTOMER tự đổi điểm của mình
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

        } catch (NoSuchElementException e) {
            ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error(e.getMessage()));
        } catch (JsonParseException | IllegalArgumentException | ArithmeticException | UnsupportedOperationException e) {
            ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Dữ liệu không hợp lệ. Số điểm phải là số nguyên, khác 0; lý do tối đa 200 ký tự."));
        } catch (IllegalStateException e) {
            ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error(e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Lỗi Server: " + e.getMessage()));
        }
    }
}
