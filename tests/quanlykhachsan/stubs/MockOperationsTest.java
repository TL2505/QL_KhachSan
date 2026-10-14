package quanlykhachsan.stubs;

import java.net.*;
import java.net.http.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MockOperationsTest {
    @Test void paymentAndHousekeepingMocksExposeUsableContracts()throws Exception {
        try(var mock=new MockApiServer(0)){mock.start();var client=HttpClient.newHttpClient();String base="http://127.0.0.1:"+mock.port();
            var qr=send(client,base+"/api/v1/folios/999/vietqr","GET",null);assertEquals(200,qr.statusCode());assertTrue(qr.body().contains("qr_image_base64"));
            assertTrue(send(client,base+"/api/v1/folios/999/payment-status","GET",null).body().contains("open"));
            assertEquals(200,send(client,base+"/mock/fire-webhook","GET",null).statusCode());
            assertTrue(send(client,base+"/api/v1/folios/999/payment-status","GET",null).body().contains("settled"));
            assertEquals(200,send(client,base+"/api/v1/rooms/101/housekeeping","PUT","{\"status\":\"vacant_clean\"}").statusCode());
            assertTrue(send(client,base+"/api/v1/rooms/101/minibar","GET",null).body().contains("unit_price"));
            assertEquals(201,send(client,base+"/api/v1/rooms/101/minibar","POST","{\"items\":[{\"item_id\":1,\"quantity\":2}]}").statusCode());
            assertEquals(409,send(client,base+"/api/v1/rooms/101/maintenance","PUT","{\"status\":\"out_of_order\"}").statusCode());
            assertEquals(200,send(client,base+"/api/v1/rooms/101/maintenance","PUT","{\"status\":\"out_of_order\",\"manager_pin\":\"246810\"}").statusCode());
        }
    }
    private static HttpResponse<String> send(HttpClient c,String uri,String method,String body)throws Exception {
        var q=HttpRequest.newBuilder(URI.create(uri)).method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
        if(body!=null)q.header("Content-Type","application/json").header("Idempotency-Key",UUID.randomUUID().toString());
        return c.send(q.build(),HttpResponse.BodyHandlers.ofString());
    }
}
