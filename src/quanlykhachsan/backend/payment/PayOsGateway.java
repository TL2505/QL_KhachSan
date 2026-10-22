package quanlykhachsan.backend.payment;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public final class PayOsGateway implements PaymentGateway {
    private final PayOsConfig config;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public PayOsGateway(PayOsConfig config){this.config=config;}
    public String provider(){return "payos";}
    public Beneficiary beneficiary(){return new Beneficiary(config.account(),config.holder(),config.bin());}
    public void requireConfigured(){config.requireLink();}
    public JsonObject createLink(long orderCode,long amount,String reference,long expiresAt)throws Exception {
        config.requireLink();
        JsonObject signed=new JsonObject();signed.addProperty("amount",amount);signed.addProperty("cancelUrl",config.cancelUrl());signed.addProperty("description",reference);signed.addProperty("orderCode",orderCode);signed.addProperty("returnUrl",config.returnUrl());
        JsonObject body=signed.deepCopy();body.addProperty("expiredAt",expiresAt);body.addProperty("signature",PayOsSignature.sign(signed,config.checksumKey()));
        // Recover an uncertain POST using the SAME persisted orderCode, never a new charge.
        JsonObject response;
        try {
            response=call("POST","/v2/payment-requests",body);
        } catch(Exception postFailure) {
            try {
                response=call("GET","/v2/payment-requests/"+orderCode,null);
            } catch(Exception recoveryFailure) {
                recoveryFailure.addSuppressed(postFailure);
                throw recoveryFailure;
            }
        }
        if(!"00".equals(text(response,"code")))response=call("GET","/v2/payment-requests/"+orderCode,null);
        JsonObject data=response.getAsJsonObject("data");
        if(!"00".equals(text(response,"code"))||data==null||!PayOsSignature.verify(data,text(response,"signature"),config.checksumKey()))throw new IllegalStateException("PAYMENT_PROVIDER_RESPONSE_INVALID");
        if(data.get("orderCode").getAsLong()!=orderCode||data.get("amount").getAsLong()!=amount||!reference.equals(text(data,"description"))||!config.account().equals(text(data,"accountNumber"))||!config.bin().equals(text(data,"bin"))||!config.holder().equalsIgnoreCase(text(data,"accountName")))throw new IllegalStateException("PAYMENT_BENEFICIARY_MISMATCH");
        String url=text(data,"checkoutUrl");if(!url.startsWith("https://")||text(data,"paymentLinkId").isBlank()||text(data,"qrCode").isBlank())throw new IllegalStateException("PAYMENT_PROVIDER_LINK_INVALID");
        return data;
    }
    private JsonObject call(String method,String path,JsonObject data)throws Exception {
        var b=HttpRequest.newBuilder(URI.create("https://api-merchant.payos.vn"+path)).timeout(Duration.ofSeconds(12)).header("x-client-id",config.clientId()).header("x-api-key",config.apiKey()).header("Content-Type","application/json");
        var req=method.equals("GET")?b.GET().build():b.POST(HttpRequest.BodyPublishers.ofString(data.toString())).build();
        var response=http.send(req,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200||response.body().length()>65536)throw new IllegalStateException("PAYMENT_PROVIDER_UNAVAILABLE");
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    static String text(JsonObject j,String key){return j.has(key)&&!j.get(key).isJsonNull()?j.get(key).getAsString():"";}
}
