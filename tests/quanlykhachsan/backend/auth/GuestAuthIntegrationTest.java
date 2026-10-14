package quanlykhachsan.backend.auth;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.support.Phase2TestDatabase;
import static org.junit.jupiter.api.Assertions.*;

class GuestAuthIntegrationTest {
    @Test void validExpiredConsumedAndRateLimitedOtpAreVerifiedOverHttp() throws Exception {
        Phase2TestDatabase.require();
        String phone="0907654321"; AtomicReference<String> delivered=new AtomicReference<>();
        var service=new GuestAuthService((p,email,code)->delivered.set(code));
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/auth/guest-otp",new GuestAuthController(service)); server.start();
        try {
            var client=HttpClient.newHttpClient(); String base="http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/auth/guest-otp/";
            var sent=post(client,base+"request","{\"phone\":\""+phone+"\"}");
            assertEquals(200,sent.statusCode()); assertNotNull(delivered.get()); assertTrue(delivered.get().matches("[0-9]{6}"));
            assertFalse(sent.body().contains(delivered.get()));
            assertEquals(429,post(client,base+"request","{\"phone\":\""+phone+"\"}").statusCode());
            String payload="{\"phone\":\""+phone+"\",\"otp_code\":\""+delivered.get()+"\"}";
            var verified=post(client,base+"verify",payload); assertEquals(200,verified.statusCode());
            var json=com.google.gson.JsonParser.parseString(verified.body()).getAsJsonObject().getAsJsonObject("data");
            var claims=io.jsonwebtoken.Jwts.parser().verifyWith(JwtTokenUtil.signingKey()).build().parseSignedClaims(json.get("access_token").getAsString()).getPayload();
            assertEquals("guest",claims.get("role")); assertEquals(phone,claims.get("phone")); assertTrue(claims.get("customer_id",Integer.class)>0);
            assertEquals(86400000L,claims.getExpiration().getTime()-claims.getIssuedAt().getTime());
            assertEquals(400,post(client,base+"verify",payload).statusCode());
            try(Connection c=DatabaseConnection.getConnection(); PreparedStatement q=c.prepareStatement("INSERT INTO guest_otps(phone,otp_code,expires_at) VALUES (?,'654321',DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 MINUTE))")) { q.setString(1,phone); q.executeUpdate(); }
            assertEquals(400,post(client,base+"verify","{\"phone\":\""+phone+"\",\"otp_code\":\"654321\"}").statusCode());
            assertEquals(400,post(client,base+"request","{\"phone\":\"invalid\"}").statusCode());
        } finally {
            server.stop(0);
            try(Connection c=DatabaseConnection.getConnection()) {
                for(String table:new String[]{"guest_otps","customers"}) try(PreparedStatement q=c.prepareStatement("DELETE FROM "+table+" WHERE phone=?")) { q.setString(1,phone); q.executeUpdate(); }
            }
        }
    }
    private static HttpResponse<String> post(HttpClient c,String uri,String body)throws Exception {
        return c.send(HttpRequest.newBuilder(URI.create(uri)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
