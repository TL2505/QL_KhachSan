package quanlykhachsan.backend.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacSignatureUtil {
    private HmacSignatureUtil(){ }
    public static String sign(String secret,byte[] body) {
        if(secret==null || secret.length()<32)throw new IllegalStateException("Bank webhook secret is not configured");
        try {Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(body));}
        catch(java.security.GeneralSecurityException ex){throw new IllegalStateException("HMAC is unavailable",ex);}
    }
    public static boolean verify(String secret,byte[] body,String signature) {
        if(signature==null || !signature.matches("(?i)[0-9a-f]{64}"))return false;
        return MessageDigest.isEqual(HexFormat.of().parseHex(signature),HexFormat.of().parseHex(sign(secret,body)));
    }
}
