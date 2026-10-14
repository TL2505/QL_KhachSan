package quanlykhachsan.backend;

import org.junit.jupiter.api.Test;
import quanlykhachsan.support.Phase2TestDatabase;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.ExecutorService;
import static org.junit.jupiter.api.Assertions.*;

class ProductionServerRegistrationTest {
 @Test void mergedServerPreservesPublicPortalProtectedStaffRoutesAndDeepHealth() throws Exception {
  Phase2TestDatabase.require();
  var server=Main.createServer(0);server.start();
  try {
   String base="http://127.0.0.1:"+server.getAddress().getPort();
   var client=HttpClient.newHttpClient();
   var health=get(client,base+"/api/v1/health");
   assertEquals(200,health.statusCode());assertTrue(health.body().contains("UP"));
   assertEquals("nosniff",health.headers().firstValue("X-Content-Type-Options").orElseThrow());
   var portal=get(client,base+"/booking/");assertEquals(200,portal.statusCode());assertTrue(portal.body().contains("<html"));
   assertEquals(401,get(client,base+"/api/v1/rooms").statusCode());
   assertEquals(400,get(client,base+"/api/v1/pricing/quote").statusCode());
   assertEquals(400,get(client,base+"/api/v1/rooms/availability").statusCode());
   assertEquals(404,get(client,base+"/api/v1/unknown-endpoint").statusCode());
  } finally {server.stop(0);((ExecutorService)server.getExecutor()).shutdownNow();}
 }
 private static HttpResponse<String> get(HttpClient c,String url)throws Exception {
  return c.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.ofString());
 }
}
