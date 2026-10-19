package quanlykhachsan.backend.report;

import java.math.*;
import java.time.LocalDate;
import java.util.*;
import quanlykhachsan.backend.pricing.YieldRatePolicy;

/** Transparent internal advisory calculated only from persisted closed business days. */
public final class RevenueHistoryPolicy {
    public record Day(LocalDate date,BigDecimal revenue,int occupied,int inventory) { }
    public record Recommendation(String source,String history_status,int history_days,BigDecimal on_books_occupancy,
            BigDecimal predicted_occupancy,BigDecimal adr,BigDecimal revpar,BigDecimal recommended_multiplier) { }
    public static Recommendation recommend(List<Day> days,LocalDate today,LocalDate target,int booked,int inventory){
        if(days==null||inventory<1||booked<0||booked>inventory||target.isBefore(today))throw new IllegalArgumentException("Invalid revenue source data");
        var seen=new HashSet<LocalDate>();
        for(var day:days)if(day.date()==null||!day.date().isBefore(today)||!seen.add(day.date())||day.revenue()==null||day.revenue().signum()<0||day.inventory()<1||day.occupied()<0||day.occupied()>day.inventory()||(day.occupied()==0&&day.revenue().signum()>0))throw new IllegalArgumentException("Invalid revenue history");
        BigDecimal onBooks=ratio(BigDecimal.valueOf(booked).multiply(BigDecimal.valueOf(100)),inventory);
        if(days.size()<7||days.stream().map(Day::date).max(LocalDate::compareTo).orElse(LocalDate.MIN).isBefore(today.minusDays(14)))
            return new Recommendation("INSUFFICIENT_HISTORY","At least seven recent reconciled business days required",days.size(),onBooks,null,null,null,null);
        var weekdays=days.stream().filter(d->d.date().getDayOfWeek()==target.getDayOfWeek()).toList();
        var sample=weekdays.size()>=2?weekdays:days;
        long sold=sample.stream().mapToLong(Day::occupied).sum(),capacity=sample.stream().mapToLong(Day::inventory).sum();
        BigDecimal revenue=sample.stream().map(Day::revenue).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal occupancy=ratio(BigDecimal.valueOf(sold).multiply(BigDecimal.valueOf(100)),capacity).max(onBooks);
        BigDecimal multiplier=occupancy.compareTo(new BigDecimal("80"))>=0?new BigDecimal("1.25"):occupancy.compareTo(new BigDecimal("50"))>=0?new BigDecimal("1.10"):BigDecimal.ONE;
        return new Recommendation("PERSISTED_HISTORY","AVAILABLE",days.size(),onBooks,occupancy,sold==0?null:ratio(revenue,sold),ratio(revenue,capacity),YieldRatePolicy.recommendation(multiplier));
    }
    private static BigDecimal ratio(BigDecimal value,long denominator){return value.divide(BigDecimal.valueOf(denominator),2,RoundingMode.HALF_UP);}
}
