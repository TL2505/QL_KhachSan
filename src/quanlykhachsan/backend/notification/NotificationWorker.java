package quanlykhachsan.backend.notification;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Durable queue shared by all payment methods and booking lifecycle events. */
public final class NotificationWorker implements AutoCloseable {
    public record Mail(long id,String recipient,String subject,String body) { }
    public record Outcome(String state,String error) {
        public Outcome {if(!Set.of("SENT","RETRY","FAILED","UNKNOWN").contains(state))throw new IllegalArgumentException("Invalid email outcome");}
    }
    @FunctionalInterface public interface Sender {Outcome send(Mail mail);}
    @FunctionalInterface public interface Connections {Connection open()throws SQLException;}
    private final Connections connections;private final Sender sender;private final boolean enabled;private final Clock clock;
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"notification-outbox");t.setDaemon(true);return t;});
    public NotificationWorker(){this(DatabaseConnection::getConnection,new SmtpMailSender(),Boolean.parseBoolean(System.getenv().getOrDefault("NOTIFICATION_EMAIL_ENABLED","false")),Clock.system(ZoneId.of("Asia/Ho_Chi_Minh")));}
    public NotificationWorker(Connections connections,Sender sender,boolean enabled,Clock clock){this.connections=connections;this.sender=sender;this.enabled=enabled;this.clock=clock;}
    public void start(){System.out.println("[NOTIFICATIONS] Email delivery "+(enabled?"enabled":"disabled; set NOTIFICATION_EMAIL_ENABLED=true after SMTP verification"));executor.scheduleWithFixedDelay(()->{try{reminders();lifecycle();recoverExpiredLeases();if(enabled)for(int i=0;i<10&&deliverOne();i++){} }catch(Exception e){System.err.println("[NOTIFICATIONS] Worker temporarily unavailable: "+e.getClass().getSimpleName());}},10,30,TimeUnit.SECONDS);}
    public void reminders()throws SQLException {
        LocalDateTime now=LocalDateTime.now(clock);
        try(Connection c=connections.open()){
            c.setAutoCommit(false);
            try{
                // Lock each booking before enqueue; cancellation/check-in cannot race the reminder snapshot.
                try(var q=c.prepareStatement("SELECT b.id,b.status,b.check_in_date,b.check_out_date,b.payment_choice,COALESCE((SELECT SUM(f.balance) FROM folios f WHERE f.booking_id=b.id),0.00) FROM bookings b WHERE b.customer_type='Web' AND ((b.status='confirmed' AND b.check_in_date>? AND b.check_in_date<=?) OR (b.status='checked_in' AND b.check_out_date>? AND TIMESTAMPADD(MICROSECOND,ROUND(TIMESTAMPDIFF(MICROSECOND,b.check_in_date,b.check_out_date)*0.9),b.check_in_date)<=?)) AND NOT EXISTS (SELECT 1 FROM notification_outbox o WHERE o.event_key=CONCAT(IF(b.status='confirmed','checkin_reminder','checkout_reminder'),':',b.id,':',DATE_FORMAT(IF(b.status='confirmed',b.check_in_date,b.check_out_date),'%Y-%m-%dT%H:%i:%s'))) ORDER BY b.id LIMIT 200 FOR UPDATE SKIP LOCKED")){
                    q.setObject(1,now);q.setObject(2,now.plusHours(24));q.setObject(3,now);q.setObject(4,now);
                    try(var r=q.executeQuery()){while(r.next()){
                        int id=r.getInt(1);boolean checkin="confirmed".equals(r.getString(2));String type=checkin?"checkin_reminder":"checkout_reminder";LocalDateTime date=r.getObject(checkin?3:4,LocalDateTime.class);
                        String message=checkin
                            ?"Hệ thống sẽ tự động ghi nhận nhận phòng vào "+date.toString().replace('T',' ')+" (giờ Việt Nam)."
                            :quanlykhachsan.backend.booking.WebBookingLifecycleEngine.checkoutReminderMessage(
                                r.getObject(3,LocalDateTime.class),date,r.getString(5),r.getBigDecimal(6));
                        LifecycleNotifications.booking(c,id,type,reminderKey(type,id,date),checkin?"Nhắc lịch nhận phòng":"Nhắc lịch check-out",message);
                    }}
                }
                c.commit();
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
        }
    }
    /** Web-only idempotent check-in/check-out actions share this worker's 30-second cadence. */
    public void lifecycle()throws SQLException {
        new quanlykhachsan.backend.booking.WebBookingLifecycleEngine(connections::open,clock).processDue();
    }
    public void recoverExpiredLeases()throws SQLException {
        try(Connection c=connections.open()){
            c.setAutoCommit(false);
            try{
                try(var q=c.prepareStatement("UPDATE notification_delivery_attempts a JOIN notification_outbox o ON o.id=a.outbox_id AND o.attempts=a.attempt_number SET a.state='UNKNOWN',a.error_code='WORKER_INTERRUPTED',a.finished_at=CURRENT_TIMESTAMP WHERE o.state='SENDING' AND o.lease_until<CURRENT_TIMESTAMP AND a.state='SENDING'")){q.executeUpdate();}
                try(var q=c.prepareStatement("UPDATE notification_outbox SET state='UNKNOWN',last_error='WORKER_INTERRUPTED',lease_token=NULL,lease_until=NULL WHERE state='SENDING' AND lease_until<CURRENT_TIMESTAMP")){q.executeUpdate();}c.commit();
            }catch(SQLException e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
        }
    }
    public boolean deliverOne()throws SQLException {
        if(!enabled)return false;
        Mail mail;int attempt;String token=UUID.randomUUID().toString();
        try(Connection c=connections.open()){
            c.setAutoCommit(false);
            try{
                try(var q=c.prepareStatement("SELECT id,recipient,subject,body,attempts,booking_id,event_type,event_key FROM notification_outbox WHERE state IN ('PENDING','RETRY') AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED")){
                    try(var r=q.executeQuery()){
                        if(!r.next()){c.rollback();return false;}
                        mail=new Mail(r.getLong(1),r.getString(2),r.getString(3),r.getString(4));attempt=r.getInt(5)+1;
                        // Reminders waiting across downtime are cancelled after their lifecycle/date changes.
                        if(r.getString(7).endsWith("_reminder")&&!reminderCurrent(c,r.getInt(6),r.getString(7),r.getString(8))){try(var u=c.prepareStatement("UPDATE notification_outbox SET state='SKIPPED',last_error='REMINDER_OBSOLETE' WHERE id=?")){u.setLong(1,mail.id());u.executeUpdate();}c.commit();return true;}
                    }
                }
                try(var q=c.prepareStatement("UPDATE notification_outbox SET state='SENDING',attempts=?,lease_token=?,lease_until=DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 5 MINUTE) WHERE id=?")){q.setInt(1,attempt);q.setString(2,token);q.setLong(3,mail.id());q.executeUpdate();}
                try(var q=c.prepareStatement("INSERT INTO notification_delivery_attempts(outbox_id,attempt_number,state) VALUES(?,?,'SENDING')")){q.setLong(1,mail.id());q.setInt(2,attempt);q.executeUpdate();}c.commit();
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
        }
        Outcome outcome;try{outcome=Objects.requireNonNull(sender.send(mail));}catch(Exception e){outcome=new Outcome("UNKNOWN","SENDER_OUTCOME_UNKNOWN");}
        String state=outcome.state().equals("RETRY")&&attempt>=5?"FAILED":outcome.state();
        try(Connection c=connections.open()){
            c.setAutoCommit(false);
            try{
                try(var q=c.prepareStatement("UPDATE notification_outbox SET state=?,last_error=?,next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP,INTERVAL ? SECOND),sent_at=IF(?='SENT',CURRENT_TIMESTAMP,NULL),lease_token=NULL,lease_until=NULL WHERE id=? AND state='SENDING' AND lease_token=?")){
                    q.setString(1,state);q.setString(2,outcome.error());q.setLong(3,retrySeconds(attempt));q.setString(4,state);q.setLong(5,mail.id());q.setString(6,token);if(q.executeUpdate()!=1){c.rollback();return true;}
                }
                try(var q=c.prepareStatement("UPDATE notification_delivery_attempts SET state=?,error_code=?,finished_at=CURRENT_TIMESTAMP WHERE outbox_id=? AND attempt_number=? AND state='SENDING'")){q.setString(1,state);q.setString(2,outcome.error());q.setLong(3,mail.id());q.setInt(4,attempt);q.executeUpdate();}c.commit();
            }catch(SQLException e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
        }
        return true;
    }
    private boolean reminderCurrent(Connection c,int booking,String type,String key)throws SQLException {
        try(var q=c.prepareStatement("SELECT status,check_in_date,check_out_date,customer_type FROM bookings WHERE id=?")){q.setInt(1,booking);try(var r=q.executeQuery()){if(!r.next()||!"Web".equalsIgnoreCase(r.getString(4)))return false;boolean in=type.equals("checkin_reminder");LocalDateTime date=r.getObject(in?2:3,LocalDateTime.class);return (in?"confirmed":"checked_in").equals(r.getString(1))&&date.isAfter(LocalDateTime.now(clock))&&key.equals(reminderKey(type,booking,date));}}
    }
    private static String reminderKey(String type,int booking,LocalDateTime date){return type+":"+booking+":"+date.format(java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss"));}
    public static long retrySeconds(int attempt){return Math.min(3600,30L*(1L<<Math.min(7,Math.max(0,attempt-1))));}
    public void close(){executor.shutdownNow();}
}
