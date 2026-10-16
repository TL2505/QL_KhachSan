package quanlykhachsan.backend.system;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Nightly privacy processing after audit. Unknown actual checkout dates fail closed. */
public final class PiiAnonymizerJob implements AutoCloseable {
    private static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"hotel-pii-retention");t.setDaemon(true);return t;});
    public void start(){
        LocalDateTime now=LocalDateTime.now(ZONE),next=now.toLocalDate().atTime(0,10);
        if(!next.isAfter(now))next=next.plusDays(1);
        executor.scheduleAtFixedRate(()->{
            try(var c=DatabaseConnection.getConnection()){purge(c,LocalDateTime.now(ZONE));}
            catch(SQLException e){org.slf4j.LoggerFactory.getLogger(PiiAnonymizerJob.class).error("Privacy retention failed; transaction rolled back");}
        },Duration.between(now,next).toMillis(),TimeUnit.DAYS.toMillis(1),TimeUnit.MILLISECONDS);
    }
    public static int purge(Connection c,LocalDateTime now) throws SQLException {
        now=now.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if(!c.getAutoCommit())throw new SQLException("Retention must own its transaction");
        c.setAutoCommit(false);
        try{
            byte[] head;
            try(var q=c.prepareStatement("DELETE FROM ai_chat_logs WHERE created_at<?")){q.setTimestamp(1,Timestamp.valueOf(now.minusMonths(6)));q.executeUpdate();}
            try(var q=c.prepareStatement("DELETE FROM ai_ticket_messages WHERE created_at<?")){q.setTimestamp(1,Timestamp.valueOf(now.minusMonths(6)));q.executeUpdate();}
            try(var q=c.createStatement();var r=q.executeQuery("SELECT event_hash FROM privacy_audit_head WHERE id=1 FOR UPDATE")){
                if(!r.next())throw new SQLException("Privacy audit head is unavailable");head=r.getBytes(1);
            }
            List<Integer> candidates=new ArrayList<>();
            try(var q=c.prepareStatement("SELECT DISTINCT customer_id FROM bookings WHERE status='checked_out' AND check_out_actual<?")){
                q.setTimestamp(1,Timestamp.valueOf(now.minusDays(30)));try(var r=q.executeQuery()){while(r.next())candidates.add(r.getInt(1));}
            }
            int changed=0;
            for(int id:candidates){
                try(var q=c.prepareStatement("SELECT id FROM customers WHERE id=? FOR UPDATE")){q.setInt(1,id);try(var r=q.executeQuery()){if(!r.next())continue;}}
                LocalDateTime latest=null;boolean unsafe=false;
                // A locking/current read observes a newly committed stay after taking the parent lock.
                try(var q=c.prepareStatement("SELECT status,check_out_actual FROM bookings WHERE customer_id=? FOR UPDATE")){
                    q.setInt(1,id);try(var r=q.executeQuery()){while(r.next()){
                        String status=r.getString(1);Timestamp actual=r.getTimestamp(2);
                        if(Set.of("confirmed","checked_in","reserved").contains(status) || ("checked_out".equals(status)&&actual==null)){unsafe=true;break;}
                        if("checked_out".equals(status)&&actual!=null&&(latest==null||actual.toLocalDateTime().isAfter(latest)))latest=actual.toLocalDateTime();
                    }}
                }
                if(unsafe||latest==null||!latest.isBefore(now.minusDays(30)))continue;
                int purged;
                try(var q=c.prepareStatement("UPDATE guest_identity_vault SET encrypted_raw_payload=NULL,face_embedding_blob=NULL WHERE customer_id=? AND captured_at<=? AND (encrypted_raw_payload IS NOT NULL OR face_embedding_blob IS NOT NULL)")){
                    q.setInt(1,id);q.setTimestamp(2,Timestamp.valueOf(latest));purged=q.executeUpdate();
                }
                if(purged>0){head=audit(c,head,id,"BIOMETRIC_PURGE",now);changed++;}
                if(!latest.isBefore(now.minusYears(3)))continue;
                try(var q=c.prepareStatement("SELECT id FROM folios WHERE booking_id IN (SELECT id FROM bookings WHERE customer_id=?) AND (balance<>0 OR status NOT IN ('settled','closed','cancelled','void')) FOR UPDATE")){
                    q.setInt(1,id);try(var r=q.executeQuery()){if(r.next())continue;}
                }
                int anonymized;
                try(var q=c.prepareStatement("UPDATE customers SET full_name=CONCAT('Anonymized guest ',id),phone=CONCAT('ANON-',id),email=NULL,address=NULL,date_of_birth=NULL,company_name=NULL,tax_code=NULL,identity_hash=NULL,identity_card=NULL WHERE id=? AND (full_name<>CONCAT('Anonymized guest ',id) OR identity_hash IS NOT NULL)")){
                    q.setInt(1,id);anonymized=q.executeUpdate();
                }
                if(anonymized>0){
                    try(var q=c.prepareStatement("DELETE FROM guest_identity_vault WHERE customer_id=?")){q.setInt(1,id);q.executeUpdate();}
                    try(var q=c.prepareStatement("UPDATE users SET full_name=CONCAT('Anonymized user ',id),email=NULL,phone=NULL,username=CONCAT('anonymized-user-',id),status='inactive' WHERE customer_id=?")){q.setInt(1,id);q.executeUpdate();}
                    head=audit(c,head,id,"GUEST_ANONYMIZED",now);changed++;
                }
            }
            try(var q=c.prepareStatement("UPDATE privacy_audit_head SET event_hash=? WHERE id=1")){q.setBytes(1,head);q.executeUpdate();}
            c.commit();return changed;
        }catch(SQLException | RuntimeException e){c.rollback();throw e;}
        finally{c.setAutoCommit(true);}
    }
    private static byte[] audit(Connection c,byte[] previous,int customer,String type,LocalDateTime time) throws SQLException {
        try{
            var digest=MessageDigest.getInstance("SHA-256");digest.update(previous);
            byte[] hash=digest.digest((customer+"|"+type+"|"+time).getBytes(StandardCharsets.UTF_8));
            try(var q=c.prepareStatement("INSERT INTO privacy_audit(customer_id,event_type,occurred_at,previous_hash,event_hash) VALUES (?,?,?,?,?)")){
                q.setInt(1,customer);q.setString(2,type);q.setTimestamp(3,Timestamp.valueOf(time));q.setBytes(4,previous);q.setBytes(5,hash);q.executeUpdate();
            }return hash;
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 unavailable",e);}
    }
    public static boolean verifyAudit(Connection c) throws SQLException {
        byte[] previous=new byte[32];
        try(var q=c.createStatement();var rows=q.executeQuery("SELECT customer_id,event_type,occurred_at,previous_hash,event_hash FROM privacy_audit ORDER BY id")){
            while(rows.next()){
                if(!MessageDigest.isEqual(previous,rows.getBytes(4)))return false;
                try{
                    var digest=MessageDigest.getInstance("SHA-256");digest.update(previous);
                    byte[] expected=digest.digest((rows.getInt(1)+"|"+rows.getString(2)+"|"+rows.getTimestamp(3).toLocalDateTime()).getBytes(StandardCharsets.UTF_8));
                    if(!MessageDigest.isEqual(expected,rows.getBytes(5)))return false;previous=expected;
                }catch(NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 unavailable",e);}
            }
        }
        try(var q=c.createStatement();var r=q.executeQuery("SELECT event_hash FROM privacy_audit_head WHERE id=1")){return r.next()&&MessageDigest.isEqual(previous,r.getBytes(1));}
    }
    @Override public void close(){executor.shutdownNow();}
}
