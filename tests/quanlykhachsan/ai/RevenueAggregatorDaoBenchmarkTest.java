package quanlykhachsan.ai;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.report.RevenueAggregatorDao;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.support.Phase2TestDatabase;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in benchmark. Uses a rollback-only transaction in the disposable fixture. */
class RevenueAggregatorDaoBenchmarkTest {
    @Test void aggregateClosedSnapshotsWithOneHundredThousandHistoricalBookings()throws Exception{
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("phase3.benchmark"),"Explicit benchmark opt-in required");
        Phase2TestDatabase.require();
        try(var c=DatabaseConnection.getConnection()){
            c.setAutoCommit(false);
            try{
                int customer,room;
                try(var q=c.createStatement();var r=q.executeQuery("SELECT MIN(id) FROM customers")){r.next();customer=r.getInt(1);}
                try(var q=c.createStatement();var r=q.executeQuery("SELECT MIN(id) FROM rooms")){r.next();room=r.getInt(1);}
                LocalDate start=LocalDate.of(1800,1,1);
                try(var q=c.prepareStatement("INSERT INTO bookings(customer_id,room_id,check_in_date,check_out_date,total_price,status) VALUES (?,?,?,?,100000,'checked_out')")){
                    for(int i=0;i<100000;i++){
                        LocalDate day=start.plusDays(i%730);q.setInt(1,customer);q.setInt(2,room);
                        q.setTimestamp(3,Timestamp.valueOf(day.atTime(14,0)));q.setTimestamp(4,Timestamp.valueOf(day.plusDays(1).atTime(12,0)));q.addBatch();
                        if(i%1000==999)q.executeBatch();
                    }
                }
                try(var q=c.prepareStatement("INSERT INTO daily_business_reports(audit_date,room_revenue,occupied_rooms,available_rooms) VALUES (?,1000000,5,10)")){
                    for(int i=0;i<730;i++){q.setDate(1,java.sql.Date.valueOf(start.plusDays(i)));q.addBatch();}q.executeBatch();
                }
                var dao=new RevenueAggregatorDao();var result=dao.history(c,start,start.plusDays(729));
                assertEquals(730,result.size());assertEquals(50,result.get(0).occupancy());assertEquals(0,new java.math.BigDecimal("100000").compareTo(result.get(0).revpar()));
                long[] samples=new long[20];
                for(int i=0;i<samples.length;i++){long then=System.nanoTime();assertEquals(730,dao.history(c,start,start.plusDays(729)).size());samples[i]=System.nanoTime()-then;}
                Arrays.sort(samples);double p95=samples[18]/1e6;
                System.out.printf(Locale.ROOT,"PHASE3_REVENUE_BENCHMARK bookings=100000 closed_days=730 warm_samples=20 p95_ms=%.3f max_ms=%.3f%n",p95,samples[19]/1e6);
                assertTrue(samples[19]<80_000_000L,"Every measured warmed aggregate query must be below 80ms");
            }finally{c.rollback();c.setAutoCommit(true);}
        }
    }
}
