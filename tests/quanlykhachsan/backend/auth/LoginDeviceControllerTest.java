package quanlykhachsan.backend.auth;

import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LoginDeviceControllerTest {
    @Test void inventoryIsBoundToAuthenticatedUserForEveryRole() throws Exception {
        var fixture = new AccountSecurityTest.Fixture();
        var controller = new AccountSecurityController(fixture.service,(exchange,userId)->
            List.of(Map.of("userId",userId,"browser","Chrome","current",true)));
        for(String role:List.of("admin","front_desk","customer")) {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/api/v1/account",exchange->{
                exchange.setAttribute("userId",1); exchange.setAttribute("role",role); controller.handle(exchange);
            }); server.start();
            try {
                var result=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(
                    "http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/account/devices?userId=999")).GET().build(),HttpResponse.BodyHandlers.ofString());
                assertEquals(200,result.statusCode());
                assertTrue(result.body().contains("\"userId\":1"));
                assertFalse(result.body().contains("999"));
                assertEquals("no-store",result.headers().firstValue("Cache-Control").orElseThrow());
            } finally {server.stop(0);}
        }
    }
    @Test void browserDetectionKeepsEdgeAndMobilePlatformsDistinct() {
        assertEquals("Microsoft Edge",LoginDeviceService.browser("Mozilla Windows Chrome/130 Safari/537 Edg/130"));
        assertEquals("Chrome",LoginDeviceService.browser("Mozilla Android Chrome/130 Safari/537"));
        assertEquals("Android",LoginDeviceService.platform("Mozilla Linux Android"));
        assertEquals("iOS / iPadOS",LoginDeviceService.platform("Mozilla iPhone like Mac OS X"));
    }
}
