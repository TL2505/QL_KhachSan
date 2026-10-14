package quanlykhachsan.backend.customer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import quanlykhachsan.backend.customer.dto.CustomerCreateRequest;
import quanlykhachsan.backend.customer.dto.CustomerResponse;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.GlobalExceptionHandler;
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.utils.SecurityUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Customer Profile Management Controller (TASK-A2.2).
 * <p>
 * Endpoints:
 * - POST /api/v1/customers: Register customer profile (duplicate ID/Phone returns 409).
 * - GET /api/v1/customers: Search or list customer profiles (sub-20ms indexed lookup).
 * - GET/PUT/DELETE /api/v1/customers/{id}: Single customer operations.
 */
public class CustomerController implements HttpHandler {

    private final CustomerService customerService = new CustomerService();
    private final CustomerDAO customerDao = new CustomerDAOImpl();
    private final Gson gson = JsonUtil.getGson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String rawPath = exchange.getRequestURI().getPath();
        String path = (rawPath == null || rawPath.isEmpty()) ? "/" : rawPath;
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String method = exchange.getRequestMethod();

        try {

            // Collection endpoint: /api/v1/customers or /api/customers
            if ("/api/v1/customers".equals(path) || "/api/customers".equals(path)) {
                if ("POST".equalsIgnoreCase(method)) {
                    handleCreate(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    handleSearch(exchange);
                    return;
                } else {
                    ApiResponseUtil.sendError(exchange, 405, "ERR_405_METHOD_NOT_ALLOWED", "Method Not Allowed");
                    return;
                }
            }

            // Single resource endpoint: /api/v1/customers/{id} or /api/customers/{id}
            String[] parts = path.split("/");
            if (parts.length >= 4 && (path.startsWith("/api/v1/customers/") || path.startsWith("/api/customers/"))) {
                int id;
                try {
                    id = Integer.parseInt(parts[parts.length - 1]);
                } catch (NumberFormatException e) {
                    ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Invalid customer ID format");
                    return;
                }

                if ("GET".equalsIgnoreCase(method)) {

                    Customer c = customerService.getCustomerById(id);
                    if (c != null) {
                        CustomerResponse dto = CustomerMapper.toCustomerResponse(c);
                        ApiResponseUtil.sendSuccess(exchange, 200, dto);
                    } else {
                        ApiResponseUtil.sendError(exchange, 404, "ERR_404_NOT_FOUND", "Customer not found with ID: " + id);
                    }
                } else if ("PUT".equalsIgnoreCase(method)) {
                    handlePut(exchange, id, gson);
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    handleDelete(exchange, id);
                } else {

                    ApiResponseUtil.sendError(exchange, 405, "ERR_405_METHOD_NOT_ALLOWED", "Method Not Allowed");
                }
                return;

            }

            ApiResponseUtil.sendError(exchange, 404, "ERR_404_NOT_FOUND", "Endpoint not found: " + path);

        } catch (Exception e) {
            GlobalExceptionHandler.handle(exchange, e);
        }
    }

    /**
     * POST /api/v1/customers - Creates customer profile enforcing uniqueness on identity & phone.
     */
    public void handleCreate(HttpExchange exchange) throws IOException {
        try {
            String requestBody;
            try (InputStream is = exchange.getRequestBody()) {
                requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
            }

            if (requestBody.isEmpty()) {
                throw new IllegalArgumentException("Request body cannot be empty");
            }

            JsonObject obj;
            try {
                obj = JsonParser.parseString(requestBody).getAsJsonObject();
            } catch (Exception e) {
                throw new IllegalArgumentException("Malformed JSON request body");
            }

            String fullName = obj.has("full_name") && !obj.get("full_name").isJsonNull()
                    ? obj.get("full_name").getAsString()
                    : (obj.has("fullName") && !obj.get("fullName").isJsonNull() ? obj.get("fullName").getAsString() : null);

            String phone = obj.has("phone") && !obj.get("phone").isJsonNull()
                    ? obj.get("phone").getAsString() : null;

            String identityCard = obj.has("identity_card") && !obj.get("identity_card").isJsonNull()
                    ? obj.get("identity_card").getAsString()
                    : (obj.has("identityCard") && !obj.get("identityCard").isJsonNull() ? obj.get("identityCard").getAsString() : null);

            String email = obj.has("email") && !obj.get("email").isJsonNull()
                    ? obj.get("email").getAsString() : null;

            String loyaltyLevel = obj.has("loyalty_level") && !obj.get("loyalty_level").isJsonNull()
                    ? obj.get("loyalty_level").getAsString()
                    : (obj.has("loyaltyLevel") && !obj.get("loyaltyLevel").isJsonNull() ? obj.get("loyaltyLevel").getAsString() : "Member");

            int id = customerDao.createCustomer(fullName, phone, identityCard, email, loyaltyLevel);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", id);
            data.put("full_name", fullName != null ? fullName.trim() : "");
            data.put("phone", phone != null ? phone.trim() : "");
            data.put("identity_card", identityCard != null ? identityCard.trim() : "");
            data.put("loyalty_level", loyaltyLevel != null ? loyaltyLevel.trim() : "Member");
            ApiResponseUtil.sendSuccess(exchange, 201, data);
        } catch (Exception e) {
            GlobalExceptionHandler.handle(exchange, e);
        }
    }

    /**
     * GET /api/v1/customers - Fast indexed search for customer profiles by keyword.
     */
    public void handleSearch(HttpExchange exchange) throws IOException {
        try {
            String query = exchange.getRequestURI().getQuery();
            String keyword = null;
            if (query != null && !query.isBlank()) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=", 2);
                    if (pair.length < 2) continue;
                    String k = pair[0].trim();
                    String v = java.net.URLDecoder.decode(pair[1].trim(), StandardCharsets.UTF_8);
                    if ("search".equalsIgnoreCase(k) || "q".equalsIgnoreCase(k) || "keyword".equalsIgnoreCase(k)) {
                        keyword = v;
                    }
                }
            }

            List<Map<String, Object>> customers = customerDao.searchCustomers(keyword);
            ApiResponseUtil.sendSuccess(exchange, 200, customers, Map.of("total", customers.size()));
        } catch (Exception e) {
            GlobalExceptionHandler.handle(exchange, e);
        }
    }

    private void handlePut(HttpExchange exchange, int id, Gson gson) throws Exception {
        if (!SecurityUtil.hasPermission(exchange, 1, 2)) return;
        InputStream is = exchange.getRequestBody();
        String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        CustomerCreateRequest req = gson.fromJson(requestBody, CustomerCreateRequest.class);

        Customer c = customerService.getCustomerById(id);
        if (c == null) {
            ApiResponseUtil.sendError(exchange, 404, "ERR_404_NOT_FOUND", "Không tìm thấy ID: " + id);
            return;
        }

        if (req.getName() != null && !req.getName().isEmpty()) c.setFullName(req.getName());
        if (req.getPhone() != null && !req.getPhone().isEmpty()) c.setPhone(req.getPhone());
        if (req.getCccd() != null && !req.getCccd().isEmpty()) c.setIdentityCard(req.getCccd());

        if (customerService.updateCustomer(c)) {
            ApiResponseUtil.sendSuccess(exchange, 200, Map.of("message", "Cập nhật khách hàng thành công!"));
        } else {
            ApiResponseUtil.sendError(exchange, 500, "ERR_500_SERVER_ERROR", "Không thể cập nhật khách hàng!");
        }
    }

    private void handleDelete(HttpExchange exchange, int id) throws Exception {
        if (!SecurityUtil.hasPermission(exchange, 1)) return;
        if (customerService.deleteCustomer(id)) {
            ApiResponseUtil.sendSuccess(exchange, 200, Map.of("message", "Xóa khách hàng thành công!"));
        } else {
            ApiResponseUtil.sendError(exchange, 500, "ERR_500_SERVER_ERROR", "Không thể xóa khách hàng!");
        }
    }
}
