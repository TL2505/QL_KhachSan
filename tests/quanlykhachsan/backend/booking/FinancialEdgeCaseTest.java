package quanlykhachsan.backend.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FinancialEdgeCaseTest {
    @Test
    void splitAcrossThreeCardsAdjustmentsRefundAndFxRoundingSettleToZero() {
        BigDecimal balance = new BigDecimal("100.00");
        balance = FolioLedgerMath.apply(balance, "credit", new BigDecimal("10.00")); // credit adjustment
        balance = FolioLedgerMath.apply(balance, "credit", new BigDecimal("20.00")); // first card
        balance = FolioLedgerMath.apply(balance, "credit", new BigDecimal("25.00")); // second card
        balance = FolioLedgerMath.apply(balance, "credit", new BigDecimal("30.00")); // third card
        balance = FolioLedgerMath.apply(balance, "debit", FolioLedgerMath.convert(
            new BigDecimal("4.004"), new BigDecimal("1.00"))); // refund rounded to cents
        balance = FolioLedgerMath.apply(balance, "credit", new BigDecimal("19.00"));
        assertEquals(new BigDecimal("0.00"), balance);
    }
}
