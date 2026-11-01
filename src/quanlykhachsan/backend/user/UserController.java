package quanlykhachsan.backend.user;

import quanlykhachsan.backend.auth.AuthService;
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

    private AuthService authService = new AuthService();
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
        } else if ("PUT".equalsIgnoreCase(method) && pathInfo.matches("/\\d+/role")) {
            int userId = Integer.parseInt(pathInfo.substring(1, pathInfo.length() - "/role".length()));
            handleUpdateRole(exchange, userId);
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
        if (!SecurityUtil.checkAdmin(exchange)) return;
        List<quanlykhachsan.backend.user.Role> roles = userDAO.selectAllRoles();
        ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(roles));
    }

    private void handleUpdateRole(HttpExchange exchange, int userId) throws IOException {
        if (!SecurityUtil.checkAdmin(exchange)) return;
        try {
            String body = new String(exchange.getRequestBody().readNBytes(4097), StandardCharsets.UTF_8);
            if (body.length() > 4096) {
                ApiResponseUtil.write(exchange, 413, ApiResponseUtil.error("Yêu cầu quá lớn"));
                return;
            }
            JsonObject request = gson.fromJson(body, JsonObject.class);
            if (request == null || !request.has("roleId") || !request.get("roleId").isJsonPrimitive()
                    || !request.getAsJsonPrimitive("roleId").isNumber()) {
                ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Thiếu mã vai trò hợp lệ"));
                return;
            }
            int roleId = request.get("roleId").getAsInt();
            List<quanlykhachsan.backend.user.Role> roles = userDAO.selectAllRoles();
            quanlykhachsan.backend.user.Role targetRole = roles.stream()
                    .filter(role -> role.getId() == roleId).findFirst().orElse(null);
            if (targetRole == null) {
                ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Vai trò không tồn tại"));
                return;
            }

            List<User> users = userDAO.selectUser();
            User target = users.stream().filter(user -> user.getId() == userId).findFirst().orElse(null);
            if (target == null) {
                ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Không tìm thấy tài khoản"));
                return;
            }
            int actorId = exchange.getAttribute("userId") instanceof Number n ? n.intValue() : -1;
            if (actorId == userId && target.getRoleId() != roleId) {
                ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error("Không thể tự thay đổi quyền của tài khoản đang đăng nhập"));
                return;
            }
            int adminRoleId = roles.stream().filter(role -> "admin".equalsIgnoreCase(role.getName()))
                    .mapToInt(quanlykhachsan.backend.user.Role::getId).findFirst().orElse(-1);
            if (target.getRoleId() == adminRoleId && roleId != adminRoleId) {
                long activeAdmins = users.stream()
                        .filter(user -> user.getRoleId() == adminRoleId && isActive(user.getStatus())).count();
                if (activeAdmins <= 1) {
                    ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error("Cần giữ lại ít nhất một quản trị viên đang hoạt động"));
                    return;
                }
            }
            int customerRoleId = roles.stream().filter(role -> "customer".equalsIgnoreCase(role.getName()))
                    .mapToInt(quanlykhachsan.backend.user.Role::getId).findFirst().orElse(-1);
            if (roleId == customerRoleId && target.getCustomerId() == null) {
                ApiResponseUtil.write(exchange, 409, ApiResponseUtil.error("Tài khoản này chưa có hồ sơ khách hàng để gán vai trò khách hàng"));
                return;
            }
            if (target.getRoleId() == roleId || userDAO.updateRole(userId, roleId)) {
                System.out.printf("[ACCESS_CONTROL] admin=%d changed user=%d role=%d->%d%n",
                        actorId, userId, target.getRoleId(), roleId);
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.success("Cập nhật quyền truy cập thành công"));
            } else {
                ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Tài khoản đã thay đổi hoặc không còn tồn tại"));
            }
        } catch (com.google.gson.JsonParseException | IllegalArgumentException ex) {
            ApiResponseUtil.write(exchange, 400, ApiResponseUtil.error("Thông tin vai trò không hợp lệ"));
        } catch (Exception ex) {
            ApiResponseUtil.write(exchange, 503, ApiResponseUtil.error("Chưa thể cập nhật quyền truy cập"));
        }
    }

    private static boolean isActive(String status) {
        return status == null || status.isBlank()
                || !java.util.Set.of("inactive", "disabled", "locked", "suspended", "deleted")
                        .contains(status.toLowerCase(java.util.Locale.ROOT));
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
                c.setIdentityCard(null);
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
                ? reqObj.get("identityCard").getAsString() : null);
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
