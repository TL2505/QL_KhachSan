package quanlykhachsan.backend.booking;

import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.HmacSignatureUtil;
import static org.junit.jupiter.api.Assertions.*;

class BankWebhookControllerTest {
    @Test void signatureValidationPrecedesSettlementAndPayloadParsing()throws Exception {
        String secret="test-only-bank-secret-32-characters-long";AtomicInteger calls=new AtomicInteger();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/webhooks/vietqr",new BankWebhookController(secret,p->{calls.incrementAndGet();return "{\"status\":\"SETTLED\"}";}));server.start();
        try {
            String url="http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/webhooks/vietqr";
            String body="{\"bank_trans_id\":\"TEST-1\",\"amount\":1000000,\"reference_code\":\"FOLIO-MASTER-1\",\"trans_time\":\"2026-10-14T12:00:00+07:00\"}";
            String signature=HmacSignatureUtil.sign(secret,body.getBytes(StandardCharsets.UTF_8));var client=HttpClient.newHttpClient();
            assertEquals(401,post(client,url,body,null).statusCode());assertEquals(401,post(client,url,body+" ",signature).statusCode());assertEquals(0,calls.get());
            var valid=post(client,url,body,signature);assertEquals(200,valid.statusCode());assertTrue(valid.body().contains("SETTLED"));assertEquals(1,calls.get());
        }finally{server.stop(0);}
    }
    private static HttpResponse<String> post(HttpClient c,String url,String body,String signature)throws Exception {
        var q=HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(body));if(signature!=null)q.header("X-Signature",signature);return c.send(q.build(),HttpResponse.BodyHandlers.ofString());
    }
}
