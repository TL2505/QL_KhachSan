package quanlykhachsan.backend.ai;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Map;
import quanlykhachsan.backend.utils.ApiResponseUtil;

public final class AiProviderSettingsController implements HttpHandler {
    private static final String PATH = "/api/v1/admin/ai-settings";
    private final AiProviderSettings settings;
    private final CloudAiClient client;
    public AiProviderSettingsController() { this(AiProviderSettings.instance(), new CloudAiClient()); }
    AiProviderSettingsController(AiProviderSettings settings, CloudAiClient client) { this.settings = settings; this.client = client; }
    @Override public void handle(HttpExchange e) throws IOException {
        e.getResponseHeaders().set("Cache-Control", "no-store");
        if (e.getAttribute("userId") == null || !"admin".equalsIgnoreCase(String.valueOf(e.getAttribute("role")))) {
            ApiResponseUtil.sendError(e, 403, "ERR_403_FORBIDDEN", "Chỉ quản trị viên được quản lý API key AI."); return;
        }
        String path = e.getRequestURI().getPath();
        try {
            if (path.equals(PATH + "/models") && e.getRequestMethod().equals("POST")) {
                byte[] bytes = e.getRequestBody().readNBytes(2049);
                if (bytes.length > 2048) { ApiResponseUtil.sendError(e, 413, "ERR_413_PAYLOAD_TOO_LARGE", "Yêu cầu quá dài."); return; }
                var config = settings.forModels(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject());
                var list = client.models(config);
                var models = list.models().stream().map(model -> Map.of("id", model.id(), "name", model.name())).toList();
                ApiResponseUtil.sendSuccess(e, Map.of("provider", config.provider(), "models", models, "more_available", list.moreAvailable())); return;
            }
            if (path.equals(PATH + "/test") && e.getRequestMethod().equals("POST")) {
                var c = settings.read();
                if (c.apiKey().isEmpty()) { ApiResponseUtil.sendError(e, 409, "AI_KEY_REQUIRED", "Vui lòng lưu API key trước khi kiểm tra."); return; }
                client.answer(c, "OK", true);
                ApiResponseUtil.sendSuccess(e, Map.of("message", "Kết nối AI thành công.", "provider", c.provider(), "model", c.model())); return;
            }
            if (!path.equals(PATH)) { ApiResponseUtil.sendError(e, 404, "ERR_404_NOT_FOUND", "Route not found"); return; }
            switch (e.getRequestMethod()) {
                case "GET" -> ApiResponseUtil.sendSuccess(e, settings.metadata());
                case "PUT" -> {
                    byte[] bytes = e.getRequestBody().readNBytes(49153);
                    if (bytes.length > 49152) { ApiResponseUtil.sendError(e, 413, "ERR_413_PAYLOAD_TOO_LARGE", "Cấu hình quá dài."); return; }
                    settings.update(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject());
                    ApiResponseUtil.sendSuccess(e, settings.metadata());
                }
                case "DELETE" -> { settings.delete(); ApiResponseUtil.sendSuccess(e, settings.metadata()); }
                default -> ApiResponseUtil.sendError(e, 405, "ERR_405_METHOD_NOT_ALLOWED", "Method not allowed");
            }
        } catch (CloudAiClient.GenerationException ex) {
            ApiResponseUtil.sendError(e, 422, ex.code, ex.publicMessage());
        } catch (CloudAiClient.ProviderException ex) {
            ApiResponseUtil.sendError(e, 422, "AI_PROVIDER_REJECTED", ex.publicMessage());
        } catch (IllegalArgumentException | JsonParseException | IllegalStateException ex) {
            ApiResponseUtil.sendError(e, 400, "AI_SETTINGS_INVALID", "Cấu hình không hợp lệ. Kiểm tra nhà cung cấp, model và API key.");
        } catch (java.net.http.HttpTimeoutException ex) {
            ApiResponseUtil.sendError(e, 422, "AI_PROVIDER_TIMEOUT", "Nhà cung cấp AI không phản hồi trong 45 giây. Vui lòng thử lại hoặc chọn model phản hồi nhanh hơn.");
        } catch (IOException | GeneralSecurityException ex) {
            ApiResponseUtil.sendError(e, 503, "AI_SETTINGS_UNAVAILABLE", "Không thể đọc/lưu cấu hình hoặc kết nối AI. Vui lòng kiểm tra dịch vụ máy chủ.");
        }
    }
}
