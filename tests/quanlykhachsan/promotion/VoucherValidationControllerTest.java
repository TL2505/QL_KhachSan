package quanlykhachsan.promotion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.net.*;
import java.net.http.*;
import java.time.LocalDate;
import java.util.UUID;
import com.sun.net.httpserver.*;
import com.google.gson.*;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.backend.promotion.VoucherValidationController;
import quanlykhachsan.support.Phase2TestDatabase;
class VoucherValidationControllerTest {
    @Test void clientTotalCannotSetPriceAndDatesAreRequired()throws Exception {
        Phase2TestDatabase.require();String code="HTTP_"+UUID.randomUUID().toString().substring(0,8);int id;
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("INSERT INTO promotions(name,code,discount_type,discount_value,start_date,end_date,status,max_discount,minimum_spend,max_uses_per_user) VALUES ('Synthetic HTTP fixture',?,'percentage',20,DATE_SUB(NOW(),INTERVAL 1 DAY),DATE_ADD(NOW(),INTERVAL 1 DAY),'active',500000,0,1)",Statement.RETURN_GENERATED_KEYS)){q.setString(1,code);q.executeUpdate();try(ResultSet r=q.getGeneratedKeys()){r.next();id=r.getInt(1);}}
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var context=server.createContext("/api/v1/promotions/validate",new VoucherValidationController());context.getFilters().add(new JwtFilter());server.start();
        try {
            var client=HttpClient.newHttpClient();var uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/promotions/validate");
            JsonObject b=new JsonObject();b.addProperty("code",code);b.addProperty("total_amount",1);
            assertEquals(400,post(client,uri,b).statusCode());b.addProperty("room_type_id",Phase2TestDatabase.scalar("SELECT MIN(id) FROM room_types"));b.addProperty("check_in_date",LocalDate.now().plusDays(2).toString());b.addProperty("check_out_date",LocalDate.now().plusDays(3).toString());
            var first=post(client,uri,b);assertEquals(200,first.statusCode(),first.body());b.addProperty("total_amount",999999999);
            var second=post(client,uri,b);assertEquals(200,second.statusCode());assertEquals(JsonParser.parseString(first.body()).getAsJsonObject().get("data"),JsonParser.parseString(second.body()).getAsJsonObject().get("data"));
            assertEquals(0,Phase2TestDatabase.scalar("SELECT COUNT(*) FROM promotion_redemptions WHERE promotion_id="+id));
        }finally{server.stop(0);try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("DELETE FROM promotions WHERE id=?")){q.setInt(1,id);q.executeUpdate();}}
    }
    private static HttpResponse<String> post(HttpClient client,URI uri,JsonObject b)throws Exception{return client.send(HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.ofString(b.toString())).build(),HttpResponse.BodyHandlers.ofString());}
}
