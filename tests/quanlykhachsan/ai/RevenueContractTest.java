package quanlykhachsan.ai;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.report.*;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.support.Phase2TestDatabase;
import static org.junit.jupiter.api.Assertions.*;

class RevenueContractTest {
    @Test void frozenForecastAndExplicitArrayAliasPreserveReadOnlyRole()throws Exception{
        Phase2TestDatabase.require();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var context=server.createContext("/api/v1/revenue/",new YieldRecommendationController());context.getFilters().add(new JwtFilter());server.start();
        try{
            var client=HttpClient.newHttpClient();String base="http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/revenue/";
            String token=JwtTokenUtil.issueToken(1,"synthetic-revenue","revenue_manager");
            var request=HttpRequest.newBuilder(URI.create(base+"forecast?days=2")).header("Authorization","Bearer "+token).GET().build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode());
            var data=JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("data");
            assertEquals(2,data.getAsJsonArray("forecast").size());assertTrue(data.has("model"));assertTrue(data.has("generated_at"));
            var day=data.getAsJsonArray("forecast").get(0).getAsJsonObject();assertTrue(day.has("confidence_lower"));assertTrue(day.get("recommended_bar").isJsonNull());
            response=client.send(HttpRequest.newBuilder(URI.create(base+"forecast?days=2&format=array")).header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(2,JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("data").size());
            response=client.send(HttpRequest.newBuilder(URI.create(base+"apply-rates")).header("Authorization","Bearer "+token).POST(HttpRequest.BodyPublishers.ofString("{}" )).build(),HttpResponse.BodyHandlers.ofString());assertEquals(403,response.statusCode());
        }finally{server.stop(0);}
    }
    @Test void bothRateAliasesDeserializeAndMissingVersionMeansCreateOnly(){
        for(String field:new String[]{"approved_multiplier","multiplier"}){
            var rate=JsonUtil.getGson().fromJson("{\"room_type_id\":1,\"date\":\"2026-10-16\",\""+field+"\":1.2}",RateApprovalService.Rate.class);
            assertEquals(0,rate.version());assertEquals(0,new java.math.BigDecimal("1.2").compareTo(rate.multiplier()));
        }
    }
}
