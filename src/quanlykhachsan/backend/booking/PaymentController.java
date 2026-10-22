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
            try(var c=DatabaseConnection.getConnection()){
                long folioId;
                try(var q=c.prepareStatement("SELECT id FROM folios WHERE booking_id=? ORDER BY (folio_type='master') DESC,id LIMIT 1")){
                    q.setInt(1,booking);try(var r=q.executeQuery()){
                        if(r.next()) folioId=r.getLong(1);
                        else {
                            // Old demo walk-in bookings predate folio creation. Bootstrap a demo-only folio
                            // from the reservation total so the mock collection follows the same ledger path.
                            if(!Boolean.parseBoolean(System.getenv("HOTEL_DEMO_MOCK_PAYMENTS"))){ApiResponseUtil.sendError(e,404,"ERR_404_NOT_FOUND","Folio not found");return;}
                            try(var create=c.prepareStatement("INSERT INTO folios(booking_id,folio_type,balance,status,code) SELECT id,'master',total_price,'open',CONCAT('DEMO-MOCK-B',id) FROM bookings WHERE id=? AND status='checked_in'",Statement.RETURN_GENERATED_KEYS)){
                                create.setInt(1,booking);
                                if(create.executeUpdate()!=1){ApiResponseUtil.sendError(e,404,"ERR_404_NOT_FOUND","Folio not found");return;}
                                try(var generated=create.getGeneratedKeys()){if(!generated.next())throw new SQLException("Mock folio ID missing");folioId=generated.getLong(1);}
                            }
                        }
                    }
                }
                BigDecimal outstanding;
                try(var q=c.prepareStatement("SELECT COALESCE(SUM(balance),0.00) FROM folios WHERE booking_id=?")){
                    q.setInt(1,booking);try(var r=q.executeQuery()){r.next();outstanding=r.getBigDecimal(1);}
                }
                // The legacy checkout screen submits the reservation total on every visit. If a prior
                // attempt already settled the folio, treat this as checkout-only so we never collect twice.
                BigDecimal amountToCollect=outstanding.signum()==0?BigDecimal.ZERO:amount;
                String hash=quanlykhachsan.backend.ai.AiInteractionDao.hash("legacy-payment|"+booking+"|"+amountToCollect+"|"+method+"|checkout");
                var result=new FolioService().pay(c,new FolioDao.PaymentRequest(folioId,key,hash,amountToCollect,method,true,ZoneId.of("Asia/Ho_Chi_Minh"),Integer.parseInt(String.valueOf(e.getAttribute("userId")))));
                ApiResponseUtil.write(e,result.statusCode(),result.body());
            }
        }catch(IllegalArgumentException|NullPointerException ex){ApiResponseUtil.sendError(e,400,"ERR_400_BAD_REQUEST","Valid cash/POS payment and UUIDv4 Idempotency-Key required");}
        catch(IllegalStateException ex){ApiResponseUtil.sendError(e,409,"ERR_409_CONFLICT",ex.getMessage());}
        catch(SQLException ex){ApiResponseUtil.sendError(e,503,"ERR_503_UNAVAILABLE","Payment was not confirmed; retry the same Idempotency-Key");}
    }
}
