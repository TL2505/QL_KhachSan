package quanlykhachsan.backend.invoice;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import quanlykhachsan.backend.utils.JsonUtil;

/** Narrow MISA meInvoice boundary: only documented issue, read, download and cancel operations. */
public final class MisaMeInvoiceProvider {
    public enum Mode { POS, HSM_CODE, HSM_NO_CODE }
    public record Settings(String environment, String origin, String apiV3, Mode mode,
            String appId, String taxCode, String username, String password,
            String invoiceSeries, String templateId, String invoiceName,
            String sellerName, String sellerAddress, String sellerPhone, String sellerEmail) {
        public static Settings load() {
            String environment = env("MISA_EINVOICE_ENV", "test").toLowerCase(Locale.ROOT);
            if (!environment.equals("test") && !environment.equals("production")) throw new IllegalStateException("MISA_EINVOICE_ENV must be test or production");
            String origin = environment.equals("production") ? "https://api.meinvoice.vn" : "https://testapi.meinvoice.vn";
            String modeText = env("MISA_EINVOICE_MODE", "");
            if (modeText.isBlank()) throw new IllegalStateException("MISA_EINVOICE_MODE must be selected after the seller's registered invoice method is confirmed");
            Mode mode;
            try { mode = Mode.valueOf(modeText.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { throw new IllegalStateException("MISA_EINVOICE_MODE must be POS, HSM_CODE, or HSM_NO_CODE"); }
            return new Settings(environment, origin, origin + "/api/v3", mode,
                    required("MISA_EINVOICE_APP_ID"), required("MISA_EINVOICE_TAX_CODE"),
                    required("MISA_EINVOICE_USERNAME"), required("MISA_EINVOICE_PASSWORD"),
                    required("MISA_EINVOICE_SERIES"), required("MISA_EINVOICE_TEMPLATE_ID"),
                    required("MISA_EINVOICE_NAME"), required("HOTEL_LEGAL_NAME"),
                    required("HOTEL_LEGAL_ADDRESS"), env("HOTEL_PHONE", ""), env("HOTEL_EMAIL", ""));
        }
        private static String required(String key) { String v = System.getenv(key); if (v == null || v.isBlank()) throw new IllegalStateException(key + " is required"); return v.trim(); }
        private static String env(String key, String fallback) { String v = System.getenv(key); return v == null || v.isBlank() ? fallback : v.trim(); }
        public boolean usesTaxCodePath() { return mode != Mode.HSM_NO_CODE; }
    }

    public record Published(String transactionId, String templateNo, String series, String number,
            String invoiceDate, String errorCode) { }
    public record Status(String transactionId, int publishStatus, String taxCode, int sendTaxStatus,
            boolean deleted, String deletedAt, String deletedReason) { }
    public record Download(byte[] pdf) { }

    public static final class ProviderException extends Exception {
        private final String code;
        private final int httpStatus;
        public ProviderException(String code, String message, int httpStatus, Throwable cause) { super(message, cause); this.code=code; this.httpStatus=httpStatus; }
        public String code() { return code; }
        public int httpStatus() { return httpStatus; }
    }

    private record CachedToken(String value, Instant expiresAt) { }
    private final Settings settings;
    private final HttpClient http;
    private final AtomicReference<CachedToken> apiToken = new AtomicReference<>();
    private final AtomicReference<CachedToken> integrationToken = new AtomicReference<>();

    public MisaMeInvoiceProvider() { this(Settings.load()); }
    MisaMeInvoiceProvider(Settings settings) {
        this.settings=settings;
        this.http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public Settings settings() { return settings; }

    public Published publish(JsonObject invoiceData, String receiverName, String receiverEmail) throws ProviderException {
        return publish(invoiceData, receiverName, receiverEmail, null);
    }

    public Published publish(JsonObject invoiceData, String receiverName, String receiverEmail, JsonObject reference) throws ProviderException {
        JsonObject requestItem = new JsonObject();
        JsonObject providerData = invoiceData.deepCopy();
        if (reference != null) for (String key : reference.keySet()) providerData.add(key, reference.get(key).deepCopy());
        JsonArray request = new JsonArray();
        if (settings.mode() == Mode.POS) {
            // MISA's POS contract uses OrgInvoiceData and requires this marker.
            providerData.addProperty("IsInvoiceCalculatingMachine", true);
            requestItem.add("OrgInvoiceData", providerData);
            requestItem.addProperty("IsSendEmail", receiverEmail != null && !receiverEmail.isBlank());
            requestItem.addProperty("ReceiverName", receiverName == null ? "" : receiverName);
            requestItem.addProperty("ReceiverEmail", receiverEmail == null ? "" : receiverEmail);
            request.add(requestItem);
            return parsePublished(postApi("/code/itg/invoice-calculating/invoiceandpublish", request));
        }
        requestItem.addProperty("RefID", providerData.get("RefID").getAsString());
        requestItem.add("OriginalInvoiceData", providerData);
        requestItem.addProperty("IsSendEmail", receiverEmail != null && !receiverEmail.isBlank());
        requestItem.addProperty("ReceiverName", receiverName == null ? "" : receiverName);
        requestItem.addProperty("ReceiverEmail", receiverEmail == null ? "" : receiverEmail);
        requestItem.addProperty("IsInvoiceSummary", false);
        String path = (settings.usesTaxCodePath() ? "/code" : "") + "/itg/invoicepublishing/publishhsm";
        request.add(requestItem);
        return parsePublished(postApi(path, request));
    }

    public Status status(String transactionId) throws ProviderException {
        JsonArray body = new JsonArray(); body.add(transactionId);
        JsonObject root = postApi((settings.usesTaxCodePath()?"/code":"") + "/itg/invoicepublished/invoicestatus", body);
        JsonArray data = dataArray(root);
        if (data.size()==0) throw new ProviderException("STATUS_NOT_FOUND", "MISA did not return an invoice status", 200, null);
        JsonObject item=data.get(0).getAsJsonObject();
        return new Status(string(item,"TransactionID"), integer(item,"PublishStatus"), string(item,"InvoiceCode"),
                integer(item,"SendTaxStatus"), bool(item,"IsDelete"), string(item,"DeletedDate"), string(item,"DeletedReason"));
    }

    public Published findByReference(String refId, String invoiceDate) throws ProviderException {
        String day = invoiceDate == null || invoiceDate.length()<10 ? java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toString() : invoiceDate.substring(0,10);
        JsonObject body=new JsonObject();body.addProperty("StartDate",day);body.addProperty("ToDate",day);body.addProperty("skip",0);body.addProperty("take",100);
        String path=(settings.usesTaxCodePath()?"/code":"")+"/invoicepublished/getbydate";
        JsonObject root=postApi(path,body);
        for(JsonElement element:dataArray(root)) {
            JsonObject item=element.getAsJsonObject();
            if(refId.equals(string(item,"RefID"))) return new Published(string(item,"TransactionID"),string(item,"InvTemplateNo"),string(item,"InvSeries"),string(item,"InvNo"),string(item,"InvDate"),"");
        }
        return null;
    }

    public String viewUrl(String transactionId) throws ProviderException {
        JsonArray body=new JsonArray();body.add(transactionId);
        JsonObject root=postApi((settings.usesTaxCodePath()?"/code":"")+"/itg/invoicepublished/linkview",body);
        String link=primitiveData(root);
        try { URI uri=URI.create(link); String host=uri.getHost()==null?"":uri.getHost().toLowerCase(Locale.ROOT); if(!"https".equals(uri.getScheme()) || !(host.equals("meinvoice.vn") || host.endsWith(".meinvoice.vn"))) throw new IllegalArgumentException(); }
        catch(Exception ex){throw new ProviderException("INVALID_PROVIDER_LINK","MISA returned an invalid invoice view URL",200,ex);}
        return link;
    }

    public Download downloadPdf(String transactionId) throws ProviderException {
        JsonArray body=new JsonArray();body.add(transactionId);
        String prefix=settings.usesTaxCodePath()?"/code":"";
        String path=prefix+"/itg/invoicepublished/downloadinvoice?downloadDataType="+URLEncoder.encode("PDF",StandardCharsets.UTF_8);
        JsonObject root=postApi(path,body);
        JsonArray data=dataArray(root);
        if(data.size()==0) throw new ProviderException("PDF_NOT_FOUND","MISA did not return the issued PDF",200,null);
        JsonObject item=data.get(0).getAsJsonObject();String encoded=string(item,"Data");
        if(encoded==null || encoded.isBlank()) throw new ProviderException("PDF_EMPTY","MISA returned an empty PDF",200,null);
        try { byte[] pdf=Base64.getDecoder().decode(encoded); if(pdf.length<5 || pdf[0]!='%' || pdf[1]!='P' || pdf[2]!='D' || pdf[3]!='F' || pdf[4]!='-') throw new IllegalArgumentException(); return new Download(pdf); }
        catch(IllegalArgumentException ex){throw new ProviderException("PDF_INVALID","MISA returned an invalid PDF payload",200,ex);}
    }

    public void cancel(String transactionId, String invoiceSeries, String reason) throws ProviderException {
        JsonObject body=new JsonObject();body.addProperty("TransactionID",transactionId);body.addProperty("InvSeries",invoiceSeries);body.addProperty("CancelReason",reason);
        JsonObject root=postOrigin("/api/integration/invoice/cancel",body,integrationToken());
        requireSuccess(root);
    }

    private JsonObject postApi(String path, JsonElement body) throws ProviderException {
        String token=apiToken();
        HttpRequest request=HttpRequest.newBuilder(URI.create(settings.apiV3()+path)).timeout(Duration.ofSeconds(30))
                .header("Content-Type","application/json").header("Authorization","Bearer "+token)
                .header("CompanyTaxCode",settings.taxCode()).POST(HttpRequest.BodyPublishers.ofString(JsonUtil.getGson().toJson(body),StandardCharsets.UTF_8)).build();
        return send(request);
    }

    private JsonObject postOrigin(String path, JsonElement body, String token) throws ProviderException {
        HttpRequest.Builder builder=HttpRequest.newBuilder(URI.create(settings.origin()+path)).timeout(Duration.ofSeconds(30))
                .header("Content-Type","application/json");
        if(token!=null && !token.isBlank())builder.header("Authorization","Bearer "+token);
        return send(builder.POST(HttpRequest.BodyPublishers.ofString(JsonUtil.getGson().toJson(body),StandardCharsets.UTF_8)).build());
    }

    private String apiToken() throws ProviderException { return token(apiToken,"/api/v3/auth/token"); }
    private String integrationToken() throws ProviderException { return token(integrationToken,"/api/integration/auth/token"); }
    private String token(AtomicReference<CachedToken> cache,String path) throws ProviderException {
        CachedToken existing=cache.get();if(existing!=null && existing.expiresAt().isAfter(Instant.now().plusSeconds(60)))return existing.value();
        JsonObject body=new JsonObject();body.addProperty("appid",settings.appId());body.addProperty("taxcode",settings.taxCode());body.addProperty("username",settings.username());body.addProperty("password",settings.password());
        JsonObject root=postOrigin(path,body,"");requireSuccess(root);
        String value=primitiveData(root);
        if(value==null || value.isBlank() || value.equals("{}"))throw new ProviderException("TOKEN_EMPTY","MISA did not return an access token",200,null);
        CachedToken updated=new CachedToken(value,Instant.now().plus(Duration.ofDays(13)));cache.set(updated);return value;
    }

    private JsonObject send(HttpRequest request) throws ProviderException {
        try {
            HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonObject root;
            try { root=JsonParser.parseString(response.body()).getAsJsonObject(); }
            catch(Exception ex){throw new ProviderException("INVALID_PROVIDER_RESPONSE","MISA returned an unreadable response",response.statusCode(),ex);}
            if(response.statusCode()<200 || response.statusCode()>=300) throw new ProviderException(providerCode(root,"HTTP_"+response.statusCode()),"MISA request failed",response.statusCode(),null);
            requireSuccess(root);return root;
        } catch(ProviderException ex){throw ex;}
        catch(InterruptedException ex){Thread.currentThread().interrupt();throw new ProviderException("INTERRUPTED","MISA request was interrupted; issuance state must be reconciled",0,ex);}
        catch(IOException | IllegalArgumentException ex){throw new ProviderException("NETWORK_UNKNOWN","MISA request outcome is unknown; reconcile before retrying",0,ex);}
    }

    private static void requireSuccess(JsonObject root) throws ProviderException {
        boolean success=root.has("Success")?root.get("Success").getAsBoolean():root.has("success") && root.get("success").getAsBoolean();
        String code=providerCode(root,"");
        if(!success || !code.isBlank()) throw new ProviderException(code.isBlank()?"PROVIDER_REJECTED":code,"MISA rejected the operation",200,null);
    }

    private static Published parsePublished(JsonObject root) throws ProviderException {
        JsonArray data=dataArray(root);if(data.size()==0) throw new ProviderException("PUBLISH_NO_RESULT","MISA did not return invoice-level publication results",200,null);
        JsonObject item=data.get(0).getAsJsonObject();String error=string(item,"ErrorCode");
        if(error!=null && !error.isBlank() && !error.equals("0") && !error.equals("000"))throw new ProviderException(error,"MISA rejected the invoice",200,null);
        String transaction=string(item,"TransactionID");String number=string(item,"InvNo");
        if(transaction==null || transaction.isBlank() || number==null || number.isBlank())throw new ProviderException("PUBLISH_INCOMPLETE","MISA accepted the request but did not confirm an issued invoice; reconcile before retrying",200,null);
        return new Published(transaction,string(item,"InvTemplateNo"),string(item,"InvSeries"),number,string(item,"InvDate"),"");
    }

    private static JsonArray dataArray(JsonObject root) throws ProviderException {
        JsonElement data=root.get("Data");if(data==null || data.isJsonNull())return new JsonArray();
        try { if(data.isJsonPrimitive() && data.getAsJsonPrimitive().isString()) {
            String raw=data.getAsString().trim();
            try { data=JsonParser.parseString(raw); }
            catch(Exception listWithoutBrackets) { if(raw.startsWith("{") && raw.endsWith("}")) data=JsonParser.parseString("["+raw+"]"); else throw listWithoutBrackets; }
        }} catch(Exception ex){throw new ProviderException("INVALID_PROVIDER_DATA","MISA returned malformed data",200,ex);}
        if(data.isJsonArray())return data.getAsJsonArray();
        if(data.isJsonObject()){JsonArray single=new JsonArray();single.add(data);return single;}
        return new JsonArray();
    }
    private static String primitiveData(JsonObject root) throws ProviderException {
        JsonElement data=root.get("Data");if(data==null || data.isJsonNull())return null;
        if(data.isJsonPrimitive())return data.getAsString();
        if(data.isJsonObject()) { JsonObject value=data.getAsJsonObject();for(String key:new String[]{"Token","token","AccessToken","access_token"})if(value.has(key))return value.get(key).getAsString(); }
        return data.toString();
    }
    private static String providerCode(JsonObject root,String fallback) {
        for(String key:new String[]{"ErrorCode","errorCode"})if(root.has(key) && !root.get(key).isJsonNull()) {String value=root.get(key).getAsString();if(!value.isBlank()&&!value.equals("0")&&!value.equals("000"))return value;}
        return fallback;
    }
    private static String string(JsonObject x,String key){return x.has(key)&&!x.get(key).isJsonNull()?x.get(key).getAsString():null;}
    private static int integer(JsonObject x,String key){try{return x.has(key)&&!x.get(key).isJsonNull()?x.get(key).getAsInt():0;}catch(Exception ex){return 0;}}
    private static boolean bool(JsonObject x,String key){try{return x.has(key)&&!x.get(key).isJsonNull()&&x.get(key).getAsBoolean();}catch(Exception ex){return false;}}
}
