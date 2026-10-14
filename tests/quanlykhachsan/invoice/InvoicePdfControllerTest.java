package quanlykhachsan.invoice;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.sql.*;
import java.net.*;
import java.net.http.*;
import com.sun.net.httpserver.*;
import quanlykhachsan.backend.invoice.*;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.financial.PaymentFixture;
import quanlykhachsan.support.Phase2TestDatabase;
class InvoicePdfControllerTest {
    @Test void finalizedPdfIsRestrictedAndMissingConfigurationFailsClosed()throws Exception {
        Phase2TestDatabase.require();
        try(PaymentFixture fixture=new PaymentFixture(new BigDecimal("3402000"))) {
            long invoice;int customer;
            try(Connection c=DatabaseConnection.getConnection()) {
                try(PreparedStatement q=c.prepareStatement("UPDATE folio_transactions SET amount=3000000 WHERE folio_id=?")){q.setLong(1,fixture.folio);q.executeUpdate();}
                try(PreparedStatement q=c.prepareStatement("INSERT INTO folio_transactions(folio_id,transaction_type,charge_type,amount,business_date) VALUES (?,'debit','service_charge',150000,CURRENT_DATE),(?,'debit','vat',252000,CURRENT_DATE)")){q.setLong(1,fixture.folio);q.setLong(2,fixture.folio);q.executeUpdate();}
                try(PreparedStatement q=c.prepareStatement("INSERT INTO folio_transactions(folio_id,transaction_type,charge_type,amount,business_date) VALUES (?,'credit','payment',3402000,CURRENT_DATE)")){q.setLong(1,fixture.folio);q.executeUpdate();}
                try(PreparedStatement q=c.prepareStatement("UPDATE folios SET balance=0,status='settled' WHERE id=?")){q.setLong(1,fixture.folio);q.executeUpdate();}
                try(PreparedStatement q=c.prepareStatement("UPDATE bookings SET status='checked_out' WHERE id=?")){q.setInt(1,fixture.booking);q.executeUpdate();}
                c.setAutoCommit(false);InvoiceFinalizationService.finalizePaid(c,fixture.booking);c.commit();
                try(PreparedStatement q=c.prepareStatement("SELECT i.id,b.customer_id FROM invoices i JOIN bookings b ON b.id=i.booking_id WHERE i.booking_id=?")){q.setInt(1,fixture.booking);try(ResultSet r=q.executeQuery()){r.next();invoice=r.getLong(1);customer=r.getInt(2);}}
            }
            var hotel=new VatInvoicePdfGenerator.Hotel("SYNTHETIC HOTEL","Synthetic test address","SYNTHETIC-MST","https://invoice.example.invalid/verify");
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var context=server.createContext("/api/v1/invoices/",new InvoicePdfController(hotel));context.getFilters().add(new JwtFilter());server.start();
            try {
                var client=HttpClient.newHttpClient();var uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/invoices/"+invoice+"/pdf");
                assertEquals(401,send(client,uri,null).statusCode());
                for(String role:new String[]{"housekeeping","maintenance","f&b"})assertEquals(403,send(client,uri,JwtTokenUtil.issueToken(1,"fixture",role)).statusCode());
                assertEquals(200,send(client,uri,guest(customer)).statusCode());assertEquals(403,send(client,uri,guest(customer+100000)).statusCode());
                var response=send(client,uri,JwtTokenUtil.issueToken(1,"fixture","front_desk"));assertEquals(200,response.statusCode());assertEquals("application/pdf",response.headers().firstValue("Content-Type").orElse(""));assertTrue(new String(response.body(),java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-"));assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
                server.removeContext(context);var unavailable=server.createContext("/api/v1/invoices/",new InvoicePdfController());unavailable.getFilters().add(new JwtFilter());
                if(System.getenv("HOTEL_LEGAL_NAME")==null)assertEquals(503,send(client,uri,JwtTokenUtil.issueToken(1,"fixture","front_desk")).statusCode());
            }finally{server.stop(0);}
        }
    }
    private static HttpResponse<byte[]> send(HttpClient client,URI uri,String token)throws Exception {var b=HttpRequest.newBuilder(uri).GET();if(token!=null)b.header("Authorization","Bearer "+token);return client.send(b.build(),HttpResponse.BodyHandlers.ofByteArray());}
    private static String guest(int customer){return io.jsonwebtoken.Jwts.builder().subject("synthetic-guest").claim("role","guest").claim("customer_id",customer).expiration(java.util.Date.from(java.time.Instant.now().plusSeconds(300))).signWith(JwtTokenUtil.signingKey()).compact();}
}
