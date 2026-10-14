package quanlykhachsan.backend.booking;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Financial precision helpers shared by settlement and ledger edge-case coverage. */
public final class FolioLedgerMath {
    private FolioLedgerMath() { }
    public static BigDecimal apply(BigDecimal balance, String transactionType, BigDecimal amount) {
        if (balance == null || amount == null || transactionType == null) throw new IllegalArgumentException("Ledger values are required");
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        if (value.signum() < 0) throw new IllegalArgumentException("Ledger amounts must be non-negative");
        if ("debit".equals(transactionType)) return balance.add(value).setScale(2, RoundingMode.HALF_UP);
        if ("credit".equals(transactionType)) return balance.subtract(value).setScale(2, RoundingMode.HALF_UP);
        throw new IllegalArgumentException("Transaction type must be debit or credit");
    }
    public static BigDecimal convert(BigDecimal amount, BigDecimal exchangeRate) {
        if (amount == null || exchangeRate == null || amount.signum() < 0 || exchangeRate.signum() <= 0)
            throw new IllegalArgumentException("Amount and positive exchange rate are required");
        return amount.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
    }
}
