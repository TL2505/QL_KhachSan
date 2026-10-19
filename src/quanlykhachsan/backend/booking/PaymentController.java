package quanlykhachsan.backend.booking;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.time.ZoneId;
import quanlykhachsan.backend.utils.*;

/** Compatibility route; the folio service owns all accounting and replay protection. */
public final class PaymentController implements HttpHandler {
    public void handle(HttpExchange e)throws IOException {
        if(!SecurityUtil.hasPermission(e,1,2))return;
        if(!e.getRequestMethod().equals("POST")){ApiResponseUtil.sendError(e,405,"ERR_405_METHOD_NOT_ALLOWED","Method not allowed");return;}
        try {
            var body=PortalValidation.body(e);
            int booking=body.get("bookingId").getAsInt();
            BigDecimal amount=body.get("amount").getAsBigDecimal();
            String method=body.get("paymentMethod").getAsString();
            String key=e.getRequestHeaders().getFirst("Idempotency-Key");
            if(key==null&&body.has("idempotencyKey"))key=body.get("idempotencyKey").getAsString();
            if(key==null||!key.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))throw new IllegalArgumentException();
            try(var c=DatabaseConnection.getConnection();var q=c.prepareStatement("SELECT id FROM folios WHERE booking_id=? ORDER BY (folio_type='master') DESC,id LIMIT 1")){
                q.setInt(1,booking);try(var r=q.executeQuery()){
                    if(!r.next()){ApiResponseUtil.sendError(e,404,"ERR_404_NOT_FOUND","Folio not found");return;}
                    String hash=quanlykhachsan.backend.ai.AiInteractionDao.hash("legacy-payment|"+booking+"|"+amount+"|"+method);
                    var result=new FolioService().pay(c,new FolioDao.PaymentRequest(r.getLong(1),key,hash,amount,method,false,ZoneId.of("Asia/Ho_Chi_Minh"),Integer.parseInt(String.valueOf(e.getAttribute("userId")))));
                    ApiResponseUtil.write(e,result.statusCode(),result.body());
                }
            }
        }catch(IllegalArgumentException|NullPointerException ex){ApiResponseUtil.sendError(e,400,"ERR_400_BAD_REQUEST","Valid cash/POS payment and UUIDv4 Idempotency-Key required");}
        catch(IllegalStateException ex){ApiResponseUtil.sendError(e,409,"ERR_409_CONFLICT",ex.getMessage());}
        catch(SQLException ex){ApiResponseUtil.sendError(e,503,"ERR_503_UNAVAILABLE","Payment was not confirmed; retry the same Idempotency-Key");}
    }
}
