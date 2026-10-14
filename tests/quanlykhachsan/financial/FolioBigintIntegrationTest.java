package quanlykhachsan.financial;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.UUID;
import quanlykhachsan.backend.booking.*;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.support.Phase2TestDatabase;
class FolioBigintIntegrationTest {
    @Test void identifiersAboveIntegerRangeSurviveSettlementAndReplay()throws Exception {
        Phase2TestDatabase.require();String key=UUID.randomUUID().toString();long high=3000000000L;
        try(PaymentFixture f=new PaymentFixture(BigDecimal.TEN)) {
            try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("UPDATE folios SET id=? WHERE id=?")){q.setLong(1,high);q.setLong(2,f.folio);assertEquals(1,q.executeUpdate());}
            var request=new FolioDao.PaymentRequest(high,key,"synthetic-high-id-fixture",new BigDecimal("5.00"),"cash",false,ZoneId.of("Asia/Ho_Chi_Minh"));
            try {
                try(Connection c=DatabaseConnection.getConnection()) {
                    var first=new FolioService().pay(c,request);assertEquals(200,first.statusCode());assertEquals(first.body(),new FolioService().pay(c,request).body());
                    assertEquals(5,Phase2TestDatabase.scalar("SELECT balance FROM folios WHERE id="+high));
                }
            }finally {try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("DELETE FROM folios WHERE id=? AND booking_id=?")){q.setLong(1,high);q.setInt(2,f.booking);q.executeUpdate();}}
        }finally {
            try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("DELETE FROM idempotency_keys WHERE id=?")){q.setString(1,key);q.executeUpdate();}
            // Restore only this isolated schema's generated-ID sequence after deleting the fixture.
            long next=Phase2TestDatabase.scalar("SELECT COALESCE(MAX(id),0)+1 FROM folios");
            try(Connection c=DatabaseConnection.getConnection();Statement q=c.createStatement()){q.execute("ALTER TABLE folios AUTO_INCREMENT="+next);}
        }
    }
}
