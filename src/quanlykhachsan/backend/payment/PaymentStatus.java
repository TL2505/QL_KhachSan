package quanlykhachsan.backend.payment;

import com.google.gson.*;
import java.sql.*;
import java.math.BigDecimal;

/** One projection for every collection method. Booking confirmation is a separate lifecycle state. */
public final class PaymentStatus {
    private PaymentStatus() { }
    public static JsonObject forBooking(Connection c,int booking)throws SQLException {
        BigDecimal remaining=BigDecimal.ZERO,paid=BigDecimal.ZERO;String error=null;
        try(var q=c.prepareStatement("SELECT id,balance FROM folios WHERE booking_id=?")){q.setInt(1,booking);try(var r=q.executeQuery()){while(r.next()){
            var f=forFolio(c,r.getLong(1),r.getBigDecimal(2));remaining=remaining.add(r.getBigDecimal(2));paid=paid.add(f.get("paid_amount").getAsBigDecimal());if("RECONCILIATION_ERROR".equals(f.get("payment_status").getAsString()))error=f.get("reconciliation_reason").getAsString();
        }}}
        JsonObject j=new JsonObject();j.addProperty("payment_status",remaining.signum()==0&&paid.signum()>0?"PAID":error!=null?"RECONCILIATION_ERROR":paid.signum()>0?"PARTIAL":"WAITING");j.addProperty("remaining_amount",remaining);j.addProperty("paid_amount",paid);j.addProperty("reconciliation_reason",error);return j;
    }
    public static JsonObject forFolio(Connection c,long folio,BigDecimal balance)throws SQLException {
        JsonObject j=new JsonObject();BigDecimal paid=BigDecimal.ZERO;String reason=null;
        try(var q=c.prepareStatement("SELECT COALESCE(SUM(amount),0) FROM folio_transactions WHERE folio_id=? AND transaction_type='credit' AND charge_type='payment'")){q.setLong(1,folio);try(var r=q.executeQuery()){r.next();paid=r.getBigDecimal(1);}}
        // Transaction order remains deterministic when callbacks arrive within the same second.
        try(var q=c.prepareStatement("SELECT payment_status,reason FROM payment_reconciliations WHERE folio_id=? ORDER BY id DESC LIMIT 1")){q.setLong(1,folio);try(var r=q.executeQuery()){if(r.next()&&"RECONCILIATION_ERROR".equals(r.getString(1)))reason=r.getString(2);}}
        boolean error=reason!=null;
        String state=balance.signum()==0&&paid.signum()>0?"PAID":error?"RECONCILIATION_ERROR":paid.signum()>0?"PARTIAL":"WAITING";
        j.addProperty("payment_status",state);j.addProperty("paid_amount",paid);j.addProperty("remaining_amount",balance);j.addProperty("reconciliation_reason",error?reason:null);return j;
    }
    public static JsonArray history(Connection c,Long folio,Integer customer,int limit,int offset)throws SQLException {
        String sql="SELECT p.id,p.provider,p.payment_method,p.direction,p.transaction_id,p.folio_id,p.order_code,p.amount,p.applied_amount,p.excess_amount,p.payment_status,p.reason,p.transaction_at,p.created_at,(SELECT COUNT(*) FROM payment_webhook_deliveries d WHERE d.reconciliation_id=p.id) AS webhook_deliveries FROM payment_reconciliations p LEFT JOIN folios f ON f.id=p.folio_id LEFT JOIN bookings b ON b.id=f.booking_id WHERE (? IS NULL OR p.folio_id=?) AND (? IS NULL OR b.customer_id=?) ORDER BY p.id DESC LIMIT ? OFFSET ?";
        try(var q=c.prepareStatement(sql)){q.setObject(1,folio);q.setObject(2,folio);q.setObject(3,customer);q.setObject(4,customer);q.setInt(5,limit);q.setInt(6,offset);try(var r=q.executeQuery()){return rows(r);}}
    }
    public static JsonArray rows(ResultSet r)throws SQLException {
        JsonArray a=new JsonArray();var meta=r.getMetaData();while(r.next()){JsonObject j=new JsonObject();for(int i=1;i<=meta.getColumnCount();i++){Object v=r.getObject(i);String key=meta.getColumnLabel(i);if(v==null)j.add(key,JsonNull.INSTANCE);else if(v instanceof Number n)j.addProperty(key,n);else if(v instanceof Boolean b)j.addProperty(key,b);else j.addProperty(key,v.toString());}a.add(j);}return a;
    }
}
