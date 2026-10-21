package quanlykhachsan.backend.room;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.*;
import static org.junit.jupiter.api.Assertions.*;
class AdminRoomOrderApiTest {
 static class Service extends RoomService {
  List<Boolean> orders=new ArrayList<>();AtomicInteger writes=new AtomicInteger();
  @Override public List<Room> getAllRooms(boolean newest){orders.add(newest);Room room=new Room(42,"A42",1,500000,"vacant_dirty");room.setCreatedAt(Timestamp.valueOf("2026-10-21 12:00:00").toLocalDateTime());return List.of(room);}
  @Override public RoomType getRoomTypeById(int id){return new RoomType(1,"Standard","",500000,2);}
  @Override public boolean addRoom(Room room){writes.incrementAndGet();room.setId(42);return true;}
 }
 private HttpResponse<String> request(Service service,String suffix,String role,String method)throws Exception {
  System.setProperty("hotel.jwt.secret","isolated-room-order-jwt-secret-at-least-32-bytes");
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/api/v1/rooms",new RoomController(service)).getFilters().add(new JwtFilter());server.start();
  try{
   var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/rooms"+suffix)).header("X-User-Role","ADMIN");
   if(role!=null)builder.header("Authorization","Bearer "+JwtTokenUtil.issueToken(7,"isolated-admin",role));
   String body=method.equals("POST")?"{\"roomNumber\":\"A42\",\"roomTypeId\":1,\"price\":500000,\"status\":\"vacant_dirty\"}":"";
   return HttpClient.newHttpClient().send(builder.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
  }finally{server.stop(0);}
 }
 @Test void adminNewestAndDefaultStaffRoomListsUseSeparateBackendOrders()throws Exception {
  var service=new Service();var newest=request(service,"?sort=newest","ADMIN","GET");
  assertEquals(200,newest.statusCode());assertEquals("no-store",newest.headers().firstValue("Cache-Control").orElseThrow());
  assertEquals("2026-10-21T12:00",JsonParser.parseString(newest.body()).getAsJsonObject().getAsJsonArray("data").get(0).getAsJsonObject().get("createdAt").getAsString());
  assertEquals(200,request(service,"","STAFF","GET").statusCode());assertEquals(List.of(true,false),service.orders);
 }
 @Test void sortIsWhitelistedAndCannotGrantAdminPermissions()throws Exception {
  var service=new Service();assertEquals(403,request(service,"?sort=newest",null,"GET").statusCode());assertEquals(403,request(service,"?sort=newest","STAFF","GET").statusCode());
  assertEquals(400,request(service,"?sort=created_at%3BDROP","ADMIN","GET").statusCode());assertTrue(service.orders.isEmpty());
 }
 @Test void createReturnsAuthoritativeRoomIdAndKeepsWriteAuthorization()throws Exception {
  var service=new Service();var response=request(service,"","ADMIN","POST");assertEquals(201,response.statusCode());
  assertEquals(42,JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("data").get("id").getAsInt());
  assertEquals(403,request(service,"","STAFF","POST").statusCode());assertEquals(403,request(service,"",null,"POST").statusCode());assertEquals(1,service.writes.get());
 }
}
