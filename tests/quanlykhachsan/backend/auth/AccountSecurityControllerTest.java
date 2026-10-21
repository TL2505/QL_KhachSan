package quanlykhachsan.backend.auth;

import com.sun.net.httpserver.HttpServer;
import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.backend.user.UserController;
import static org.junit.jupiter.api.Assertions.*;

class AccountSecurityControllerTest {
    @Test void sameAuthenticatedFlowWorksForEveryRoleAndRevokesCompletedSession()throws Exception {
        for(String role:List.of("ADMIN","STAFF","CUSTOMER")){
            var f=new AccountSecurityTest.Fixture();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/api/v1/account",new AccountSecurityController(f.service)).getFilters().add(new JwtFilter());server.start();
            try {
                String base="http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/account";
                String token=JwtTokenUtil.issueToken(1,"synthetic-"+role,role);
                assertEquals(401,send(base+"/profile",null,null).statusCode());
                var profile=send(base+"/profile",token,null);assertEquals(200,profile.statusCode());assertEquals("no-store",profile.headers().firstValue("Cache-Control").orElse(""));
                assertFalse(profile.body().contains(AccountSecurityTest.HASH));
                assertEquals(400,send(base+"/profile",token,"null").statusCode());
                assertEquals(200,send(base+"/profile",token,"{\"username\":\"someone-else\",\"userId\":99,\"fullName\":\"Own name\",\"email\":\"guest@example.invalid\"}").statusCode());
                assertEquals("Own name",f.store.read(1).fullName());assertNull(f.store.read(99));
                assertEquals(400,send(base+"/password/confirm",token,"{\"newPassword\":\"New-password-456\"}").statusCode());
                var start=data(send(base+"/email/request",token,"{}"));
                assertEquals(429,send(base+"/email/request",token,"{}").statusCode());
                assertEquals(422,send(base+"/email/verify",token,otp(start,"abcdef")).statusCode());
                assertEquals(200,send(base+"/email/verify",token,otp(start,f.code())).statusCode());
                var password=data(send(base+"/password/request",token,"{\"currentPassword\":\"Old-password-123\"}"));
                assertEquals(200,send(base+"/password/confirm",token,otp(password,f.code()).replace("}",",\"newPassword\":\"New-password-456\"}")).statusCode());
                assertTrue(SecurityUtil.verifyPassword(AccountSecurityTest.NEXT,f.store.read(1).passwordHash()));
                assertEquals(401,send(base+"/profile",token,null).statusCode());
            } finally {server.stop(0);}
        }
    }
    @Test void legacyPasswordEndpointsCannotBypassOtp()throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        for(String route:List.of("/api/v1/users/change-password","/api/v1/auth/change-password"))server.createContext(route,new UserController()).getFilters().add(new JwtFilter());
        server.start();
        try {
            for(String path:List.of("/api/v1/users/change-password","/api/v1/auth/change-password")){
                var response=send("http://127.0.0.1:"+server.getAddress().getPort()+path,JwtTokenUtil.issueToken(991,"legacy-test","CUSTOMER"),"{\"username\":\"victim\",\"oldPassword\":\"Old-password-123\",\"newPassword\":\"New-password-456\"}");
                assertEquals(400,response.statusCode());
            }
        } finally {server.stop(0);}
    }
    private static JsonObject data(HttpResponse<String> response){assertEquals(200,response.statusCode(),response.body());return JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("data");}
    private static String otp(JsonObject start,String code){return "{\"challengeId\":\""+start.get("challengeId").getAsString()+"\",\"code\":\""+code+"\"}";}
    private static HttpResponse<String> send(String url,String token,String body)throws Exception {
        var r=HttpRequest.newBuilder(URI.create(url)).header("Content-Type","application/json");if(token!=null)r.header("Authorization","Bearer "+token);
        return HttpClient.newHttpClient().send(body==null?r.GET().build():r.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
