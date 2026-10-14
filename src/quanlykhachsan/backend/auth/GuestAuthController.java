package quanlykhachsan.backend.auth;

import com.sun.net.httpserver.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import quanlykhachsan.backend.utils.*;

public final class GuestAuthController implements HttpHandler {
    private final GuestAuthService service;
    public GuestAuthController() { this(new GuestAuthService()); }
    public GuestAuthController(GuestAuthService service) { this.service=service; }
    @Override public void handle(HttpExchange e) throws IOException {
        if (!e.getRequestMethod().equals("POST")) { ApiResponseUtil.write(e,405,ApiResponseUtil.error("Method not allowed")); return; }
        try {
            JsonObject body = PortalValidation.body(e);
            String phone = PortalValidation.phone(PortalValidation.text(body,"phone",20,true));
            JsonObject data = new JsonObject();
            String path=e.getRequestURI().getPath();
            if (path.equals("/api/v1/auth/guest-otp/request")) {
                service.request(phone,PortalValidation.email(PortalValidation.text(body,"email",100,false)));
                data.addProperty("message","OTP verification code sent successfully."); data.addProperty("expires_in_seconds",300);
            } else if (path.equals("/api/v1/auth/guest-otp/verify")) {
                var verified=service.verify(phone,PortalValidation.text(body,"otp_code",6,true));
                data.addProperty("access_token",verified.token()); data.addProperty("token_type","Bearer"); data.addProperty("expires_in_seconds",86400);
                JsonObject guest=new JsonObject(); guest.addProperty("customer_id",verified.customerId()); guest.addProperty("phone",verified.phone());
                try(var c=DatabaseConnection.getConnection();var q=c.prepareStatement("SELECT loyalty_level,total_loyalty_points FROM customers WHERE id=?")){q.setInt(1,verified.customerId());try(var r=q.executeQuery()){if(!r.next())throw new IllegalStateException();guest.addProperty("loyalty_level",r.getString(1));guest.addProperty("total_points",r.getInt(2));}}
                data.add("guest_profile",guest);
            } else { ApiResponseUtil.write(e,404,ApiResponseUtil.error("Not found")); return; }
            ApiResponseUtil.write(e,200,ApiResponseUtil.successWithData(data));
        } catch (GuestAuthService.RateLimitException ex) {
            e.getResponseHeaders().set("Retry-After","60"); ApiResponseUtil.write(e,429,ApiResponseUtil.error("Please wait before retrying verification"));
        } catch (IllegalArgumentException ex) { ApiResponseUtil.write(e,400,ApiResponseUtil.error("Invalid contact or verification code")); }
        catch (Exception ex) { ApiResponseUtil.write(e,503,ApiResponseUtil.error("Verification is unavailable; please contact the Front Desk")); }
    }
}
