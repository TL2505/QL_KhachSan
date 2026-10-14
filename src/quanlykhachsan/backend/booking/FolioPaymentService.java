package quanlykhachsan.backend.booking;

import com.google.gson.JsonObject;
import java.math.*;
import java.sql.*;
import quanlykhachsan.backend.utils.*;

/** Bank transaction, applied credit, excess liability and delivery audit commit together. */
public final class FolioPaymentService {
    public String settle(Payment payment)throws SQLException {
        if(payment.bankTransId()==null || !payment.bankTransId().matches("[A-Za-z0-9_-]{1,100}")
                || payment.reference()==null || !payment.reference().matches("[A-Za-z0-9-]{1,64}")
                || payment.amount()==null || payment.amount().signum()<=0 || payment.amount().scale()>2
                || payment.amount().compareTo(new BigDecimal("9999999999.99"))>0)throw new IllegalArgumentException("Invalid bank payment");
        try(Connection c=DatabaseConnection.getConnection()) {
            FolioDao locks=new FolioDao();String lockKey="bank:"+payment.bankTransId();locks.acquireLock(c,lockKey);
            try {
                c.setAutoCommit(false);
                long folio;BigDecimal balance;String status;
                try(PreparedStatement q=c.prepareStatement("SELECT id,balance,status FROM folios WHERE code=? FOR UPDATE")) {
                    q.setString(1,payment.reference());try(ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Unknown payment reference");folio=r.getLong(1);balance=r.getBigDecimal(2);status=r.getString(3);}
                }
                try(PreparedStatement q=c.prepareStatement("SELECT folio_id,amount,response_body FROM bank_webhook_logs WHERE bank_trans_id=?")) {
                    q.setString(1,payment.bankTransId());try(ResultSet r=q.executeQuery()){if(r.next()){
                        if(r.getLong(1)!=folio || r.getBigDecimal(2).compareTo(payment.amount())!=0)throw new IllegalStateException("Bank transaction was reused with different data");
                        String response=r.getString(3);delivery(c,payment.bankTransId(),"DUPLICATE");c.commit();return response;
                    }}
                }
                if(status.equals("closed"))throw new IllegalStateException("Folio is closed");
                BigDecimal applied=payment.amount().min(balance).setScale(2),excess=payment.amount().subtract(applied).setScale(2);
                if(applied.signum()>0)credit(c,folio,"payment",applied,payment.bankTransId(),"Bank transfer payment");
                if(excess.signum()>0)credit(c,folio,"credit_adjustment",excess,payment.bankTransId(),"Excess overpayment credit");
                BigDecimal remaining=balance.subtract(applied).setScale(2);
                try(PreparedStatement q=c.prepareStatement("UPDATE folios SET balance=?,status=? WHERE id=?")) {q.setBigDecimal(1,remaining);q.setString(2,remaining.signum()==0?"settled":"open");q.setLong(3,folio);if(q.executeUpdate()!=1)throw new SQLException("Folio disappeared");}
                JsonObject data=new JsonObject();data.addProperty("status",remaining.signum()==0?"SETTLED":"PARTIAL");data.addProperty("folio_id",folio);data.addProperty("balance",remaining);data.addProperty("excess_credit",excess);
                String response=ApiResponseUtil.successWithData(data);
                try(PreparedStatement q=c.prepareStatement("INSERT INTO bank_webhook_logs(bank_trans_id,folio_id,amount,signature,status,raw_payload,response_body) VALUES (?,?,?,?,'PROCESSED',?,?)")) {
                    q.setString(1,payment.bankTransId());q.setLong(2,folio);q.setBigDecimal(3,payment.amount());q.setString(4,payment.signature());q.setString(5,payment.rawPayload());q.setString(6,response);q.executeUpdate();
                }
                delivery(c,payment.bankTransId(),"PROCESSED");c.commit();return response;
            }catch(SQLException | RuntimeException ex){c.rollback();throw ex;}
            finally{c.setAutoCommit(true);locks.releaseLock(c,lockKey);}
        }
    }
    private static void delivery(Connection c,String transaction,String status)throws SQLException {
        try(PreparedStatement q=c.prepareStatement("INSERT INTO bank_webhook_deliveries(bank_trans_id,status) VALUES (?,?)")){q.setString(1,transaction);q.setString(2,status);q.executeUpdate();}
    }
    private static void credit(Connection c,long folio,String type,BigDecimal amount,String reference,String description)throws SQLException {
        try(PreparedStatement q=c.prepareStatement("INSERT INTO folio_transactions(folio_id,transaction_type,charge_type,description,amount,business_date,reference_id) SELECT ?,'credit',?,?,?,business_date,? FROM hotel_business_state WHERE id=1")) {
            q.setLong(1,folio);q.setString(2,type);q.setString(3,description);q.setBigDecimal(4,amount);q.setString(5,reference);if(q.executeUpdate()!=1)throw new SQLException("Hotel business date missing");
        }
    }
    public record Payment(String bankTransId,BigDecimal amount,String reference,String signature,String rawPayload) { }
}
