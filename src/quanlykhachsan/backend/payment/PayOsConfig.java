package quanlykhachsan.backend.payment;

import java.net.URI;
import java.util.Map;

public record PayOsConfig(String clientId,String apiKey,String checksumKey,String account,String holder,String bin,String returnUrl,String cancelUrl) {
    public static PayOsConfig environment(){return from(System.getenv());}
    public static PayOsConfig from(Map<String,String> env){return new PayOsConfig(get(env,"PAYOS_CLIENT_ID"),get(env,"PAYOS_API_KEY"),get(env,"PAYOS_CHECKSUM_KEY"),get(env,"PAYOS_ACCOUNT_NUMBER"),get(env,"PAYOS_ACCOUNT_NAME"),get(env,"PAYOS_BANK_BIN"),get(env,"PAYOS_RETURN_URL"),get(env,"PAYOS_CANCEL_URL"));}
    private static String get(Map<String,String> env,String key){return env.getOrDefault(key,"").trim();}
    public void requireWebhook(){if(checksumKey.isBlank()||!account.matches("[0-9]{6,40}")||holder.isBlank()||!bin.matches("[0-9]{6}"))throw new IllegalStateException("payOS beneficiary/checksum configuration is incomplete");}
    public void requireLink(){requireWebhook();if(clientId.isBlank()||apiKey.isBlank())throw new IllegalStateException("payOS credentials are missing");url(returnUrl);url(cancelUrl);}
    private static void url(String s){URI u=URI.create(s);if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null)throw new IllegalStateException("payOS callback URL must use public HTTPS");}
}
