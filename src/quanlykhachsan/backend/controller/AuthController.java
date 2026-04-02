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
        String path = exchange.getRequestURI().getPath();
        System.out.println("📥 Nhận yêu cầu: " + exchange.getRequestMethod() + " tại " + path);
        
        // Chỉ xử lý method POST 
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            
            // 1. Đọc body của request
            InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            
            // Dùng Gson để parse
            Gson gson = new Gson();
            JsonObject reqObj = gson.fromJson(requestBody, JsonObject.class);
            JsonObject resObj = new JsonObject();
            int statusCode = 200;
            
            if ("/api/auth/login".equals(path)) {
                String username = reqObj.has("username") ? reqObj.get("username").getAsString() : null;
                String password = reqObj.has("password") ? reqObj.get("password").getAsString() : null;

                // 2. Xử lý logic gọi xuống tầng Service
                User user = authService.login(username, password);

                // 3. Chuẩn bị dữ liệu trả về (Response JSON)
                if (user != null) {
                    String roleStr = (user.getRoleId() == 1) ? "ADMIN" : "USER";
                    
                    resObj.addProperty("status", "success");
                    resObj.addProperty("message", "Đăng nhập thành công");
                    
                    JsonObject dataObj = new JsonObject();
                    dataObj.addProperty("userId", user.getId());
                    dataObj.addProperty("username", user.getUsername());
                    dataObj.addProperty("role", roleStr);
                    dataObj.addProperty("fullName", user.getFullName() != null ? user.getFullName() : "");
                    dataObj.addProperty("email", user.getEmail() != null ? user.getEmail() : "");
                    dataObj.addProperty("phone", user.getPhone() != null ? user.getPhone() : "");
                    
                    resObj.add("data", dataObj);
                } else {
                    statusCode = 401; // Unauthorized
                    resObj.addProperty("status", "error");
                    resObj.addProperty("message", "Sai tên đăng nhập hoặc mật khẩu");
                    resObj.add("data", null);
                }
            } else if ("/api/auth/register".equals(path)) {
                User newUser = new User();
                newUser.setUsername(reqObj.has("username") ? reqObj.get("username").getAsString() : null);
                newUser.setPassword(reqObj.has("password") ? reqObj.get("password").getAsString() : null);
                newUser.setFullName(reqObj.has("fullName") ? reqObj.get("fullName").getAsString() : "");
                newUser.setEmail(reqObj.has("email") ? reqObj.get("email").getAsString() : "");
                newUser.setPhone(reqObj.has("phone") ? reqObj.get("phone").getAsString() : "");
                newUser.setStatus("pending");
                newUser.setRoleId(2); // default as staff
                
                if (newUser.getUsername() == null || newUser.getPassword() == null || newUser.getUsername().isEmpty() || newUser.getPassword().isEmpty()) {
                    statusCode = 400;
                    resObj.addProperty("status", "error");
                    resObj.addProperty("message", "Tên đăng nhập và mật khẩu không được trống.");
                    resObj.add("data", null);
                } else {
                    boolean success = authService.register(newUser);
                    if (success) {
                        resObj.addProperty("status", "success");
                        resObj.addProperty("message", "Đăng ký thành công. Vui lòng chờ phê duyệt.");
                        resObj.add("data", null);
                    } else {
                        statusCode = 400; // Bad request
                        resObj.addProperty("status", "error");
                        resObj.addProperty("message", "Tên đăng nhập đã tồn tại.");
                        resObj.add("data", null);
                    }
                }
            } else {
                statusCode = 404;
                resObj.addProperty("status", "error");
                resObj.addProperty("message", "Đường dẫn không tồn tại");
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
