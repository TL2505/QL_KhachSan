package quanlykhachsan.backend.payment;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PayOsWebhookControllerTest {
    @Test void verifiesBeforeSettlementAndIgnoresUnsignedOuterClaims()throws Exception {
        var config=new PayOsConfig("","","synthetic-checksum-only","12345678","SYNTHETIC","970422","","");
        AtomicInteger calls=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/webhooks/payos",new PayOsWebhookController(config,p->{calls.incrementAndGet();assertEquals("99",p.resultCode());return "{}";}));server.start();
        try{
            JsonObject d=JsonParser.parseString("{\"orderCode\":1,\"amount\":1000,\"reference\":\"TEST-1\",\"transactionDateTime\":\"2026-10-22 12:00:00\",\"currency\":\"VND\",\"accountNumber\":\"12345678\",\"description\":\"AH0000001\",\"paymentLinkId\":\"synthetic\",\"code\":\"99\"}").getAsJsonObject();
            JsonObject body=new JsonObject();body.add("data",d);body.addProperty("success",true);body.addProperty("code","00");body.addProperty("signature",PayOsSignature.sign(d,config.checksumKey()));
            URI uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/webhooks/payos");
            assertEquals(200,post(uri,body.toString()));assertEquals(1,calls.get());d.addProperty("amount",9999);assertEquals(401,post(uri,body.toString()));assertEquals(1,calls.get());
            assertEquals(413,post(uri,"x".repeat(16385)));
        }finally{server.stop(0);}
    }
    private static int post(URI uri,String body)throws Exception {return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.discarding()).statusCode();}
}
