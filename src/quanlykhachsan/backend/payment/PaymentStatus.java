package quanlykhachsan.backend.payment;

import com.google.gson.*;
import java.sql.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** One projection for every collection method. Booking confirmation is a separate lifecycle state. */
public final class PaymentStatus {
    private static final ZoneId BUSINESS_ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    public static final long QR_WINDOW_MINUTES=60;
    public static final long QR_WEBHOOK_GRACE_MINUTES=2;
    private PaymentStatus() { }
    public static JsonObject forBooking(Connection c,int booking)throws SQLException {
        BigDecimal remaining=BigDecimal.ZERO,paid=BigDecimal.ZERO;String error=null;
        try(var q=c.prepareStatement("SELECT id,balance FROM folios WHERE booking_id=?")){q.setInt(1,booking);try(var r=q.executeQuery()){while(r.next()){
            var f=forFolio(c,r.getLong(1),r.getBigDecimal(2));remaining=remaining.add(r.getBigDecimal(2));paid=paid.add(f.get("paid_amount").getAsBigDecimal());if("RECONCILIATION_ERROR".equals(f.get("payment_status").getAsString()))error=f.get("reconciliation_reason").getAsString();
        }}}
        BookingWindow window=bookingWindow(c,booking);
        JsonObject j=projection(remaining,paid,error,window);
        return j;
    }
    public static JsonObject forFolio(Connection c,long folio,BigDecimal balance)throws SQLException {
        JsonObject j=new JsonObject();BigDecimal paid=BigDecimal.ZERO;String reason=null;
        try(var q=c.prepareStatement("SELECT COALESCE(SUM(amount),0) FROM folio_transactions WHERE folio_id=? AND transaction_type='credit' AND charge_type='payment'")){q.setLong(1,folio);try(var r=q.executeQuery()){r.next();paid=r.getBigDecimal(1);}}
        // Transaction order remains deterministic when callbacks arrive within the same second.
        try(var q=c.prepareStatement("SELECT payment_status,reason FROM payment_reconciliations WHERE folio_id=? ORDER BY id DESC LIMIT 1")){q.setLong(1,folio);try(var r=q.executeQuery()){if(r.next()&&"RECONCILIATION_ERROR".equals(r.getString(1)))reason=r.getString(2);}}
        int booking;
        try(var q=c.prepareStatement("SELECT booking_id FROM folios WHERE id=?")){q.setLong(1,folio);try(var r=q.executeQuery()){if(!r.next())throw new SQLException("Folio not found");booking=r.getInt(1);}}
        return projection(balance,paid,reason,bookingWindow(c,booking));
    }
    public static BookingWindow bookingWindow(Connection c,int booking)throws SQLException {
        expireQrLinksForBooking(c,booking);
        try(var q=c.prepareStatement("SELECT payment_choice,created_at,status FROM bookings WHERE id=?")){
            q.setInt(1,booking);try(var r=q.executeQuery()){
                if(!r.next())return new BookingWindow(null,null,null,null);
                String choice=r.getString(1);LocalDateTime created=r.getObject(2,LocalDateTime.class);String bookingStatus=r.getString(3);
                LocalDateTime deadline=created==null?null:created.plusMinutes(QR_WINDOW_MINUTES);
                try(var link=c.prepareStatement("SELECT state,expires_at FROM payment_links WHERE folio_id IN (SELECT id FROM folios WHERE booking_id=?) ORDER BY order_code DESC LIMIT 1")){
                    link.setInt(1,booking);try(var lr=link.executeQuery()){if(lr.next()){
                        LocalDateTime linkDeadline=lr.getObject(2,LocalDateTime.class);
                        if(deadline==null||linkDeadline!=null&&linkDeadline.isBefore(deadline))deadline=linkDeadline;
                        return new BookingWindow(choice,deadline,lr.getString(1),bookingStatus);
                    }}
                }
                return new BookingWindow(choice,deadline,null,bookingStatus);
            }
        }
    }
    public static BookingWindow bookingWindowForFolio(Connection c,long folio)throws SQLException {
        expireQrLinkIfNeeded(c,folio);
        try(var q=c.prepareStatement("SELECT b.payment_choice,b.created_at,b.status FROM bookings b JOIN folios f ON f.booking_id=b.id WHERE f.id=?")){
            q.setLong(1,folio);try(var r=q.executeQuery()){
                if(!r.next())return new BookingWindow(null,null,null,null);
                String choice=r.getString(1);LocalDateTime created=r.getObject(2,LocalDateTime.class);String bookingStatus=r.getString(3);
                LocalDateTime deadline=created==null?null:created.plusMinutes(QR_WINDOW_MINUTES);
                try(var link=c.prepareStatement("SELECT state,expires_at FROM payment_links WHERE folio_id=? ORDER BY order_code DESC LIMIT 1")){
                    link.setLong(1,folio);try(var lr=link.executeQuery()){if(lr.next()){
                        LocalDateTime linkDeadline=lr.getObject(2,LocalDateTime.class);
                        if(deadline==null||linkDeadline!=null&&linkDeadline.isBefore(deadline))deadline=linkDeadline;
                        return new BookingWindow(choice,deadline,lr.getString(1),bookingStatus);
                    }}
                }
                return new BookingWindow(choice,deadline,null,bookingStatus);
            }
        }
    }
    private static void expireQrLinkIfNeeded(Connection c,long folio)throws SQLException {
        String sql="UPDATE payment_links p JOIN folios f ON f.id=p.folio_id JOIN bookings b ON b.id=f.booking_id SET p.state='EXPIRED' WHERE p.folio_id=? AND p.state IN ('CREATING','PENDING') AND (p.expires_at<=CURRENT_TIMESTAMP OR (b.customer_type='Web' AND b.payment_choice='qr' AND b.created_at<=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL "+QR_WINDOW_MINUTES+" MINUTE)))";
        try(var q=c.prepareStatement(sql)){
            q.setLong(1,folio);q.executeUpdate();
        }
    }
    private static void expireQrLinksForBooking(Connection c,int booking)throws SQLException {
        String sql="UPDATE payment_links p JOIN folios f ON f.id=p.folio_id JOIN bookings b ON b.id=f.booking_id SET p.state='EXPIRED' WHERE b.id=? AND p.state IN ('CREATING','PENDING') AND (p.expires_at<=CURRENT_TIMESTAMP OR (b.customer_type='Web' AND b.payment_choice='qr' AND b.created_at<=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL "+QR_WINDOW_MINUTES+" MINUTE)))";
        try(var q=c.prepareStatement(sql)){
            q.setInt(1,booking);q.executeUpdate();
        }
    }
    private static JsonObject projection(BigDecimal remaining,BigDecimal paid,String error,BookingWindow window) {
        LocalDateTime now=LocalDateTime.now(BUSINESS_ZONE);
        boolean hasError=error!=null;
        boolean expiredQr="qr".equals(window.choice())&&("EXPIRED".equalsIgnoreCase(window.sessionState())||window.deadline()!=null&&!window.deadline().isAfter(now));
        String state=remaining.signum()==0&&paid.signum()>0?"PAID":hasError?"RECONCILIATION_ERROR":paid.signum()>0?"PARTIAL":expiredQr?"QR_EXPIRED":remaining.signum()<=0?"UNTRACKED":
                "counter".equals(window.choice())?"WAITING":
                "qr".equals(window.choice())?(window.deadline()!=null&&window.deadline().isAfter(now)&&!"EXPIRED".equalsIgnoreCase(window.sessionState())?"QR_PENDING":"QR_EXPIRED"):"UNTRACKED";
        long seconds=window.deadline()==null?0:Math.max(0,Duration.between(now,window.deadline()).toSeconds());
        JsonObject j=new JsonObject();j.addProperty("payment_status",state);j.addProperty("remaining_amount",remaining);j.addProperty("paid_amount",paid);j.addProperty("reconciliation_reason",hasError?error:null);
        j.addProperty("payment_choice",window.choice());j.addProperty("payment_deadline_at",window.deadline()==null?null:window.deadline().toString());j.addProperty("payment_seconds_remaining",seconds);j.addProperty("payment_session_status",window.sessionState());
        LocalDateTime reconciliationDeadline=window.reconciliationDeadline();
        j.addProperty("payment_reconciliation_deadline_at",reconciliationDeadline==null?null:reconciliationDeadline.toString());
        j.addProperty("payment_reconciliation_pending",window.reconciliationPending(now));
        j.addProperty("booking_status",window.bookingStatus());
        return j;
    }
    public record BookingWindow(String choice,LocalDateTime deadline,String sessionState,String bookingStatus) {
        public BookingWindow(String choice,LocalDateTime deadline){this(choice,deadline,null,null);}
        public BookingWindow(String choice,LocalDateTime deadline,String sessionState){this(choice,deadline,sessionState,null);}
        public LocalDateTime reconciliationDeadline(){return deadline==null?null:deadline.plusMinutes(QR_WEBHOOK_GRACE_MINUTES);}
        public boolean reconciliationPending(LocalDateTime now){
            LocalDateTime graceEnd=reconciliationDeadline();
            return "qr".equals(choice)&&"pending".equalsIgnoreCase(bookingStatus)&&deadline!=null&&!now.isBefore(deadline)&&graceEnd!=null&&now.isBefore(graceEnd);
        }
        public boolean qrOpen(){return "qr".equals(choice)&&deadline!=null&&deadline.isAfter(LocalDateTime.now(BUSINESS_ZONE))&&!("EXPIRED".equalsIgnoreCase(sessionState));}
    }
    public static JsonArray history(Connection c,Long folio,Integer customer,int limit,int offset)throws SQLException {
        String sql="SELECT p.id,p.provider,p.payment_method,p.direction,p.transaction_id,p.folio_id,p.order_code,p.amount,p.applied_amount,p.excess_amount,p.payment_status,p.reason,p.transaction_at,p.created_at,(SELECT COUNT(*) FROM payment_webhook_deliveries d WHERE d.reconciliation_id=p.id) AS webhook_deliveries,b.id AS booking_id,CONCAT('BKG-',CASE WHEN b.customer_type='Web' THEN 'WEB' ELSE 'WALKIN' END,'-',YEAR(b.check_in_date),'-',b.id) AS booking_code,c.full_name AS customer_name,r.room_number FROM payment_reconciliations p LEFT JOIN folios f ON f.id=p.folio_id LEFT JOIN bookings b ON b.id=f.booking_id LEFT JOIN customers c ON c.id=b.customer_id LEFT JOIN rooms r ON r.id=b.room_id WHERE p.amount<>0 AND (? IS NULL OR p.folio_id=?) AND (? IS NULL OR b.customer_id=?) ORDER BY p.id DESC LIMIT ? OFFSET ?";
        try(var q=c.prepareStatement(sql)){q.setObject(1,folio);q.setObject(2,folio);q.setObject(3,customer);q.setObject(4,customer);q.setInt(5,limit);q.setInt(6,offset);try(var r=q.executeQuery()){return rows(r);}}
    }
    public static long historyCount(Connection c,Long folio,Integer customer)throws SQLException {
        String sql="SELECT COUNT(*) FROM payment_reconciliations p LEFT JOIN folios f ON f.id=p.folio_id LEFT JOIN bookings b ON b.id=f.booking_id WHERE p.amount<>0 AND (? IS NULL OR p.folio_id=?) AND (? IS NULL OR b.customer_id=?)";
        try(var q=c.prepareStatement(sql)){q.setObject(1,folio);q.setObject(2,folio);q.setObject(3,customer);q.setObject(4,customer);try(var r=q.executeQuery()){return r.next()?r.getLong(1):0;}}
    }
    public static JsonArray rows(ResultSet r)throws SQLException {
        JsonArray a=new JsonArray();var meta=r.getMetaData();while(r.next()){JsonObject j=new JsonObject();for(int i=1;i<=meta.getColumnCount();i++){Object v=r.getObject(i);String key=meta.getColumnLabel(i);if(v==null)j.add(key,JsonNull.INSTANCE);else if(v instanceof Number n)j.addProperty(key,n);else if(v instanceof Boolean b)j.addProperty(key,b);else j.addProperty(key,v.toString());}a.add(j);}return a;
    }
}
