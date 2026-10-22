package quanlykhachsan.backend.payment;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.*;
import quanlykhachsan.backend.utils.ApiResponseUtil;

public final class PayOsWebhookController implements HttpHandler {
    @FunctionalInterface public interface Settlement {String settle(PaymentReconciliationService.VerifiedPayment p)throws Exception;}
    private final PayOsConfig config;private final Settlement settlement;
    public PayOsWebhookController(){this(PayOsConfig.environment(),new PaymentReconciliationService()::settle);}
    public PayOsWebhookController(PayOsConfig c,Settlement s){config=c;settlement=s;}
    public void handle(HttpExchange e)throws IOException {
        if(!e.getRequestURI().getPath().equals("/api/v1/webhooks/payos")){write(e,404,"Unknown webhook");return;}
        if(!e.getRequestMethod().equals("POST")){write(e,405,"Method not allowed");return;}
        try{
            config.requireWebhook();byte[] bytes=e.getRequestBody().readNBytes(16385);if(bytes.length>16384){write(e,413,"Payload too large");return;}
            JsonObject body=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();JsonObject d=body.getAsJsonObject("data");
            if(d==null||!PayOsSignature.verify(d,PayOsGateway.text(body,"signature"),config.checksumKey())){write(e,401,"Invalid webhook signature");return;}
            LocalDateTime at=LocalDateTime.parse(d.get("transactionDateTime").getAsString(),DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT));
            var p=new PaymentReconciliationService.VerifiedPayment("payos",d.get("reference").getAsString(),d.get("orderCode").getAsBigDecimal().longValueExact(),d.get("amount").getAsBigDecimal(),PayOsGateway.text(d,"currency"),PayOsGateway.text(d,"accountNumber"),PayOsGateway.text(d,"description"),PayOsGateway.text(d,"paymentLinkId"),at,PayOsGateway.text(d,"code"));
            ApiResponseUtil.write(e,200,settlement.settle(p));
        }catch(IllegalStateException ex){write(e,503,"payOS chưa được cấu hình đầy đủ hoặc đang tạm ngưng");}
        catch(IllegalArgumentException|NullPointerException|JsonParseException|java.time.DateTimeException|ArithmeticException ex){write(e,400,"Invalid webhook payload");}
        catch(Exception ex){write(e,503,"Payment reconciliation temporarily unavailable; retry webhook");}
    }
    private static void write(HttpExchange e,int status,String msg)throws IOException {ApiResponseUtil.write(e,status,ApiResponseUtil.error(msg));}
}
