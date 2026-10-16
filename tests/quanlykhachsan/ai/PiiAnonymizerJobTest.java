package quanlykhachsan.ai;

import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import quanlykhachsan.support.Phase2TestDatabase;
import quanlykhachsan.backend.customer.*;
import quanlykhachsan.backend.system.PiiAnonymizerJob;
import quanlykhachsan.backend.utils.DatabaseConnection;
import static org.junit.jupiter.api.Assertions.*;

class PiiAnonymizerJobTest {
    @Test void retentionPurgesOnlyExpiredDataPreservesLedgerAndChainsAudit() throws Exception {
        Phase2TestDatabase.require();LocalDateTime now=LocalDateTime.of(2026,10,16,12,0);
        var customers=new ArrayList<Integer>();var bookings=new ArrayList<Integer>();
        try(var c=DatabaseConnection.getConnection()){
            try{
                for(int age:new int[]{31,1100,10}){
                    String suffix=UUID.randomUUID().toString().replace("-", "").substring(0,12);
                    int customer=new CustomerDAOImpl().createCustomer("Synthetic retention","RET"+suffix,"ID"+suffix,null,"Member");customers.add(customer);
                    c.setAutoCommit(false);GuestIdentityVaultDao.configured().save(c,customer,"ID"+suffix,new byte[]{1,2,3},new byte[]{4,5,6});c.commit();c.setAutoCommit(true);
                    LocalDateTime checkout=now.minusDays(age);
                    try(var q=c.prepareStatement("UPDATE guest_identity_vault SET captured_at=? WHERE customer_id=?")){q.setTimestamp(1,Timestamp.valueOf(checkout.minusDays(1)));q.setInt(2,customer);q.executeUpdate();}
                    int booking;
                    try(var q=c.prepareStatement("INSERT INTO bookings(customer_id,room_id,check_in_date,check_out_date,check_out_actual,status,total_price) SELECT ?,MIN(id),?,?,?,'checked_out',100 FROM rooms",Statement.RETURN_GENERATED_KEYS)){
                        q.setInt(1,customer);q.setTimestamp(2,Timestamp.valueOf(checkout.minusDays(2)));q.setTimestamp(3,Timestamp.valueOf(checkout));q.setTimestamp(4,Timestamp.valueOf(checkout));q.executeUpdate();try(var r=q.getGeneratedKeys()){r.next();booking=r.getInt(1);bookings.add(booking);}
                    }
                    long folio;
                    try(var q=c.prepareStatement("INSERT INTO folios(booking_id,balance,status,code) VALUES (?,0,'settled',?)",Statement.RETURN_GENERATED_KEYS)){
                        q.setInt(1,booking);q.setString(2,"RET-"+suffix);q.executeUpdate();try(var r=q.getGeneratedKeys()){r.next();folio=r.getLong(1);}
                    }
                    for(String type:List.of("debit","credit"))try(var q=c.prepareStatement("INSERT INTO folio_transactions(folio_id,transaction_type,charge_type,amount,business_date) VALUES (?,?,?,100,CURRENT_DATE)")){
                        q.setLong(1,folio);q.setString(2,type);q.setString(3,type.equals("debit")?"room_rate":"payment");q.executeUpdate();
                    }
                }
                long ledgerBefore=scalar(c,"SELECT COUNT(*) FROM folio_transactions");
                assertTrue(PiiAnonymizerJob.purge(c,now)>=3);
                assertEquals(ledgerBefore,scalar(c,"SELECT COUNT(*) FROM folio_transactions"));
                assertEquals(1,scalar(c,"SELECT COUNT(*) FROM guest_identity_vault WHERE customer_id="+customers.get(0)+" AND encrypted_raw_payload IS NULL AND face_embedding_blob IS NULL"));
                assertEquals(0,scalar(c,"SELECT COUNT(*) FROM guest_identity_vault WHERE customer_id="+customers.get(1)));
                assertEquals(1,scalar(c,"SELECT COUNT(*) FROM customers WHERE id="+customers.get(1)+" AND phone=CONCAT('ANON-',id) AND identity_hash IS NULL"));
                assertEquals(1,scalar(c,"SELECT COUNT(*) FROM guest_identity_vault WHERE customer_id="+customers.get(2)+" AND face_embedding_blob IS NOT NULL"));
                assertEquals(0,PiiAnonymizerJob.purge(c,now));
                assertTrue(PiiAnonymizerJob.verifyAudit(c));
                assertEquals(3,scalar(c,"SELECT COUNT(*) FROM bookings WHERE id IN ("+bookings.get(0)+","+bookings.get(1)+","+bookings.get(2)+")"));
            }finally{
                for(int booking:bookings){try(var q=c.prepareStatement("DELETE FROM folios WHERE booking_id=?")){q.setInt(1,booking);q.executeUpdate();}try(var q=c.prepareStatement("DELETE FROM bookings WHERE id=?")){q.setInt(1,booking);q.executeUpdate();}}
                for(int customer:customers)try(var q=c.prepareStatement("DELETE FROM customers WHERE id=?")){q.setInt(1,customer);q.executeUpdate();}
            }
        }
    }
    private long scalar(Connection c,String sql) throws SQLException{try(var q=c.createStatement();var r=q.executeQuery(sql)){r.next();return r.getLong(1);}}
}
