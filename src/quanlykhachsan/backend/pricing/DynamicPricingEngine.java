package quanlykhachsan.backend.pricing;

import java.math.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import quanlykhachsan.backend.booking.*;
import quanlykhachsan.backend.utils.PortalValidation.DateRange;

/** Reads one occupancy matrix per stay, counting each physical room at most once. */
public final class DynamicPricingEngine {
    public Quote quote(Connection c,int roomTypeId,DateRange range) throws SQLException {
        BigDecimal base;
        try(PreparedStatement q=c.prepareStatement("SELECT base_price FROM room_types WHERE id=?")) {
            q.setInt(1,roomTypeId);try(ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Unknown room category");base=r.getBigDecimal(1);}
        }
        int operational;
        try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT COUNT(*) FROM rooms WHERE status NOT IN ('out_of_order','out_of_service')")){r.next();operational=r.getInt(1);}
        if(operational==0)throw new IllegalStateException("No operational inventory");
        List<Reservation> reservations=new ArrayList<>();
        try(PreparedStatement q=c.prepareStatement("SELECT b.room_id,DATE(b.check_in_date),DATE(b.check_out_date) FROM bookings b JOIN rooms r ON r.id=b.room_id WHERE b.status IN ('confirmed','checked_in') AND r.status NOT IN ('out_of_order','out_of_service') AND b.check_in_date<? AND b.check_out_date>?")) {
            q.setTimestamp(1,Timestamp.valueOf(range.checkOut().atTime(12,0)));q.setTimestamp(2,Timestamp.valueOf(range.checkIn().atTime(14,0)));
            try(ResultSet r=q.executeQuery()){while(r.next())reservations.add(new Reservation(r.getInt(1),r.getDate(2).toLocalDate(),r.getDate(3).toLocalDate()));}
        }
        List<NightlyRate> nights=new ArrayList<>();BigDecimal subtotal=BigDecimal.ZERO;
        for(LocalDate day=range.checkIn();day.isBefore(range.checkOut());day=day.plusDays(1)) {
            Set<Integer> occupied=new HashSet<>();for(Reservation r:reservations)if(!day.isBefore(r.in()) && day.isBefore(r.out()))occupied.add(r.room());
            BigDecimal multiplier=PricingTier.forOccupancy(occupied.size(),operational).multiplier();
            BigDecimal amount=nightly(base,multiplier);subtotal=subtotal.add(amount);
            nights.add(new NightlyRate(day.toString(),base,multiplier,amount,occupied.size(),operational));
        }
        var charges=FinancialCalculator.calculate(subtotal,BigDecimal.ZERO,TaxConfig.fromRuntimeConfiguration());
        return new Quote(roomTypeId,base,List.copyOf(nights),charges.getRoomFee(),charges.getServiceFee(),charges.getVat(),charges.getTotalAmount());
    }
    public static BigDecimal nightly(BigDecimal base,BigDecimal multiplier) {
        if(base==null || base.signum()<=0 || multiplier==null || multiplier.signum()<=0)throw new IllegalArgumentException("Invalid rate");
        return base.multiply(multiplier).setScale(2,RoundingMode.HALF_UP);
    }
    private record Reservation(int room,LocalDate in,LocalDate out) { }
    public record NightlyRate(String date,BigDecimal base_price,BigDecimal multiplier,BigDecimal amount,int occupied_rooms,int operational_rooms) { }
    public record Quote(int room_type_id,BigDecimal base_price,List<NightlyRate> nightly_rates,BigDecimal room_subtotal,BigDecimal service_charge,BigDecimal vat,BigDecimal total_amount) { }
}
