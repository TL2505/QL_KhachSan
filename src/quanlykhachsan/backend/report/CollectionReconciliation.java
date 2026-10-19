package quanlykhachsan.backend.report;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;

/** Independent cashbook/bank evidence is matched per reference, folio and exact amount. */
public final class CollectionReconciliation {
    public record Result(BigDecimal verified_bank,BigDecimal bank_ledger,BigDecimal cash_pos,
            BigDecimal cash_pos_ledger,long unmatched_entries,BigDecimal bank_variance,BigDecimal cash_pos_variance,BigDecimal folio_variance,boolean reconciled) { }
    public static Result calculate(BigDecimal bank,BigDecimal bankLedger,BigDecimal cash,BigDecimal cashLedger,long unmatched){
        return calculate(bank,bankLedger,cash,cashLedger,unmatched,BigDecimal.ZERO);
    }
    public static Result calculate(BigDecimal bank,BigDecimal bankLedger,BigDecimal cash,BigDecimal cashLedger,long unmatched,BigDecimal folioVariance){
        if(bank==null||bankLedger==null||cash==null||cashLedger==null||unmatched<0)throw new IllegalArgumentException("Incomplete reconciliation evidence");
        if(folioVariance==null)throw new IllegalArgumentException("Folio evidence is required");
        BigDecimal bankVariance=bank.subtract(bankLedger),cashVariance=cash.subtract(cashLedger);
        return new Result(bank,bankLedger,cash,cashLedger,unmatched,bankVariance,cashVariance,folioVariance,unmatched==0&&bankVariance.signum()==0&&cashVariance.signum()==0&&folioVariance.signum()==0);
    }
    public Result read(Connection c,LocalDate date)throws SQLException {
        BigDecimal bank=scalar(c,"SELECT COALESCE(SUM(amount),0) FROM bank_webhook_logs WHERE status='PROCESSED' AND business_date<=?",date);
        BigDecimal bankLedger=scalar(c,"SELECT COALESCE(SUM(t.amount),0) FROM folio_transactions t WHERE t.transaction_type='credit' AND t.charge_type IN ('payment','credit_adjustment') AND t.business_date<=? AND EXISTS(SELECT 1 FROM bank_webhook_logs b WHERE b.bank_trans_id=t.reference_id AND b.folio_id=t.folio_id AND b.status='PROCESSED')",date);
        BigDecimal cash=scalar(c,"SELECT COALESCE(SUM(amount),0) FROM staff_collections WHERE business_date<=?",date);
        BigDecimal cashLedger=scalar(c,"SELECT COALESCE(SUM(t.amount),0) FROM folio_transactions t WHERE t.transaction_type='credit' AND t.charge_type='payment' AND t.business_date<=? AND EXISTS(SELECT 1 FROM staff_collections s WHERE s.reference_id=t.reference_id AND s.folio_id=t.folio_id)",date);
        long unmatched=scalar(c,"SELECT COUNT(*) FROM folio_transactions t WHERE t.transaction_type='credit' AND t.charge_type IN ('payment','credit_adjustment') AND t.business_date<=? AND NOT EXISTS(SELECT 1 FROM bank_webhook_logs b WHERE b.bank_trans_id=t.reference_id AND b.folio_id=t.folio_id AND b.status='PROCESSED') AND NOT EXISTS(SELECT 1 FROM staff_collections s WHERE s.reference_id=t.reference_id AND s.folio_id=t.folio_id AND t.charge_type='payment')",date).longValueExact();
        unmatched+=scalar(c,"SELECT COUNT(*) FROM bank_webhook_logs b WHERE (b.business_date<=? OR b.business_date IS NULL) AND (b.status<>'PROCESSED' OR b.business_date IS NULL OR b.amount<>(SELECT COALESCE(SUM(t.amount),0) FROM folio_transactions t WHERE t.reference_id=b.bank_trans_id AND t.folio_id=b.folio_id AND t.transaction_type='credit' AND t.charge_type IN ('payment','credit_adjustment') AND t.business_date=b.business_date))",date).longValueExact();
        unmatched+=scalar(c,"SELECT COUNT(*) FROM staff_collections s WHERE s.business_date<=? AND s.amount<>(SELECT COALESCE(SUM(t.amount),0) FROM folio_transactions t WHERE t.reference_id=s.reference_id AND t.folio_id=s.folio_id AND t.transaction_type='credit' AND t.charge_type='payment' AND t.business_date=s.business_date)",date).longValueExact();
        BigDecimal ledger=scalar(c,"SELECT COALESCE(SUM(CASE WHEN transaction_type='debit' THEN amount WHEN charge_type<>'credit_adjustment' THEN -amount ELSE 0 END),0) FROM folio_transactions WHERE business_date<=?",date);
        BigDecimal balances;
        try(var q=c.createStatement();var r=q.executeQuery("SELECT COALESCE(SUM(balance),0) FROM folios")){if(!r.next())throw new SQLException("Folio balances unavailable");balances=r.getBigDecimal(1);}
        return calculate(bank,bankLedger,cash,cashLedger,unmatched,ledger.subtract(balances));
    }
    private BigDecimal scalar(Connection c,String sql,LocalDate date)throws SQLException{
        try(var q=c.prepareStatement(sql)){q.setDate(1,java.sql.Date.valueOf(date));try(var r=q.executeQuery()){if(!r.next()||r.getBigDecimal(1)==null)throw new SQLException("Reconciliation source missing");return r.getBigDecimal(1);}}
    }
}
