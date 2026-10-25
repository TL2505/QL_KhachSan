package quanlykhachsan.backend.auth;

import com.sun.net.httpserver.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Map;
import quanlykhachsan.backend.utils.*;

public final class AccountSecurityController implements HttpHandler {
    private static final AccountSecurityService SHARED = new AccountSecurityService();
    private static final quanlykhachsan.backend.catalog.MediaController MEDIA = new quanlykhachsan.backend.catalog.MediaController();
    private final AccountSecurityService service;
    private final Devices devices;
    @FunctionalInterface interface Devices { Object read(HttpExchange exchange,int userId) throws Exception; }
    public AccountSecurityController() { this(SHARED); }
    public AccountSecurityController(AccountSecurityService service) { this(service, LoginDeviceService::devices); }
    AccountSecurityController(AccountSecurityService service,Devices devices) { this.service=service;this.devices=devices; }
    public void handle(HttpExchange exchange) throws IOException {
        String path=exchange.getRequestURI().getPath();
        String action=path.replaceFirst("^/api/(?:v1/)?account/?","");
        handleAction(exchange,action);
    }
    public void handleAction(HttpExchange e,String action) throws IOException {
        e.getResponseHeaders().set("Cache-Control","no-store");
        Object identity=e.getAttribute("userId");
        if (!(identity instanceof Number id) || id.intValue()<=0) { ApiResponseUtil.write(e,401,ApiResponseUtil.error("Vui lòng đăng nhập lại.")); return; }
        int userId=((Number)identity).intValue();
        try {
            String method=e.getRequestMethod();
            if ("avatar".equals(action)) { MEDIA.handleAvatar(e,userId); return; }
            if ("profile".equals(action) && "GET".equals(method)) { ok(e,service.profile(userId));return; }
            if ("devices".equals(action) && "GET".equals(method)) { service.account(userId);ok(e,devices.read(e,userId));return; }
            if (!"POST".equals(method) && !"PUT".equals(method)) { ApiResponseUtil.write(e,405,ApiResponseUtil.error("Method Not Allowed"));return; }
            JsonObject body=PortalValidation.body(e);
            switch(action) {
                case "profile" -> ok(e,service.saveProfile(userId,PortalValidation.text(body,"fullName",100,true),PortalValidation.text(body,"phone",20,false),PortalValidation.text(body,"email",100,false)));
                case "email/request" -> start(e,service.beginEmail(userId));
                case "email/verify" -> ok(e,service.verifyEmail(userId,PortalValidation.text(body,"challengeId",100,true),PortalValidation.text(body,"code",6,true)));
                case "password/request" -> start(e,service.beginPassword(userId,password(body,"currentPassword")));
                case "password/confirm" -> {
                    service.changePassword(userId,PortalValidation.text(body,"challengeId",100,true),PortalValidation.text(body,"code",6,true),password(body,"newPassword"));
                    String auth=e.getRequestHeaders().getFirst("Authorization");
                    if(auth!=null && auth.startsWith("Bearer ")) {
                        String token=auth.substring(7).trim();
                        TokenBlacklistService.shared().revoke(token,JwtTokenUtil.parseClaims(token).getExpiration().toInstant());
                    }
                    ok(e,Map.of("passwordChanged",true,"requiresLogin",true));
                }
                default -> ApiResponseUtil.write(e,404,ApiResponseUtil.error("Không tìm thấy chức năng."));
            }
        } catch(AccountSecurityService.Failure error) {
            if(error.retryAfter>0)e.getResponseHeaders().set("Retry-After",Integer.toString(error.retryAfter));
            ApiResponseUtil.write(e,error.status,ApiResponseUtil.error(error.getMessage()));
        } catch(IllegalArgumentException error) {
            ApiResponseUtil.write(e,400,ApiResponseUtil.error("Thông tin không hợp lệ. Cần điền đầy đủ dữ liệu và mã OTP gồm 6 chữ số."));
        } catch(java.sql.SQLIntegrityConstraintViolationException error) {
            ApiResponseUtil.write(e,409,ApiResponseUtil.error("Email hoặc thông tin này đã được sử dụng."));
        } catch(Exception error) {
            ApiResponseUtil.write(e,503,ApiResponseUtil.error("Chưa thể hoàn tất thao tác. Vui lòng thử lại sau."));
        }
    }
    private static String password(JsonObject body,String name) {
        var value=body.get(name);
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw new IllegalArgumentException("Invalid password");
        return value.getAsString();
    }
    private static void start(HttpExchange e,AccountSecurityService.Start s) throws IOException {
        ok(e,Map.of("challengeId",s.challengeId(),"maskedEmail",s.maskedEmail(),"expiresAt",s.expiresAt().toString(),"resendAt",s.resendAt().toString()));
    }
    private static void ok(HttpExchange e,Object value) throws IOException { ApiResponseUtil.write(e,200,ApiResponseUtil.successWithData(value)); }
}
