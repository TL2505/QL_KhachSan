package quanlykhachsan.backend.auth;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.auth.dto.AuthUserInfo;
import static org.junit.jupiter.api.Assertions.*;

class GoogleTrustedLoginTest {
    @Test void firstLoginSetsHttpOnlyDeviceCookieAndSecondLoginDoesNotSendOtp() throws Exception {
        List<String> sent = new ArrayList<>();
        var rejectGoogle = new AtomicBoolean(false);
        var service = new GoogleOtpAuthService(Clock.systemUTC(), credential -> {
            if (rejectGoogle.get()) throw new GoogleOtpAuthService.AuthFailure(401,"Rejected identity");
            return new GoogleOtpAuthService.GoogleIdentity("fixture-subject","guest@example.com","Guest");
        }, (email,code) -> sent.add(code), identity -> {
            var info = new AuthUserInfo(); info.setToken("synthetic-session-token"); return info;
        }, new GoogleOtpAuthService.DeviceTrust() {
            public boolean recognizes(GoogleOtpAuthService.GoogleIdentity identity,String token) { return "synthetic-device-proof".equals(token); }
            public String remember(GoogleOtpAuthService.GoogleIdentity identity) { return "synthetic-device-proof"; }
        });
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/auth/google",new GoogleOtpAuthController(service)); server.start();
        try {
            var client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/auth/google/";
            var first = post(client,base+"request-otp","{\"credential\":\"synthetic-google-token\"}",null);
            assertEquals(200,first.statusCode());
            assertEquals(1,sent.size());
            assertFalse(first.body().contains("synthetic-session-token"));
            String challenge = com.google.gson.JsonParser.parseString(first.body()).getAsJsonObject().getAsJsonObject("data").get("challenge_id").getAsString();
            var verified = post(client,base+"verify-otp","{\"challenge_id\":\""+challenge+"\",\"otp\":\""+sent.get(0)+"\"}",null);
            assertEquals(200,verified.statusCode());
            String cookie = verified.headers().firstValue("Set-Cookie").orElseThrow();
            assertTrue(cookie.contains("HttpOnly")); assertTrue(cookie.contains("SameSite=Strict"));
            assertFalse(verified.body().contains("synthetic-device-proof"));
            var second = post(client,base+"request-otp","{\"credential\":\"synthetic-google-token\"}",cookie.split(";",2)[0]);
            assertEquals(200,second.statusCode());
            assertTrue(second.body().contains("synthetic-session-token"));
            assertEquals(1,sent.size());
            rejectGoogle.set(true);
            var rejected = post(client,base+"request-otp","{\"credential\":\"invalid\"}",cookie.split(";",2)[0]);
            assertEquals(401,rejected.statusCode());
            assertFalse(rejected.body().contains("synthetic-session-token"));
            rejectGoogle.set(false);
            post(client,base+"request-otp","{\"credential\":\"synthetic-google-token\"}",null);
            assertEquals(2,sent.size(),"A browser without device proof must still receive OTP");
        } finally { server.stop(0); }
    }
    private HttpResponse<String> post(HttpClient client,String url,String body,String cookie) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).header("Content-Type","application/json");
        if(cookie!=null) request.header("Cookie",cookie);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
