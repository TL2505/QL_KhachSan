package quanlykhachsan.ai;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import quanlykhachsan.support.Phase2TestDatabase;
import quanlykhachsan.backend.report.*;
import quanlykhachsan.backend.pricing.*;
import quanlykhachsan.backend.utils.DatabaseConnection;
import static org.junit.jupiter.api.Assertions.*;

class YieldSafetySanityTest {
    @Test void floorCeilingAndExplicitOverride() {
        assertEquals(new BigDecimal("0.80"),YieldRatePolicy.recommendation(new BigDecimal("0.70")));
        assertEquals(new BigDecimal("2.50"),YieldRatePolicy.recommendation(new BigDecimal("3.00")));
        assertThrows(IllegalArgumentException.class,()->YieldRatePolicy.approve(new BigDecimal("0.69"),true,"Synthetic override"));
        assertThrows(IllegalArgumentException.class,()->YieldRatePolicy.approve(new BigDecimal("2.51"),true,"Synthetic override"));
        assertThrows(SecurityException.class,()->YieldRatePolicy.approve(new BigDecimal("0.75"),false,"Synthetic override"));
        assertThrows(SecurityException.class,()->YieldRatePolicy.approve(new BigDecimal("0.75"),true,null));
        assertEquals(new BigDecimal("0.70"),YieldRatePolicy.approve(new BigDecimal("0.70"),true,"Synthetic override"));
    }
    @Test void approvalsAreAtomicVersionedAndNeverMutateInventory() throws Exception {
        Phase2TestDatabase.require();LocalDate date=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(90);
        try(var c=DatabaseConnection.getConnection()){
            int type;try(var q=c.createStatement();var r=q.executeQuery("SELECT MIN(id) FROM room_types")){r.next();type=r.getInt(1);}
            try{
                long bookings=count(c,"SELECT COUNT(*) FROM bookings"),versions=count(c,"SELECT SUM(version) FROM rooms");
                var service=new RateApprovalService();
                service.apply(c,List.of(new RateApprovalService.Rate(type,date.toString(),new BigDecimal("0.75"),0,"Synthetic approval")),1,true,LocalDate.now());
                assertEquals(1,count(c,"SELECT version FROM pricing_rules WHERE room_type_id="+type+" AND rate_date='"+date+"'"));
                assertThrows(IllegalStateException.class,()->service.apply(c,List.of(
                    new RateApprovalService.Rate(type,date.toString(),new BigDecimal("1.25"),1,null),
                    new RateApprovalService.Rate(type,date.plusDays(1).toString(),new BigDecimal("1.25"),9,null)),1,true,LocalDate.now()));
                assertEquals(1,count(c,"SELECT version FROM pricing_rules WHERE room_type_id="+type+" AND rate_date='"+date+"'"));
                assertEquals(1,count(c,"SELECT COUNT(*) FROM pricing_rule_audit WHERE room_type_id="+type+" AND rate_date='"+date+"'"));
                assertEquals(bookings,count(c,"SELECT COUNT(*) FROM bookings"));assertEquals(versions,count(c,"SELECT SUM(version) FROM rooms"));
                var quote=new DynamicPricingEngine().quote(c,type,new quanlykhachsan.backend.utils.PortalValidation.DateRange(date,date.plusDays(1)));
                assertEquals(new BigDecimal("0.75"),quote.nightly_rates().get(0).multiplier());
            }finally{
                for(String table:List.of("pricing_rule_audit","pricing_rules"))try(var q=c.prepareStatement("DELETE FROM "+table+" WHERE room_type_id=? AND rate_date BETWEEN ? AND ?")){q.setInt(1,type);q.setDate(2,java.sql.Date.valueOf(date));q.setDate(3,java.sql.Date.valueOf(date.plusDays(1)));q.executeUpdate();}
            }
        }
    }
    @Test void sixHourCacheHasExactExpiry() {
        class MutableClock extends Clock {Instant now=Instant.parse("2026-10-16T00:00:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now;}}
        var clock=new MutableClock();var cache=new ForecastCache<String>(clock);var calls=new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Supplier<String> load=()->"value"+calls.incrementAndGet();
        assertEquals("value1",cache.get("30",load));clock.now=clock.now.plusSeconds(21599);assertEquals("value1",cache.get("30",load));
        clock.now=clock.now.plusSeconds(1);assertEquals("value2",cache.get("30",load));
    }
    private long count(Connection c,String sql)throws SQLException{try(var q=c.createStatement();var r=q.executeQuery(sql)){r.next();return r.getLong(1);}}
}
