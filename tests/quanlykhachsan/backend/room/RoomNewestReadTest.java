package quanlykhachsan.backend.room;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RoomNewestReadTest {
 private Connection source(boolean newest, boolean fail){
  return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
   if(!m.getName().equals("prepareStatement"))throw new AssertionError("Must preserve caller connection: "+m.getName());
   assertEquals("SELECT r.*, t.name AS type_name FROM rooms r JOIN room_types t ON t.id=r.room_type_id ORDER BY "+(newest?"r.created_at DESC, r.id DESC":"r.id"),a[0]);
   return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(q,method,args)->{
    if(method.getName().equals("executeQuery")){
     if(fail)throw new SQLException("Isolated read failure","08001");
     int[] ids=newest?new int[]{7,3,99}:new int[]{3,7,99};int[] index={-1};
     return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSet.class},(r,get,values)->switch(get.getName()){
      case "next"->++index[0]<ids.length;
      case "getInt"->values[0].equals("id")?ids[index[0]]:1;
      case "getDouble"->500000d;
      case "getString"->"vacant_dirty";
      case "getTimestamp"->Timestamp.valueOf(ids[index[0]]==99?"2025-10-21 12:00:00":"2026-10-21 12:00:00");
      case "close"->null;
      default->throw new AssertionError(get.getName());
     });
    }
    if(method.getName().equals("close"))return null;
    throw new AssertionError(method.getName());
   });
  });
 }
 @Test void newestOrderComesFromSqlAndMapsRealTimestamp()throws Exception{
  var rooms=RoomDAOImpl.readRooms(source(true,false),true);
  assertEquals(List.of(7,3,99),rooms.stream().map(Room::getId).toList());
  assertEquals("2026-10-21T12:00",RoomMapper.toRoomResponse(rooms.get(0)).getCreatedAt());
 }
 @Test void normalRoomMapOrderRemainsAscending()throws Exception{
  assertEquals(List.of(3,7,99),RoomDAOImpl.readRooms(source(false,false),false).stream().map(Room::getId).toList());
 }
 @Test void databaseFailureDoesNotBecomeEmptySuccess(){assertThrows(SQLException.class,()->RoomDAOImpl.readRooms(source(true,true),true));}
 @Test void creationFieldRemainsCompatibleWithDesktopRoomDeserialization()throws Exception{
  var room=RoomDAOImpl.readRooms(source(true,false),true).get(0);
  var gson=quanlykhachsan.backend.utils.JsonUtil.getGson();
  var decoded=gson.fromJson(gson.toJson(RoomMapper.toRoomResponse(room)),Room.class);
  assertEquals(room.getCreatedAt(),decoded.getCreatedAt());assertEquals(room.getId(),decoded.getId());
 }
}
