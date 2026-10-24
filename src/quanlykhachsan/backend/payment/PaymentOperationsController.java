package quanlykhachsan.backend.payment;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import quanlykhachsan.backend.hotelservice.ServiceOrderController;
import quanlykhachsan.backend.utils.*;

/** Read-only operational audit. Staff see all; customers see only their own events. */
public final class PaymentOperationsController implements HttpHandler {
    public void handle(HttpExchange e)throws IOException {
        if(!e.getRequestMethod().equals("GET")){reply(e,405,"Method not allowed");return;}
        try(var c=DatabaseConnection.getConnection()){
            String role=String.valueOf(e.getAttribute("role")).toLowerCase(Locale.ROOT);boolean staff=Set.of("admin","front_desk","staff").contains(role);
            Integer customer=staff?null:ServiceOrderController.resolveCustomerId(e);if(!staff&&(customer==null||customer<1)){reply(e,403,"Access denied");return;}
            Map<String,String> params=new HashMap<>();String query=e.getRequestURI().getRawQuery();if(query!=null)for(String part:query.split("&")){String[] p=part.split("=",2);if(p.length==2)params.put(p[0],java.net.URLDecoder.decode(p[1],java.nio.charset.StandardCharsets.UTF_8));}
            int limit=Integer.parseInt(params.getOrDefault("limit","50")),offset=Integer.parseInt(params.getOrDefault("offset","0"));if(limit<1||limit>100||offset<0)throw new IllegalArgumentException();
            String path=e.getRequestURI().getPath();JsonElement data;
            if(path.equals("/api/v1/payment-operations/transactions")){
                Long folio=params.containsKey("folio_id")?Long.valueOf(params.get("folio_id")):null;JsonObject page=new JsonObject();page.add("items",PaymentStatus.history(c,folio,customer,limit,offset));page.addProperty("total",PaymentStatus.historyCount(c,folio,customer));data=page;
            }else if(path.equals("/api/v1/payment-operations/notifications")){
                JsonObject page=new JsonObject();try(var q=c.prepareStatement("SELECT id,event_key,booking_id,event_type,state,attempts,last_error,next_attempt_at,sent_at,created_at FROM notification_outbox WHERE (? IS NULL OR customer_id=?) ORDER BY id DESC LIMIT ? OFFSET ?")){q.setObject(1,customer);q.setObject(2,customer);q.setInt(3,limit);q.setInt(4,offset);try(var r=q.executeQuery()){page.add("items",PaymentStatus.rows(r));}}
                try(var q=c.prepareStatement("SELECT COUNT(*) FROM notification_outbox WHERE (? IS NULL OR customer_id=?)")){q.setObject(1,customer);q.setObject(2,customer);try(var r=q.executeQuery()){page.addProperty("total",r.next()?r.getLong(1):0);}}data=page;
            }else if(path.equals("/api/v1/payment-operations/attempts")){
                long id=Long.parseLong(params.getOrDefault("id","0"));if(id<1)throw new IllegalArgumentException();
                try(var q=c.prepareStatement("SELECT a.attempt_number,a.state,a.error_code,a.started_at,a.finished_at FROM notification_delivery_attempts a JOIN notification_outbox o ON o.id=a.outbox_id WHERE o.id=? AND (? IS NULL OR o.customer_id=?) ORDER BY a.attempt_number")){q.setLong(1,id);q.setObject(2,customer);q.setObject(3,customer);try(var r=q.executeQuery()){data=PaymentStatus.rows(r);}}
            }else if(path.equals("/api/v1/payment-operations/configuration")&&staff){
                JsonObject j=new JsonObject();boolean ready;try{PayOsConfig.environment().requireLink();ready=true;}catch(Exception ex){ready=false;}j.addProperty("qr_provider","payos");j.addProperty("qr_configured",ready);j.addProperty("email_enabled",Boolean.parseBoolean(System.getenv().getOrDefault("NOTIFICATION_EMAIL_ENABLED","false")));data=j;
            }else{reply(e,404,"Audit route not found");return;}
            ApiResponseUtil.write(e,200,ApiResponseUtil.successWithData(data));
        }catch(IllegalArgumentException ex){reply(e,400,"Invalid pagination or identifier");}
        catch(Exception ex){reply(e,503,"Audit history temporarily unavailable");}
    }
    private static void reply(HttpExchange e,int status,String msg)throws IOException{ApiResponseUtil.write(e,status,ApiResponseUtil.error(msg));}
}
