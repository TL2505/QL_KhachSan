package quanlykhachsan.backend.booking;

import java.sql.*;
import java.util.concurrent.*;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Expires unpaid fifteen-minute holds, preserving a balancing reversal in the ledger. */
public final class PendingBookingExpiryJob implements AutoCloseable {
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"pending-booking-expiry");t.setDaemon(true);return t;});
    public void start(){worker.scheduleWithFixedDelay(()->{
        if(quanlykhachsan.backend.system.SystemStateHolder.isFrozen())return;
        try(var c=DatabaseConnection.getConnection()){expire(c);}catch(SQLException ex){org.slf4j.LoggerFactory.getLogger(getClass()).error("Pending booking expiry failed",ex);}
    },1,15,TimeUnit.SECONDS);}
    public int expire(Connection c)throws SQLException {
        c.setAutoCommit(false);
        try {
            var ids=new java.util.ArrayList<Integer>();
            try(var q=c.prepareStatement("SELECT id FROM bookings WHERE status='pending' AND created_at<=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 15 MINUTE) ORDER BY id LIMIT 50 FOR UPDATE");var r=q.executeQuery()){while(r.next())ids.add(r.getInt(1));}
            int expired=0;
            for(int id:ids){
                try(var q=c.prepareStatement("SELECT COUNT(*) FROM folio_transactions t JOIN folios f ON f.id=t.folio_id WHERE f.booking_id=? AND t.transaction_type='credit' AND t.charge_type='payment'")){
                    q.setInt(1,id);try(var r=q.executeQuery()){r.next();if(r.getInt(1)>0)continue;}
                }
                try(var q=c.prepareStatement("SELECT id,balance FROM folios WHERE booking_id=? FOR UPDATE")){
                    q.setInt(1,id);try(var r=q.executeQuery()){while(r.next()){
                        long folio=r.getLong(1);var balance=r.getBigDecimal(2);
                        if(balance.signum()>0)try(var post=c.prepareStatement("INSERT INTO folio_transactions(folio_id,transaction_type,charge_type,description,amount,business_date,reference_id) SELECT ?,'credit','adjustment','Expired unpaid reservation hold',?,business_date,? FROM hotel_business_state WHERE id=1")){
                            post.setLong(1,folio);post.setBigDecimal(2,balance);post.setString(3,"EXPIRED-HOLD-"+id+"-"+folio);if(post.executeUpdate()!=1)throw new SQLException("Missing business date");
                        }
                    }}
                }
                try(var q=c.prepareStatement("UPDATE folios SET balance=0,status='closed' WHERE booking_id=?")){q.setInt(1,id);q.executeUpdate();}
                try(var q=c.prepareStatement("UPDATE bookings SET status='cancelled' WHERE id=? AND status='pending'")){q.setInt(1,id);expired+=q.executeUpdate();}
                try(var q=c.prepareStatement("UPDATE rooms r JOIN bookings b ON b.room_id=r.id SET r.status='vacant_clean',r.version=r.version+1 WHERE b.id=? AND r.status='reserved' AND NOT EXISTS(SELECT 1 FROM bookings active WHERE active.room_id=r.id AND active.status IN ('pending','confirmed','checked_in') AND DATE(active.check_in_date)<=CURRENT_DATE AND DATE(active.check_out_date)>CURRENT_DATE)")){q.setInt(1,id);q.executeUpdate();}
            }
            c.commit();return expired;
        }catch(SQLException|RuntimeException ex){c.rollback();throw ex;}finally{c.setAutoCommit(true);}
    }
    public void close(){worker.shutdownNow();}
}
