package quanlykhachsan.backend.ai;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.sql.SQLException;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import quanlykhachsan.backend.utils.*;

public final class VirtualConciergeController implements HttpHandler,AutoCloseable {
    @FunctionalInterface interface TicketCreator { int create(String session,String category,String correlation,Integer owner,String role) throws SQLException; }
    private final TicketCreator ticketCreator;
    private final java.util.function.Consumer<AiInteractionDao.Event> eventSink;
    public VirtualConciergeController(){this(new EscalationTicketDao()::create);}
    VirtualConciergeController(TicketCreator ticketCreator){this(ticketCreator,null);}
    VirtualConciergeController(TicketCreator ticketCreator,java.util.function.Consumer<AiInteractionDao.Event> eventSink){this(ticketCreator,eventSink,AiProviderSettings.instance(),new CloudAiClient());}
    VirtualConciergeController(TicketCreator ticketCreator,java.util.function.Consumer<AiInteractionDao.Event> eventSink,AiProviderSettings cloudSettings,CloudAiClient cloudClient){this.ticketCreator=Objects.requireNonNull(ticketCreator);this.eventSink=eventSink;this.cloudSettings=Objects.requireNonNull(cloudSettings);this.cloudClient=Objects.requireNonNull(cloudClient);}
    private final AiInteractionDao audit=new AiInteractionDao();
    private final EscalationTicketDao tickets=new EscalationTicketDao();
    private final EscalationPolicy policy=new EscalationPolicy();
    private final SessionRateLimiter limiter=new SessionRateLimiter(Clock.systemUTC());
    private final AtomicBoolean guestEnabled=new AtomicBoolean("true".equalsIgnoreCase(System.getenv("AI_GUEST_ENABLED")));
    private volatile AiServiceClient client;
    private final CloudAiClient cloudClient;
    private final AiProviderSettings cloudSettings;
    private final ConciergeConversationMemory conversations=new ConciergeConversationMemory();
    private record Strike(int count,long updated){}
    private final Map<String,Strike> strikes=new HashMap<>();
    public boolean checkChatBudget(HttpExchange e) throws IOException {
        if(!Set.of("admin","manager","gm","staff","front_desk","guest","customer").contains(String.valueOf(e.getAttribute("role")).toLowerCase(Locale.ROOT))){error(e,403,"ERR_403_FORBIDDEN","Concierge access denied");return false;}
        String token=e.getRequestHeaders().getFirst("Authorization");
        if(token==null||e.getAttribute("userId")==null){error(e,401,"ERR_401_UNAUTHORIZED","Authenticated concierge identity required");return false;}
        if(!limiter.acquire(AiInteractionDao.hash(e.getAttribute("role")+"|"+e.getAttribute("userId")))){e.getResponseHeaders().set("Retry-After","60");error(e,429,"ERR_429_RATE_LIMITED","Concierge request limit exceeded");return false;}
        e.setAttribute("concierge_rate_checked",Boolean.TRUE);return true;
    }
    public synchronized void recordRejectedPrompt(HttpExchange e,JsonObject body) throws IOException {
        String supplied=body.has("session_id")&&body.get("session_id").isJsonPrimitive()?body.get("session_id").getAsString():"invalid-session";
        if(!supplied.matches("[A-Za-z0-9_-]{1,64}"))supplied="invalid-session";
        String bound=AiInteractionDao.hash(e.getAttribute("role")+"|"+e.getAttribute("userId")+"|"+supplied),correlation=UUID.randomUUID().toString();
        record(bound,false,body.get("message").getAsString(),"BLOCKED",0,correlation);
        String owner=AiInteractionDao.hash(e.getAttribute("role")+"|"+e.getAttribute("userId"));
        long now=System.currentTimeMillis();strikes.entrySet().removeIf(entry->now-entry.getValue().updated()>3_600_000);
        if(!strikes.containsKey(owner)&&strikes.size()>=10_000)return;
        Strike old=strikes.get(owner);int count=old==null?1:old.count()+1;strikes.put(owner,new Strike(count,now));
        if(count==2)try{ticket(e,bound,"SECURITY_REPEAT",correlation);}
        catch(SQLException ex){org.slf4j.LoggerFactory.getLogger(VirtualConciergeController.class).error("Security escalation persistence failed; correlation={}",correlation);}
    }
    private static final Pattern SENSITIVE=Pattern.compile("(?i)\\d{12,}|password|mat khau|api[_ -]?key|system prompt|ignore previous");
    @Override public void handle(HttpExchange e) throws IOException {
        String path=e.getRequestURI().getPath(),role=String.valueOf(e.getAttribute("role")).toLowerCase(Locale.ROOT);
        boolean staff=Set.of("admin","manager","gm","staff","front_desk").contains(role),management=Set.of("admin","manager","gm","it","devops").contains(role);
        String correlation=UUID.randomUUID().toString();
        try{
            if(path.matches("/api/v1/ai/concierge/tickets/[0-9]+/messages")){
                int ticket=Integer.parseInt(path.split("/")[6]),actor=Integer.parseInt(String.valueOf(e.getAttribute("userId")));
                var chat=new TicketChatDao();
                if(e.getRequestMethod().equals("GET")){
                    long after=Long.parseLong(PortalValidation.query(e).getOrDefault("after","0"));if(after<0)throw new IllegalArgumentException();
                    ApiResponseUtil.sendSuccess(e,chat.read(ticket,actor,role,after));return;
                }
                if(e.getRequestMethod().equals("POST")){
                    if(!checkChatBudget(e))return;
                    long message=chat.append(ticket,actor,role,field(body(e),"message",2000));ApiResponseUtil.sendSuccess(e,201,Map.of("id",message));return;
                }
                error(e,405,"ERR_405_METHOD_NOT_ALLOWED","Method not allowed");return;
            }
            if(path.equals("/api/v1/ai/concierge/state")){
                if(!management){error(e,403,"ERR_403_FORBIDDEN","AI operations access required");return;}
                if(e.getRequestMethod().equals("PUT")){
                    JsonObject body=body(e);if(!body.has("guest_enabled")||!body.getAsJsonPrimitive("guest_enabled").isBoolean())throw new IllegalArgumentException();
                    guestEnabled.set(body.get("guest_enabled").getAsBoolean());
                }else if(!e.getRequestMethod().equals("GET")){error(e,405,"ERR_405_METHOD_NOT_ALLOWED","Method not allowed");return;}
                ApiResponseUtil.sendSuccess(e,Map.of("guest_enabled",guestEnabled.get(),"audit_failures",audit.failures()));return;
            }
            if(path.equals("/api/v1/ai/concierge/tickets")&&e.getRequestMethod().equals("GET")){
                if(!staff){error(e,403,"ERR_403_FORBIDDEN","Front Desk access required");return;}
                ApiResponseUtil.sendSuccess(e,tickets.open());return;
            }
            if(path.matches("/api/v1/ai/concierge/tickets/[0-9]+/ack")&&e.getRequestMethod().equals("POST")){
                if(!staff){error(e,403,"ERR_403_FORBIDDEN","Front Desk access required");return;}
                int id=Integer.parseInt(path.split("/")[6]);
                if(!tickets.acknowledge(id,Integer.parseInt(String.valueOf(e.getAttribute("userId"))))){error(e,409,"ERR_409_CONFLICT","Ticket already acknowledged or unavailable");return;}
                ApiResponseUtil.sendSuccess(e,Map.of("ticket_id",id,"status","ACKNOWLEDGED"));return;
            }
            if(path.equals("/api/v1/ai/concierge/tool-log")){
                if(!e.getRequestMethod().equals("POST")){error(e,405,"ERR_405_METHOD_NOT_ALLOWED","Method not allowed");return;}
                if(!staff&&!Set.of("guest","customer").contains(role)){error(e,403,"ERR_403_FORBIDDEN","Concierge access denied");return;}
                if(!checkChatBudget(e))return;
                JsonObject toolEvent=body(e);
                if(toolEvent.size()!=2)throw new IllegalArgumentException();
                String session=field(toolEvent,"session_id",64),outcome=field(toolEvent,"outcome",20);
                if(!session.matches("[A-Za-z0-9_-]{1,64}")||!Set.of("success","no_results","unavailable").contains(outcome))throw new IllegalArgumentException();
                String bound=AiInteractionDao.hash(role+"|"+e.getAttribute("userId")+"|"+session);
                String decision=switch(outcome){case "success"->"TOOL_SEARCH";case "no_results"->"TOOL_NO_RESULTS";default->"TOOL_UNAVAILABLE";};
                try { recordAsync(bound,staff,"room_search:"+outcome,decision,0,correlation).get(3,java.util.concurrent.TimeUnit.SECONDS); }
                catch(InterruptedException ex){Thread.currentThread().interrupt();error(e,503,"ERR_503_AI_AUDIT_UNAVAILABLE","Không thể ghi nhận lần tra cứu. Vui lòng thử lại.");return;}
                catch(java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ex){error(e,503,"ERR_503_AI_AUDIT_UNAVAILABLE","Không thể ghi nhận lần tra cứu. Vui lòng thử lại.");return;}
                ApiResponseUtil.sendSuccess(e,200,Map.of("logged",true),Map.of("correlation_id",correlation));return;
            }
            if(!Set.of("/api/v1/ai/concierge/chat","/api/v1/ai/concierge/escalate").contains(path)){error(e,404,"ERR_404_NOT_FOUND","Route not found");return;}
            if(!e.getRequestMethod().equals("POST")){error(e,405,"ERR_405_METHOD_NOT_ALLOWED","Method not allowed");return;}
            if(!staff&&!Set.of("guest","customer").contains(role)){error(e,403,"ERR_403_FORBIDDEN","Concierge access denied");return;}
            if(!Boolean.TRUE.equals(e.getAttribute("concierge_rate_checked"))&&!checkChatBudget(e))return;
            JsonObject body=body(e);String session=field(body,"session_id",64);
            if(!session.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException();
            // Same supplied session ID under another authenticated identity gets a different database key.
            String bound=AiInteractionDao.hash(role+"|"+e.getAttribute("userId")+"|"+session);
            if(path.endsWith("/escalate")){
                conversations.forget(bound);
                String reason=field(body,"reason",2000);int id=ticket(e,bound,"STAFF_REQUEST",correlation);
                new TicketChatDao().append(id,Integer.parseInt(String.valueOf(e.getAttribute("userId"))),role,reason);
                record(bound,staff,reason,"ESCALATED",0,correlation);var response=new JsonObject();response.addProperty("ticket_id",id);response.addProperty("status","OPEN");response.add("room_number",JsonNull.INSTANCE);response.add("eta_minutes",JsonNull.INSTANCE);response.addProperty("message","Front Desk assistance requested");ApiResponseUtil.sendSuccess(e,response);return;
            }
            String message=field(body,"message",4000);
            // Defense in depth for alternate callers. The production chat context also has the pre-model filter.
            if(PromptGuardrailFilter.isBlocked(message)){record(bound,staff,message,"BLOCKED",0,correlation);error(e,400,"PROMPT_INJECTION_DETECTED","Yêu cầu không hợp lệ do vi phạm chính sách bảo mật.");return;}
            AiProviderSettings.Configuration cloud;
            try { cloud=cloudSettings.read(); }
            catch (GeneralSecurityException | IllegalStateException | IOException ex) {
                error(e,503,"AI_SETTINGS_UNAVAILABLE","Trợ lý AI tạm thời không khả dụng. Vui lòng liên hệ lễ tân.");return;
            }
            // Reserve before policy evaluation so concurrent duplicate sends cannot become complaints.
            try(var conversation=cloud.enabled()&&cloud.provider().equals("gemini")?conversations.open(bound,cloud):null) {
            var decision=policy.evaluate(bound,message,System.currentTimeMillis());
            if(decision.hard()||(!staff&&!guestEnabled.get()&&!cloud.enabled())||audit.failures()>0){
                conversations.forget(bound);
                escalate(e,bound,message,decision.hard()?decision.category():"STAFF_REQUEST",decision.language(),staff,correlation);return;
            }
            if(cloud.enabled()) {
                try {
                    String reply;
                    if(conversation!=null) {
                        var generated=cloudClient.generate(cloud,message,false,conversation.previous());
                        reply=generated.text();
                        conversation.commit(message,reply,generated.modelContent());
                    } else {
                        conversations.forget(bound);
                        reply=cloudClient.answer(cloud,message,false);
                    }
                    record(bound,staff,message,"ANSWER",0,correlation);
                    ApiResponseUtil.sendSuccess(e,Map.of("reply",reply,"provider",cloud.provider(),"generated",true,"citations",List.of(),"escalate",false));
                } catch(CloudAiClient.GenerationException ex) {
                    policy.forget(bound);
                    error(e,422,ex.code,ex.publicMessage());
                } catch(CloudAiClient.ProviderException ex) {
                    policy.forget(bound);
                    error(e,422,"AI_PROVIDER_REJECTED",management?ex.publicMessage():"Trợ lý AI chưa sẵn sàng do lỗi kết nối với nhà cung cấp. Vui lòng liên hệ lễ tân.");
                } catch(IOException ex) {
                    policy.forget(bound);
                    error(e,503,"AI_PROVIDER_UNAVAILABLE","Trợ lý AI tạm thời không khả dụng. Vui lòng thử lại hoặc liên hệ lễ tân.");
                }
                return;
            }
            conversations.forget(bound);
            JsonObject data;
            try{
                JsonObject request=new JsonObject();request.addProperty("message",message);request.addProperty("audience",staff?"staff":"guest");request.addProperty("language",decision.language());
                JsonObject result=JsonParser.parseString(rag().post("/rag/query",request.toString())).getAsJsonObject();
                double confidence=result.get("confidence").getAsDouble();
                if(!Double.isFinite(confidence)||confidence<0.8||confidence>1||result.get("escalate").getAsBoolean()){
                    throw new IllegalArgumentException("Unverified answer");
                }
                JsonArray citations=result.getAsJsonArray("citations");if(citations==null||citations.isEmpty())throw new IllegalArgumentException();
                var answer=new StringBuilder();var references=new JsonArray();
                for(JsonElement citation:citations){
                    var item=citation.getAsJsonObject();String text=field(item,"text",6000),source=field(item,"source",255);
                    if(SENSITIVE.matcher(EscalationPolicy.fold(text)).find())throw new IllegalArgumentException();
                    // Renderers display plain text to prevent source content from creating HTML/script.
                    if(!answer.isEmpty())answer.append("\n\n");answer.append(text);
                    var reference=new JsonObject();reference.addProperty("source",source);reference.addProperty("doc",source);reference.add("section",item.has("section")?item.get("section"):JsonNull.INSTANCE);references.add(reference);
                }
                Integer ticket=null;if(decision.category().equals("MONITOR"))ticket=ticket(e,bound,"MONITOR",correlation);
                record(bound,staff,message,decision.category().equals("MONITOR")?"MONITOR":"ANSWER",confidence,correlation);
                data=new JsonObject();data.addProperty("answer",answer.toString());data.addProperty("reply",answer.toString());data.addProperty("confidence_score",confidence);data.add("suggested_actions",new JsonArray());data.add("citations",references);data.addProperty("confidence",confidence);data.addProperty("escalate",false);data.addProperty("monitor",ticket!=null);if(ticket!=null)data.addProperty("ticket_id",ticket);
            }catch(IOException | IllegalStateException | IllegalArgumentException | JsonParseException | NullPointerException ex){
                escalate(e,bound,message,"LOW_CONFIDENCE",decision.language(),staff,correlation);return;
            }
            ApiResponseUtil.sendSuccess(e,200,data,Map.of("correlation_id",correlation));
            } catch(ConciergeConversationMemory.Busy ex) {
                e.getResponseHeaders().set("Retry-After","2");
                error(e,409,"AI_CONVERSATION_BUSY","Câu hỏi trước đang được xử lý. Vui lòng đợi câu trả lời rồi gửi tiếp.");
            }
        }catch(BodyTooLarge ex){error(e,413,"ERR_413_PAYLOAD_TOO_LARGE","Request too large");}
        catch(IllegalArgumentException | JsonParseException | IllegalStateException | NullPointerException ex){error(e,400,"ERR_400_BAD_REQUEST","Invalid concierge request");}
        catch(SQLException ex){error(e,503,"ERR_503_AI_UNAVAILABLE","Staff assistance could not be queued; contact the Front Desk directly");}
        catch(SecurityException ex){error(e,403,"ERR_403_FORBIDDEN","Ticket access denied");}
    }
    private AiServiceClient rag() throws IOException {
        if(client==null)synchronized(this){if(client==null)try{client=AiServiceClient.fromEnvironment();}
            catch(GeneralSecurityException | IllegalArgumentException | IllegalStateException ex){throw new IOException("RAG unavailable");}}
        return client;
    }
    private void escalate(HttpExchange e,String session,String message,String category,String language,boolean staff,String correlation) throws SQLException,IOException {
        int ticket=ticket(e,session,category,correlation);
        if(!message.isBlank())new TicketChatDao().append(ticket,Integer.parseInt(String.valueOf(e.getAttribute("userId"))),String.valueOf(e.getAttribute("role")).toLowerCase(Locale.ROOT),message.substring(0,Math.min(message.length(),2000)));
        record(session,staff,message,"ESCALATED",0,correlation);
        ApiResponseUtil.sendSuccess(e,200,Map.of("answer",EscalationPolicy.fallback(language),"reply",EscalationPolicy.fallback(language),"confidence_score",0,"citations",List.of(),"suggested_actions",List.of(),"escalate",true,"ticket_id",ticket),Map.of("correlation_id",correlation));
    }
    private void record(String session,boolean staff,String message,String action,double confidence,String correlation){recordAsync(session,staff,message,action,confidence,correlation);}
    private java.util.concurrent.CompletableFuture<Void> recordAsync(String session,boolean staff,String message,String action,double confidence,String correlation){var event=new AiInteractionDao.Event(session,staff?"STAFF":"GUEST",message,action,confidence,correlation);if(eventSink==null)return audit.record(event);eventSink.accept(event);return java.util.concurrent.CompletableFuture.completedFuture(null);}
    private int ticket(HttpExchange e,String session,String category,String correlation)throws SQLException{return ticketCreator.create(session,category,correlation,Integer.parseInt(String.valueOf(e.getAttribute("userId"))),String.valueOf(e.getAttribute("role")).toLowerCase(Locale.ROOT));}
    private static JsonObject body(HttpExchange e) throws IOException {
        byte[] bytes=e.getRequestBody().readNBytes(16_385);if(bytes.length>16_384)throw new BodyTooLarge();
        return JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static String field(JsonObject body,String name,int max){
        if(!body.has(name)||!body.get(name).isJsonPrimitive()||!body.getAsJsonPrimitive(name).isString())throw new IllegalArgumentException();
        String text=body.get(name).getAsString();if(text.isBlank()||text.length()>max)throw new IllegalArgumentException();return text;
    }
    private static void error(HttpExchange e,int status,String code,String message) throws IOException {ApiResponseUtil.sendError(e,status,code,message);}
    private static final class BodyTooLarge extends IOException{}
    @Override public void close(){conversations.clear();audit.close();}
}
