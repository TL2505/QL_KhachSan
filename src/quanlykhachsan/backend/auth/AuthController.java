package quanlykhachsan.backend.auth;

import quanlykhachsan.backend.user.User;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.auth.dto.LoginRequest;
import quanlykhachsan.backend.auth.dto.RegisterRequest;
import quanlykhachsan.backend.auth.dto.AuthResponse;

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
            Gson gson = JsonUtil.getGson();
            JsonObject reqObj = gson.fromJson(requestBody, JsonObject.class);
            AuthResponse response = new AuthResponse();
            int statusCode = 200;
            
            if ("/api/v1/auth/login".equals(path) || path.endsWith("/auth/login")) {
                LoginRequest loginRequest = gson.fromJson(reqObj, LoginRequest.class);

                User user = authService.login(loginRequest.getUsername(), loginRequest.getPassword());

                if (user != null) {
                    quanlykhachsan.backend.user.UserDAO userDAO = new quanlykhachsan.backend.user.UserDAOImpl();
                    int adminId = userDAO.getRoleIdByName("admin");
                    int customerId = userDAO.getRoleIdByName("customer");
                    
                    String roleStr = "STAFF";
                    if (user.getRoleId() == adminId) roleStr = "ADMIN";
                    else if (user.getRoleId() == customerId) roleStr = "CUSTOMER";
                    else {
                        roleStr = null;
                        for(var role:userDAO.selectAllRoles())if(role.getId()==user.getRoleId())roleStr=role.getName();
                        if(roleStr==null || !java.util.Set.of("staff","front_desk","housekeeping","maintenance","f&b","manager","gm","revenue_manager").contains(roleStr.toLowerCase(java.util.Locale.ROOT))) {
                            ApiResponseUtil.write(exchange,403,ApiResponseUtil.error("Role is not authorized"));return;
                        }
                    }

                    quanlykhachsan.backend.auth.dto.AuthUserInfo userInfo = AuthMapper.toUserInfo(user, roleStr);
                    try { LoginDeviceService.recordLogin(exchange, user.getId()); }
                    catch (java.sql.SQLException ex) {
                        org.slf4j.LoggerFactory.getLogger(AuthController.class).error(
                            "[LOGIN DEVICE_FAILURE] SQLState={} code={}", ex.getSQLState(), ex.getErrorCode());
                        ApiResponseUtil.write(exchange,503,ApiResponseUtil.error("Chưa thể ghi nhận thiết bị đăng nhập. Vui lòng thử lại.")); return;
                    }
                    String token = "CUSTOMER".equals(roleStr) && user.getCustomerId() != null
                        ? quanlykhachsan.backend.utils.JwtTokenUtil.issueToken(
                            user.getId(), user.getUsername(), roleStr, user.getPhone(), user.getCustomerId())
                        : quanlykhachsan.backend.utils.JwtTokenUtil.issueToken(
                            user.getId(), user.getUsername(), roleStr);
                    userInfo.setToken(token);
                    response = AuthMapper.success("Đăng nhập thành công", userInfo);
                } else {
                    statusCode = 401;
                    org.slf4j.LoggerFactory.getLogger(AuthController.class).info("[LOGIN REJECTED] Credentials did not match");
                    response = AuthMapper.error("Sai tên đăng nhập hoặc mật khẩu");
                }
            } else if ("/api/v1/auth/register".equals(path) || path.endsWith("/auth/register")) {
                RegisterRequest registerRequest = gson.fromJson(reqObj, RegisterRequest.class);
                
                if (registerRequest.getUsername() == null || registerRequest.getUsername().isBlank()) {
                    statusCode = 400;
                    response = AuthMapper.fieldError("Vui lòng kiểm tra thông tin đăng ký.", "email", "Vui lòng nhập email hoặc tên đăng nhập.");
                } else if (registerRequest.getFullName() == null || registerRequest.getFullName().isBlank()) {
                    statusCode = 400;
                    response = AuthMapper.fieldError("Vui lòng kiểm tra thông tin đăng ký.", "fullName", "Vui lòng nhập họ và tên.");
                } else if (registerRequest.getPassword() == null || registerRequest.getPassword().isEmpty()) {
                    statusCode = 400;
                    response = AuthMapper.fieldError("Vui lòng kiểm tra thông tin đăng ký.", "password", "Vui lòng nhập mật khẩu.");
                } else {
                    User newUser = new User();
                    newUser.setUsername(registerRequest.getUsername());
                    newUser.setPassword(registerRequest.getPassword());
                    newUser.setFullName(registerRequest.getFullName() != null ? registerRequest.getFullName() : "Khách hàng mới");
                    newUser.setEmail(registerRequest.getEmail() != null ? registerRequest.getEmail() : "");
                    newUser.setPhone(registerRequest.getPhone() != null ? registerRequest.getPhone() : "");
                    
                    quanlykhachsan.backend.customer.Customer customer = new quanlykhachsan.backend.customer.Customer();
                    customer.setFullName(newUser.getFullName());
                    customer.setEmail(newUser.getEmail());
                    customer.setPhone(newUser.getPhone());
                    
                    customer.setIdentityCard(registerRequest.getIdentityCard() == null || registerRequest.getIdentityCard().isBlank()
                        ? null : registerRequest.getIdentityCard().trim());
                    customer.setAddress(registerRequest.getAddress() != null ? registerRequest.getAddress() : "");

                    try {
                        boolean success = authService.registerCustomer(newUser, customer);
                        if (success) {
                            response = AuthMapper.success("Đăng ký thành công! Chào mừng bạn đến với hệ thống.", null);
                        } else {
                            statusCode = 400;
                            response = AuthMapper.error("Đăng ký thất bại.");
                        }
                    } catch (IllegalStateException e) {
                        statusCode = 409;
                        String message = e.getMessage();
                        String lower = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
                        if (lower.contains("tên đăng nhập") || lower.contains("username")) {
                            response = AuthMapper.fieldError(message, "email", message);
                        } else if (lower.contains("cccd") || lower.contains("hộ chiếu")) {
                            response = AuthMapper.fieldError(message, "identityCard", message);
                        } else response = AuthMapper.error(message);
                    } catch (java.sql.SQLException e) {
                        org.slf4j.LoggerFactory.getLogger(AuthController.class).warn(
                            "[REGISTER FAILED] SQLState={} vendorCode={}", e.getSQLState(), e.getErrorCode());
                        statusCode = 409;
                        String dbMsg = String.valueOf(e.getMessage()).toLowerCase(java.util.Locale.ROOT);
                        String userMsg = "Không thể tạo tài khoản. Vui lòng kiểm tra lại thông tin.";
                        if (dbMsg.contains("username")) {
                            userMsg = "Tên đăng nhập đã tồn tại.";
                            response = AuthMapper.fieldError(userMsg, "email", userMsg);
                        } else if (dbMsg.contains("identity_hash") || dbMsg.contains("identity_card") || dbMsg.contains("cccd")) {
                            userMsg = "Số CCCD/Hộ chiếu đã được sử dụng.";
                            response = AuthMapper.fieldError(userMsg, "identityCard", userMsg);
                        } else response = AuthMapper.error(userMsg);
                    } catch (Exception e) {
                        org.slf4j.LoggerFactory.getLogger(AuthController.class).warn(
                            "[REGISTER FAILED] errorType={}", e.getClass().getSimpleName());
                        statusCode = 500;
                        response = AuthMapper.error("Lỗi hệ thống: " + e.getMessage());
                    }
                }
            } else if (path.endsWith("/auth/google") || path.endsWith("/auth/facebook")) {
                statusCode = 410;
                response = AuthMapper.error("Luồng đăng nhập này đã ngừng hỗ trợ. Vui lòng dùng Google kèm OTP hoặc đăng nhập bằng mật khẩu.");
            } else {
                statusCode = 404;
                response = AuthMapper.error("Đường dẫn không tồn tại");
            }
            
            String responseStr = gson.toJson(response);

            // 4. Gửi Response về cho Frontend
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            
            // Chống lỗi CORS khi Frontend gọi API từ nguồn khác
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", quanlykhachsan.backend.utils.CorsFilter.resolveOrigin(exchange));
            
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
