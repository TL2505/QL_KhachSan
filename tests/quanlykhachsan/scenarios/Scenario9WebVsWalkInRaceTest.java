package quanlykhachsan.scenarios;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.booking.*;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.support.Phase2TestDatabase;
import static org.junit.jupiter.api.Assertions.*;

class Scenario9WebVsWalkInRaceTest {
    @Test void twentyHttpRequestsProduceOneBookingAndOneVersionIncrement() throws Exception {
        Phase2TestDatabase.require();int room;
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("INSERT INTO rooms(room_number,room_type_id,price,status) SELECT ?,MIN(id),1000000,'vacant_clean' FROM room_types",Statement.RETURN_GENERATED_KEYS)) {
            q.setString(1,"RACE"+UUID.randomUUID().toString().substring(0,10));q.executeUpdate();try(ResultSet r=q.getGeneratedKeys()){r.next();room=r.getInt(1);}
        }
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var serverWorkers=Executors.newFixedThreadPool(20);server.setExecutor(serverWorkers);
        var publicContext=server.createContext("/api/v1/bookings/public",new PublicBookingController());publicContext.getFilters().add(new JwtFilter());
        var staffContext=server.createContext("/api/v1/bookings/walk-in",new PublicBookingController(true));staffContext.getFilters().add(new JwtFilter());
        server.start();var workers=Executors.newFixedThreadPool(20);
        String phone="0909876543";LocalDate day=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        String body="{\"customer_name\":\"Concurrency Test\",\"identity_card\":\"P123456789\",\"phone\":\""+phone+"\",\"room_id\":"+room+",\"room_version\":0,\"check_in_date\":\""+day+" 14:00:00\",\"check_out_date\":\""+day.plusDays(1)+" 12:00:00\"}";
        String base="http://127.0.0.1:"+server.getAddress().getPort();String token=JwtTokenUtil.issueToken(1,"reception","front_desk");
        List<String> keys=new ArrayList<>();List<Future<HttpResponse<String>>> futures=new ArrayList<>();
        CountDownLatch ready=new CountDownLatch(20),start=new CountDownLatch(1);
        try {
            var client=HttpClient.newHttpClient();
            for(int i=0;i<20;i++) { final boolean staff=i>=10;String key=UUID.randomUUID().toString();keys.add(key);
                futures.add(workers.submit(()->{ready.countDown();if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Race start timed out");return send(client,base,staff,body,key,token);}));
            }
            assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();int success=0,conflicts=0,winner=-1;String winnerBody=null;
            for(int i=0;i<20;i++){var response=futures.get(i).get(30,TimeUnit.SECONDS);if(response.statusCode()==201){success++;winner=i;winnerBody=response.body();}else{assertEquals(409,response.statusCode(),response.body());conflicts++;}}
            assertEquals(1,success);assertEquals(19,conflicts);
            assertEquals(1,Phase2TestDatabase.scalar("SELECT version FROM rooms WHERE id="+room));
            assertEquals(1,Phase2TestDatabase.scalar("SELECT COUNT(*) FROM bookings WHERE room_id="+room));
            var replay=send(client,base,winner>=10,body,keys.get(winner),token);assertEquals(201,replay.statusCode());assertEquals(winnerBody,replay.body());
            assertEquals(400,send(client,base,false,body,null,token).statusCode());
            assertEquals(403,send(client,base,true,body,UUID.randomUUID().toString(),JwtTokenUtil.issueToken(1,"cleaner","housekeeping")).statusCode());
            try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("SELECT f.balance,SUM(t.amount) FROM folios f JOIN bookings b ON b.id=f.booking_id JOIN folio_transactions t ON t.folio_id=f.id WHERE b.room_id=? GROUP BY f.id")) {
                q.setInt(1,room);try(ResultSet r=q.executeQuery()){assertTrue(r.next());assertEquals(r.getBigDecimal(1),r.getBigDecimal(2));}
            }
        } finally {
            workers.shutdownNow();workers.awaitTermination(5,TimeUnit.SECONDS);server.stop(0);serverWorkers.shutdownNow();
            try(Connection c=DatabaseConnection.getConnection();Statement q=c.createStatement()) {
                q.executeUpdate("DELETE f FROM folios f JOIN bookings b ON b.id=f.booking_id WHERE b.room_id="+room);
                q.executeUpdate("DELETE FROM bookings WHERE room_id="+room);q.executeUpdate("DELETE FROM rooms WHERE id="+room);
                try(PreparedStatement d=c.prepareStatement("DELETE FROM customers WHERE phone=?")){d.setString(1,phone);d.executeUpdate();}
                for(String key:keys)try(PreparedStatement d=c.prepareStatement("DELETE FROM idempotency_keys WHERE id=?")){d.setString(1,key);d.executeUpdate();}
            }
        }
    }
    private static HttpResponse<String> send(HttpClient client,String base,boolean staff,String body,String key,String token)throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+"/api/v1/bookings/"+(staff?"walk-in":"public"))).timeout(Duration.ofSeconds(30))
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if(staff)request.header("Authorization","Bearer "+token);if(key!=null)request.header("Idempotency-Key",key);
        return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
}
