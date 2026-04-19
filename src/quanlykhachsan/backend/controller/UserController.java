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
import java.sql.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.utils.JsonUtil;

public class UserController implements HttpHandler {

    private AuthService authService = new AuthService();
    private UserDAO userDAO = new UserDAOImpl();
    private Gson gson = JsonUtil.getGson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("📥 Nhận yêu cầu: " + exchange.getRequestMethod() + " tại " + exchange.getRequestURI());

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String pathInfo = path.replace("/api/users", "");

        if (path.equals("/api/users/register")) {
            handleRegister(exchange);
            return;
        }

        if (path.equals("/api/roles") && "GET".equalsIgnoreCase(method)) {
            handleGetAllRoles(exchange);
            return;
        }

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
            sendJson(exchange, statusCode, gson.toJson(resObj));
            return;
        }

        try {
            if (path.equals("/api/users/update-profile")) {
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
                    resObj.addProperty("message", "Mật khẩu cũ không chính xác");
                }
            }
        } catch (Exception e) {
            statusCode = 500;
            resObj.addProperty("status", "error");
            resObj.addProperty("message", "Lỗi hệ thống: " + e.getMessage());
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

    private void handleGetAllRoles(HttpExchange exchange) throws IOException {
        List<quanlykhachsan.backend.model.Role> roles = userDAO.selectAllRoles();
        JsonObject res = new JsonObject();
        res.addProperty("status", "success");
        res.add("data", gson.toJsonTree(roles));
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

            int customerRoleId = userDAO.getRoleIdByName("customer");
            boolean ok;
            if (user.getRoleId() == customerRoleId && customerRoleId != -1) {
                // Nếu là khách hàng, tạo hồ sơ khách hàng trước
                quanlykhachsan.backend.model.Customer c = new quanlykhachsan.backend.model.Customer();
                c.setFullName(user.getFullName() != null ? user.getFullName() : user.getUsername());
                c.setIdentityCard("AUTO-" + System.currentTimeMillis());
                c.setPhone(user.getPhone() != null ? user.getPhone() : "");
                c.setEmail(user.getEmail() != null ? user.getEmail() : "");
                
                ok = authService.registerCustomer(user, c);
            } else {
                user.setPassword(SecurityUtil.hashPassword(user.getPassword()));
                if (user.getStatus() == null || user.getStatus().isEmpty()) {
                    user.setStatus("active");
                }
                ok = userDAO.insert(user);
            }
            
            if (ok) {
                JsonObject res = new JsonObject();
                res.addProperty("status", "success");
                res.addProperty("message", "Thêm người dùng thành công");
                sendJson(exchange, 201, res.toString());
            } else {
                sendJson(exchange, 500, buildError("Thêm người dùng thất bại."));
            }
        } catch (java.sql.SQLIntegrityConstraintViolationException e) {
            String dbMsg = e.getMessage().toLowerCase();
            String userMsg = "Dữ liệu bị trùng lặp: ";
            if (dbMsg.contains("username")) userMsg = "Tên đăng nhập đã tồn tại";
            else if (dbMsg.contains("identity_card") || dbMsg.contains("cccd")) userMsg = "Số CCCD/Passport đã được sử dụng";
            else userMsg += e.getMessage();
            sendJson(exchange, 400, buildError(userMsg));
        } catch (Exception e) {
            sendJson(exchange, 500, buildError("Lỗi hệ thống: " + e.getMessage()));
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

            int customerRoleId = userDAO.getRoleIdByName("customer");
            // Nếu người dùng được đổi sang quyền Khách hàng mà chưa có customer_id liên kết
            if (existingUser.getRoleId() == customerRoleId && customerRoleId != -1 && existingUser.getCustomerId() == null) {
                quanlykhachsan.backend.model.Customer c = new quanlykhachsan.backend.model.Customer();
                c.setFullName(existingUser.getFullName() != null && !existingUser.getFullName().isEmpty() 
                    ? existingUser.getFullName() : existingUser.getUsername());
                c.setIdentityCard("LINK-" + System.currentTimeMillis());
                c.setPhone(existingUser.getPhone() != null ? existingUser.getPhone() : "");
                c.setEmail(existingUser.getEmail() != null ? existingUser.getEmail() : "");
                
                quanlykhachsan.backend.dao.CustomerDAO customerDAO = new quanlykhachsan.backend.daoimpl.CustomerDAOImpl();
                int customerId = customerDAO.addAndReturnId(c);
                if (customerId > 0) {
                    existingUser.setCustomerId(customerId);
                }
            }

            userDAO.updateUser(existingUser);

            JsonObject res = new JsonObject();
            res.addProperty("status", "success");
            res.addProperty("message", "Cập nhật người dùng thành công");
            sendJson(exchange, 200, res.toString());
        } catch (java.sql.SQLIntegrityConstraintViolationException e) {
            String dbMsg = e.getMessage().toLowerCase();
            String userMsg = "Dữ liệu bị trùng lặp: ";
            if (dbMsg.contains("username")) userMsg = "Tên đăng nhập đã tồn tại";
            else if (dbMsg.contains("identity_card") || dbMsg.contains("cccd")) userMsg = "Số CCCD/Passport đã được sử dụng";
            else userMsg += e.getMessage();
            sendJson(exchange, 400, buildError(userMsg));
        } catch (Exception e) {
            sendJson(exchange, 500, buildError("Lỗi hệ thống: " + e.getMessage()));
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

        try {
            userDAO.deleteUser(existingUser);
            JsonObject res = new JsonObject();
            res.addProperty("status", "success");
            res.addProperty("message", "Xóa người dùng thành công");
            sendJson(exchange, 200, res.toString());
        } catch (Exception e) {
            sendJson(exchange, 500, buildError("Lỗi hệ thống khi xóa: " + e.getMessage()));
        }
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

    private void handleRegister(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, buildError("Method Not Allowed"));
            return;
        }
        try {
            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject reqObj = gson.fromJson(body, JsonObject.class);

            User user = new User();
            user.setUsername(reqObj.get("username").getAsString());
            user.setPassword(reqObj.get("password").getAsString());
            user.setFullName(reqObj.get("fullName").getAsString());
            user.setEmail(reqObj.get("email").getAsString());
            user.setPhone(reqObj.get("phone").getAsString());

            quanlykhachsan.backend.model.Customer customer = new quanlykhachsan.backend.model.Customer();
            customer.setFullName(user.getFullName());
            customer.setEmail(user.getEmail());
            customer.setPhone(user.getPhone());
            customer.setIdentityCard(reqObj.has("identityCard") && !reqObj.get("identityCard").getAsString().isEmpty() 
                ? reqObj.get("identityCard").getAsString() 
                : "REG-" + System.currentTimeMillis());
            customer.setAddress(reqObj.has("address") ? reqObj.get("address").getAsString() : "");

            boolean success = authService.registerCustomer(user, customer);
            if (success) {
                JsonObject res = new JsonObject();
                res.addProperty("status", "success");
                res.addProperty("message", "Đăng ký thành công");
                sendJson(exchange, 201, res.toString());
            } else {
                sendJson(exchange, 400, buildError("Đăng ký thất bại."));
            }
        } catch (java.sql.SQLIntegrityConstraintViolationException e) {
            String dbMsg = e.getMessage().toLowerCase();
            String userMsg = "Dữ liệu bị trùng lặp: ";
            if (dbMsg.contains("username")) userMsg = "Tên đăng nhập đã tồn tại";
            else if (dbMsg.contains("identity_card") || dbMsg.contains("cccd")) userMsg = "Số CCCD/Passport đã được sử dụng";
            else userMsg += e.getMessage();
            sendJson(exchange, 400, buildError(userMsg));
        } catch (Exception e) {
            sendJson(exchange, 500, buildError("Lỗi hệ thống: " + e.getMessage()));
        }
    }
}
