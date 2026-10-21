package quanlykhachsan.backend.room;
import java.lang.reflect.Proxy;
import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RoomCreationReceiptTest {
 static class Database {
  boolean duplicate,commitFailure,committed,rolledBack;
  Connection open(){return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
   if(m.getName().equals("commit")){if(commitFailure)throw new SQLException("isolated commit failure");committed=true;}
   if(m.getName().equals("rollback"))rolledBack=true;
   if(m.getName().equals("prepareStatement")){
    boolean insert=((String)a[0]).startsWith("INSERT INTO rooms");
    if(insert)assertEquals(Statement.RETURN_GENERATED_KEYS,a[1]);
    return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(q,method,args)->{
     if(method.getName().equals("executeUpdate"))return 1;
     if(method.getName().equals("executeQuery")||method.getName().equals("getGeneratedKeys")){
      int[] count={0};return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSet.class},(r,get,values)->switch(get.getName()){
       case "next"->++count[0]==1&&(insert||duplicate);
       case "getInt"->42;
       case "close"->null;
       default->throw new AssertionError(get.getName());
      });
     }
     return null;
    });
   }
   return null;
  });}
 }
 private Room room(){return new Room(0,"A42",1,500000,"vacant_dirty");}
 @Test void returnedIdIsPublishedOnlyAfterSuccessfulCommit(){var db=new Database();var room=room();new RoomDAOImpl(db::open).addRoom(room);assertTrue(db.committed);assertEquals(42,room.getId());}
 @Test void failedCommitRollsBackAndDoesNotPublishCreatedId(){var db=new Database();db.commitFailure=true;var room=room();assertThrows(IllegalStateException.class,()->new RoomDAOImpl(db::open).addRoom(room));assertTrue(db.rolledBack);assertEquals(0,room.getId());}
 @Test void duplicateRoomKeepsConflictAndNeverCommits(){var db=new Database();db.duplicate=true;assertThrows(RoomConflictException.class,()->new RoomDAOImpl(db::open).addRoom(room()));assertTrue(db.rolledBack);assertFalse(db.committed);}
}
