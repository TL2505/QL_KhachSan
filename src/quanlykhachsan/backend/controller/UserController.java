package quanlykhachsan.backend.controller;

import quanlykhachsan.backend.service.AuthService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class UserController implements HttpHandler {

    private AuthService authService = new AuthService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("📥 Nhận yêu cầu: " + exchange.getRequestMethod() + " tại " + exchange.getRequestURI());

        String path = exchange.getRequestURI().getPath();

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) || "PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            
            InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Gson gson = new Gson();
            JsonObject reqObj = gson.fromJson(requestBody, JsonObject.class);
            JsonObject resObj = new JsonObject();
            int statusCode = 200;

            String username = reqObj.has("username") ? reqObj.get("username").getAsString() : null;

            if (username == null) {
                statusCode = 400;
                resObj.addProperty("status", "error");
                resObj.addProperty("message", "Thiếu username");
            } else if (path.equals("/api/users/update-profile")) {
                String fullName = reqObj.has("fullName") ? reqObj.get("fullName").getAsString() : null;
                String email = reqObj.has("email") ? reqObj.get("email").getAsString() : null;
                String phone = reqObj.has("phone") ? reqObj.get("phone").getAsString() : null;

                boolean success = authService.updateProfile(username, fullName, email, phone);
                if (success) {
                    resObj.addProperty("status", "success");
                    resObj.addProperty("message", "Cập nhật hồ sơ thành công");
                } else {
                    statusCode = 500;
                    resObj.addProperty("status", "error");
                    resObj.addProperty("message", "Lỗi khi cập nhật hồ sơ");
                }
            } else if (path.equals("/api/users/change-password")) {
                String oldPassword = reqObj.has("oldPassword") ? reqObj.get("oldPassword").getAsString() : null;
                String newPassword = reqObj.has("newPassword") ? reqObj.get("newPassword").getAsString() : null;

                boolean success = authService.changePassword(username, oldPassword, newPassword);
                if (success) {
                    resObj.addProperty("status", "success");
                    resObj.addProperty("message", "Đổi mật khẩu thành công");
                } else {
                    statusCode = 400;
                    resObj.addProperty("status", "error");
                    resObj.addProperty("message", "Mật khẩu cũ không chính xác hoặc lỗi hệ thống");
                }
            } else {
                statusCode = 404;
                resObj.addProperty("status", "error");
                resObj.addProperty("message", "Endpoint không tồn tại");
            }

            String responseStr = gson.toJson(resObj);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            byte[] responseBytes = responseStr.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
            
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }
}
