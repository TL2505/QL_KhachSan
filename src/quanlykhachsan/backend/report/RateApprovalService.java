package quanlykhachsan.backend.report;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import quanlykhachsan.backend.pricing.YieldRatePolicy;

public final class RateApprovalService {
    public record Rate(int room_type_id,String date,@com.google.gson.annotations.SerializedName(value="approved_multiplier",alternate={"multiplier"}) BigDecimal multiplier,int version,String override_reason){}
    /** Deterministic lock order; entire batch and its audit either commit or roll back. */
    public void apply(Connection c,List<Rate> rates,int actor,boolean admin,LocalDate today) throws SQLException {
        if(!admin)throw new SecurityException("Only Admin/GM may apply rates");
        if(actor<=0||rates==null||rates.isEmpty()||rates.size()>1000)throw new IllegalArgumentException("Invalid approval batch");
        var ordered=new ArrayList<>(rates);var keys=new HashSet<String>();
        for(var rate:ordered){
            LocalDate date=LocalDate.parse(rate.date());
            if(rate.room_type_id()<1||rate.version()<0||date.isBefore(today)||date.isAfter(today.plusDays(365)))throw new IllegalArgumentException("Invalid rate window");
            YieldRatePolicy.approve(rate.multiplier(),admin,rate.override_reason());
            if(!keys.add(rate.room_type_id()+"|"+rate.date()))throw new IllegalArgumentException("Duplicate rate entry");
        }
        ordered.sort(Comparator.comparingInt(Rate::room_type_id).thenComparing(Rate::date));
        if(!c.getAutoCommit())throw new SQLException("Rate service must own transaction");
        c.setAutoCommit(false);
        try{
            for(var rate:ordered){
                // Parent lock also serializes creation of a previously absent rule.
                try(var q=c.prepareStatement("SELECT id FROM room_types WHERE id=? FOR UPDATE")){q.setInt(1,rate.room_type_id());try(var r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Unknown room category");}}
                BigDecimal old=null;int version=0;
                try(var q=c.prepareStatement("SELECT multiplier,version FROM pricing_rules WHERE room_type_id=? AND rate_date=? FOR UPDATE")){
                    q.setInt(1,rate.room_type_id());q.setDate(2,java.sql.Date.valueOf(rate.date()));try(var r=q.executeQuery()){if(r.next()){old=r.getBigDecimal(1);version=r.getInt(2);}}
                }
                if(version!=rate.version())throw new IllegalStateException("Rate version conflict; refresh recommendations");
                if(version==0){
                    try(var q=c.prepareStatement("INSERT INTO pricing_rules(room_type_id,rate_date,multiplier,approved_by,override_reason) VALUES (?,?,?,?,?)")){bind(q,rate,actor);q.executeUpdate();}
                }else{
                    try(var q=c.prepareStatement("UPDATE pricing_rules SET multiplier=?,approved_by=?,override_reason=?,version=version+1,approved_at=CURRENT_TIMESTAMP WHERE room_type_id=? AND rate_date=? AND version=?")){
                        q.setBigDecimal(1,rate.multiplier());q.setInt(2,actor);q.setString(3,rate.override_reason());q.setInt(4,rate.room_type_id());q.setDate(5,java.sql.Date.valueOf(rate.date()));q.setInt(6,version);
                        if(q.executeUpdate()!=1)throw new IllegalStateException("Rate version conflict");
                    }
                }
                try(var q=c.prepareStatement("INSERT INTO pricing_rule_audit(room_type_id,rate_date,old_multiplier,new_multiplier,actor_id,new_version,override_reason) VALUES (?,?,?,?,?,?,?)")){
                    q.setInt(1,rate.room_type_id());q.setDate(2,java.sql.Date.valueOf(rate.date()));q.setBigDecimal(3,old);q.setBigDecimal(4,rate.multiplier());q.setInt(5,actor);q.setInt(6,version+1);q.setString(7,rate.override_reason());q.executeUpdate();
                }
            }c.commit();
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        finally{c.setAutoCommit(true);}
    }
    private void bind(PreparedStatement q,Rate rate,int actor) throws SQLException{q.setInt(1,rate.room_type_id());q.setDate(2,java.sql.Date.valueOf(rate.date()));q.setBigDecimal(3,rate.multiplier());q.setInt(4,actor);q.setString(5,rate.override_reason());}
}
