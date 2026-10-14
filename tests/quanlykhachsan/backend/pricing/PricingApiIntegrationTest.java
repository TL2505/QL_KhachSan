package quanlykhachsan.backend.pricing;

import com.sun.net.httpserver.*;
import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.room.RoomAvailabilityController;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.support.Phase2TestDatabase;
import static org.junit.jupiter.api.Assertions.*;

class PricingApiIntegrationTest {
    @Test void publicAvailabilityAndQuotesHaveSafeProjectionsAndAuthoritativeTaxes()throws Exception {
        Phase2TestDatabase.require();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/pricing/quote",new PricingController()).getFilters().add(new JwtFilter());
        server.createContext("/api/v1/rooms/availability",new RoomAvailabilityController()).getFilters().add(new JwtFilter());server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();LocalDate date=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(10);
            var client=HttpClient.newHttpClient();String range="check_in="+date+"&check_out="+date.plusDays(2);
            var response=get(client,base+"/api/v1/pricing/quote?room_type_id=1&"+range);assertEquals(200,response.statusCode());
            var quote=JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("data");
            assertEquals(2,quote.getAsJsonArray("nightly_rates").size());
            assertEquals(quote.get("total_amount").getAsBigDecimal(),quote.get("room_subtotal").getAsBigDecimal().add(quote.get("service_charge").getAsBigDecimal()).add(quote.get("vat").getAsBigDecimal()));
            var inventory=get(client,base+"/api/v1/rooms/availability?"+range);assertEquals(200,inventory.statusCode());
            assertFalse(inventory.body().contains("customer"));assertFalse(inventory.body().contains("phone"));assertFalse(inventory.body().contains("booking_id"));
            assertEquals(400,get(client,base+"/api/v1/pricing/quote?room_type_id=1&check_in=invalid&check_out=invalid").statusCode());
            assertEquals(400,get(client,base+"/api/v1/rooms/availability").statusCode());
        } finally {server.stop(0);}
    }
    private static HttpResponse<String> get(HttpClient c,String uri)throws Exception{return c.send(HttpRequest.newBuilder(URI.create(uri)).GET().build(),HttpResponse.BodyHandlers.ofString());}
}
