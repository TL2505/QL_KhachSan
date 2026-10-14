package quanlykhachsan.backend.booking;

import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.financial.PaymentFixture;
import quanlykhachsan.support.Phase2TestDatabase;
import static org.junit.jupiter.api.Assertions.*;

class FolioQuerySecurityTest {
    @Test void financialQueriesRequireAllowedRoleAndGuestOwnership()throws Exception {
        Phase2TestDatabase.require();try(var fixture=new PaymentFixture(new BigDecimal("1000000.00"))) {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            var ctx=server.createContext("/api/v1/folios",e->{if(e.getRequestURI().getPath().endsWith("vietqr"))new VietQrController("970422","0901234567899","TEST BENEFICIARY").handle(e);else new PaymentPollController().handle(e);});ctx.getFilters().add(new JwtFilter());server.start();
            try {
                var client=HttpClient.newHttpClient();String base="http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/folios/"+fixture.folio;
                String staff=JwtTokenUtil.issueToken(1,"reception","front_desk"),cleaner=JwtTokenUtil.issueToken(1,"cleaner","housekeeping");
                assertEquals(401,get(client,base+"/payment-status",null).statusCode());assertEquals(403,get(client,base+"/payment-status",cleaner).statusCode());
                assertEquals(200,get(client,base+"/payment-status",staff).statusCode());
                var qr=get(client,base+"/vietqr",staff);assertEquals(200,qr.statusCode());assertTrue(qr.body().contains("qr_image_base64"));
                String alien=io.jsonwebtoken.Jwts.builder().subject("999999").claim("role","guest").claim("customer_id",999999).claim("phone","0901234567").expiration(java.util.Date.from(java.time.Instant.now().plusSeconds(300))).signWith(JwtTokenUtil.signingKey()).compact();
                assertEquals(403,get(client,base+"/payment-status",alien).statusCode());assertEquals(403,get(client,base+"/vietqr",alien).statusCode());
            }finally{server.stop(0);}
        }
    }
    private static HttpResponse<String> get(HttpClient c,String url,String token)throws Exception{var q=HttpRequest.newBuilder(URI.create(url));if(token!=null)q.header("Authorization","Bearer "+token);return c.send(q.build(),HttpResponse.BodyHandlers.ofString());}
}
