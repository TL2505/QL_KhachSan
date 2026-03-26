package quanlykhachsan.backend.controller;

import quanlykhachsan.backend.model.User;
import quanlykhachsan.backend.service.AuthService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class AuthController implements HttpHandler {

    private AuthService authService = new AuthService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Chỉ xử lý method POST cho /api/auth/login
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            
            // 1. Đọc body của request (chứa username, password Json)
            InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            
            // Hàm phân tích chuỗi JSON đơn giản (Do project chưa cài thư viện Gson/Jackson)
            String username = extractJsonValue(requestBody, "username");
            String password = extractJsonValue(requestBody, "password");

            // 2. Xử lý logic gọi xuống tầng Service
            User user = authService.login(username, password);

            // 3. Chuẩn bị dữ liệu trả về (Response JSON)
            String responseStr = "";
            int statusCode = 200;

            if (user != null) {
                // Đăng nhập thành công -> Trả JSON đúng API Contract
                // Sửa tạm role dựa trên roleId để phù hợp contract (Ví dụ: roleId 1 là ADMIN, 2 là RES...)
                String roleStr = (user.getRoleId() == 1) ? "ADMIN" : "USER";
                
                responseStr = "{\n" +
                        "  \"status\": \"success\",\n" +
                        "  \"message\": \"Đăng nhập thành công\",\n" +
                        "  \"data\": {\n" +
                        "    \"userId\": " + user.getId() + ",\n" +
                        "    \"username\": \"" + user.getUsername() + "\",\n" +
                        "    \"role\": \"" + roleStr + "\"\n" +
                        "  }\n" +
                        "}";
            } else {
                // Đăng nhập thất bại
                statusCode = 401; // Unauthorized
                responseStr = "{\n" +
                        "  \"status\": \"error\",\n" +
                        "  \"message\": \"Sai tên đăng nhập hoặc mật khẩu\",\n" +
                        "  \"data\": null\n" +
                        "}";
            }

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

    /**
     * Hàm tiện ích giúp parse JSON chuỗi gốc để lấy value nhanh (vì không dùng thư viện)
     * Ví dụ JSON: { "username" : "admin" } -> Trả về: admin
     */
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
