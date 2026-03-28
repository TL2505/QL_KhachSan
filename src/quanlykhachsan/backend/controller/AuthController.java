package quanlykhachsan.backend.controller;

import quanlykhachsan.backend.model.User;
import quanlykhachsan.backend.service.AuthService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class AuthController implements HttpHandler {

    private AuthService authService = new AuthService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Chỉ xử lý method POST cho /api/auth/login
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            
            // 1. Đọc body của request
            InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            
            // Dùng Gson để parse
            Gson gson = new Gson();
            JsonObject reqObj = gson.fromJson(requestBody, JsonObject.class);
            
            String username = reqObj.has("username") ? reqObj.get("username").getAsString() : null;
            String password = reqObj.has("password") ? reqObj.get("password").getAsString() : null;

            // 2. Xử lý logic gọi xuống tầng Service
            User user = authService.login(username, password);

            // 3. Chuẩn bị dữ liệu trả về (Response JSON)
            JsonObject resObj = new JsonObject();
            int statusCode = 200;

            if (user != null) {
                String roleStr = (user.getRoleId() == 1) ? "ADMIN" : "USER";
                
                resObj.addProperty("status", "success");
                resObj.addProperty("message", "Đăng nhập thành công");
                
                JsonObject dataObj = new JsonObject();
                dataObj.addProperty("userId", user.getId());
                dataObj.addProperty("username", user.getUsername());
                dataObj.addProperty("role", roleStr);
                
                resObj.add("data", dataObj);
            } else {
                statusCode = 401; // Unauthorized
                resObj.addProperty("status", "error");
                resObj.addProperty("message", "Sai tên đăng nhập hoặc mật khẩu");
                resObj.add("data", null);
            }
            
            String responseStr = gson.toJson(resObj);

            // 4. Gửi Response về cho Frontend
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            
            // Chống lỗi CORS khi Frontend gọi API từ nguồn khác (nếu dùng Web, còn Swing thì không sao)
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            
            byte[] responseBytes = responseStr.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
            
        } else {
            // Nếu gửi GET, PUT, DELETE... trả về lỗi 405 Method Not Allowed
            exchange.sendResponseHeaders(405, -1);
        }
    }

}
