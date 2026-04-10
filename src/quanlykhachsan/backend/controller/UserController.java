package quanlykhachsan.backend.controller;

import quanlykhachsan.backend.service.AuthService;
import quanlykhachsan.backend.dao.UserDAO;
import quanlykhachsan.backend.daoimpl.UserDAOImpl;
import quanlykhachsan.backend.model.User;
import quanlykhachsan.backend.utils.SecurityUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class UserController implements HttpHandler {

    private AuthService authService = new AuthService();
    private UserDAO userDAO = new UserDAOImpl();
    private Gson gson = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("📥 Nhận yêu cầu: " + exchange.getRequestMethod() + " tại " + exchange.getRequestURI());

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String pathInfo = path.replace("/api/users", "");

        if (path.equals("/api/users/update-profile") || path.equals("/api/users/change-password")) {
            handleProfileActions(exchange, path);
            return;
        }

        // CRUD Endpoints for /api/users
        if ("GET".equalsIgnoreCase(method) && (pathInfo.isEmpty() || pathInfo.equals("/"))) {
            handleGetAllUsers(exchange);
        } else if ("POST".equalsIgnoreCase(method) && (pathInfo.isEmpty() || pathInfo.equals("/"))) {
            handleAddUser(exchange);
        } else if ("PUT".equalsIgnoreCase(method) && pathInfo.matches("/\\d+")) {
            int userId = Integer.parseInt(pathInfo.substring(1));
            handleUpdateUser(exchange, userId);
        } else if ("DELETE".equalsIgnoreCase(method) && pathInfo.matches("/\\d+")) {
            int userId = Integer.parseInt(pathInfo.substring(1));
            handleDeleteUser(exchange, userId);
        } else {
            sendJson(exchange, 404, buildError("Endpoint không tồn tại"));
        }
    }

    private void handleProfileActions(HttpExchange exchange, String path) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()) && !"PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, buildError("Method Not Allowed"));
            return;
        }
        InputStream is = exchange.getRequestBody();
        String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
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
        }
        sendJson(exchange, statusCode, gson.toJson(resObj));
    }

    private void handleGetAllUsers(HttpExchange exchange) throws IOException {
        if (!SecurityUtil.checkAdmin(exchange)) return;
        List<User> users = userDAO.selectUser();
        // Remove passwords before returning to frontend
        for (User u : users) {
            u.setPassword("");
        }
        JsonObject res = new JsonObject();
        res.addProperty("status", "success");
        res.add("data", gson.toJsonTree(users));
        sendJson(exchange, 200, res.toString());
    }

    private void handleAddUser(HttpExchange exchange) throws IOException {
        if (!SecurityUtil.checkAdmin(exchange)) return;
        try {
            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            User user = gson.fromJson(body, User.class);

            if (user.getUsername() == null || user.getPassword() == null || user.getRoleId() == 0) {
                sendJson(exchange, 400, buildError("Thiếu thông tin bắt buộc (username, password, role_id)"));
                return;
            }

            if (userDAO.findByUsername(user.getUsername()) != null) {
                sendJson(exchange, 400, buildError("Tên đăng nhập đã tồn tại"));
                return;
            }

            user.setPassword(SecurityUtil.hashPassword(user.getPassword()));
            if (user.getStatus() == null || user.getStatus().isEmpty()) {
                user.setStatus("active");
            }

            boolean ok = userDAO.insert(user);
            if (ok) {
                JsonObject res = new JsonObject();
                res.addProperty("status", "success");
                res.addProperty("message", "Thêm người dùng thành công");
                sendJson(exchange, 201, res.toString());
            } else {
                sendJson(exchange, 500, buildError("Thêm người dùng thất bại"));
            }
        } catch (Exception e) {
            sendJson(exchange, 500, buildError("Lỗi server: " + e.getMessage()));
        }
    }

    private void handleUpdateUser(HttpExchange exchange, int userId) throws IOException {
        if (!SecurityUtil.checkAdmin(exchange)) return;
        try {
            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            User reqUser = gson.fromJson(body, User.class);

            // Need to get the existing user from DB to keep the old password if not changed
            List<User> users = userDAO.selectUser();
            User existingUser = users.stream().filter(u -> u.getId() == userId).findFirst().orElse(null);

            if (existingUser == null) {
                sendJson(exchange, 404, buildError("Không tìm thấy người dùng"));
                return;
            }

            // Update fields
            existingUser.setFullName(reqUser.getFullName());
            existingUser.setEmail(reqUser.getEmail());
            existingUser.setPhone(reqUser.getPhone());
            existingUser.setRoleId(reqUser.getRoleId());
            existingUser.setStatus(reqUser.getStatus());

            if (reqUser.getPassword() != null && !reqUser.getPassword().trim().isEmpty()) {
                existingUser.setPassword(SecurityUtil.hashPassword(reqUser.getPassword()));
            }

            userDAO.updateUser(existingUser);

            JsonObject res = new JsonObject();
            res.addProperty("status", "success");
            res.addProperty("message", "Cập nhật người dùng thành công");
            sendJson(exchange, 200, res.toString());
        } catch (Exception e) {
            sendJson(exchange, 500, buildError("Lỗi server: " + e.getMessage()));
        }
    }

    private void handleDeleteUser(HttpExchange exchange, int userId) throws IOException {
        if (!SecurityUtil.checkAdmin(exchange)) return;
        
        List<User> users = userDAO.selectUser();
        User existingUser = users.stream().filter(u -> u.getId() == userId).findFirst().orElse(null);
        
        if (existingUser == null) {
            sendJson(exchange, 404, buildError("Không tìm thấy người dùng"));
            return;
        }

        userDAO.deleteUser(existingUser);

        JsonObject res = new JsonObject();
        res.addProperty("status", "success");
        res.addProperty("message", "Xóa người dùng thành công");
        sendJson(exchange, 200, res.toString());
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String buildError(String msg) {
        JsonObject obj = new JsonObject();
        obj.addProperty("status", "error");
        obj.addProperty("message", msg);
        return gson.toJson(obj);
    }
}
