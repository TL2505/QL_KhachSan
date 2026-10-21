package quanlykhachsan.backend.user;

import quanlykhachsan.backend.utils.SecurityUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.user.dto.UserResponse;
import quanlykhachsan.backend.user.dto.UserCreateRequest;
import quanlykhachsan.backend.user.dto.UserUpdateRequest;

public class UserController implements HttpHandler {

    private UserDAO userDAO = new UserDAOImpl();
    private Gson gson = JsonUtil.getGson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("📥 Nhận yêu cầu: " + exchange.getRequestMethod() + " tại " + exchange.getRequestURI());

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

        String path = exchange.getRequestURI().getPath();
        // Main exposes this controller below /api/v1, while the original
        // controller predicates were written for the legacy /api paths.
        // Normalize once so both public API versions reach the same handlers.
        String legacyPath = path.startsWith("/api/v1/")
                ? "/api/" + path.substring("/api/v1/".length())
                : path;
        String method = exchange.getRequestMethod();
        String pathInfo = legacyPath.replace("/api/users", "");

        if (legacyPath.equals("/api/users/register")) {
            handleRegister(exchange);
            return;
        }

        if (legacyPath.equals("/api/roles") && "GET".equalsIgnoreCase(method)) {
            handleGetAllRoles(exchange);
            return;
        }

        if (legacyPath.equals("/api/users/update-profile") || legacyPath.equals("/api/users/change-password") || legacyPath.equals("/api/auth/change-password")) {
            new quanlykhachsan.backend.auth.AccountSecurityController().handleAction(exchange,
                    legacyPath.endsWith("update-profile") ? "profile" : "password/confirm");
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
            ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Endpoint không tồn tại"));
        }
    }

    private void handleGetAllUsers(HttpExchange exchange) throws IOException {
        if (!SecurityUtil.checkAdmin(exchange)) return;
        List<User> users = userDAO.selectUser();
        List<quanlykhachsan.backend.user.Role> roles = userDAO.selectAllRoles();
        List<UserResponse> dtoList = new java.util.ArrayList<>();
        for (User u : users) {
            String rName = roles.stream()
                .filter(r -> r.getId() == u.getRoleId())
                .map(quanlykhachsan.backend.user.Role::getName)
                .findFirst()
                .orElse("unknown");
            dtoList.add(UserMapper.toUserResponse(u, rName));
        }
        ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(dtoList));
    }

    private void handleGetAllRoles(HttpExchange exchange) throws IOException {
        List<quanlykhachsan.backend.user.Role> roles = userDAO.selectAllRoles();
        ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(roles));
    }

    private void handleAddUser(HttpExchange exchange) throws IOException {
        if (!SecurityUtil.checkAdmin(exchange)) return;
        try {
            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            UserCreateRequest req = gson.fromJson(body, UserCreateRequest.class);

            if (req.getUsername() == null || req.getPassword() == null || req.getRoleId() == 0) {
                ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Thiếu thông tin bắt buộc (username, password, role_id)"));
                return;
            }

            if (userDAO.findByUsername(req.getUsername()) != null) {
                ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Tên đăng nhập đã tồn tại"));
                return;
            }

            User user = UserMapper.toUser(req);
            int customerRoleId = userDAO.getRoleIdByName("customer");
            boolean ok;
            if (user.getRoleId() == customerRoleId && customerRoleId != -1) {
                // Nếu là khách hàng, tạo hồ sơ khách hàng trước
                quanlykhachsan.backend.customer.Customer c = new quanlykhachsan.backend.customer.Customer();
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
                ApiResponseUtil.write(exchange, 201, ApiResponseUtil.success("Thêm người dùng thành công"));
            } else {
                ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Thêm người dùng thất bại."));
            }
        } catch (java.sql.SQLIntegrityConstraintViolationException e) {
            String dbMsg = e.getMessage().toLowerCase();
            String userMsg = "Dữ liệu bị trùng lặp: ";
            if (dbMsg.contains("username")) userMsg = "Tên đăng nhập đã tồn tại";
            else if (dbMsg.contains("identity_card") || dbMsg.contains("cccd")) userMsg = "Số CCCD/Passport đã được sử dụng";
            else userMsg += e.getMessage();
            ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error(userMsg));
        } catch (Exception e) {
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Lỗi hệ thống: " + e.getMessage()));
        }
    }

    private void handleUpdateUser(HttpExchange exchange, int userId) throws IOException {
        if (!SecurityUtil.checkAdmin(exchange)) return;
        try {
            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            UserUpdateRequest req = gson.fromJson(body, UserUpdateRequest.class);

            // Need to get the existing user from DB to keep the old password if not changed
            List<User> users = userDAO.selectUser();
            User existingUser = users.stream().filter(u -> u.getId() == userId).findFirst().orElse(null);

            if (existingUser == null) {
                ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Không tìm thấy người dùng"));
                return;
            }

            // Update fields
            existingUser.setFullName(req.getFullName());
            existingUser.setEmail(req.getEmail());
            existingUser.setPhone(req.getPhone());
            existingUser.setRoleId(req.getRoleId());
            existingUser.setStatus(req.getStatus());

            if (req.getPassword() != null && !req.getPassword().trim().isEmpty()) {
                existingUser.setPassword(SecurityUtil.hashPassword(req.getPassword()));
            }

            int customerRoleId = userDAO.getRoleIdByName("customer");
            // Nếu người dùng được đổi sang quyền Khách hàng mà chưa có customer_id liên kết
            if (existingUser.getRoleId() == customerRoleId && customerRoleId != -1 && existingUser.getCustomerId() == null) {
                quanlykhachsan.backend.customer.Customer c = new quanlykhachsan.backend.customer.Customer();
                c.setFullName(existingUser.getFullName() != null && !existingUser.getFullName().isEmpty() 
                    ? existingUser.getFullName() : existingUser.getUsername());
                c.setIdentityCard("LINK-" + System.currentTimeMillis());
                c.setPhone(existingUser.getPhone() != null ? existingUser.getPhone() : "");
                c.setEmail(existingUser.getEmail() != null ? existingUser.getEmail() : "");
                
                quanlykhachsan.backend.customer.CustomerDAO customerDAO = new quanlykhachsan.backend.customer.CustomerDAOImpl();
                int customerId = customerDAO.addAndReturnId(c);
                if (customerId > 0) {
                    existingUser.setCustomerId(customerId);
                }
            }

            userDAO.updateUser(existingUser);

            ApiResponseUtil.write(exchange, 200, ApiResponseUtil.success("Cập nhật người dùng thành công"));
        } catch (java.sql.SQLIntegrityConstraintViolationException e) {
            String dbMsg = e.getMessage().toLowerCase();
            String userMsg = "Dữ liệu bị trùng lặp: ";
            if (dbMsg.contains("username")) userMsg = "Tên đăng nhập đã tồn tại";
            else if (dbMsg.contains("identity_card") || dbMsg.contains("cccd")) userMsg = "Số CCCD/Passport đã được sử dụng";
            else userMsg += e.getMessage();
            ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error(userMsg));
        } catch (Exception e) {
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Lỗi hệ thống: " + e.getMessage()));
        }
    }

    private void handleDeleteUser(HttpExchange exchange, int userId) throws IOException {
        if (!SecurityUtil.checkAdmin(exchange)) return;
        
        List<User> users = userDAO.selectUser();
        User existingUser = users.stream().filter(u -> u.getId() == userId).findFirst().orElse(null);
        
        if (existingUser == null) {
            ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Không tìm thấy người dùng"));
            return;
        }

        try {
            userDAO.deleteUser(existingUser);
            ApiResponseUtil.write(exchange, 200, ApiResponseUtil.success("Xóa người dùng thành công"));
        } catch (Exception e) {
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Lỗi hệ thống khi xóa: " + e.getMessage()));
        }
    }


    private void handleRegister(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            ApiResponseUtil.write(exchange, 405, ApiResponseUtil.error("Method Not Allowed"));
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

            quanlykhachsan.backend.customer.Customer customer = new quanlykhachsan.backend.customer.Customer();
            customer.setFullName(user.getFullName());
            customer.setEmail(user.getEmail());
            customer.setPhone(user.getPhone());
            customer.setIdentityCard(reqObj.has("identityCard") && !reqObj.get("identityCard").getAsString().isEmpty() 
                ? reqObj.get("identityCard").getAsString() 
                : "REG-" + System.currentTimeMillis());
            customer.setAddress(reqObj.has("address") ? reqObj.get("address").getAsString() : "");

            boolean success = authService.registerCustomer(user, customer);
            if (success) {
                ApiResponseUtil.write(exchange, 201, ApiResponseUtil.success("Đăng ký thành công"));
            } else {
                ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Đăng ký thất bại."));
            }
        } catch (java.sql.SQLIntegrityConstraintViolationException e) {
            String dbMsg = e.getMessage().toLowerCase();
            String userMsg = "Dữ liệu bị trùng lặp: ";
            if (dbMsg.contains("username")) userMsg = "Tên đăng nhập đã tồn tại";
            else if (dbMsg.contains("identity_card") || dbMsg.contains("cccd")) userMsg = "Số CCCD/Passport đã được sử dụng";
            else userMsg += e.getMessage();
            ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error(userMsg));
        } catch (Exception e) {
            ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Lỗi hệ thống: " + e.getMessage()));
        }
    }
}
