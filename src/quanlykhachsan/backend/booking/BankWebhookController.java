package quanlykhachsan.backend.booking;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.*;
import quanlykhachsan.backend.utils.*;

public final class BankWebhookController implements HttpHandler {
    @FunctionalInterface public interface Settlement { String settle(FolioPaymentService.Payment payment)throws Exception; }
    private final String secret;private final Settlement settlement;
    public BankWebhookController(){this(System.getenv("BANK_WEBHOOK_SECRET"),new FolioPaymentService()::settle);}
    public BankWebhookController(String secret,Settlement settlement){this.secret=secret;this.settlement=settlement;}
    @Override public void handle(HttpExchange e)throws IOException {
        if(!e.getRequestURI().getPath().equals("/api/v1/webhooks/vietqr")){ApiResponseUtil.write(e,404,ApiResponseUtil.error("Not found"));return;}
        if(!e.getRequestMethod().equals("POST")){ApiResponseUtil.write(e,405,ApiResponseUtil.error("Method not allowed"));return;}
        try {
            byte[] raw=e.getRequestBody().readNBytes(16385);
            if(raw.length>16384){ApiResponseUtil.write(e,413,ApiResponseUtil.error("Payload too large"));return;}
            String signature=e.getRequestHeaders().getFirst("X-Signature");
            if(secret==null || secret.getBytes(StandardCharsets.UTF_8).length<32){ApiResponseUtil.write(e,503,ApiResponseUtil.error("Webhook verification is not configured"));return;}
            if(!HmacSignatureUtil.verify(secret,raw,signature)){ApiResponseUtil.write(e,401,ApiResponseUtil.error("Invalid webhook signature"));return;}
            JsonObject body;
            try{body=JsonParser.parseString(new String(raw,StandardCharsets.UTF_8)).getAsJsonObject();}catch(RuntimeException ex){throw new IllegalArgumentException();}
            String id=PortalValidation.text(body,"bank_trans_id",100,true),reference=PortalValidation.text(body,"reference_code",64,true);
            String time=PortalValidation.text(body,"trans_time",40,true);
            try{OffsetDateTime.parse(time);}catch(DateTimeParseException ex){LocalDateTime.parse(time,DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT));}
            JsonElement amountValue=body.get("amount");
            if(amountValue==null || !amountValue.isJsonPrimitive() || !amountValue.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException();
            BigDecimal amount=amountValue.getAsBigDecimal();
            String response=settlement.settle(new FolioPaymentService.Payment(id,amount,reference,signature,new String(raw,StandardCharsets.UTF_8)));
            ApiResponseUtil.write(e,200,response);
        }catch(IllegalArgumentException | DateTimeException ex){ApiResponseUtil.write(e,400,ApiResponseUtil.error("Invalid webhook payload"));}
        catch(IllegalStateException ex){ApiResponseUtil.write(e,409,ApiResponseUtil.error("Payment cannot be applied to this reference"));}
        catch(Exception ex){ApiResponseUtil.write(e,503,ApiResponseUtil.error("Payment reconciliation is unavailable; retry the same bank transaction"));}
    }
}
