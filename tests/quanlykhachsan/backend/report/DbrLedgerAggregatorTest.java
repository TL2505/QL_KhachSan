package quanlykhachsan.backend.report;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.financial.PaymentFixture;
import quanlykhachsan.support.Phase2TestDatabase;
class DbrLedgerAggregatorTest {
    @Test void postedLedgerReconcilesAndMismatchIsRejected()throws Exception {
        Phase2TestDatabase.require();var date=LocalDate.now();var aggregator=new DbrLedgerAggregator();DbrSnapshot baseline;
        try(Connection c=DatabaseConnection.getConnection()){baseline=aggregator.capture(c,date);}
        try(PaymentFixture f=new PaymentFixture(new BigDecimal("1000000"));Connection c=DatabaseConnection.getConnection()) {
            var snapshot=aggregator.capture(c,date);assertEquals(baseline.room_revenue().add(new BigDecimal("1000000.00")),snapshot.room_revenue());assertEquals(new BigDecimal("0.00"),snapshot.reconciliation_variance());
            assertEquals(snapshot.cumulative_debits(),snapshot.cumulative_credits().add(snapshot.receivables_balance()));
            try(PreparedStatement q=c.prepareStatement("UPDATE folios SET balance=balance+0.01 WHERE id=?")){q.setLong(1,f.folio);q.executeUpdate();}
            assertThrows(SQLException.class,()->aggregator.capture(c,date));
            assertEquals(new BigDecimal("30.00"),DbrLedgerAggregator.ratio(new BigDecimal("900"),30));
            assertEquals(new BigDecimal("4.50"),DbrLedgerAggregator.ratio(new BigDecimal("900"),200));
            assertEquals(new BigDecimal("0.00"),DbrLedgerAggregator.ratio(BigDecimal.TEN,0));
        }
    }
}
