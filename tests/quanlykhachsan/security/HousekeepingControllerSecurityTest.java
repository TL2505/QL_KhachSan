package quanlykhachsan.security;

import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.room.*;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.support.Phase2TestDatabase;
import static org.junit.jupiter.api.Assertions.*;

public class HousekeepingControllerSecurityTest {
    @Test public void rolesAndCleaningTransitionsAreEnforcedOverHttp()throws Exception {
        Phase2TestDatabase.require();int room;
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("INSERT INTO rooms(room_number,room_type_id,price,status) SELECT ?,MIN(id),1000000,'vacant_dirty' FROM room_types",Statement.RETURN_GENERATED_KEYS)){q.setString(1,"HK"+UUID.randomUUID().toString().substring(0,10));q.executeUpdate();try(ResultSet r=q.getGeneratedKeys()){r.next();room=r.getInt(1);}}
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/api/v1/rooms",new RoomController()).getFilters().add(new JwtFilter());server.start();
        try {
            String url="http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/rooms/"+room;var client=HttpClient.newHttpClient();
            String desk=JwtTokenUtil.issueToken(1,"desk","front_desk"),cleaner=JwtTokenUtil.issueToken(1,"cleaner","housekeeping");
            String payload="{\"status\":\"vacant_clean\",\"room_version\":0,\"cleaning_duration_seconds\":300,\"inspection_notes\":\"Inspected\"}";
            assertEquals(403,put(client,url+"/housekeeping",payload,desk).statusCode());
            assertEquals(403,put(client,url+"/status",payload,desk).statusCode(),"Legacy status endpoint must not bypass the state machine");
            assertEquals(200,put(client,url+"/housekeeping",payload,cleaner).statusCode());
            assertEquals(1,Phase2TestDatabase.scalar("SELECT COUNT(*) FROM room_status_audits WHERE room_id="+room));
            assertEquals(300,Phase2TestDatabase.scalar("SELECT cleaning_duration_seconds FROM room_status_audits WHERE room_id="+room));
            try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("UPDATE rooms SET status='occupied_clean' WHERE id=?")){q.setInt(1,room);q.executeUpdate();}
            assertEquals(409,put(client,url+"/housekeeping","{\"status\":\"vacant_clean\"}",cleaner).statusCode());
        }finally{server.stop(0);try(Connection c=DatabaseConnection.getConnection();Statement q=c.createStatement()){q.executeUpdate("DELETE FROM room_status_audits WHERE room_id="+room);q.executeUpdate("DELETE FROM rooms WHERE id="+room);}}
    }
    private static HttpResponse<String> put(HttpClient c,String url,String body,String token)throws Exception{return c.send(HttpRequest.newBuilder(URI.create(url)).header("Authorization","Bearer "+token).header("Content-Type","application/json").PUT(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());}
}
