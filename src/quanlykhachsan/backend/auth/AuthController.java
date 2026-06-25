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
            JsonObject resObj = new JsonObject();
            int statusCode = 200;
            
            if ("/api/auth/login".equals(path)) {
                String username = reqObj.has("username") ? reqObj.get("username").getAsString() : null;
                String password = reqObj.has("password") ? reqObj.get("password").getAsString() : null;

                // 2. Xử lý logic gọi xuống tầng Service
                User user = authService.login(username, password);

                // 3. Chuẩn bị dữ liệu trả về (Response JSON)
                if (user != null) {
                    quanlykhachsan.backend.user.UserDAO userDAO = new quanlykhachsan.backend.user.UserDAOImpl();
                    int adminId = userDAO.getRoleIdByName("admin");
                    int customerId = userDAO.getRoleIdByName("customer");
                    
                    String roleStr = "STAFF"; // Mặc định
                    if (user.getRoleId() == adminId) roleStr = "ADMIN";
                    else if (user.getRoleId() == customerId) roleStr = "CUSTOMER";
                    
                    resObj.addProperty("status", "success");
                    resObj.addProperty("message", "Đăng nhập thành công");
                    
                    JsonObject dataObj = new JsonObject();
                    dataObj.addProperty("userId", user.getId());
                    dataObj.addProperty("username", user.getUsername());
                    dataObj.addProperty("role", roleStr);
                    dataObj.addProperty("fullName", user.getFullName() != null ? user.getFullName() : "");
                    dataObj.addProperty("email", user.getEmail() != null ? user.getEmail() : "");
                    dataObj.addProperty("phone", user.getPhone() != null ? user.getPhone() : "");
                    dataObj.addProperty("customerId", user.getCustomerId());
                    
                    resObj.add("data", dataObj);
                } else {
                    statusCode = 401; // Unauthorized
                    resObj.addProperty("status", "error");
                    resObj.addProperty("message", "Sai tên đăng nhập hoặc mật khẩu");
                    resObj.add("data", null);
                }
            } else if ("/api/auth/register".equals(path)) {
                // Đăng ký công khai mặc định là Khách hàng (Role 3)
                String username = reqObj.has("username") ? reqObj.get("username").getAsString() : null;
                String password = reqObj.has("password") ? reqObj.get("password").getAsString() : null;
                
                if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
                    statusCode = 400;
                    resObj.addProperty("status", "error");
                    resObj.addProperty("message", "Tên đăng nhập và mật khẩu không được trống.");
                } else {
                    User newUser = new User();
                    newUser.setUsername(username);
                    newUser.setPassword(password);
                    newUser.setFullName(reqObj.has("fullName") ? reqObj.get("fullName").getAsString() : "Khách hàng mới");
                    newUser.setEmail(reqObj.has("email") ? reqObj.get("email").getAsString() : "");
                    newUser.setPhone(reqObj.has("phone") ? reqObj.get("phone").getAsString() : "");
                    
                    quanlykhachsan.backend.customer.Customer customer = new quanlykhachsan.backend.customer.Customer();
                    customer.setFullName(newUser.getFullName());
                    customer.setEmail(newUser.getEmail());
                    customer.setPhone(newUser.getPhone());
                    
                    String idCard = (reqObj.has("identityCard") && !reqObj.get("identityCard").getAsString().trim().isEmpty()) 
                                    ? reqObj.get("identityCard").getAsString().trim() 
                                    : "TEMP-" + System.currentTimeMillis();
                    customer.setIdentityCard(idCard);
                    customer.setAddress(reqObj.has("address") ? reqObj.get("address").getAsString() : "");

                    try {
                        boolean success = authService.registerCustomer(newUser, customer);
                        if (success) {
                            resObj.addProperty("status", "success");
                            resObj.addProperty("message", "Đăng ký thành công! Chào mừng bạn đến với hệ thống.");
                        } else {
                            statusCode = 400;
                            resObj.addProperty("status", "error");
                            resObj.addProperty("message", "Đăng ký thất bại.");
                        }
                    } catch (java.sql.SQLIntegrityConstraintViolationException e) {
                        statusCode = 400;
                        resObj.addProperty("status", "error");
                        String dbMsg = e.getMessage().toLowerCase();
                        String userMsg = "Lỗi trùng lặp dữ liệu: ";
                        if (dbMsg.contains("username")) userMsg = "Tên đăng nhập đã tồn tại";
                        else if (dbMsg.contains("identity_card") || dbMsg.contains("cccd")) userMsg = "Số CCCD/Passport đã được sử dụng";
                        else userMsg += e.getMessage(); // Hiển thị lỗi SQL thực tế để debug
                        
                        resObj.addProperty("message", userMsg);
                    } catch (Exception e) {
                        statusCode = 500;
                        resObj.addProperty("status", "error");
                        resObj.addProperty("message", "Lỗi hệ thống: " + e.getMessage());
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
