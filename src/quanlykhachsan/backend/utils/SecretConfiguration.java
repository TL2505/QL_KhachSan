package quanlykhachsan.backend.utils;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;

/** Docker secret file support; values and secret paths are never logged. */
public final class SecretConfiguration {
    private SecretConfiguration(){}
    private static final java.util.Properties PROPS = loadConfigProperties();

    private static java.util.Properties loadConfigProperties() {
        java.util.Properties props = new java.util.Properties();
        java.io.File file = new java.io.File(System.getProperty("hotel.config.file", "config.properties"));
        if (file.exists()) {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                props.load(fis);
            } catch (Exception ignored) {}
        }
        return props;
    }

    public static String required(String name){
        String value=System.getenv(name),file=System.getenv(name+"_FILE");
        if(value!=null&&!value.isBlank()&&file!=null&&!file.isBlank())throw new IllegalStateException("Configure only one source for "+name);
        if(file!=null&&!file.isBlank()){
            try{value=Files.readString(Path.of(file),StandardCharsets.UTF_8).strip();}
            catch(IOException e){throw new IllegalStateException("Required secret file is unavailable");}
        }
        if (value == null || value.isBlank()) {
            String propKey = switch (name) {
                case "AES_VAULT_KEY" -> "aes.vault.key";
                case "HMAC_IDENTITY_KEY" -> "hmac.identity.key";
                case "JWT_SECRET" -> "jwt.secret";
                default -> null;
            };
            if (propKey != null && PROPS.containsKey(propKey)) {
                String pVal = PROPS.getProperty(propKey);
                if (pVal != null && !pVal.isBlank()) value = pVal.trim();
            }
        }
        if(value==null||value.isBlank()){
            if ("HMAC_IDENTITY_KEY".equals(name)) {
                return quanlykhachsan.backend.utils.SecurityUtil.getHmacIdentityKey();
            }
            if ("AES_VAULT_KEY".equals(name)) {
                return quanlykhachsan.backend.security.AesGcmVaultEngine.FALLBACK_AES_VAULT_KEY_BASE64;
            }
            throw new IllegalStateException(name+" is required");
        }
        return value;
    }
}
