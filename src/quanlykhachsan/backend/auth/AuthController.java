package quanlykhachsan.backend.auth;

import quanlykhachsan.backend.user.User;
import quanlykhachsan.backend.auth.AuthService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.auth.dto.LoginRequest;
import quanlykhachsan.backend.auth.dto.RegisterRequest;
import quanlykhachsan.backend.auth.dto.AuthResponse;
import quanlykhachsan.backend.auth.dto.AuthUserInfo;

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
            
            if ("/api/auth/login".equals(path)) {
                LoginRequest loginRequest = gson.fromJson(reqObj, LoginRequest.class);

                User user = authService.login(loginRequest.getUsername(), loginRequest.getPassword());

                if (user != null) {
                    quanlykhachsan.backend.user.UserDAO userDAO = new quanlykhachsan.backend.user.UserDAOImpl();
                    int adminId = userDAO.getRoleIdByName("admin");
                    int customerId = userDAO.getRoleIdByName("customer");
                    
                    String roleStr = "STAFF";
                    if (user.getRoleId() == adminId) roleStr = "ADMIN";
                    else if (user.getRoleId() == customerId) roleStr = "CUSTOMER";

                    response = AuthMapper.success("Đăng nhập thành công", AuthMapper.toUserInfo(user, roleStr));
                } else {
                    statusCode = 401;
                    response = AuthMapper.error("Sai tên đăng nhập hoặc mật khẩu");
                }
            } else if ("/api/auth/register".equals(path)) {
                RegisterRequest registerRequest = gson.fromJson(reqObj, RegisterRequest.class);
                
                if (registerRequest.getUsername() == null || registerRequest.getPassword() == null || registerRequest.getUsername().isEmpty() || registerRequest.getPassword().isEmpty()) {
                    statusCode = 400;
                    response = AuthMapper.error("Tên đăng nhập và mật khẩu không được trống.");
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
                    
                    String idCard = (registerRequest.getIdentityCard() != null && !registerRequest.getIdentityCard().trim().isEmpty())
                                    ? registerRequest.getIdentityCard().trim()
                                    : "TEMP-" + System.currentTimeMillis();
                    customer.setIdentityCard(idCard);
                    customer.setAddress(registerRequest.getAddress() != null ? registerRequest.getAddress() : "");

                    try {
                        boolean success = authService.registerCustomer(newUser, customer);
                        if (success) {
                            response = AuthMapper.success("Đăng ký thành công! Chào mừng bạn đến với hệ thống.", null);
                        } else {
                            statusCode = 400;
                            response = AuthMapper.error("Đăng ký thất bại.");
                        }
                    } catch (java.sql.SQLIntegrityConstraintViolationException e) {
                        statusCode = 400;
                        String dbMsg = e.getMessage().toLowerCase();
                        String userMsg = "Lỗi trùng lặp dữ liệu: ";
                        if (dbMsg.contains("username")) userMsg = "Tên đăng nhập đã tồn tại";
                        else if (dbMsg.contains("identity_card") || dbMsg.contains("cccd")) userMsg = "Số CCCD/Passport đã được sử dụng";
                        else userMsg += e.getMessage();
                        response = AuthMapper.error(userMsg);
                    } catch (Exception e) {
                        statusCode = 500;
                        response = AuthMapper.error("Lỗi hệ thống: " + e.getMessage());
                    }
                }
            } else {
                statusCode = 404;
                response = AuthMapper.error("Đường dẫn không tồn tại");
            }
            
            String responseStr = gson.toJson(response);

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
