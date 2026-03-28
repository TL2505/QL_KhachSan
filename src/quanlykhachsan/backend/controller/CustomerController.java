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

        // Xét sử dụng chung Gson
        Gson gson = new Gson();

        // 1. GET /api/customers (Lấy danh sách khách hàng)
        if ("GET".equalsIgnoreCase(method)) {
            List<Customer> customers = customerService.getAllCustomers();
            
            JsonObject resObj = new JsonObject();
            resObj.addProperty("status", "success");
            resObj.add("data", gson.toJsonTree(customers));

            sendResponse(exchange, 200, gson.toJson(resObj));

        } 
        // 2. POST /api/customers (Thêm khách hàng)
        else if ("POST".equalsIgnoreCase(method)) {
            InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            // Bóc tách JSON bằng Gson
            JsonObject reqObj = gson.fromJson(requestBody, JsonObject.class);
            String name = reqObj.has("name") ? reqObj.get("name").getAsString() : null;
            String phone = reqObj.has("phone") ? reqObj.get("phone").getAsString() : null;
            String cccd = reqObj.has("cccd") ? reqObj.get("cccd").getAsString() : null;

            Customer newCustomer = new Customer();
            newCustomer.setFullName(name);
            newCustomer.setPhone(phone);
            newCustomer.setIdentityCard(cccd);
            
            // Chặn dữ liệu rỗng
            if (name == null || phone == null || cccd == null) {
                sendResponse(exchange, 400, "{\n  \"status\": \"error\",\n  \"message\": \"Thiếu trường thông tin!\",\n  \"data\": null\n}");
                return;
            }

            boolean success = customerService.addCustomer(newCustomer);

            if (success) {
                String response = "{\n  \"status\": \"success\",\n  \"message\": \"Thêm khách hàng thành công!\",\n  \"data\": null\n}";
                sendResponse(exchange, 200, response);
            } else {
                String response = "{\n  \"status\": \"error\",\n  \"message\": \"Không thể thêm khách hàng (Có thể trùng CCCD)\",\n  \"data\": null\n}";
                sendResponse(exchange, 500, response);
            }

        } else {
            exchange.sendResponseHeaders(405, -1);
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
