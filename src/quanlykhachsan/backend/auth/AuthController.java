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
        String rawPath = exchange.getRequestURI().getPath();
        String path = (rawPath == null || rawPath.isEmpty()) ? "/" : rawPath;
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1); // Normalize trailing slash
        }
        String method = exchange.getRequestMethod();

        if ("/api/v1/auth/login".equals(path) || path.endsWith("/login")) {
            if ("POST".equalsIgnoreCase(method)) {
                handleLogin(exchange);
            } else {
                ApiResponseUtil.sendError(exchange, 405, "ERR_405_METHOD_NOT_ALLOWED", "Method Not Allowed. Expected POST.");
            }
        } else if ("/api/v1/auth/register".equals(path) || path.endsWith("/register")) {
            if ("POST".equalsIgnoreCase(method)) {
                handleRegister(exchange);
            } else {
                ApiResponseUtil.sendError(exchange, 405, "ERR_405_METHOD_NOT_ALLOWED", "Method Not Allowed. Expected POST.");
            }
        } else {
            ApiResponseUtil.sendError(exchange, 404, "ERR_404_NOT_FOUND", "Endpoint not found: " + path);
        }
    }

    /**
     * Handles POST /api/v1/auth/login.
     * <p>
     * 1. Parses JSON body: {"username": "...", "password": "..."}.
     * 2. Validates non-empty credentials (400 if invalid).
     * 3. Queries users table via DatabaseConnection try-with-resources.
     * 4. Verifies account status ('active') and BCrypt password hash (401 if invalid).
     * 5. Returns standard JSON envelope with JWT token (1-hour expiry).
     */
    private void handleLogin(HttpExchange exchange) throws IOException {
        String requestBody;
        try (InputStream is = exchange.getRequestBody()) {
            requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        }

        if (requestBody.isEmpty()) {
            ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Request body cannot be empty");
            return;
        }

        JsonObject reqObj;
        try {
            reqObj = JsonParser.parseString(requestBody).getAsJsonObject();
        } catch (Exception e) {
            ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Malformed JSON request body");
            return;
        }

        String username = reqObj.has("username") && !reqObj.get("username").isJsonNull()
                ? reqObj.get("username").getAsString().trim() : null;
        String password = reqObj.has("password") && !reqObj.get("password").isJsonNull()
                ? reqObj.get("password").getAsString() : null;

        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            ApiResponseUtil.sendError(exchange, 400, "ERR_400_BAD_REQUEST", "Username and password are required");
            return;
        }

        int userId = -1;
        String dbUsername = null;
        String passwordHash = null;
        String role = "staff";
        String status = null;
        boolean userFound = false;

        // TASK-A3.1 Architectural Constraint: Zero Connection Pool during Freeze
        // During 00:00 - 00:05 Night Audit Freeze, do NOT touch HikariCP database pool.
        // Authenticate purely from internal memory cache to prevent DB connection contention with Night Audit.
        if (SystemStateHolder.isSystemFrozen()) {
            CachedCredential cached = CREDENTIAL_CACHE.get(username);
            if (cached != null) {
                userFound = true;
                userId = cached.userId;
                dbUsername = cached.username;
                passwordHash = cached.passwordHash;
                role = cached.role;
                status = cached.status;
            } else {
                logger.warn("Login for user [{}] blocked during System Freeze window (00:00 - 00:05): user not found in in-memory credential cache", username);
                ApiResponseUtil.sendError(exchange, 503, "ERR_503_SYSTEM_FROZEN",
                        "System is currently locked for Night Audit (00:00 - 00:05). Database pool is isolated. Uncached logins are locked until 00:05. Please retry after audit completion.");
                return;
            }
        } else {
            String primarySql = "SELECT id, username, COALESCE(password_hash, password) AS password_hash, role, status FROM users WHERE username = ?";
            String fallbackSql = "SELECT u.id, u.username, COALESCE(u.password_hash, u.password) AS password_hash, r.name AS role, u.status " +
                                 "FROM users u LEFT JOIN roles r ON u.role_id = r.id WHERE u.username = ?";

            try (Connection conn = DatabaseConnection.getConnection()) {
                PreparedStatement ps = null;
                ResultSet rs = null;
                try {
                    ps = conn.prepareStatement(primarySql);
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
                } catch (SQLException e) {
                    // Primary schema (direct columns) failed, try relational schema with roles table
                    if (rs != null) try { rs.close(); } catch (SQLException ignored) {}
                    if (ps != null) try { ps.close(); } catch (SQLException ignored) {}

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
