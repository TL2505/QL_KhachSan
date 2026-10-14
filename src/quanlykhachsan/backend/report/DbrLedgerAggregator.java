package quanlykhachsan.backend.report;
import java.math.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import quanlykhachsan.backend.utils.*;

/** Capture at the frozen audit boundary; later reads use the saved snapshot. */
public final class DbrLedgerAggregator {
    public DbrSnapshot capture(Connection c,LocalDate date)throws SQLException {
        BigDecimal debits,credits,excess,receivables;
        try(PreparedStatement q=c.prepareStatement("SELECT COALESCE(SUM(CASE WHEN transaction_type='debit' THEN amount ELSE 0 END),0),COALESCE(SUM(CASE WHEN transaction_type='credit' AND charge_type<>'credit_adjustment' THEN amount ELSE 0 END),0),COALESCE(SUM(CASE WHEN transaction_type='credit' AND charge_type='credit_adjustment' THEN amount ELSE 0 END),0) FROM folio_transactions WHERE business_date<=?")) {
            q.setDate(1,java.sql.Date.valueOf(date));try(ResultSet r=q.executeQuery()){r.next();debits=r.getBigDecimal(1);credits=r.getBigDecimal(2);excess=r.getBigDecimal(3);}
        }
        try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT COALESCE(SUM(balance),0) FROM folios")){r.next();receivables=r.getBigDecimal(1);}
        BigDecimal variance=debits.subtract(credits).subtract(receivables).setScale(2,RoundingMode.HALF_UP);
        if(variance.signum()!=0)throw new SQLException("Ledger snapshot does not reconcile");
        Map<String,BigDecimal> departments=new LinkedHashMap<>();
        try(PreparedStatement q=c.prepareStatement("SELECT charge_type,SUM(CASE WHEN transaction_type='debit' THEN amount ELSE -amount END) FROM folio_transactions WHERE business_date=? AND charge_type NOT IN ('payment','credit_adjustment') GROUP BY charge_type ORDER BY charge_type")) {
            q.setDate(1,java.sql.Date.valueOf(date));try(ResultSet r=q.executeQuery()){while(r.next())departments.put(r.getString(1),r.getBigDecimal(2).setScale(2));}
        }
        int operational,occupied;
        try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT COUNT(*) FROM rooms WHERE status NOT IN ('out_of_order','out_of_service')")){r.next();operational=r.getInt(1);}
        try(PreparedStatement q=c.prepareStatement("SELECT COUNT(DISTINCT b.room_id) FROM bookings b JOIN rooms r ON r.id=b.room_id WHERE b.status IN ('checked_in','checked_out') AND DATE(b.check_in_date)<=? AND DATE(COALESCE(b.check_out_actual,b.check_out_date))>? AND r.status NOT IN ('out_of_order','out_of_service')")) {
            q.setDate(1,java.sql.Date.valueOf(date));q.setDate(2,java.sql.Date.valueOf(date));try(ResultSet r=q.executeQuery()){r.next();occupied=r.getInt(1);}
        }
        BigDecimal room=departments.getOrDefault("room_rate",new BigDecimal("0.00"));
        BigDecimal total=departments.values().stream().reduce(new BigDecimal("0.00"),BigDecimal::add);
        return new DbrSnapshot(date.toString(),room,total,occupied,operational,ratio(BigDecimal.valueOf(occupied).multiply(BigDecimal.valueOf(100)),operational),ratio(room,occupied),ratio(room,operational),Map.copyOf(departments),debits,credits,receivables,excess,variance);
    }
    public DbrSnapshot generate(LocalDate date)throws SQLException {
        try(Connection c=DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);try {
                // Audit holds the global freeze. A report cannot silently overwrite a closed snapshot.
                DbrSnapshot existing=read(c,date);if(existing!=null){c.rollback();return existing;}
                var snapshot=capture(c,date);
                var metrics=new DbrLedgerDao.DbrMetrics(snapshot.total_revenue(),snapshot.room_revenue(),snapshot.occupied_rooms(),snapshot.operational_rooms(),snapshot.occupancy_rate(),snapshot.adr(),snapshot.revpar());
                new DbrLedgerDao().save(c,date,metrics);
                try(PreparedStatement q=c.prepareStatement("UPDATE daily_business_reports SET snapshot_json=? WHERE audit_date=?")){q.setString(1,JsonUtil.getGson().toJson(snapshot));q.setDate(2,java.sql.Date.valueOf(date));q.executeUpdate();}
                c.commit();return snapshot;
            }catch(Exception x){c.rollback();if(x instanceof SQLException s)throw s;throw x;}
            finally{c.setAutoCommit(true);}
        }
    }
    public DbrSnapshot read(Connection c,LocalDate date)throws SQLException {
        try(PreparedStatement q=c.prepareStatement("SELECT snapshot_json FROM daily_business_reports WHERE audit_date=?")){q.setDate(1,java.sql.Date.valueOf(date));try(ResultSet r=q.executeQuery()){return r.next() && r.getString(1)!=null?JsonUtil.getGson().fromJson(r.getString(1),DbrSnapshot.class):null;}}
    }
    public static BigDecimal ratio(BigDecimal value,int denominator){return denominator==0?new BigDecimal("0.00"):value.divide(BigDecimal.valueOf(denominator),2,RoundingMode.HALF_UP);}
}
