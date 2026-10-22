package quanlykhachsan.backend.auth;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import quanlykhachsan.backend.utils.ApiResponseUtil;
import quanlykhachsan.backend.utils.JsonUtil;

/** Public endpoints for the Google Identity + OTP login flow. */
public final class GoogleOtpAuthController implements HttpHandler {
    private static final String DEVICE_COOKIE = "aurelia_google_device";
    private final GoogleOtpAuthService service;
    private final LoginRecorder recorder;
    @FunctionalInterface interface LoginRecorder { void record(HttpExchange exchange,int userId) throws Exception; }
    public GoogleOtpAuthController() { this(new GoogleOtpAuthService(), LoginDeviceService::recordLogin); }
    GoogleOtpAuthController(GoogleOtpAuthService service) { this(service,(exchange,userId)->{}); }
    GoogleOtpAuthController(GoogleOtpAuthService service,LoginRecorder recorder) { this.service = service; this.recorder=recorder; }
    @Override public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        try {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && path.endsWith("/config")) {
                JsonObject config = new JsonObject(); config.addProperty("enabled", service.isConfigured()); if (service.isConfigured()) config.addProperty("client_id", service.clientId());
                ApiResponseUtil.write(exchange, 200, ApiResponseUtil.successWithData(config)); return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { ApiResponseUtil.write(exchange, 405, ApiResponseUtil.error("Phương thức không được hỗ trợ.")); return; }
            JsonObject body = JsonUtil.getGson().fromJson(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
            GoogleOtpAuthService.StartResult result;
            if (path.endsWith("/request-otp")) {
                var start = service.begin(value(body, "credential"), deviceCookie(exchange));
                if (start.user() != null) {
                    recorder.record(exchange,start.user().getUserId());
                    ApiResponseUtil.write(exchange, 200, JsonUtil.getGson().toJson(AuthMapper.success("Đăng nhập Google thành công trên thiết bị đã xác thực.", start.user())));
                    return;
                }
                result = start.challenge();
            }
            else if (path.endsWith("/resend-otp")) result = service.resend(value(body, "challenge_id"));
            else if (path.endsWith("/verify-otp")) {
                var verified = service.verifyWithDevice(value(body, "challenge_id"), value(body, "otp"));
                recorder.record(exchange,verified.user().getUserId());
                if (verified.deviceToken() != null) {
                    boolean secure = exchange instanceof com.sun.net.httpserver.HttpsExchange
                        || "https".equalsIgnoreCase(exchange.getRequestHeaders().getFirst("X-Forwarded-Proto"));
                    exchange.getResponseHeaders().add("Set-Cookie", DEVICE_COOKIE + "=" + verified.deviceToken()
                        + "; Path=/api/v1/auth/google; Max-Age=" + GoogleDeviceTrust.MAX_AGE_SECONDS
                        + "; HttpOnly; SameSite=Strict" + (secure ? "; Secure" : ""));
                }
                ApiResponseUtil.write(exchange, 200, JsonUtil.getGson().toJson(AuthMapper.success("Đăng nhập Google thành công.", verified.user())));
                return;
            }
            else if (path.equals("/api/v1/auth/google") || path.endsWith("/auth/google")) {
                ApiResponseUtil.write(exchange, 410, ApiResponseUtil.error("Vui lòng dùng nút Google và xác nhận mã OTP để đăng nhập.")); return;
            }
            else { ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Đường dẫn không tồn tại.")); return; }
            JsonObject data = new JsonObject(); data.addProperty("challenge_id", result.challengeId()); data.addProperty("email", result.maskedEmail()); data.addProperty("expires_at", result.expiresAt().toString());
            ApiResponseUtil.write(exchange, 200, ApiResponseUtil.success("Mã xác thực đã được gửi tới email của bạn.", data));
        } catch (GoogleOtpAuthService.AuthFailure e) { ApiResponseUtil.write(exchange, e.status, ApiResponseUtil.error(e.getMessage())); }
        catch (Exception e) { ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Không thể xử lý đăng nhập Google. Vui lòng thử lại.")); }
    }
    private static String value(JsonObject body, String key) { return body != null && body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsString() : ""; }
    private static String deviceCookie(HttpExchange exchange) {
        for (String header : exchange.getRequestHeaders().getOrDefault("Cookie", java.util.List.of())) {
            for (String cookie : header.split(";")) {
                String[] pair = cookie.trim().split("=", 2);
                if (pair.length == 2 && pair[0].equals(DEVICE_COOKIE)) return pair[1];
            }
        }
        return null;
    }
}
