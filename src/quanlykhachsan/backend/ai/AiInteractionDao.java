package quanlykhachsan.backend.ai;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Audit stores content fingerprints and decision categories, never raw user or model payloads. */
public final class AiInteractionDao implements AutoCloseable {
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(256),r->{var t=new Thread(r,"ai-audit-writer");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final AtomicLong failures=new AtomicLong();
    public record Event(String session,String sender,String message,String decision,double confidence,String correlation){}
    public CompletableFuture<Void> record(Event event){
        // Hash synchronously before enqueuing; unnecessary PII cannot linger in the audit queue.
        String fingerprint="sha256:"+hash(event.message());
        if(!Double.isFinite(event.confidence())||!event.session().matches("[a-f0-9]{64}")||!Set.of("ANSWER","ESCALATED","BLOCKED","UNAVAILABLE","MONITOR").contains(event.decision()))throw new IllegalArgumentException("Invalid audit metadata");
        Event safe=new Event(event.session(),event.sender(),"",event.decision(),event.confidence(),event.correlation());
        var result=new CompletableFuture<Void>();
        try{executor.execute(()->{
            try(var c=DatabaseConnection.getConnection();var q=c.prepareStatement("INSERT INTO ai_chat_logs(session_id,sender,prompt,response,confidence_score,correlation_id) VALUES (?,?,?,?,?,?)")){
                q.setString(1,safe.session());q.setString(2,safe.sender());q.setString(3,fingerprint);q.setString(4,safe.decision());q.setBigDecimal(5,java.math.BigDecimal.valueOf(Math.max(0,Math.min(1,safe.confidence()))).setScale(2,java.math.RoundingMode.HALF_UP));q.setString(6,safe.correlation());q.executeUpdate();result.complete(null);
            }catch(SQLException e){failures.incrementAndGet();org.slf4j.LoggerFactory.getLogger(AiInteractionDao.class).error("AI audit persistence failed; correlation={}",safe.correlation());result.completeExceptionally(new IllegalStateException("AI audit persistence failed"));}
        });}catch(RejectedExecutionException e){failures.incrementAndGet();result.completeExceptionally(e);}
        return result;
    }
    public long failures(){return failures.get();}
    public static String hash(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 unavailable",e);}
    }
    @Override public void close(){executor.shutdown();try{if(!executor.awaitTermination(3,TimeUnit.SECONDS))executor.shutdownNow();}catch(InterruptedException e){Thread.currentThread().interrupt();executor.shutdownNow();}}
}
