package quanlykhachsan.backend.booking;

import java.sql.*;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import quanlykhachsan.backend.notification.LifecycleNotifications;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Idempotent automatic stay lifecycle for bookings made through the website. */
public final class WebBookingLifecycleEngine {
    @FunctionalInterface public interface Connections { Connection open() throws SQLException; }
    private static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter KEY_TIME=DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");
    private static final Duration COUNTER_CHECKOUT_GRACE=Duration.ofMinutes(30);
    private final Connections connections;
    private final Clock clock;

    public WebBookingLifecycleEngine(){this(DatabaseConnection::getConnection,Clock.system(ZONE));}
    public WebBookingLifecycleEngine(Connections connections,Clock clock){this.connections=Objects.requireNonNull(connections);this.clock=Objects.requireNonNull(clock);}

    /** Queues the two reminders and performs any due web check-in/check-out in one transaction. */
    public int processDue() throws SQLException {
        if(quanlykhachsan.backend.system.SystemStateHolder.isFrozen())return 0;
        LocalDateTime now=LocalDateTime.now(clock);
        try(Connection c=connections.open()){
            c.setAutoCommit(false);
            try{
                enqueueReminders(c,now);
                int changed=autoCheckIn(c,now)+autoCheckOut(c,now);
                c.commit();return changed;
            }catch(SQLException|RuntimeException ex){c.rollback();throw ex;}finally{c.setAutoCommit(true);}
        }
    }

    private static void enqueueReminders(Connection c,LocalDateTime now)throws SQLException {
        String checkin="SELECT b.id,b.check_in_date FROM bookings b WHERE b.customer_type='Web' AND b.status='confirmed' AND b.check_in_date>? AND b.check_in_date<=? "
            +"AND NOT EXISTS(SELECT 1 FROM notification_outbox o WHERE o.event_key=CONCAT('checkin_reminder:',b.id,':',DATE_FORMAT(b.check_in_date,'%Y-%m-%dT%H:%i:%s'))) "
            +"ORDER BY b.check_in_date,b.id LIMIT 200 FOR UPDATE SKIP LOCKED";
        try(PreparedStatement q=c.prepareStatement(checkin)){
            // These columns are MariaDB DATETIME values in Vietnam wall time. Passing
            // Timestamp makes Connector/J convert them through the session timezone.
            q.setObject(1,now);q.setObject(2,now.plusHours(24));
            try(ResultSet r=q.executeQuery()){while(r.next()){
                int id=r.getInt(1);LocalDateTime at=r.getObject(2,LocalDateTime.class);
                LifecycleNotifications.booking(c,id,"checkin_reminder",reminderKey("checkin_reminder",id,at),"Nhắc lịch nhận phòng","Hệ thống sẽ tự động ghi nhận nhận phòng vào "+format(at)+" (giờ Việt Nam). Nếu cần thay đổi kế hoạch, vui lòng huỷ trước thời điểm này hoặc liên hệ hỗ trợ sau khi đã nhận phòng.");
            }}
        }

        String checkout="SELECT b.id,b.check_in_date,b.check_out_date,b.payment_choice,COALESCE((SELECT SUM(f.balance) FROM folios f WHERE f.booking_id=b.id),0.00) "
            +"FROM bookings b WHERE b.customer_type='Web' AND b.status='checked_in' AND b.check_out_date>? "
            +"AND TIMESTAMPADD(MICROSECOND,ROUND(TIMESTAMPDIFF(MICROSECOND,b.check_in_date,b.check_out_date)*0.9),b.check_in_date)<=? "
            +"AND NOT EXISTS(SELECT 1 FROM notification_outbox o WHERE o.event_key=CONCAT('checkout_reminder:',b.id,':',DATE_FORMAT(b.check_out_date,'%Y-%m-%dT%H:%i:%s'))) "
            +"ORDER BY b.check_out_date,b.id LIMIT 200 FOR UPDATE SKIP LOCKED";
        try(PreparedStatement q=c.prepareStatement(checkout)){
            q.setObject(1,now);q.setObject(2,now);
            try(ResultSet r=q.executeQuery()){while(r.next()){
                int id=r.getInt(1);LocalDateTime in=r.getObject(2,LocalDateTime.class);LocalDateTime out=r.getObject(3,LocalDateTime.class);
                LifecycleNotifications.booking(c,id,"checkout_reminder",reminderKey("checkout_reminder",id,out),"Nhắc lịch check-out",checkoutReminderMessage(in,out,r.getString(4),r.getBigDecimal(5)));
            }}
        }
    }

    private static int autoCheckIn(Connection c,LocalDateTime now)throws SQLException {
        var due=new java.util.ArrayList<int[]>();
        try(PreparedStatement q=c.prepareStatement("SELECT id,room_id FROM bookings WHERE customer_type='Web' AND status='confirmed' AND check_in_date<=? AND check_out_date>? ORDER BY check_in_date,id LIMIT 50 FOR UPDATE SKIP LOCKED")){
            q.setObject(1,now);q.setObject(2,now);try(ResultSet r=q.executeQuery()){while(r.next())due.add(new int[]{r.getInt(1),r.getInt(2)});}
        }
        int count=0;
        for(int[] booking:due){
            boolean ready;
            try(PreparedStatement q=c.prepareStatement("SELECT status,ready_not_before,EXISTS(SELECT 1 FROM cleaning_tasks t WHERE t.room_id=rooms.id AND t.status IN ('pending','in_progress')) AS active_task FROM rooms WHERE id=? FOR UPDATE")){
                q.setInt(1,booking[1]);try(ResultSet r=q.executeQuery()){
                    if(!r.next())continue;Timestamp readyAt=r.getTimestamp("ready_not_before");
                    ready=("reserved".equals(r.getString("status"))||"vacant_clean".equals(r.getString("status")))
                        &&(readyAt==null||!readyAt.toLocalDateTime().isAfter(now))&&!r.getBoolean("active_task");
                }
            }
            if(!ready)continue;
            try(PreparedStatement q=c.prepareStatement("UPDATE bookings SET status='checked_in',check_in_actual=? WHERE id=? AND customer_type='Web' AND status='confirmed'")){
                q.setObject(1,now);q.setInt(2,booking[0]);if(q.executeUpdate()!=1)continue;
            }
            try(PreparedStatement q=c.prepareStatement("UPDATE rooms SET status='occupied_clean',version=version+1 WHERE id=? AND status IN ('reserved','vacant_clean')")){
                q.setInt(1,booking[1]);if(q.executeUpdate()!=1)throw new SQLException("Room changed during automatic check-in");
            }
            LifecycleNotifications.booking(c,booking[0],"checkin_welcome","checkin:"+booking[0],"Đã tự động nhận phòng","Hệ thống đã tự động ghi nhận nhận phòng cho đơn đặt phòng của Quý khách.");count++;
        }
        return count;
    }

    private static int autoCheckOut(Connection c,LocalDateTime now)throws SQLException {
        var due=new java.util.ArrayList<DueCheckout>();
        try(PreparedStatement q=c.prepareStatement("SELECT b.id,b.room_id,b.payment_choice,b.check_out_date,b.checkout_overdue_at,t.hourly_extra_hour_price FROM bookings b JOIN rooms r ON r.id=b.room_id JOIN room_types t ON t.id=r.room_type_id WHERE b.customer_type='Web' AND b.status='checked_in' AND b.check_out_date<=? ORDER BY b.check_out_date,b.id LIMIT 50 FOR UPDATE SKIP LOCKED")){
            q.setObject(1,now);try(ResultSet r=q.executeQuery()){while(r.next())due.add(new DueCheckout(r.getInt(1),r.getInt(2),r.getString(3),r.getObject(4,LocalDateTime.class),r.getObject(5,LocalDateTime.class),r.getBigDecimal(6)));}
        }
        int count=0;FolioDao folios=new FolioDao();
        for(DueCheckout booking:due){
            // Counter-payment stays stop at checkout until reception records the
            // outstanding balance. FolioService then settles and checks out atomically.
            java.math.BigDecimal balance=folios.bookingBalance(c,booking.id());
            if(holdsCheckoutForCounterPayment(booking.paymentChoice(),balance)){
                if (booking.hourlyRate()!=null && booking.hourlyRate().signum()>0) {
                    OverstayChargeService.reconcile(c,booking.id(),booking.checkOutDate(),now,booking.hourlyRate(),true);
                }
                if(booking.checkoutOverdueAt()==null&&isCheckoutPaymentOverdue(booking.paymentChoice(),balance,booking.checkOutDate(),now)){
                    try(PreparedStatement q=c.prepareStatement("UPDATE bookings SET checkout_overdue_at=? WHERE id=? AND status='checked_in' AND checkout_overdue_at IS NULL")){
                        q.setObject(1,now);q.setInt(2,booking.id());int marked=q.executeUpdate();count+=marked;
                        if(marked==1)quanlykhachsan.backend.notification.StaffNotifications.checkoutOverdue(c,booking.id(),booking.checkOutDate());
                    }
                }
                continue;
            }
            if(folios.checkout(c,booking.id(),booking.roomId()))count++;
        }
        return count;
    }

    private record DueCheckout(int id,int roomId,String paymentChoice,LocalDateTime checkOutDate,LocalDateTime checkoutOverdueAt,java.math.BigDecimal hourlyRate) { }

    static boolean holdsCheckoutForCounterPayment(String paymentChoice,java.math.BigDecimal bookingBalance) {
        return "counter".equalsIgnoreCase(paymentChoice)&&bookingBalance!=null&&bookingBalance.signum()>0;
    }

    static boolean isCheckoutPaymentOverdue(String paymentChoice,java.math.BigDecimal bookingBalance,LocalDateTime checkOut,LocalDateTime now) {
        return holdsCheckoutForCounterPayment(paymentChoice,bookingBalance)&&checkOut!=null&&now!=null
            &&!now.isBefore(checkOut.plus(COUNTER_CHECKOUT_GRACE));
    }

    public static String checkoutReminderMessage(LocalDateTime checkIn,LocalDateTime checkOut,String paymentChoice,java.math.BigDecimal balance) {
        String message="Thời gian lưu trú dự kiến: "+format(checkIn)+" đến "+format(checkOut)+". Hệ thống sẽ tự động ghi nhận trả phòng vào "+format(checkOut)+" và chuyển phòng sang trạng thái cần dọn dẹp.";
        if("counter".equalsIgnoreCase(paymentChoice)&&balance!=null&&balance.signum()>0){
            message+=" Đơn còn phải thanh toán "+balance.toPlainString()+" VND. Vui lòng thanh toán tại quầy khi check-out; hệ thống sẽ chờ nhân viên xác nhận thu tiền trước khi kết thúc đơn. Quý khách được miễn phụ thu 30 phút đầu sau giờ check-out; sau đó mỗi giờ bắt đầu tính thêm một giờ phụ thu theo giá hạng phòng. Nếu lưu trú quá 6 giờ sau giờ check-out, hệ thống cộng một lần phí phạt bằng một giờ phụ thu. Nếu quá 30 phút mà chưa thanh toán, bộ phận lễ tân sẽ liên hệ Quý khách.";
        }
        return message;
    }

    static String reminderKey(String type,int booking,LocalDateTime date){return type+":"+booking+":"+date.format(KEY_TIME);}
    private static String format(LocalDateTime value){return value.toString().replace('T',' ');}
}
