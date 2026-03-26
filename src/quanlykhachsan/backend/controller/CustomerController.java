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

public class CustomerController implements HttpHandler {

    private CustomerService customerService = new CustomerService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        // 1. GET /api/customers (Lấy danh sách khách hàng)
        if ("GET".equalsIgnoreCase(method)) {
            List<Customer> customers = customerService.getAllCustomers();
            StringBuilder json = new StringBuilder("{\n  \"status\": \"success\",\n  \"data\": [\n");
            
            for (int i = 0; i < customers.size(); i++) {
                Customer c = customers.get(i);
                json.append("    {\n");
                json.append("      \"id\": ").append(c.getId()).append(",\n");
                json.append("      \"name\": \"").append(c.getFullName()).append("\",\n");
                json.append("      \"phone\": \"").append(c.getPhone()).append("\",\n");
                json.append("      \"cccd\": \"").append(c.getIdentityCard()).append("\"\n");
                json.append("    }");
                if (i < customers.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("  ]\n}");

            sendResponse(exchange, 200, json.toString());

        } 
        // 2. POST /api/customers (Thêm khách hàng)
        else if ("POST".equalsIgnoreCase(method)) {
            InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            // Bóc tách JSON thủ công (dựa theo Contract)
            String name = extractJsonValue(requestBody, "name");
            String phone = extractJsonValue(requestBody, "phone");
            String cccd = extractJsonValue(requestBody, "cccd");

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

    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return null;
        int colonIndex = json.indexOf(":", keyIndex);
        if (colonIndex == -1) return null;
        int startQuote = json.indexOf("\"", colonIndex);
        if (startQuote == -1) return null;
        int endQuote = json.indexOf("\"", startQuote + 1);
        if (endQuote == -1) return null;
        return json.substring(startQuote + 1, endQuote);
    }
}
