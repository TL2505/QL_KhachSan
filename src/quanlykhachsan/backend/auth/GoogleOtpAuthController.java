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
    private final GoogleOtpAuthService service = new GoogleOtpAuthService();
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
            if (path.endsWith("/request-otp")) result = service.begin(value(body, "credential"));
            else if (path.endsWith("/resend-otp")) result = service.resend(value(body, "challenge_id"));
            else if (path.endsWith("/verify-otp")) { ApiResponseUtil.write(exchange, 200, JsonUtil.getGson().toJson(AuthMapper.success("Đăng nhập Google thành công.", service.verify(value(body, "challenge_id"), value(body, "otp"))))); return; }
            else { ApiResponseUtil.write(exchange, 404, ApiResponseUtil.error("Đường dẫn không tồn tại.")); return; }
            JsonObject data = new JsonObject(); data.addProperty("challenge_id", result.challengeId()); data.addProperty("email", result.maskedEmail()); data.addProperty("expires_at", result.expiresAt().toString());
            ApiResponseUtil.write(exchange, 200, ApiResponseUtil.success("Mã xác thực đã được gửi tới email của bạn.", data));
        } catch (GoogleOtpAuthService.AuthFailure e) { ApiResponseUtil.write(exchange, e.status, ApiResponseUtil.error(e.getMessage())); }
        catch (Exception e) { ApiResponseUtil.write(exchange, 500, ApiResponseUtil.error("Không thể xử lý đăng nhập Google. Vui lòng thử lại.")); }
    }
    private static String value(JsonObject body, String key) { return body != null && body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsString() : ""; }
}
