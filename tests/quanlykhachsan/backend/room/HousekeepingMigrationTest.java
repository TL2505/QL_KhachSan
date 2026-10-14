package quanlykhachsan.backend.room;

import java.sql.*;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.support.Phase2TestDatabase;
import static org.junit.jupiter.api.Assertions.*;

class HousekeepingMigrationTest {
    @Test void stockAuditsForeignKeysAndOosStateSurviveRepeatMigration()throws Exception {
        Phase2TestDatabase.require();
        try(Connection c=DatabaseConnection.getConnection();Statement q=c.createStatement()) {
            UpdateDbUtility.migrateHousekeeping(c);
            try(ResultSet r=c.getMetaData().getTables(c.getCatalog(),null,"minibar_items",new String[]{"TABLE"})){assertTrue(r.next());}
            assertThrows(SQLException.class,()->q.execute("INSERT INTO minibar_items(room_id,name,unit_price,standard_stock,current_stock) VALUES(2147483647,'Test',1,1,1)"));
            assertThrows(SQLException.class,()->q.execute("INSERT INTO minibar_items(room_id,name,unit_price,standard_stock,current_stock) SELECT MIN(id),'Negative',1,1,-1 FROM rooms"));
            try(ResultSet r=q.executeQuery("SHOW COLUMNS FROM rooms LIKE 'status'")){r.next();assertTrue(r.getString("Type").contains("out_of_service"));}
        }
    }
}
