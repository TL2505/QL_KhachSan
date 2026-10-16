package quanlykhachsan.security;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.auth.*;
import quanlykhachsan.backend.utils.*;
import static org.junit.jupiter.api.Assertions.*;

class Phase3SecurityHardeningTest {
    @Test void forwardedIpIsAcceptedOnlyFromExplicitTrustedProxy()throws Exception{
        for(boolean trusted:new boolean[]{false,true}){
            var clock=new MutableClock();
            var limiter=new IpRateLimiter(clock,100,trusted?java.util.Set.of(java.net.InetAddress.getByName("127.0.0.1")):java.util.Set.of());
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            var context=server.createContext("/",e->{e.sendResponseHeaders(204,-1);e.close();});context.getFilters().add(limiter);server.start();
            try{
                var client=HttpClient.newHttpClient();var url=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/");
                for(int i=0;i<60;i++)assertEquals(204,client.send(HttpRequest.newBuilder(url).header("X-Real-IP","192.0.2.1").GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode());
                assertEquals(429,client.send(HttpRequest.newBuilder(url).header("X-Real-IP","192.0.2.1").GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode());
                assertEquals(trusted?204:429,client.send(HttpRequest.newBuilder(url).header("X-Real-IP","192.0.2.2").GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode());
            }finally{server.stop(0);}
        }
    }
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-16T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }

    @Test void tokenBucketRefillsBoundsMemoryAndSeparatesAddresses() {
        var clock = new MutableClock(); var limiter = new IpRateLimiter(clock, 2);
        for (int i=0;i<60;i++) assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a")); assertTrue(limiter.tryAcquire("b"));
        assertFalse(limiter.tryAcquire("c"));
        clock.now = clock.now.plusMillis(999); assertFalse(limiter.tryAcquire("a"));
        clock.now = clock.now.plusMillis(1); assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        clock.now = clock.now.plusSeconds(60); assertTrue(limiter.tryAcquire("c"));
    }

    @Test void blacklistExpiresWithoutEvictingLiveRevocations() {
        var clock = new MutableClock(); var blacklist = new TokenBlacklistService(clock, 1);
        assertTrue(blacklist.revoke("synthetic-a", clock.now.plusSeconds(60)));
        assertTrue(blacklist.isRevoked("synthetic-a"));
        assertFalse(blacklist.revoke("synthetic-b", clock.now.plusSeconds(60)));
        assertTrue(blacklist.isRevoked("synthetic-a"));
        clock.now = clock.now.plusSeconds(60);
        assertFalse(blacklist.isRevoked("synthetic-a"));
        assertTrue(blacklist.revoke("synthetic-b", clock.now.plusSeconds(60)));
    }

    @Test void httpHeadersRateLimitInvalidBearerAndLogoutRevocation() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var limiter = new IpRateLimiter(new MutableClock(), 100);
        var logout = server.createContext("/api/v1/auth/logout",new LogoutController());
        logout.getFilters().addAll(java.util.List.of(new SecurityHardeningFilter(),limiter,new JwtFilter()));
        var resource = server.createContext("/api/v1/private", e -> { e.sendResponseHeaders(204,-1);e.close(); });
        resource.getFilters().addAll(java.util.List.of(new SecurityHardeningFilter(),limiter,new JwtFilter()));
        server.start();
        try {
            var client = HttpClient.newHttpClient();
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            String token=JwtTokenUtil.issueToken(17,"synthetic-staff","front_desk");
            var spoofed=client.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/private"))
                    .header("X-User-Role","guest").GET().build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(401,spoofed.statusCode());
            assertEquals(204,send(client,base+"/api/v1/private","GET",token).statusCode());
            assertEquals(405,send(client,base+"/api/v1/auth/logout","GET",token).statusCode());
            assertEquals(200,send(client,base+"/api/v1/auth/logout","POST",token).statusCode());
            assertEquals(401,send(client,base+"/api/v1/private","GET",token).statusCode());
            for(int i=0;i<58;i++) assertEquals(401,send(client,base+"/api/v1/private","GET","invalid").statusCode());
            var blocked=send(client,base+"/api/v1/private","GET","invalid");
            assertEquals(429,blocked.statusCode());
            assertTrue(blocked.body().contains("ERR_429_RATE_LIMITED"));
            assertEquals("nosniff",blocked.headers().firstValue("X-Content-Type-Options").orElseThrow());
            assertEquals("DENY",blocked.headers().firstValue("X-Frame-Options").orElseThrow());
            assertTrue(blocked.headers().firstValue("Strict-Transport-Security").isPresent());
            assertEquals("default-src 'self'",blocked.headers().firstValue("Content-Security-Policy").orElseThrow());
            String fresh=JwtTokenUtil.issueToken(18,"synthetic-staff","front_desk");
            assertEquals(204,send(client,base+"/api/v1/private","GET",fresh).statusCode());
        } finally { server.stop(0); }
    }

    @Test void productionServerSharesIpBudgetAcrossRoutes() throws Exception {
        var server = quanlykhachsan.backend.Main.createServer(0);
        server.start();
        try {
            var client=HttpClient.newHttpClient();
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            boolean limited=false;
            for(int i=0;i<90;i++) {
                var response=send(client,base+(i%2==0?"/missing-fixture-a":"/booking/missing-fixture-b"),"GET","invalid");
                if(response.statusCode()==429){limited=true;break;}
            }
            assertTrue(limited,"The production route setup must share a single limiter");
        } finally {
            server.stop(0);
            ((java.util.concurrent.ExecutorService)server.getExecutor()).shutdownNow();
        }
    }
    private HttpResponse<String> send(HttpClient client,String uri,String method,String token) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(uri)).header("Authorization","Bearer "+token)
                .method(method,HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }
}
