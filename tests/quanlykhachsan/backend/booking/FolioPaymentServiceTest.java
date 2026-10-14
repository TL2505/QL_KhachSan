package quanlykhachsan.backend.booking;

import java.math.BigDecimal;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import quanlykhachsan.financial.PaymentFixture;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.support.Phase2TestDatabase;
import static org.junit.jupiter.api.Assertions.*;

class FolioPaymentServiceTest {
    @Test void partialExactAndOverpaymentsKeepBalancesSafeAndReplayStable()throws Exception {
        Phase2TestDatabase.require();
        for(String amount:new String[]{"500000.00","1000000.00","1500000.00"})try(var f=new PaymentFixture(new BigDecimal("1000000.00"))) {
            var payment=new FolioPaymentService.Payment(UUID.randomUUID().toString(),new BigDecimal(amount),f.code,"test-signature","{}");
            var service=new FolioPaymentService();String response=service.settle(payment);assertEquals(response,service.settle(payment));
            try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("SELECT balance,status FROM folios WHERE id=?")){q.setLong(1,f.folio);try(ResultSet r=q.executeQuery()){r.next();assertEquals(new BigDecimal("1000000.00").subtract(new BigDecimal(amount)).max(new BigDecimal("0.00")),r.getBigDecimal(1));assertEquals(amount.equals("500000.00")?"open":"settled",r.getString(2));}}
        }
    }
}
