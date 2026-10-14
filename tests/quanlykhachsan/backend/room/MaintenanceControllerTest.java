package quanlykhachsan.backend.room;

import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.support.Phase2TestDatabase;
import static org.junit.jupiter.api.Assertions.*;

class MaintenanceControllerTest {
    @Test void conflictingReservationRequiresManagerPinAndReplayDoesNotRepeatAudit()throws Exception {
        Phase2TestDatabase.require();int room,booking;
        try(Connection c=DatabaseConnection.getConnection()) {
            try(PreparedStatement q=c.prepareStatement("INSERT INTO rooms(room_number,room_type_id,price,status) SELECT ?,MIN(id),1000000,'reserved' FROM room_types",Statement.RETURN_GENERATED_KEYS)){q.setString(1,"MT"+UUID.randomUUID().toString().substring(0,10));q.executeUpdate();try(ResultSet r=q.getGeneratedKeys()){r.next();room=r.getInt(1);}}
            try(PreparedStatement q=c.prepareStatement("INSERT INTO bookings(customer_id,room_id,check_in_date,check_out_date,status) SELECT MIN(id),?,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 1 DAY),DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 2 DAY),'confirmed' FROM customers",Statement.RETURN_GENERATED_KEYS)){q.setInt(1,room);q.executeUpdate();try(ResultSet r=q.getGeneratedKeys()){r.next();booking=r.getInt(1);}}
        }
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var context=server.createContext("/api/v1/rooms",new MaintenanceController(SecurityUtil.hashPassword("246810")));context.getFilters().add(new JwtFilter());server.start();
        String key=UUID.randomUUID().toString();
        try {
            String url="http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/rooms/"+room+"/maintenance";var client=HttpClient.newHttpClient();
            String fields="\"status\":\"out_of_order\",\"reason\":\"Test AC repair\",\"estimated_finish\":\""+LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(3).withNano(0)+"\"";
            String token=JwtTokenUtil.issueToken(1,"engineer","maintenance");
            assertEquals(400,put(client,url,"{"+fields+"}",token,null).statusCode());
            assertEquals(409,put(client,url,"{"+fields+"}",token,key).statusCode());
            assertEquals(403,put(client,url,"{"+fields+",\"manager_pin\":\"000000\"}",token,key).statusCode());
            String body="{"+fields+",\"manager_pin\":\"246810\"}";var result=put(client,url,body,token,key);assertEquals(200,result.statusCode(),result.body());
            assertEquals(result.body(),put(client,url,body,token,key).body());assertEquals(1,Phase2TestDatabase.scalar("SELECT COUNT(*) FROM room_status_audits WHERE room_id="+room));
            assertEquals(403,put(client,url,body,JwtTokenUtil.issueToken(1,"desk","front_desk"),UUID.randomUUID().toString()).statusCode());
        }finally{server.stop(0);try(Connection c=DatabaseConnection.getConnection();Statement q=c.createStatement()){q.executeUpdate("DELETE FROM room_status_audits WHERE room_id="+room);q.executeUpdate("DELETE FROM bookings WHERE id="+booking);q.executeUpdate("DELETE FROM rooms WHERE id="+room);try(PreparedStatement d=c.prepareStatement("DELETE FROM idempotency_keys WHERE id=?")){d.setString(1,key);d.executeUpdate();}}}
    }
    private static HttpResponse<String> put(HttpClient c,String url,String body,String token,String key)throws Exception{var q=HttpRequest.newBuilder(URI.create(url)).header("Authorization","Bearer "+token).header("Content-Type","application/json").PUT(HttpRequest.BodyPublishers.ofString(body));if(key!=null)q.header("Idempotency-Key",key);return c.send(q.build(),HttpResponse.BodyHandlers.ofString());}
}
