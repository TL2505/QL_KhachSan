package quanlykhachsan.backend.payment;

import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;
import quanlykhachsan.backend.booking.FolioDao;
import quanlykhachsan.backend.notification.LifecycleNotifications;
import quanlykhachsan.backend.utils.*;

/** Shared settlement boundary for authenticated gateway adapters. Never accept browser payment claims. */
public final class PaymentReconciliationService {
    @FunctionalInterface public interface Connections {Connection open()throws SQLException;}
    private final Connections connections;
    public PaymentReconciliationService(){this(DatabaseConnection::getConnection);}
    public PaymentReconciliationService(Connections c){connections=c;}
    public record VerifiedPayment(String provider,String transactionId,long orderCode,BigDecimal amount,String currency,String account,String reference,String linkId,LocalDateTime occurredAt,String resultCode) {
        public VerifiedPayment {
            if(provider==null||!provider.matches("[a-z0-9_]{1,24}")||transactionId==null||!transactionId.matches("[A-Za-z0-9_.-]{1,100}")||orderCode<1||amount==null||amount.signum()<=0||amount.stripTrailingZeros().scale()>0||amount.compareTo(new BigDecimal("9999999999"))>0||occurredAt==null||reference==null||account==null||linkId==null||currency==null||resultCode==null)throw new IllegalArgumentException("Invalid verified payment");
        }
        String hash(){JsonObject j=new JsonObject();j.addProperty("provider",provider);j.addProperty("transaction",transactionId);j.addProperty("order",orderCode);j.addProperty("amount",amount.stripTrailingZeros().toPlainString());j.addProperty("currency",currency);j.addProperty("account",account);j.addProperty("reference",reference);j.addProperty("link",linkId);j.addProperty("at",occurredAt.toString());j.addProperty("code",resultCode);return sha256(j.toString());}
    }
    public String settle(VerifiedPayment p)throws SQLException {
        try(Connection c=connections.open()){
            var locks=new FolioDao();String lock="gateway:"+p.provider()+":"+p.transactionId();locks.acquireLock(c,lock);
            try{
                c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);c.setAutoCommit(false);
                try{
                    try(var q=c.prepareStatement("SELECT id,fact_hash,response_body FROM payment_reconciliations WHERE provider=? AND transaction_id=?")){
                        q.setString(1,p.provider());q.setString(2,p.transactionId());try(var r=q.executeQuery()){if(r.next()){
                            boolean same=r.getString(2).equals(p.hash());delivery(c,r.getLong(1),same?"DUPLICATE":"CONFLICT");String response=same?r.getString(3):response("RECONCILIATION_ERROR","TRANSACTION_CONFLICT",null,null);c.commit();return response;
                        }}
                    }
                    Long folio=null;int booking=0;String reason=null;LocalDateTime expiry=null;String expectedAccount=null,expectedRef=null,expectedLink=null,linkState=null;LocalDateTime created=null;
                    try(var q=c.prepareStatement("SELECT folio_id,account_number,reference_code,payment_link_id,expires_at,state,created_at FROM payment_links WHERE order_code=? AND provider=?")){
                        q.setLong(1,p.orderCode());q.setString(2,p.provider());try(var r=q.executeQuery()){if(r.next()){folio=r.getLong(1);expectedAccount=r.getString(2);expectedRef=r.getString(3);expectedLink=r.getString(4);expiry=r.getObject(5,LocalDateTime.class);linkState=r.getString(6);created=r.getObject(7,LocalDateTime.class);}else reason="UNKNOWN_REFERENCE";}
                    }
                    BigDecimal balance=null,applied=BigDecimal.ZERO,excess=BigDecimal.ZERO;
                    if(folio!=null){
                        try(var q=c.prepareStatement("SELECT booking_id FROM folios WHERE id=?")){q.setLong(1,folio);try(var r=q.executeQuery()){if(r.next())booking=r.getInt(1);}}
                        String bookingState=null;
                        try(var q=c.prepareStatement("SELECT status FROM bookings WHERE id=? FOR UPDATE")){q.setInt(1,booking);try(var r=q.executeQuery()){if(r.next())bookingState=r.getString(1);}}
                        String folioState=null;
                        try(var q=c.prepareStatement("SELECT balance,status FROM folios WHERE id=? FOR UPDATE")){q.setLong(1,folio);try(var r=q.executeQuery()){if(r.next()){balance=r.getBigDecimal(1);folioState=r.getString(2);}}}
                        if(!"00".equals(p.resultCode()))reason="PROVIDER_NOT_CONFIRMED";
                        else if(!"VND".equals(p.currency()))reason="CURRENCY_MISMATCH";
                        else if(!expectedAccount.equals(p.account()))reason="BENEFICIARY_MISMATCH";
                        else if(!expectedRef.equals(p.reference()))reason="REFERENCE_MISMATCH";
                        else if(expectedLink==null||!expectedLink.equals(p.linkId()))reason="LINK_MISMATCH";
                        else if(!Set.of("pending","confirmed","checked_in").contains(Objects.toString(bookingState,""))||"closed".equals(folioState)||"CANCELLED".equals(linkState))reason="LATE_PAYMENT";
                        else if(p.occurredAt().isAfter(expiry)||p.occurredAt().isBefore(created.minusMinutes(5)))reason="OUTSIDE_PAYMENT_WINDOW";
                        else if(p.occurredAt().isAfter(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusMinutes(5)))reason="INVALID_TRANSACTION_TIME";
                        if(reason==null){
                            applied=p.amount().min(balance);excess=p.amount().subtract(applied);
                            if(applied.signum()>0)credit(c,folio,"payment",applied,p.provider()+":"+p.transactionId());
                            if(excess.signum()>0)credit(c,folio,"credit_adjustment",excess,p.provider()+":"+p.transactionId());
                            balance=balance.subtract(applied);locks.updateBalance(c,folio,balance);FolioDao.confirmPaidBooking(c,booking);
                            LifecycleNotifications.payment(c,booking,p.provider()+":"+p.transactionId(),p.amount(),balance);
                        }else LifecycleNotifications.booking(c,booking,"payment_error","payment-error:"+p.provider()+":"+p.transactionId(),"Thanh toán cần đối soát","Giao dịch chưa được ghi nhận vào đặt phòng. Mã lỗi: "+reason+". Vui lòng liên hệ lễ tân.");
                    }
                    String status=reason!=null?"RECONCILIATION_ERROR":balance.signum()==0?"PAID":"PARTIAL";
                    String response=response(status,reason==null?"APPLIED":reason,folio,balance);
                    long id=record(c,p.provider(),"bank_transfer",p.transactionId(),p.hash(),folio,p.orderCode(),p.amount(),applied,excess,status,reason==null?"APPLIED":reason,p.occurredAt(),response);
                    delivery(c,id,"RECEIVED");c.commit();return response;
                }catch(SQLException|RuntimeException e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
            }finally{locks.releaseLock(c,lock);}
        }
    }
    public static void recordCollection(Connection c,String provider,String method,String id,long folio,BigDecimal amount,BigDecimal remaining)throws SQLException {
        recordCollection(c,provider,method,id,folio,amount,amount,BigDecimal.ZERO,remaining);
    }
    public static void recordCollection(Connection c,String provider,String method,String id,long folio,BigDecimal amount,BigDecimal applied,BigDecimal excess,BigDecimal remaining)throws SQLException {
        if(c.getAutoCommit())throw new SQLException("Receipt audit requires the collection transaction");
        record(c,provider,method,id,sha256(provider+":"+id+":"+folio+":"+amount.stripTrailingZeros()),folio,0,amount,applied,excess,remaining.signum()==0?"PAID":"PARTIAL","APPLIED",LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")),"{}");
    }
    private static long record(Connection c,String provider,String method,String transaction,String hash,Long folio,long order,BigDecimal amount,BigDecimal applied,BigDecimal excess,String status,String reason,LocalDateTime at,String response)throws SQLException {
        try(var q=c.prepareStatement("INSERT INTO payment_reconciliations(provider,payment_method,transaction_id,fact_hash,folio_id,order_code,amount,applied_amount,excess_amount,payment_status,reason,transaction_at,response_body) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
            q.setString(1,provider);q.setString(2,method);q.setString(3,transaction);q.setString(4,hash);if(folio==null)q.setNull(5,Types.BIGINT);else q.setLong(5,folio);q.setLong(6,order);q.setBigDecimal(7,amount);q.setBigDecimal(8,applied);q.setBigDecimal(9,excess);q.setString(10,status);q.setString(11,reason);q.setObject(12,at);q.setString(13,response);q.executeUpdate();try(var r=q.getGeneratedKeys()){r.next();return r.getLong(1);}
        }
    }
    private static void delivery(Connection c,long id,String status)throws SQLException{try(var q=c.prepareStatement("INSERT INTO payment_webhook_deliveries(reconciliation_id,result) VALUES(?,?)")){q.setLong(1,id);q.setString(2,status);q.executeUpdate();}}
    private static void credit(Connection c,long folio,String type,BigDecimal amount,String reference)throws SQLException {
        try(var q=c.prepareStatement("INSERT INTO folio_transactions(folio_id,transaction_type,charge_type,description,amount,business_date,reference_id) SELECT ?,'credit',?,'Verified gateway payment',?,business_date,? FROM hotel_business_state WHERE id=1")){q.setLong(1,folio);q.setString(2,type);q.setBigDecimal(3,amount);q.setString(4,reference);if(q.executeUpdate()!=1)throw new SQLException("Hotel business date missing");}
    }
    private static String response(String status,String reason,Long folio,BigDecimal balance){JsonObject j=new JsonObject();j.addProperty("payment_status",status);j.addProperty("reason",reason);j.addProperty("folio_id",folio);j.addProperty("balance",balance);return ApiResponseUtil.successWithData(j);}
    public static String sha256(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
