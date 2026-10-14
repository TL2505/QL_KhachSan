package quanlykhachsan.promotion;
import java.sql.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.backend.pricing.DynamicPricingEngine;
import quanlykhachsan.backend.promotion.*;
import quanlykhachsan.financial.PaymentFixture;
import quanlykhachsan.support.Phase2TestDatabase;
class VoucherRedemptionIntegrationTest {
    @Test void quotaIsAtomicAndRollbackDoesNotSpendVoucher()throws Exception {
        Phase2TestDatabase.require();String code="TEST_"+UUID.randomUUID().toString().substring(0,8);int promo;
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("INSERT INTO promotions(name,code,discount_type,discount_value,start_date,end_date,status,max_discount,minimum_spend,max_uses_per_user) VALUES ('Synthetic voucher fixture',?,'percentage',20,DATE_SUB(NOW(),INTERVAL 1 DAY),DATE_ADD(NOW(),INTERVAL 1 DAY),'active',500000,0,1)",Statement.RETURN_GENERATED_KEYS)) {
            q.setString(1,code);q.executeUpdate();try(ResultSet r=q.getGeneratedKeys()){r.next();promo=r.getInt(1);}
        }
        var engine=new VoucherService();var day=LocalDate.now();var range=new PortalValidation.DateRange(day,day.plusDays(1));
        var quote=new DynamicPricingEngine.Quote(1,new BigDecimal("3000000"),List.of(),new BigDecimal("3000000"),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
        var pool=Executors.newFixedThreadPool(2);
        try(PaymentFixture a=new PaymentFixture(BigDecimal.TEN);PaymentFixture b=new PaymentFixture(BigDecimal.TEN)) {
            int customer=(int)Phase2TestDatabase.scalar("SELECT customer_id FROM bookings WHERE id="+a.booking);
            try(Connection c=DatabaseConnection.getConnection()) {
                c.setAutoCommit(false);var d=engine.evaluate(c,code,customer,1,range,quote,true);engine.record(c,d,customer,a.booking,a.folio);c.rollback();
            }
            assertEquals(0,Phase2TestDatabase.scalar("SELECT COUNT(*) FROM promotion_redemptions WHERE promotion_id="+promo));
            var start=new CountDownLatch(1);List<Future<Boolean>> futures=new ArrayList<>();
            for(PaymentFixture fixture:List.of(a,b))futures.add(pool.submit(()->{
                start.await();try(Connection c=DatabaseConnection.getConnection()) {
                    c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);c.setAutoCommit(false);
                    try{var d=engine.evaluate(c,code,customer,1,range,quote,true);engine.record(c,d,customer,fixture.booking,fixture.folio);c.commit();return true;}
                    catch(IllegalArgumentException x){c.rollback();return false;}
                }
            }));
            start.countDown();int wins=0;for(var f:futures)if(f.get(10,TimeUnit.SECONDS))wins++;
            assertEquals(1,wins);assertEquals(1,Phase2TestDatabase.scalar("SELECT COUNT(*) FROM promotion_redemptions WHERE promotion_id="+promo));
            try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("DELETE FROM promotion_redemptions WHERE promotion_id=?")){q.setInt(1,promo);q.executeUpdate();}
        }finally {
            pool.shutdownNow();try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("DELETE FROM promotions WHERE id=?")){q.setInt(1,promo);q.executeUpdate();}
        }
    }
}
