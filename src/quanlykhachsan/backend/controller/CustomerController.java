package quanlykhachsan.backend.controller;

import quanlykhachsan.backend.model.Customer;
import quanlykhachsan.backend.service.CustomerService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class CustomerController implements HttpHandler {

    private CustomerService customerService = new CustomerService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        Gson gson = new Gson();

        try {
            // 1. GET /api/customers
            if ("GET".equalsIgnoreCase(method)) {
                List<Customer> customers = customerService.getAllCustomers();
                JsonObject resObj = new JsonObject();
                resObj.addProperty("status", "success");
                resObj.add("data", gson.toJsonTree(customers));
                sendResponse(exchange, 200, gson.toJson(resObj));
            } 
            // 2. POST /api/customers
            else if ("POST".equalsIgnoreCase(method)) {
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject reqObj = gson.fromJson(body, JsonObject.class);
                
                Customer newCustomer = new Customer();
                newCustomer.setFullName(reqObj.has("name") ? reqObj.get("name").getAsString() : null);
                newCustomer.setPhone(reqObj.has("phone") ? reqObj.get("phone").getAsString() : null);
                newCustomer.setIdentityCard(reqObj.has("cccd") ? reqObj.get("cccd").getAsString() : null);

                if (newCustomer.getFullName() == null || newCustomer.getPhone() == null || newCustomer.getIdentityCard() == null) {
                    sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Thiếu trường thông tin!\"}");
                    return;
                }

                boolean success = customerService.addCustomer(newCustomer);
                if (success) {
                    sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Thêm khách hàng thành công!\"}");
                } else {
                    sendResponse(exchange, 500, "{\"status\": \"error\", \"message\": \"Lỗi thêm khách hàng\"}");
                }
            }
            // 3. PUT /api/customers/{id}
            else if ("PUT".equalsIgnoreCase(method)) {
                String[] parts = path.split("/");
                int id = Integer.parseInt(parts[parts.length - 1]);
                
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject reqObj = gson.fromJson(body, JsonObject.class);
                
                Customer c = new Customer();
                c.setId(id);
                c.setFullName(reqObj.has("name") ? reqObj.get("name").getAsString() : null);
                c.setPhone(reqObj.has("phone") ? reqObj.get("phone").getAsString() : null);
                c.setIdentityCard(reqObj.has("cccd") ? reqObj.get("cccd").getAsString() : null);

                boolean success = customerService.updateCustomer(c);
                if (success) {
                    sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Cập nhật khách hàng thành công!\"}");
                } else {
                    sendResponse(exchange, 500, "{\"status\": \"error\", \"message\": \"Lỗi cập nhật khách hàng\"}");
                }
            }
            // 4. DELETE /api/customers/{id}
            else if ("DELETE".equalsIgnoreCase(method)) {
                String[] parts = path.split("/");
                int id = Integer.parseInt(parts[parts.length - 1]);
                boolean success = customerService.deleteCustomer(id);
                if (success) {
                    sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Xóa khách hàng thành công!\"}");
                } else {
                    sendResponse(exchange, 500, "{\"status\": \"error\", \"message\": \"Lỗi xóa khách hàng\"}");
                }
            }
            else {
                exchange.sendResponseHeaders(405, -1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"status\": \"error\", \"message\": \"Lỗi Server Customer: " + e.getMessage() + "\"}");
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
