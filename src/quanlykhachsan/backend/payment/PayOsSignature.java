package quanlykhachsan.backend.payment;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** payOS payment signature format: sorted keys, raw values, no URL encoding. */
public final class PayOsSignature {
    private PayOsSignature() { }
    public static String canonical(JsonObject data) {
        List<String> parts=new ArrayList<>();
        new TreeSet<>(data.keySet()).forEach(key -> parts.add(key+"="+value(data.get(key))));
        return String.join("&",parts);
    }
    private static String value(JsonElement v) {
        if(v==null||v.isJsonNull())return "";
        if(v.isJsonArray()||v.isJsonObject())return sorted(v).toString();
        String text=v.getAsString();
        return text.equals("null")||text.equals("undefined")?"":text;
    }
    private static JsonElement sorted(JsonElement v) {
        if(v.isJsonObject()) {
            JsonObject result=new JsonObject();new TreeSet<>(v.getAsJsonObject().keySet()).forEach(k->result.add(k,sorted(v.getAsJsonObject().get(k))));return result;
        }
        if(v.isJsonArray()){JsonArray a=new JsonArray();v.getAsJsonArray().forEach(x->a.add(sorted(x)));return a;}
        return v;
    }
    public static String sign(JsonObject data,String key) {
        try {Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(m.doFinal(canonical(data).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException("Cannot calculate payment signature",e);}
    }
    public static boolean verify(JsonObject data,String signature,String key) {
        return signature!=null&&signature.matches("[0-9a-fA-F]{64}")&&MessageDigest.isEqual(HexFormat.of().parseHex(signature),HexFormat.of().parseHex(sign(data,key)));
    }
}
