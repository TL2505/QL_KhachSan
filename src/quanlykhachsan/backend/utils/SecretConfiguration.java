package quanlykhachsan.backend.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Properties;

/** Resolves secrets in process env, *_FILE, project .env, then config.properties order. */
public final class SecretConfiguration {
    private static final Properties PROPERTIES = loadProperties();
    private static final Properties DOTENV = loadDotenv();

    private SecretConfiguration() { }

    public static String optional(String name) {
        String value = System.getenv(name);
        String file = System.getenv(name + "_FILE");
        if (value != null && !value.isBlank() && file != null && !file.isBlank()) {
            throw new IllegalStateException("Configure only one source for " + name);
        }
        if (file != null && !file.isBlank()) {
            try { return Files.readString(Path.of(file), StandardCharsets.UTF_8).strip(); }
            catch (IOException e) { throw new IllegalStateException("Configured secret file is unavailable"); }
        }
        if (value != null && !value.isBlank()) return value.trim();
        value = DOTENV.getProperty(name);
        if (value != null && !value.isBlank()) return value.trim();
        value = PROPERTIES.getProperty(propertyName(name));
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String required(String name) {
        String value = optional(name);
        if (value != null) return value;
        if (isProduction()) throw new IllegalStateException(name + " is required in production");
        return switch (name) {
            case "JWT_SECRET" -> "AureliaDevOnlyJwtSigningSecretChangeBeforeProduction2026";
            case "AES_VAULT_KEY" -> quanlykhachsan.backend.security.AesGcmVaultEngine.FALLBACK_AES_VAULT_KEY_BASE64;
            case "HMAC_IDENTITY_KEY" -> quanlykhachsan.backend.security.AesGcmVaultEngine.FALLBACK_HMAC_IDENTITY_KEY;
            default -> throw new IllegalStateException(name + " is required");
        };
    }

    public static byte[] key256(String name) {
        String raw = required(name);
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(raw); }
        catch (IllegalArgumentException e) { bytes = raw.getBytes(StandardCharsets.UTF_8); }
        if (bytes.length == 32) return bytes;
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 is unavailable", e); }
    }

    private static boolean isProduction() {
        String mode = System.getenv("APP_ENV");
        if (mode == null) mode = System.getenv("ENVIRONMENT");
        if (mode == null) mode = DOTENV.getProperty("APP_ENV", DOTENV.getProperty("ENVIRONMENT", ""));
        return "production".equalsIgnoreCase(mode) || "prod".equalsIgnoreCase(mode);
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        Path file = Path.of(System.getProperty("hotel.config.file", "config.properties"));
        if (Files.isRegularFile(file)) try (FileInputStream in = new FileInputStream(file.toFile())) {
            props.load(in);
        } catch (IOException ignored) { }
        return props;
    }

    private static Properties loadDotenv() {
        Properties values = new Properties();
        Path file = Path.of(System.getProperty("hotel.env.file", ".env"));
        if (!Files.isRegularFile(file)) return values;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String item = line.trim();
                if (item.isEmpty() || item.startsWith("#")) continue;
                if (item.startsWith("export ")) item = item.substring(7).trim();
                int equals = item.indexOf('=');
                if (equals < 1) continue;
                String key = item.substring(0, equals).trim();
                String value = item.substring(equals + 1).trim();
                if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) value = value.substring(1, value.length() - 1);
                values.setProperty(key, value);
            }
        } catch (IOException ignored) { }
        return values;
    }

    private static String propertyName(String name) {
        return switch (name) {
            case "AES_VAULT_KEY" -> "aes.vault.key";
            case "HMAC_IDENTITY_KEY" -> "hmac.identity.key";
            default -> name.toLowerCase(java.util.Locale.ROOT).replace('_', '.');
        };
    }
}
