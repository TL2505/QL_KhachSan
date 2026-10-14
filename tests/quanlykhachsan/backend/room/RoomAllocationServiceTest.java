package quanlykhachsan.backend.room;

import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.support.Phase2TestDatabase;
import static org.junit.jupiter.api.Assertions.*;

class RoomAllocationServiceTest {
    @Test void casIncrementsOnceAndDifferentiatesStaleVersionFromUnavailableRoom() throws Exception {
        Phase2TestDatabase.require(); int room;
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("INSERT INTO rooms(room_number,room_type_id,price,status) SELECT ?,MIN(id),1000000,'vacant_clean' FROM room_types",Statement.RETURN_GENERATED_KEYS)) {
            q.setString(1,"T"+UUID.randomUUID().toString().substring(0,12));q.executeUpdate();try(ResultSet r=q.getGeneratedKeys()){r.next();room=r.getInt(1);}
        }
        try(Connection c=DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            assertThrows(OptimisticLockException.class,()->RoomAllocationService.reserve(c,room,10));c.rollback();
            RoomAllocationService.reserve(c,room,0);c.commit();
            assertEquals(1,RoomAllocationService.currentVersion(room));
            assertThrows(RoomUnavailableException.class,()->RoomAllocationService.reserve(c,room,1));c.rollback();
        } finally {try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("DELETE FROM rooms WHERE id=?")){q.setInt(1,room);q.executeUpdate();}}
    }
}
