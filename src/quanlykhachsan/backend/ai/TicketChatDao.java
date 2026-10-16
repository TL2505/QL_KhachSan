package quanlykhachsan.backend.ai;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.*;
import java.util.*;
import quanlykhachsan.backend.utils.*;

/** Warm-transfer messages are encrypted at rest and never sent to a language model. */
public final class TicketChatDao {
    private static boolean staff(String role){return Set.of("admin","manager","gm","front_desk","staff").contains(role);}
    private void authorize(Connection c,int ticket,int actor,String role) throws SQLException {
        try(var q=c.prepareStatement("SELECT owner_id,owner_role FROM ai_escalation_tickets WHERE id=?")){
            q.setInt(1,ticket);try(var r=q.executeQuery()){
                if(!r.next())throw new SecurityException("Ticket access denied");
                if(!staff(role)&&(r.getInt(1)!=actor||!role.equals(r.getString(2))))throw new SecurityException("Ticket access denied");
            }
        }
    }
    private static byte[] aad(int ticket,String sender){return ("ticket-v1:"+ticket+":"+sender).getBytes(StandardCharsets.UTF_8);}
    public long append(int ticket,int actor,String role,String message) throws SQLException {
        if(message==null||message.isBlank()||message.length()>2000)throw new IllegalArgumentException("Invalid message");
        String sender=staff(role)?"STAFF":"GUEST";
        try(var c=DatabaseConnection.getConnection()){
            authorize(c,ticket,actor,role);
            byte[] ciphertext=AesGcmEncryptionUtil.fromEnvironment().encrypt(message.getBytes(StandardCharsets.UTF_8),aad(ticket,sender));
            try(var q=c.prepareStatement("INSERT INTO ai_ticket_messages(ticket_id,sender,encrypted_body) VALUES (?,?,?)",Statement.RETURN_GENERATED_KEYS)){
                q.setInt(1,ticket);q.setString(2,sender);q.setBytes(3,ciphertext);q.executeUpdate();try(var r=q.getGeneratedKeys()){if(!r.next())throw new SQLException("Message insert returned no ID");return r.getLong(1);}
            }
        }catch(GeneralSecurityException ex){throw new SQLException("Message encryption unavailable");}
    }
    public List<Map<String,Object>> read(int ticket,int actor,String role,long after) throws SQLException {
        var rows=new ArrayList<Map<String,Object>>();
        try(var c=DatabaseConnection.getConnection()){
            authorize(c,ticket,actor,role);var aes=AesGcmEncryptionUtil.fromEnvironment();
            try(var q=c.prepareStatement("SELECT id,sender,encrypted_body,created_at FROM ai_ticket_messages WHERE ticket_id=? AND id>? ORDER BY id LIMIT 100")){
                q.setInt(1,ticket);q.setLong(2,after);try(var r=q.executeQuery()){while(r.next()){
                    byte[] plaintext=aes.decrypt(r.getBytes(3),aad(ticket,r.getString(2)));
                    try{rows.add(Map.of("id",r.getLong(1),"sender",r.getString(2),"message",new String(plaintext,StandardCharsets.UTF_8),"created_at",r.getString(4)));}
                    finally{Arrays.fill(plaintext,(byte)0);}
                }}
            }return rows;
        }catch(GeneralSecurityException ex){throw new SQLException("Message authentication failed");}
    }
}
