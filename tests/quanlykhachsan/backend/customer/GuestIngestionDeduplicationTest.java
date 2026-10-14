package quanlykhachsan.backend.customer;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.support.Phase2TestDatabase;
import static org.junit.jupiter.api.Assertions.*;

class GuestIngestionDeduplicationTest {
    @Test void concurrentNormalizedPhonesCreateExactlyOneMember() throws Exception {
        Phase2TestDatabase.require(); String phone="0908765432";
        var workers=Executors.newFixedThreadPool(10); var start=new CountDownLatch(1);
        try {
            List<Future<Integer>> results=new ArrayList<>();
            for(int i=0;i<10;i++) { final String input=i%2==0?phone:"+84"+phone.substring(1);
                results.add(workers.submit(()->{ start.await(); return new GuestIngestionService().ingest("Test Guest",input,null); })); }
            start.countDown(); Set<Integer> ids=new HashSet<>(); for(var result:results)ids.add(result.get(20,TimeUnit.SECONDS));
            assertEquals(1,ids.size());
            try(Connection c=DatabaseConnection.getConnection(); PreparedStatement q=c.prepareStatement("SELECT COUNT(*),MAX(loyalty_level),MAX(total_loyalty_points) FROM customers WHERE phone=?")) {
                q.setString(1,phone); try(ResultSet r=q.executeQuery()){ r.next(); assertEquals(1,r.getInt(1)); assertEquals("Member",r.getString(2)); assertEquals(0,r.getInt(3)); }
            }
        } finally { workers.shutdownNow(); workers.awaitTermination(5,TimeUnit.SECONDS);
            try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("DELETE FROM customers WHERE phone=?")){q.setString(1,phone);q.executeUpdate();}
        }
    }
}
