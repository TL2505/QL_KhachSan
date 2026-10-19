package quanlykhachsan.backend.promotion;

import java.math.*;
import java.sql.*;
import quanlykhachsan.backend.booking.*;
import quanlykhachsan.backend.pricing.DynamicPricingEngine.Quote;
import quanlykhachsan.backend.utils.PortalValidation.DateRange;

/** Preview is read-only. Redemption must share the booking transaction. */
public final class VoucherService {
    public record Discount(int promotion_id,String code,BigDecimal discount_amount,
            BigDecimal room_subtotal,BigDecimal service_charge,BigDecimal vat,BigDecimal total_amount) { }

    public Discount evaluate(Connection c,String code,int customer,int roomType,DateRange dates,
            Quote quote,boolean redeeming)throws SQLException {
        if(code==null || !code.matches("[A-Za-z0-9_-]{1,50}"))throw new IllegalArgumentException("Invalid voucher code");
        if(redeeming && c.getAutoCommit())throw new IllegalStateException("Redemption requires a transaction");
        try(PreparedStatement q=c.prepareStatement("SELECT id,discount_type,discount_value,max_discount,minimum_spend,max_uses_per_user,condition_type,condition_value FROM promotions WHERE code=? AND status='active' AND CURRENT_TIMESTAMP BETWEEN start_date AND end_date"+(redeeming?" FOR UPDATE":""))) {
            q.setString(1,code);try(ResultSet r=q.executeQuery()) {
                if(!r.next())throw new IllegalArgumentException("Voucher is invalid or expired");
                int id=r.getInt(1);BigDecimal cap=r.getBigDecimal(4),minimum=r.getBigDecimal(5);int limit=r.getInt(6);
                if(cap==null || minimum==null || r.wasNull() || cap.signum()<0 || minimum.signum()<0 || limit<1)
                    throw new IllegalStateException("Voucher policy requires configuration");
                if(quote.room_subtotal().compareTo(minimum)<0)throw new IllegalArgumentException("Minimum spend is not met");
                String condition=r.getString(7),value=r.getString(8);
                if("room_type".equals(condition) && !Integer.toString(roomType).equals(value))throw new IllegalArgumentException("Room category is not eligible");
                if("min_stay".equals(condition) && dates.checkIn().until(dates.checkOut(),java.time.temporal.ChronoUnit.DAYS)<Integer.parseInt(value))throw new IllegalArgumentException("Minimum stay is not met");
                if("vip_only".equals(condition)) {
                    if(customer<1)throw new IllegalArgumentException("Verified guest identity is required");
                    try(PreparedStatement vip=c.prepareStatement("SELECT loyalty_level FROM customers WHERE id=?")){vip.setInt(1,customer);try(ResultSet v=vip.executeQuery()){if(!v.next() || !"VIP".equalsIgnoreCase(v.getString(1)))throw new IllegalArgumentException("Voucher requires VIP membership");}}
                }
                if(condition!=null && !java.util.Set.of("none","room_type","min_stay","vip_only").contains(condition))throw new IllegalStateException("Unsupported voucher policy");
                if(customer>0)try(PreparedStatement uses=c.prepareStatement("SELECT COUNT(*) FROM promotion_redemptions WHERE promotion_id=? AND customer_id=?")) {
                    uses.setInt(1,id);uses.setInt(2,customer);try(ResultSet u=uses.executeQuery()){u.next();if(u.getInt(1)>=limit)throw new IllegalArgumentException("Voucher usage limit reached");}
                }
                BigDecimal discount=calculateDiscount(quote.room_subtotal(),r.getString(2),r.getBigDecimal(3),cap);
                var charges=FinancialCalculator.calculate(quote.room_subtotal().subtract(discount),BigDecimal.ZERO,TaxConfig.fromRuntimeConfiguration());
                return new Discount(id,code,discount,charges.getRoomFee(),charges.getServiceFee(),charges.getVat(),charges.getTotalAmount());
            }
        }
    }
    public void record(Connection c,Discount discount,int customer,int booking,long folio)throws SQLException {
        if(c.getAutoCommit())throw new IllegalStateException("Redemption requires a transaction");
        try(PreparedStatement q=c.prepareStatement("INSERT INTO promotion_redemptions(promotion_id,customer_id,booking_id,folio_id,discount_amount) VALUES (?,?,?,?,?)")) {
            q.setInt(1,discount.promotion_id());q.setInt(2,customer);q.setInt(3,booking);q.setLong(4,folio);q.setBigDecimal(5,discount.discount_amount());q.executeUpdate();
        }
    }
    public static BigDecimal calculateDiscount(BigDecimal room,String type,BigDecimal value,BigDecimal cap) {
        if(room.signum()<0 || value==null || cap==null || value.signum()<0 || cap.signum()<0)throw new IllegalArgumentException("Invalid promotion amounts");
        BigDecimal amount=switch(type){case "percentage"->{if(value.compareTo(new BigDecimal("100"))>0)throw new IllegalArgumentException("Invalid percentage");yield room.multiply(value).movePointLeft(2);}case "fixed_amount"->value;default->throw new IllegalArgumentException("Invalid discount type");};
        return amount.min(cap).min(room).setScale(2,RoundingMode.HALF_UP);
    }
}
