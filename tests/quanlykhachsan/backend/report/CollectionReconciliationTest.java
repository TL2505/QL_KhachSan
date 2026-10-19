package quanlykhachsan.backend.report;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectionReconciliationTest {
    private BigDecimal money(String value){return new BigDecimal(value);}
    @Test void exactIndependentEvidenceReconciles(){
        var r=CollectionReconciliation.calculate(money("0.30"),money("0.10").add(money("0.20")),money("500.00"),money("500.00"),0);
        assertTrue(r.reconciled());assertEquals(0,r.bank_variance().signum());
    }
    @Test void offsettingAndUnmatchedDiscrepanciesStayVisible(){
        var r=CollectionReconciliation.calculate(money("100.00"),money("99.99"),money("50.00"),money("50.01"),0);
        assertFalse(r.reconciled());assertEquals(money("0.01"),r.bank_variance());assertEquals(money("-0.01"),r.cash_pos_variance());
        assertFalse(CollectionReconciliation.calculate(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,1).reconciled());
        var folioMismatch=CollectionReconciliation.calculate(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,0,money("0.01"));
        assertFalse(folioMismatch.reconciled());assertEquals(money("0.01"),folioMismatch.folio_variance());
        assertThrows(IllegalArgumentException.class,()->CollectionReconciliation.calculate(null,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,0));
    }
}
