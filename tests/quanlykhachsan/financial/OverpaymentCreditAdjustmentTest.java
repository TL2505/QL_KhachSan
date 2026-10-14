package quanlykhachsan.financial;

import java.math.BigDecimal;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.booking.FolioPaymentService;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.support.Phase2TestDatabase;
import static org.junit.jupiter.api.Assertions.*;

class OverpaymentCreditAdjustmentTest {
    @Test void overpaymentPostsAppliedCreditAndSeparateExcessLiability()throws Exception {
        Phase2TestDatabase.require();try(var f=new PaymentFixture(new BigDecimal("1000000.00"))) {
            new FolioPaymentService().settle(new FolioPaymentService.Payment(UUID.randomUUID().toString(),new BigDecimal("1500000.00"),f.code,"test","{}"));
            try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("SELECT charge_type,amount FROM folio_transactions WHERE folio_id=? AND transaction_type='credit' ORDER BY id")) {
                q.setLong(1,f.folio);try(ResultSet r=q.executeQuery()){assertTrue(r.next());assertEquals("payment",r.getString(1));assertEquals(new BigDecimal("1000000.00"),r.getBigDecimal(2));assertTrue(r.next());assertEquals("credit_adjustment",r.getString(1));assertEquals(new BigDecimal("500000.00"),r.getBigDecimal(2));assertFalse(r.next());}
            }
            assertEquals(0,Phase2TestDatabase.scalar("SELECT balance FROM folios WHERE id="+f.folio));
        }
    }
}
