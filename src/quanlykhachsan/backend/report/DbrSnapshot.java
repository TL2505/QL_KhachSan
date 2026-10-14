package quanlykhachsan.backend.report;
import java.math.BigDecimal;
import java.util.Map;
public record DbrSnapshot(String business_date,BigDecimal room_revenue,BigDecimal total_revenue,
        int occupied_rooms,int operational_rooms,BigDecimal occupancy_rate,BigDecimal adr,BigDecimal revpar,
        Map<String,BigDecimal> departments,BigDecimal cumulative_debits,BigDecimal cumulative_credits,
        BigDecimal receivables_balance,BigDecimal excess_credit_liability,BigDecimal reconciliation_variance) { }
