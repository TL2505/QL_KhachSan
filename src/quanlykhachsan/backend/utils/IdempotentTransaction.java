package quanlykhachsan.backend.utils;

import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.sun.net.httpserver.HttpExchange;
import quanlykhachsan.backend.booking.FolioDao;

/** Atomic durable replay for operational writes, sharing the existing idempotency table. */
public final class IdempotentTransaction {
    private IdempotentTransaction(){ }
    public static String key(HttpExchange e) {
        String key=e.getRequestHeaders().getFirst("Idempotency-Key");
        if(key==null || !key.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))throw new IllegalArgumentException("A UUIDv4 Idempotency-Key is required");
        return key.toLowerCase(java.util.Locale.ROOT);
    }
    public static Result execute(HttpExchange e,String payload,Action action)throws Exception {
        String key=key(e);String scope=e.getRequestMethod()+"|"+e.getRequestURI().getPath()+"|"+e.getAttribute("user_id")+"|"+e.getAttribute("role")+"|"+payload;
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8)));
        var locks=new FolioDao();
        try(Connection c=DatabaseConnection.getConnection()) {
            locks.acquireLock(c,key);
            try {
                c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                c.setAutoCommit(false);
                try(PreparedStatement q=c.prepareStatement("SELECT request_hash,response_body,status_code FROM idempotency_keys WHERE id=?")) {
                    q.setString(1,key);try(ResultSet r=q.executeQuery()){if(r.next()){if(!hash.equals(r.getString(1)))throw new IllegalStateException("Idempotency-Key reused with different data");var replay=new Result(r.getInt(3),r.getString(2));c.rollback();return replay;}}
                }
                Result result=action.run(c);
                try(PreparedStatement q=c.prepareStatement("INSERT INTO idempotency_keys(id,request_hash,response_body,status_code) VALUES(?,?,?,?)")){q.setString(1,key);q.setString(2,hash);q.setString(3,result.body());q.setInt(4,result.status());q.executeUpdate();}
                c.commit();quanlykhachsan.backend.pricing.PricingQuoteCache.invalidate();return result;
            }catch(Exception ex){c.rollback();throw ex;}
            finally{try{c.setAutoCommit(true);}finally{locks.releaseLock(c,key);}}
        }
    }
    @FunctionalInterface public interface Action { Result run(Connection c)throws Exception; }
    public record Result(int status,String body) { }
}
