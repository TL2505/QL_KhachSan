package quanlykhachsan.backend.controller;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import quanlykhachsan.backend.daoimpl.UserDAOImpl;
import quanlykhachsan.backend.model.User;
import quanlykhachsan.backend.utils.JsonUtil;

public class GoogleAuthController implements HttpHandler {

    private static final Map<String, User> pendingLogins = new ConcurrentHashMap<>();
    private String clientId;
    private String clientSecret;
    private String redirectUri;

    public GoogleAuthController() {
        loadConfig();
    }

    private void loadConfig() {
        // Ưu tiên đọc từ Biến môi trường (Environment Variables)
        this.clientId = System.getenv("GOOGLE_CLIENT_ID");
        this.clientSecret = System.getenv("GOOGLE_CLIENT_SECRET");
        this.redirectUri = System.getenv("GOOGLE_REDIRECT_URI");

        // Nếu biến môi trường không có, mới đọc từ file config.properties (Dành cho Development)
        if (this.clientId == null || this.clientSecret == null) {
            try {
                Properties props = new Properties();
                java.io.File configFile = new java.io.File("config.properties");
                if (configFile.exists()) {
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(configFile)) {
                        props.load(fis);
                        if (this.clientId == null) this.clientId = props.getProperty("google.client_id");
                        if (this.clientSecret == null) this.clientSecret = props.getProperty("google.client_secret");
                        if (this.redirectUri == null) this.redirectUri = props.getProperty("google.redirect_uri");
                    }
                }
            } catch (Exception e) {
                System.err.println("[GoogleAuth] Không thể tải cấu hình từ file: " + e.getMessage());
            }
        }
        
        // Log thông báo trạng thái cấu hình (không log secret)
        if (this.clientId != null) {
            System.out.println("✅ Google Auth: Cấu hình Client ID đã sẵn sàng.");
        } else {
            System.err.println("⚠️ Google Auth: CHƯA cấu hình Client ID. Vui lòng kiểm tra biến môi trường hoặc file config.");
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.endsWith("/login")) {
            handleLogin(exchange);
        } else if (path.endsWith("/callback")) {
            handleCallback(exchange);
        } else if (path.endsWith("/status")) {
            handleStatus(exchange);
        } else {
            sendResponse(exchange, 404, "Endpoint not found");
        }
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String sid = params.get("sid");
        if (sid == null) {
            sendResponse(exchange, 400, "Missing sid parameter");
            return;
        }

        String authUrl = "https://accounts.google.com/o/oauth2/v2/auth?"
                + "client_id=" + clientId
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8")
                + "&response_type=code"
                + "&scope=" + URLEncoder.encode("openid email profile", "UTF-8")
                + "&state=" + sid;

        exchange.getResponseHeaders().set("Location", authUrl);
        exchange.sendResponseHeaders(302, -1);
    }

    private void handleCallback(HttpExchange exchange) throws IOException {
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String code = params.get("code");
        String sid = params.get("state");

        if (code == null || sid == null) {
            sendResponse(exchange, 400, "Invalid callback data");
            return;
        }

        try {
            // 1. Exchange code for token
            String tokenResponse = exchangeCodeForToken(code);
            JsonObject tokenJson = JsonUtil.getGson().fromJson(tokenResponse, JsonObject.class);
            String accessToken = tokenJson.get("access_token").getAsString();

            // 2. Get user info
            String userInfoResponse = getUserInfo(accessToken);
            JsonObject userInfo = JsonUtil.getGson().fromJson(userInfoResponse, JsonObject.class);
            String email = userInfo.get("email").getAsString();

            // 3. Find user in DB
            UserDAOImpl userDAO = new UserDAOImpl();
            User user = userDAO.findByEmail(email);

            if (user != null) {
                pendingLogins.put(sid, user);
                sendHtmlResponse(exchange, 200, "<h1>Đăng nhập thành công!</h1><p>Bạn có thể đóng trình duyệt này và quay lại ứng dụng.</p>");
            } else {
                sendHtmlResponse(exchange, 403, "<h1>Lỗi!</h1><p>Email " + email + " không có quyền truy cập hệ thống.</p>");
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String sid = params.get("sid");

        JsonObject response = new JsonObject();
        if (sid != null && pendingLogins.containsKey(sid)) {
            User user = pendingLogins.remove(sid);
            response.addProperty("status", "success");
            JsonObject data = new JsonObject();
            data.addProperty("userId", user.getId());
            data.addProperty("username", user.getUsername());
            data.addProperty("role", user.getRoleId() == 1 ? "ADMIN" : "STAFF");
            data.addProperty("fullName", user.getFullName());
            data.addProperty("email", user.getEmail());
            response.add("data", data);
        } else {
            response.addProperty("status", "pending");
        }

        sendResponse(exchange, 200, JsonUtil.getGson().toJson(response));
    }

    private String exchangeCodeForToken(String code) throws Exception {
        URL url = new URL("https://oauth2.googleapis.com/token");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        String body = "code=" + code
                + "&client_id=" + clientId
                + "&client_secret=" + clientSecret
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8")
                + "&grant_type=authorization_code";

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        return readResponse(conn);
    }

    private String getUserInfo(String accessToken) throws Exception {
        URL url = new URL("https://www.googleapis.com/oauth2/v3/userinfo");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);

        return readResponse(conn);
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        BufferedReader br = new BufferedReader(new InputStreamReader(
                (code >= 200 && code <= 299) ? conn.getInputStream() : conn.getErrorStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null) return result;
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                try {
                    result.put(entry[0], URLDecoder.decode(entry[1], "UTF-8"));
                } catch (Exception e) {}
            }
        }
        return result;
    }

    private void sendResponse(HttpExchange exchange, int code, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendHtmlResponse(HttpExchange exchange, int code, String html) throws IOException {
        String fullHtml = "<html><body style='font-family:sans-serif; text-align:center; padding-top:50px;'>" + html + "</body></html>";
        byte[] bytes = fullHtml.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
