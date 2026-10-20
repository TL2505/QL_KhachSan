package quanlykhachsan.ai;

import com.sun.net.httpserver.HttpServer;
import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.ai.*;
import quanlykhachsan.backend.utils.*;
import static org.junit.jupiter.api.Assertions.*;

class AiCheckinControllerTest {
    @Test void staffRbacInputValidationAndOfflineDegradation() throws Exception {
        var calls=new AtomicInteger();
        var controller=new AiCheckinController((path,json)->{
            calls.incrementAndGet();
            if(path.equals("/ocr"))return "{\"id_card\":\""+"0".repeat(12)+"\",\"full_name\":\"SYNTHETIC\",\"dob\":\"2000-01-01\",\"address\":\"SYNTHETIC\"}";
            throw new java.io.IOException("Synthetic offline fixture");
        });
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var context=server.createContext("/api/v1/ai/",controller);context.getFilters().add(new JwtFilter());server.start();
        server.createContext("/api/v1/customer-portal/identity/scan",controller).getFilters().add(new JwtFilter());
        try{
            String base="http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/ai/";
            var client=HttpClient.newHttpClient();
            String staff=JwtTokenUtil.issueToken(27,"synthetic-staff","front_desk"),guest=JwtTokenUtil.issueToken(28,"synthetic-guest","guest");
            byte[] png={(byte)137,80,78,71,13,10,26,10};String image=Base64.getEncoder().encodeToString(png);
            String ocr=new Gson().toJson(Map.of("image",image));
            String profileUrl="http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/customer-portal/identity/scan";
            String customer=JwtTokenUtil.issueToken(29,"synthetic-customer","CUSTOMER");
            assertEquals(401,post(client,profileUrl,ocr,null).statusCode());
            assertEquals(403,post(client,profileUrl,ocr,staff).statusCode());
            assertEquals(401,post(client,base+"id-ocr",ocr,null).statusCode());
            assertEquals(403,post(client,base+"id-ocr",ocr,guest).statusCode());
            assertEquals(0,calls.get());
            assertEquals(400,post(client,base+"id-ocr","{\"image\":\"invalid\"}",staff).statusCode());
            assertEquals(0,calls.get());
            var response=post(client,base+"id-ocr",ocr,staff);
            assertEquals(200,response.statusCode());assertEquals("SYNTHETIC",JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("data").get("full_name").getAsString());
            var canonical=post(client,base+"id-ocr",new Gson().toJson(Map.of("image_base64",image,"document_type","VIETNAMESE_CCCD")),staff);
            assertEquals(200,canonical.statusCode());assertTrue(canonical.body().contains("id_number"));assertTrue(canonical.body().contains("residence_address"));
            assertEquals(400,post(client,base+"id-ocr",new Gson().toJson(Map.of("image_base64",image,"image","invalid")),staff).statusCode());
            var offline=post(client,base+"face-verify",new Gson().toJson(Map.of("id_photo",image,"live_face",image)),staff);
            assertEquals(504,offline.statusCode());assertTrue(offline.body().contains(AiServiceClient.MANUAL_VERIFICATION));
            assertEquals(200,post(client,base+"id-ocr",ocr,staff).statusCode(),"Core request handling survives AI outage");
            var ownScan=post(client,profileUrl,ocr,customer);
            assertEquals(200,ownScan.statusCode());
            assertEquals("no-store",ownScan.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals(400,post(client,profileUrl,"{\"image\":\"invalid\"}",customer).statusCode());
            assertEquals(403,post(client,base+"face-verify",ocr,customer).statusCode());
        }finally{server.stop(0);}
    }
    private HttpResponse<String> post(HttpClient client,String uri,String body,String token) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(uri)).POST(HttpRequest.BodyPublishers.ofString(body));
        if(token!=null)builder.header("Authorization","Bearer "+token);
        return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
}
