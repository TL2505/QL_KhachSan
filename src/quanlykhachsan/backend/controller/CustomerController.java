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
                handlePost(exchange, gson);
            }
            // 3. PUT /api/customers/{id}
            else if ("PUT".equalsIgnoreCase(method)) {
                String path = exchange.getRequestURI().getPath();
                String[] parts = path.split("/");
                if (parts.length < 4) {
                    sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Thiếu ID khách hàng!\"}");
                    return;
                }
                int id = Integer.parseInt(parts[3]);
                handlePut(exchange, id, gson);
            }
            // 4. DELETE /api/customers/{id}
            else if ("DELETE".equalsIgnoreCase(method)) {
                String path = exchange.getRequestURI().getPath();
                String[] parts = path.split("/");
                if (parts.length < 4) {
                    sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Thiếu ID khách hàng!\"}");
                    return;
                }
                int id = Integer.parseInt(parts[3]);
                handleDelete(exchange, id);
            }
            else {
                exchange.sendResponseHeaders(405, -1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}");
        }
    }

    private void handlePost(HttpExchange exchange, Gson gson) throws IOException {
        InputStream is = exchange.getRequestBody();
        String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        JsonObject reqObj = gson.fromJson(requestBody, JsonObject.class);
        
        String name = reqObj.has("name") ? reqObj.get("name").getAsString() : null;
        String phone = reqObj.has("phone") ? reqObj.get("phone").getAsString() : null;
        String cccd = reqObj.has("cccd") ? reqObj.get("cccd").getAsString() : null;

        if (name == null || phone == null || cccd == null) {
            sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Thiếu thông tin!\"}");
            return;
        }

        Customer c = new Customer();
        c.setFullName(name);
        c.setPhone(phone);
        c.setIdentityCard(cccd);

        if (customerService.addCustomer(c)) {
            sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Thêm khách hàng thành công!\"}");
        } else {
            sendResponse(exchange, 500, "{\"status\": \"error\", \"message\": \"Không thể thêm khách hàng!\"}");
        }
    }

    private void handlePut(HttpExchange exchange, int id, Gson gson) throws IOException {
        InputStream is = exchange.getRequestBody();
        String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        JsonObject reqObj = gson.fromJson(requestBody, JsonObject.class);
        
        String name = reqObj.has("name") ? reqObj.get("name").getAsString() : null;
        String phone = reqObj.has("phone") ? reqObj.get("phone").getAsString() : null;
        String cccd = reqObj.has("cccd") ? reqObj.get("cccd").getAsString() : null;

        Customer c = customerService.getCustomerById(id);
        if (c == null) {
            sendResponse(exchange, 404, "{\"status\": \"error\", \"message\": \"Không tìm thấy ID: " + id + "\"}");
            return;
        }

        if (name != null) c.setFullName(name);
        if (phone != null) c.setPhone(phone);
        if (cccd != null) c.setIdentityCard(cccd);

        if (customerService.updateCustomer(c)) {
            sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Cập nhật thành công!\"}");
        } else {
            sendResponse(exchange, 500, "{\"status\": \"error\", \"message\": \"Cập nhật thất bại!\"}");
        }
    }

    private void handleDelete(HttpExchange exchange, int id) throws IOException {
        Customer c = customerService.getCustomerById(id);
        if (c == null) {
            sendResponse(exchange, 404, "{\"status\": \"error\", \"message\": \"Không tìm thấy khách hàng ID: " + id + "\"}");
            return;
        }

        if (customerService.deleteCustomer(id)) {
            sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Xóa khách hàng thành công!\"}");
        } else {
            sendResponse(exchange, 500, "{\"status\": \"error\", \"message\": \"Không thể xóa khách hàng này (Có thể do đang có lịch sử đặt phòng)\"}");
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
