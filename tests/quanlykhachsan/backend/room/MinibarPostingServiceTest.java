package quanlykhachsan.backend.room;

import java.sql.*;
import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.support.Phase2TestDatabase;
import static org.junit.jupiter.api.Assertions.*;

class MinibarPostingServiceTest {
    @Test void inventoryGuestChargeAndRollbackAreAtomic()throws Exception {
        Phase2TestDatabase.require();int room,booking,item;
        try(Connection c=DatabaseConnection.getConnection()) {
            try(PreparedStatement q=c.prepareStatement("INSERT INTO rooms(room_number,room_type_id,price,status) SELECT ?,MIN(id),1000000,'occupied_clean' FROM room_types",Statement.RETURN_GENERATED_KEYS)){q.setString(1,"MB"+UUID.randomUUID().toString().substring(0,10));q.executeUpdate();try(ResultSet r=q.getGeneratedKeys()){r.next();room=r.getInt(1);}}
            try(PreparedStatement q=c.prepareStatement("INSERT INTO bookings(customer_id,room_id,check_in_date,check_out_date,status) SELECT MIN(id),?,CURRENT_TIMESTAMP,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 1 DAY),'checked_in' FROM customers",Statement.RETURN_GENERATED_KEYS)){q.setInt(1,room);q.executeUpdate();try(ResultSet r=q.getGeneratedKeys()){r.next();booking=r.getInt(1);}}
            try(PreparedStatement q=c.prepareStatement("INSERT INTO minibar_items(room_id,name,unit_price,standard_stock,current_stock) VALUES(?,'Test Evian',50000,5,5)",Statement.RETURN_GENERATED_KEYS)){q.setInt(1,room);q.executeUpdate();try(ResultSet r=q.getGeneratedKeys()){r.next();item=r.getInt(1);}}
        }
        try(Connection c=DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);var service=new MinibarPostingService();var result=service.post(c,room,List.of(new MinibarPostingService.Item(item,2)),UUID.randomUUID().toString());c.commit();
            assertEquals(new BigDecimal("100000.00"),result.get("amount").getAsBigDecimal());
            assertEquals(3,Phase2TestDatabase.scalar("SELECT current_stock FROM minibar_items WHERE id="+item));
            assertEquals(100000,Phase2TestDatabase.scalar("SELECT balance FROM folios WHERE id="+result.get("folio_id").getAsLong()));
            assertThrows(IllegalStateException.class,()->service.post(c,room,List.of(new MinibarPostingService.Item(item,4)),UUID.randomUUID().toString()));c.rollback();
            assertEquals(3,Phase2TestDatabase.scalar("SELECT current_stock FROM minibar_items WHERE id="+item));
            service.post(c,room,List.of(new MinibarPostingService.Item(item,1)),UUID.randomUUID().toString());c.rollback();
            assertEquals(3,Phase2TestDatabase.scalar("SELECT current_stock FROM minibar_items WHERE id="+item));
        }finally{try(Connection c=DatabaseConnection.getConnection();Statement q=c.createStatement()){q.executeUpdate("DELETE FROM folios WHERE booking_id="+booking);q.executeUpdate("DELETE FROM bookings WHERE id="+booking);q.executeUpdate("DELETE FROM rooms WHERE id="+room);}}
    }
}
