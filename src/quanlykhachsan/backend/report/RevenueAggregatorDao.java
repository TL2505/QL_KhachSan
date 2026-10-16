package quanlykhachsan.backend.report;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** Aggregates only. Python receives no booking, customer, folio or payment rows. */
public final class RevenueAggregatorDao {
    public record Day(String date,double occupancy,java.math.BigDecimal room_revenue,java.math.BigDecimal revpar){}
    public List<Day> history(Connection c,LocalDate from,LocalDate through) throws SQLException {
        if(from.isAfter(through)||from.plusYears(10).isBefore(through))throw new IllegalArgumentException("Invalid history window");
        var result=new ArrayList<Day>();
        try(var q=c.prepareStatement("SELECT o.business_date,o.occupancy,r.room_revenue,r.revpar FROM v_daily_occupancy_history o JOIN v_daily_revpar_history r ON r.business_date=o.business_date WHERE o.business_date BETWEEN ? AND ? AND o.occupancy IS NOT NULL ORDER BY o.business_date")){
            q.setDate(1,java.sql.Date.valueOf(from));q.setDate(2,java.sql.Date.valueOf(through));
            try(var r=q.executeQuery()){while(r.next())result.add(new Day(r.getDate(1).toString(),r.getDouble(2),r.getBigDecimal(3),r.getBigDecimal(4)));}
        }return List.copyOf(result);
    }
}
