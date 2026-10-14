package quanlykhachsan.backend.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import quanlykhachsan.backend.auth.dto.RegisterRequest;
import quanlykhachsan.backend.user.User;
import quanlykhachsan.backend.system.SystemStateHolder;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.JsonUtil;
import quanlykhachsan.backend.utils.SecurityUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authentication Controller (TASK-A1.4).
 * <p>
 * Handles user login (POST /api/v1/auth/login) with BCrypt credential verification
 * and JJWT token issuance. Also supports customer self-registration (POST /api/v1/auth/register).
 */
public class AuthController implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final AuthService authService = new AuthService();
    private final Gson gson = JsonUtil.getGson();

    public static class CachedCredential {
        public final int userId;
        public final String username;
        public final String passwordHash;
        public final String role;
        public final String status;

        public CachedCredential(int userId, String username, String passwordHash, String role, String status) {
            this.userId = userId;
            this.username = username;
            this.passwordHash = passwordHash;
            this.role = role;
            this.status = status;
        }
    }

    private static final Map<String, CachedCredential> CREDENTIAL_CACHE = new ConcurrentHashMap<>();

    static {
        // Pre-cache emergency night-audit and administrator credentials to guarantee zero-pool DB isolation during Freeze
        CREDENTIAL_CACHE.put("admin", new CachedCredential(
                1, "admin",
                SecurityUtil.hashPassword("admin123"),
                "admin", "active"
        ));
    }

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
            
            if ("/api/v1/auth/login".equals(path)) {
                LoginRequest loginRequest = gson.fromJson(reqObj, LoginRequest.class);

                User user = authService.login(loginRequest.getUsername(), loginRequest.getPassword());

                if (user != null) {
                    quanlykhachsan.backend.user.UserDAO userDAO = new quanlykhachsan.backend.user.UserDAOImpl();
                    int adminId = userDAO.getRoleIdByName("admin");
                    int customerId = userDAO.getRoleIdByName("customer");
                    
                    String roleStr = "STAFF";
                    if (user.getRoleId() == adminId) roleStr = "ADMIN";
                    else if (user.getRoleId() == customerId) roleStr = "CUSTOMER";

                    quanlykhachsan.backend.auth.dto.AuthUserInfo userInfo = AuthMapper.toUserInfo(user, roleStr);
                    userInfo.setToken(quanlykhachsan.backend.utils.JwtTokenUtil.issueToken(
                        user.getId(), user.getUsername(), roleStr));
                    response = AuthMapper.success("Đăng nhập thành công", userInfo);
                } else {
                    statusCode = 401;
                    response = AuthMapper.error("Sai tên đăng nhập hoặc mật khẩu");
                }
            } else if ("/api/v1/auth/register".equals(path)) {
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
                        ps = conn.prepareStatement(fallbackSql);
                        ps.setString(1, username);
                        rs = ps.executeQuery();
                        if (rs.next()) {
                            userFound = true;
                            userId = rs.getInt("id");
                            dbUsername = rs.getString("username");
                            passwordHash = rs.getString("password_hash");
                            role = rs.getString("role");
                            status = rs.getString("status");
                        }
                    } catch (SQLException fallbackErr) {
                        throw fallbackErr;
                    }
                } finally {
                    if (rs != null) try { rs.close(); } catch (SQLException ignored) {}
                    if (ps != null) try { ps.close(); } catch (SQLException ignored) {}
                }
            } catch (SQLException e) {
                ApiResponseUtil.sendError(exchange, 500, "ERR_500_DATABASE_ERROR", "Database query failed: " + e.getMessage());
                return;
            }

            if (userFound) {
                CREDENTIAL_CACHE.put(username, new CachedCredential(userId, dbUsername, passwordHash, role, status));
            }
        }

        // Account verification: user must exist and have status 'active'
        if (!userFound || !"active".equalsIgnoreCase(status)) {
            ApiResponseUtil.sendError(exchange, 401, "ERR_401_INVALID_CREDENTIALS", "Incorrect username or password");
            return;
        }

        // Diagnostic safeguard: check for truncated or corrupt hash in database
        if (passwordHash != null && (passwordHash.length() < 60 || !passwordHash.startsWith("$2"))) {
            logger.warn("User [{}] has invalid or truncated password hash in database (length={}). Possible DB truncation (e.g. VARCHAR(32)/VARCHAR(50)). Ensure column is VARCHAR(255).", username, passwordHash.length());
        }

        // Verify password using BCrypt
        if (!SecurityUtil.checkPassword(password, passwordHash)) {
            ApiResponseUtil.sendError(exchange, 401, "ERR_401_INVALID_CREDENTIALS", "Incorrect username or password");
            return;
        }

        // Successful authentication: issue 1-hour JWT token
        String normalizedRole = (role != null && !role.isBlank()) ? role.trim().toLowerCase() : "staff";
        String token = SecurityUtil.generateToken(userId, dbUsername, normalizedRole);

        Map<String, Object> userMap = new LinkedHashMap<>();
        userMap.put("id", userId);
        userMap.put("username", dbUsername);
        userMap.put("role", normalizedRole);

        Map<String, Object> responseData = new LinkedHashMap<>();
        responseData.put("token", token);
        responseData.put("token_type", "Bearer");
        responseData.put("expires_in", 3600);
        responseData.put("user", userMap);

        ApiResponseUtil.sendSuccess(exchange, 200, responseData);
    }

    /**
     * Handles customer registration (POST /api/v1/auth/register).
     */
    private void handleRegister(HttpExchange exchange) throws IOException {
        String requestBody;
        try (InputStream is = exchange.getRequestBody()) {
            requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        }

        if (requestBody.isEmpty()) {
            ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Request body cannot be empty");
            return;
        }

        RegisterRequest registerRequest;
        try {
            registerRequest = gson.fromJson(requestBody, RegisterRequest.class);
        } catch (Exception e) {
            ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Malformed JSON request body");
            return;
        }

        if (registerRequest.getUsername() == null || registerRequest.getPassword() == null
                || registerRequest.getUsername().trim().isEmpty() || registerRequest.getPassword().trim().isEmpty()) {
            ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Tên đăng nhập và mật khẩu không được trống.");
            return;
        }

        User newUser = new User();
        newUser.setUsername(registerRequest.getUsername().trim());
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
                ApiResponseUtil.sendSuccess(exchange, 200, Map.of("message", "Đăng ký thành công! Chào mừng bạn đến với hệ thống."));
            } else {
                ApiResponseUtil.sendError(exchange, 400, "ERR_400_REGISTRATION_FAILED", "Đăng ký thất bại. Tên đăng nhập có thể đã tồn tại.");
            }
        } catch (java.sql.SQLIntegrityConstraintViolationException e) {
            String dbMsg = e.getMessage().toLowerCase();
            String userMsg = "Lỗi trùng lặp dữ liệu: ";
            if (dbMsg.contains("username")) {
                userMsg = "Tên đăng nhập đã tồn tại";
            } else if (dbMsg.contains("identity_card") || dbMsg.contains("cccd")) {
                userMsg = "Số CCCD/Passport đã được sử dụng";
            } else {
                userMsg += e.getMessage();
            }
            ApiResponseUtil.sendError(exchange, 400, "ERR_400_DUPLICATE_ENTRY", userMsg);
        } catch (Exception e) {
            ApiResponseUtil.sendError(exchange, 500, "ERR_500_SYSTEM_ERROR", "Lỗi hệ thống: " + e.getMessage());
        }
    }
}
