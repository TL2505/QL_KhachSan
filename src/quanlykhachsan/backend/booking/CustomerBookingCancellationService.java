package quanlykhachsan.backend.booking;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import quanlykhachsan.backend.notification.LifecycleNotifications;
import quanlykhachsan.backend.payment.PaymentLinkService;
import quanlykhachsan.backend.payment.PaymentStatus;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Customer-owned cancellation for web bookings before the automatic check-in time. */
public final class CustomerBookingCancellationService {
    @FunctionalInterface public interface Connections { Connection open() throws SQLException; }
    @FunctionalInterface public interface LinkCanceller { void cancel(Connection c,long folioId,String reason)throws Exception; }
    public record Result(int httpStatus,String message,BigDecimal refundAmount,String refundStatus) { }
    private static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private final Connections connections;
    private final LinkCanceller linkCanceller;
    private final Clock clock;

    public CustomerBookingCancellationService(){this(DatabaseConnection::getConnection,(c,folio,reason)->new PaymentLinkService().cancelActive(c,folio,reason),Clock.system(ZONE));}
    public CustomerBookingCancellationService(Connections connections,LinkCanceller linkCanceller,Clock clock){this.connections=Objects.requireNonNull(connections);this.linkCanceller=Objects.requireNonNull(linkCanceller);this.clock=Objects.requireNonNull(clock);}

    public Result cancel(int bookingId,int customerId)throws SQLException {
        try(Connection c=connections.open()){
            long folioId;
            try(PreparedStatement q=c.prepareStatement("SELECT id FROM folios WHERE booking_id=? AND folio_type='master' ORDER BY id LIMIT 1")){
                q.setInt(1,bookingId);try(ResultSet r=q.executeQuery()){if(!r.next())return result(404,"Không tìm thấy đơn đặt phòng");folioId=r.getLong(1);}
            }
            FolioDao locks=new FolioDao();String advisory="payment-link:"+folioId;locks.acquireLock(c,advisory);
            try{
                c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);c.setAutoCommit(false);
                try{
                    Booking booking=lockBooking(c,bookingId,customerId);
                    if(booking==null){c.rollback();return result(404,"Không tìm thấy đơn đặt phòng");}
                    if(!"Web".equalsIgnoreCase(booking.channel())){c.rollback();return result(403,"Chỉ đơn đặt qua website có thể tự hủy trong cổng khách hàng");}
                    if("cancelled".equals(booking.status())){RefundSummary prior=refundSummary(c,bookingId);c.commit();return new Result(200,"Đơn đặt phòng đã được hủy trước đó.",prior.amount(),prior.status());}
                    if("checked_in".equals(booking.status())||"checked_out".equals(booking.status())){c.rollback();return result(409,"Đơn đã được nhận phòng; vui lòng liên hệ bộ phận hỗ trợ để được trợ giúp.");}
                    if(!Set.of("pending","confirmed").contains(booking.status())){c.rollback();return result(409,"Trạng thái đơn hiện tại không thể tự hủy.");}
                    LocalDateTime now=LocalDateTime.now(clock);
                    if(!booking.checkIn().isAfter(now)){c.rollback();return result(409,"Đã đến hạn nhận phòng. Vui lòng liên hệ bộ phận hỗ trợ để được trợ giúp.");}
                    PaymentStatus.BookingWindow window=PaymentStatus.bookingWindow(c,bookingId);
                    if(window.reconciliationPending(now)){c.rollback();return result(409,"Nhà cung cấp đang đối soát giao dịch QR vừa hết hạn. Vui lòng tải lại lịch sử đặt phòng sau ít phút.");}

                    if("pending".equals(booking.status())&&"qr".equalsIgnoreCase(booking.paymentChoice())){
                        try{linkCanceller.cancel(c,folioId,"Customer cancelled reservation");}
                        catch(IllegalStateException ex){
                            c.rollback();String message="PAYMENT_ALREADY_COMPLETED".equals(ex.getMessage())
                                ?"Nhà cung cấp đã ghi nhận thanh toán. Vui lòng tải lại lịch sử để hệ thống cập nhật trước khi hủy."
                                :"Chưa xác nhận được việc đóng phiên QR; đơn vẫn còn hiệu lực. Vui lòng thử lại sau.";
                            return result("PAYMENT_ALREADY_COMPLETED".equals(ex.getMessage())?409:503,message);
                        }catch(Exception ex){c.rollback();return result(503,"Chưa thể đóng phiên QR với nhà cung cấp; đơn vẫn còn hiệu lực. Vui lòng thử lại sau.");}
                    }else{
                        try(PreparedStatement close=c.prepareStatement("UPDATE payment_links SET state='CANCELLED' WHERE folio_id=? AND state IN ('CREATING','PENDING')")){
                            close.setLong(1,folioId);close.executeUpdate();
                        }
                    }

                    RefundSummary refund=createRefundRecords(c,bookingId,customerId);
                    reverseCancelledBookingCharges(c,bookingId);
                    try(PreparedStatement close=c.prepareStatement("UPDATE folios SET balance=0,status='closed' WHERE booking_id=?")){
                        close.setInt(1,bookingId);close.executeUpdate();
                    }
                    try(PreparedStatement update=c.prepareStatement("UPDATE bookings SET status='cancelled' WHERE id=? AND customer_id=? AND customer_type='Web' AND status IN ('pending','confirmed')")){
                        update.setInt(1,bookingId);update.setInt(2,customerId);if(update.executeUpdate()!=1){c.rollback();return result(409,"Đơn vừa được cập nhật. Tải lại lịch sử để xem trạng thái mới.");}
                    }
                    releaseRoomIfNoOverlap(c,bookingId);
                    String message=cancellationMessage(refund);
                    LifecycleNotifications.booking(c,bookingId,"booking_cancelled","booking-cancelled:"+bookingId,"Đã hủy đặt phòng",message);
                    c.commit();return new Result(200,message,refund.amount(),refund.status());
                }catch(SQLException|RuntimeException ex){c.rollback();throw ex;}finally{c.setAutoCommit(true);}
            }finally{locks.releaseLock(c,advisory);}
        }
    }

    private static Booking lockBooking(Connection c,int id,int customer)throws SQLException {
        try(PreparedStatement q=c.prepareStatement("SELECT id,customer_type,status,payment_choice,check_in_date FROM bookings WHERE id=? AND customer_id=? FOR UPDATE")){
            q.setInt(1,id);q.setInt(2,customer);try(ResultSet r=q.executeQuery()){return r.next()?new Booking(r.getString(2),r.getString(3).toLowerCase(Locale.ROOT),r.getString(4),r.getObject(5,LocalDateTime.class)):null;}
        }
    }

    private static RefundSummary createRefundRecords(Connection c,int booking,int customer)throws SQLException {
        Map<String,BigDecimal> byProvider=new LinkedHashMap<>();BigDecimal total=BigDecimal.ZERO;
        try(PreparedStatement q=c.prepareStatement("SELECT p.provider,SUM(p.amount) FROM payment_reconciliations p JOIN folios f ON f.id=p.folio_id WHERE f.booking_id=? AND p.direction='IN' AND p.applied_amount+p.excess_amount>0 GROUP BY p.provider ORDER BY p.provider")){
            q.setInt(1,booking);try(ResultSet r=q.executeQuery()){while(r.next()){BigDecimal amount=r.getBigDecimal(2);if(amount.signum()>0){byProvider.put(r.getString(1),amount);total=total.add(amount);}}}
        }
        BigDecimal paid=BigDecimal.ZERO;
        try(PreparedStatement q=c.prepareStatement("SELECT COALESCE(SUM(t.amount),0) FROM folio_transactions t JOIN folios f ON f.id=t.folio_id WHERE f.booking_id=? AND t.transaction_type='credit' AND t.charge_type IN ('payment','credit_adjustment')")){
            q.setInt(1,booking);try(ResultSet r=q.executeQuery()){r.next();paid=r.getBigDecimal(1);}
        }
        if(paid.compareTo(total)>0){BigDecimal unexplained=paid.subtract(total);byProvider.put("unknown",unexplained);total=total.add(unexplained);}
        boolean allMock=true;
        for(var entry:byProvider.entrySet()){
            String provider=entry.getKey();boolean mock="mock".equalsIgnoreCase(provider);allMock&=mock;
            String status=mock?"REFUNDED":"MANUAL_REQUIRED";String providerRef=mock?"MOCK-REFUND-"+booking:null;
            try(PreparedStatement q=c.prepareStatement("INSERT INTO booking_refunds(booking_id,customer_id,provider,amount,status,provider_reference,reason,resolved_at) VALUES(?,?,?,?,?,?,?,IF(?='REFUNDED',CURRENT_TIMESTAMP(6),NULL)) ON DUPLICATE KEY UPDATE amount=VALUES(amount)")){
                q.setInt(1,booking);q.setInt(2,customer);q.setString(3,provider);q.setBigDecimal(4,entry.getValue());q.setString(5,status);q.setString(6,providerRef);q.setString(7,"Customer cancelled before automatic check-in");q.setString(8,status);q.executeUpdate();
            }
        }
        return new RefundSummary(total,total.signum()==0?"NOT_REQUIRED":allMock?"REFUNDED":"MANUAL_REQUIRED");
    }

    private static void reverseCancelledBookingCharges(Connection c,int booking)throws SQLException {
        var folios=new ArrayList<Long>();
        try(PreparedStatement q=c.prepareStatement("SELECT id FROM folios WHERE booking_id=? FOR UPDATE")){
            q.setInt(1,booking);try(ResultSet r=q.executeQuery()){while(r.next())folios.add(r.getLong(1));}
        }
        for(long folio:folios){
            BigDecimal netCharges;
            try(PreparedStatement q=c.prepareStatement("SELECT COALESCE(SUM(CASE WHEN transaction_type='debit' THEN amount WHEN transaction_type='credit' AND charge_type IN ('adjustment','refund') THEN -amount ELSE 0 END),0) FROM folio_transactions WHERE folio_id=?")){
                q.setLong(1,folio);try(ResultSet r=q.executeQuery()){r.next();netCharges=r.getBigDecimal(1);}
            }
            if(netCharges.signum()>0)try(PreparedStatement post=c.prepareStatement("INSERT INTO folio_transactions(folio_id,transaction_type,charge_type,description,amount,business_date,reference_id) SELECT ?,'credit','adjustment','Cancelled web booking charge reversal',?,business_date,? FROM hotel_business_state WHERE id=1")){
                post.setLong(1,folio);post.setBigDecimal(2,netCharges);post.setString(3,"CANCEL-WEB-"+booking+"-"+folio);if(post.executeUpdate()!=1)throw new SQLException("Hotel business date missing");
            }
        }
    }

    private static void releaseRoomIfNoOverlap(Connection c,int booking)throws SQLException {
        try(PreparedStatement q=c.prepareStatement("UPDATE rooms r JOIN bookings b ON b.room_id=r.id SET r.status='vacant_clean',r.version=r.version+1 WHERE b.id=? AND r.status='reserved' AND NOT EXISTS(SELECT 1 FROM bookings other WHERE other.id<>b.id AND other.room_id=b.room_id AND other.status IN ('pending','confirmed','checked_in') AND other.check_in_date<b.check_out_date AND other.check_out_date>b.check_in_date)")){
            q.setInt(1,booking);q.executeUpdate();
        }
    }

    private static RefundSummary refundSummary(Connection c,int booking)throws SQLException {
        try(PreparedStatement q=c.prepareStatement("SELECT COALESCE(SUM(amount),0),CASE WHEN SUM(status='MANUAL_REQUIRED')>0 THEN 'MANUAL_REQUIRED' WHEN SUM(status='REFUNDED')>0 THEN 'REFUNDED' ELSE 'NOT_REQUIRED' END FROM booking_refunds WHERE booking_id=?")){
            q.setInt(1,booking);try(ResultSet r=q.executeQuery()){r.next();return new RefundSummary(r.getBigDecimal(1),r.getString(2));}
        }
    }
    private static String cancellationMessage(RefundSummary refund){
        if(refund.amount().signum()==0)return "Đơn đặt phòng đã được hủy. Không có khoản thanh toán nào cần hoàn.";
        if("REFUNDED".equals(refund.status()))return "Đơn đặt phòng đã được hủy. Cổng thanh toán thử nghiệm xác nhận đã hoàn "+refund.amount().toPlainString()+" VND.";
        return "Đơn đặt phòng đã được hủy. Khoản hoàn "+refund.amount().toPlainString()+" VND đang chờ xử lý qua phương thức thanh toán ban đầu; vui lòng liên hệ hỗ trợ nếu cần cập nhật.";
    }
    private static Result result(int status,String message){return new Result(status,message,BigDecimal.ZERO,"NOT_REQUIRED");}
    private record Booking(String channel,String status,String paymentChoice,LocalDateTime checkIn) { }
    private record RefundSummary(BigDecimal amount,String status) { }
}
