package quanlykhachsan.performance;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import com.sun.net.httpserver.*;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.backend.room.MinibarController;
import quanlykhachsan.support.Phase2TestDatabase;
public class MinibarConcurrencyTest {
    @Test public void twentyRoomsPostStockAndGuestLedgersWithoutLeaks()throws Exception {
        Phase2TestDatabase.require();List<int[]> fixtures=new ArrayList<>();List<String> keys=new ArrayList<>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var handlers=Executors.newFixedThreadPool(20);server.setExecutor(handlers);var context=server.createContext("/api/v1/rooms/",new MinibarController());context.getFilters().add(new JwtFilter());server.start();var workers=Executors.newFixedThreadPool(20);
        try {
            try(Connection c=DatabaseConnection.getConnection()) {
                for(int i=0;i<20;i++) {
                    int room,booking,item;
                    try(PreparedStatement q=c.prepareStatement("INSERT INTO rooms(room_number,room_type_id,price,status) SELECT ?,MIN(id),1000000,'occupied_clean' FROM room_types",Statement.RETURN_GENERATED_KEYS)){q.setString(1,"LOAD"+UUID.randomUUID().toString().substring(0,12));q.executeUpdate();try(ResultSet r=q.getGeneratedKeys()){r.next();room=r.getInt(1);}}
                    try(PreparedStatement q=c.prepareStatement("INSERT INTO bookings(customer_id,room_id,check_in_date,check_out_date,status) SELECT MIN(id),?,NOW(),DATE_ADD(NOW(),INTERVAL 1 DAY),'checked_in' FROM customers",Statement.RETURN_GENERATED_KEYS)){q.setInt(1,room);q.executeUpdate();try(ResultSet r=q.getGeneratedKeys()){r.next();booking=r.getInt(1);}}
                    try(PreparedStatement q=c.prepareStatement("INSERT INTO minibar_items(room_id,name,unit_price,standard_stock,current_stock) VALUES (?,'Synthetic load fixture',50000,5,5)",Statement.RETURN_GENERATED_KEYS)){q.setInt(1,room);q.executeUpdate();try(ResultSet r=q.getGeneratedKeys()){r.next();item=r.getInt(1);}}
                    fixtures.add(new int[]{room,booking,item});keys.add(UUID.randomUUID().toString());
                }
            }
            var start=new CountDownLatch(1);List<Future<HttpResponse<String>>> futures=new ArrayList<>();var client=HttpClient.newHttpClient();String token=JwtTokenUtil.issueToken(1,"Synthetic cleaner","housekeeping");
            for(int i=0;i<20;i++){int[] f=fixtures.get(i);String key=keys.get(i);futures.add(workers.submit(()->{start.await();var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/rooms/"+f[0]+"/minibar")).timeout(Duration.ofSeconds(20)).header("Authorization","Bearer "+token).header("Idempotency-Key",key).POST(HttpRequest.BodyPublishers.ofString("{\"items\":[{\"item_id\":"+f[2]+",\"quantity\":2}]}")).build();return client.send(request,HttpResponse.BodyHandlers.ofString());}));}
            start.countDown();for(var future:futures){var response=future.get(25,TimeUnit.SECONDS);assertEquals(201,response.statusCode(),response.body());}
            for(int[] f:fixtures){assertEquals(3,Phase2TestDatabase.scalar("SELECT current_stock FROM minibar_items WHERE id="+f[2]));assertEquals(100000,Phase2TestDatabase.scalar("SELECT SUM(balance) FROM folios WHERE booking_id="+f[1]));assertEquals(1,Phase2TestDatabase.scalar("SELECT COUNT(*) FROM folio_transactions t JOIN folios f ON f.id=t.folio_id WHERE f.booking_id="+f[1]+" AND charge_type='minibar'"));}
            assertEquals(0,DatabaseConnection.activeConnections());assertEquals(0,DatabaseConnection.awaitingConnections());
        }finally {
            workers.shutdownNow();server.stop(0);handlers.shutdownNow();workers.awaitTermination(5,TimeUnit.SECONDS);handlers.awaitTermination(5,TimeUnit.SECONDS);
            try(Connection c=DatabaseConnection.getConnection();Statement q=c.createStatement()){for(int[] f:fixtures){q.executeUpdate("DELETE FROM folios WHERE booking_id="+f[1]);q.executeUpdate("DELETE FROM bookings WHERE id="+f[1]);q.executeUpdate("DELETE FROM rooms WHERE id="+f[0]);}for(String key:keys)try(PreparedStatement d=c.prepareStatement("DELETE FROM idempotency_keys WHERE id=?")){d.setString(1,key);d.executeUpdate();}}
        }
    }
}
