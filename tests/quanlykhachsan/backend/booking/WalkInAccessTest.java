package quanlykhachsan.backend.booking;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WalkInAccessTest {
    @Test void receptionAndLegacyStaffReachValidationButOtherRolesAreDenied() throws Exception {
        for(String role:new String[]{"staff","front_desk","admin","customer","housekeeping","maintenance"}) {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            var controller=new PublicBookingController(true);
            server.createContext("/api/v1/bookings/walk-in",exchange->{exchange.setAttribute("role",role);controller.handle(exchange);});server.start();
            try {
                var result=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/bookings/walk-in"))
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString());
                assertEquals(java.util.Set.of("staff","front_desk","admin").contains(role)?400:403,result.statusCode(),role);
            } finally {server.stop(0);}
        }
    }
}
