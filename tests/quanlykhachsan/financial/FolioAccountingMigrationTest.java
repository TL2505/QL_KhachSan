package quanlykhachsan.financial;

import java.sql.*;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.support.Phase2TestDatabase;
import static org.junit.jupiter.api.Assertions.*;

class FolioAccountingMigrationTest {
    @Test void folioIdCodeParityForeignKeysAndNonnegativeCheckAreEnforced()throws Exception {
        Phase2TestDatabase.require();
        try(Connection c=DatabaseConnection.getConnection();Statement q=c.createStatement()) {
            UpdateDbUtility.migrateFolioAccounting(c);
            try(ResultSet r=c.getMetaData().getColumns(c.getCatalog(),null,"folios","id")){assertTrue(r.next());assertEquals(Types.BIGINT,r.getInt("DATA_TYPE"));}
            try(ResultSet r=c.getMetaData().getColumns(c.getCatalog(),null,"folio_transactions","folio_id")){assertTrue(r.next());assertEquals(Types.BIGINT,r.getInt("DATA_TYPE"));}
            assertThrows(SQLException.class,()->q.executeUpdate("INSERT INTO folios(booking_id,folio_type,balance,code) SELECT MIN(id),'master',-100,'TEST-NEG' FROM bookings"));
            assertThrows(SQLException.class,()->q.executeUpdate("INSERT INTO bank_webhook_logs(bank_trans_id,folio_id,amount,signature,status,response_body) VALUES('TEST-INVALID-FK',9223372036854775807,1,'test','PROCESSED','{}')"));
        }
    }
}
