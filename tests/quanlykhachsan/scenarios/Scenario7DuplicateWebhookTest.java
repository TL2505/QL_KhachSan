package quanlykhachsan.scenarios;

import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.booking.*;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.financial.PaymentFixture;
import quanlykhachsan.support.Phase2TestDatabase;
import static org.junit.jupiter.api.Assertions.*;

class Scenario7DuplicateWebhookTest {
    @Test void fiftySignedDuplicatesApplyOneCreditAndAuditEveryDelivery()throws Exception {
        Phase2TestDatabase.require();String secret="phase2-bank-webhook-test-secret-32-characters";
        try(var fixture=new PaymentFixture(new BigDecimal("1000000.00"))) {
            String transaction="TXN-DUP-"+UUID.randomUUID();String body="{\"bank_trans_id\":\""+transaction+"\",\"amount\":1000000,\"reference_code\":\""+fixture.code+"\",\"trans_time\":\"2026-10-14T12:00:00+07:00\"}";
            String signature=HmacSignatureUtil.sign(secret,body.getBytes(StandardCharsets.UTF_8));
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var serverWorkers=Executors.newFixedThreadPool(50);server.setExecutor(serverWorkers);
            server.createContext("/api/v1/webhooks/vietqr",new BankWebhookController(secret,new FolioPaymentService()::settle));server.start();
            var workers=Executors.newFixedThreadPool(50);CountDownLatch ready=new CountDownLatch(50),start=new CountDownLatch(1);List<Future<HttpResponse<String>>> futures=new ArrayList<>();
            try {
                var client=HttpClient.newHttpClient();String url="http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/webhooks/vietqr";
                for(int i=0;i<50;i++)futures.add(workers.submit(()->{ready.countDown();start.await();return client.send(HttpRequest.newBuilder(URI.create(url)).header("X-Signature",signature).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());}));
                assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();Set<String> responses=new HashSet<>();for(var future:futures){var result=future.get(30,TimeUnit.SECONDS);assertEquals(200,result.statusCode(),result.body());responses.add(result.body());}
                assertEquals(1,responses.size());
                assertEquals(1,Phase2TestDatabase.scalar("SELECT COUNT(*) FROM bank_webhook_logs WHERE folio_id="+fixture.folio));
                assertEquals(1,Phase2TestDatabase.scalar("SELECT COUNT(*) FROM bank_webhook_deliveries WHERE bank_trans_id='"+transaction+"' AND status='PROCESSED'"));
                assertEquals(49,Phase2TestDatabase.scalar("SELECT COUNT(*) FROM bank_webhook_deliveries WHERE bank_trans_id='"+transaction+"' AND status='DUPLICATE'"));
                assertEquals(1,Phase2TestDatabase.scalar("SELECT COUNT(*) FROM folio_transactions WHERE folio_id="+fixture.folio+" AND charge_type='payment'"));
                assertEquals(0,Phase2TestDatabase.scalar("SELECT balance FROM folios WHERE id="+fixture.folio));
            }finally{workers.shutdownNow();workers.awaitTermination(5,TimeUnit.SECONDS);server.stop(0);serverWorkers.shutdownNow();}
        }
    }
}
