package quanlykhachsan.backend.ai;

import java.sql.*;
import java.util.*;
import quanlykhachsan.backend.utils.DatabaseConnection;

public final class EscalationTicketDao {
    public int create(String session,String category,String correlation) throws SQLException {
        return create(session,category,correlation,null,null);
    }
    public int create(String session,String category,String correlation,Integer owner,String role) throws SQLException {
        if(!Set.of("STAFF_REQUEST","LOW_CONFIDENCE","EMERGENCY","POLICY_REQUEST","SECURITY_REPEAT","UNSUPPORTED_LANGUAGE","MONITOR").contains(category))throw new IllegalArgumentException("Unknown escalation category");
        try(var c=DatabaseConnection.getConnection();var q=c.prepareStatement("INSERT INTO ai_escalation_tickets(session_id,issue_summary,correlation_id,owner_id,owner_role) VALUES (?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
            q.setString(1,session);q.setString(2,category);q.setString(3,correlation);if(owner==null)q.setNull(4,Types.INTEGER);else q.setInt(4,owner);q.setString(5,role);q.executeUpdate();
            try(var r=q.getGeneratedKeys()){if(!r.next())throw new SQLException("Ticket insert returned no ID");return r.getInt(1);}
        }
    }
    public List<Map<String,Object>> open() throws SQLException {
        var result=new ArrayList<Map<String,Object>>();
        try(var c=DatabaseConnection.getConnection();var q=c.prepareStatement("SELECT id,issue_summary,status,created_at,correlation_id FROM ai_escalation_tickets WHERE status IN ('OPEN','ACKNOWLEDGED') ORDER BY CASE WHEN issue_summary='EMERGENCY' THEN 0 WHEN issue_summary='MONITOR' THEN 2 ELSE 1 END,created_at LIMIT 100");var r=q.executeQuery()){
            while(r.next())result.add(Map.of("ticket_id",r.getInt(1),"issue_summary",r.getString(2),"status",r.getString(3),"created_at",r.getString(4),"correlation_id",r.getString(5)));
        }return result;
    }
    public boolean acknowledge(int ticket,int actor) throws SQLException {
        if(ticket<1||actor<1)throw new IllegalArgumentException("Invalid ticket or actor");
        try(var c=DatabaseConnection.getConnection();var q=c.prepareStatement("UPDATE ai_escalation_tickets SET status='ACKNOWLEDGED',assigned_staff_id=?,acknowledged_at=CURRENT_TIMESTAMP WHERE id=? AND status='OPEN'")){
            q.setInt(1,actor);q.setInt(2,ticket);return q.executeUpdate()==1;
        }
    }
}
