package quanlykhachsan.backend.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Enterprise AES-256-GCM Cryptographic Vault Engine.
 * Complies with Standard 4.1.0-REALISTIC-BASELINE & NIST SP 800-38D.
 * Envelope structure: [1-byte Version: 0x01][12-byte IV][Ciphertext + 128-bit Tag].
 * Enforces Zero-Crash Fallback for HMAC_IDENTITY_KEY & AES_VAULT_KEY.
 */
public final class AesGcmVaultEngine {

    public static final byte ENVELOPE_VERSION = 0x01;
    public static final int IV_LENGTH_BYTES = 12;      // 96-bit IV per NIST SP 800-38D
    public static final int TAG_LENGTH_BITS = 128;     // 128-bit authentication tag
    public static final int TAG_LENGTH_BYTES = 16;
    public static final int HEADER_LENGTH_BYTES = 1 + IV_LENGTH_BYTES; // 13 bytes
    public static final int MIN_ENVELOPE_LENGTH = HEADER_LENGTH_BYTES + TAG_LENGTH_BYTES; // 29 bytes

    // Invariant 2 (P0): Minimum 32-byte (256-bit) zero-crash fallback keys
    public static final String FALLBACK_AES_VAULT_KEY_BASE64 = "c2VjdXJlX2Flc18yNTZfdmF1bHRfa2V5X2F1cmVsaWE=";
    public static final String FALLBACK_HMAC_IDENTITY_KEY = "aurelia_default_dev_hmac_secret_2026_fallback_minimum_32_bytes";

    private static final byte[] EMPTY_AAD = new byte[0];
    private static volatile AesGcmVaultEngine INSTANCE;

    private final SecretKeySpec aesKey;
    private final byte[] hmacKeyBytes;
    private final SecureRandom secureRandom;

    public AesGcmVaultEngine(byte[] keyBytes, byte[] hmacBytes) {
        this.secureRandom = new SecureRandom();
        byte[] validAes32 = resolveTo32Bytes(keyBytes, FALLBACK_AES_VAULT_KEY_BASE64);
        this.aesKey = new SecretKeySpec(validAes32, "AES");
        this.hmacKeyBytes = resolveTo32Bytes(hmacBytes, FALLBACK_HMAC_IDENTITY_KEY);
    }

    public static AesGcmVaultEngine getInstance() {
        if (INSTANCE == null) {
            synchronized (AesGcmVaultEngine.class) {
                if (INSTANCE == null) {
                    INSTANCE = createFromEnvironment();
                }
            }
        }
        return INSTANCE;
    }

    private static AesGcmVaultEngine createFromEnvironment() {
        return new AesGcmVaultEngine(
                quanlykhachsan.backend.utils.SecretConfiguration.key256("AES_VAULT_KEY"),
                quanlykhachsan.backend.utils.SecretConfiguration.key256("HMAC_IDENTITY_KEY"));
    }

    private static byte[] decodeKeyString(String keyString) {
        if (keyString == null || keyString.isBlank()) {
            return new byte[0];
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(keyString);
            if (decoded.length == 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {}
        return keyString.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] resolveTo32Bytes(byte[] raw, String fallbackStr) {
        if (raw != null && raw.length == 32) {
            return raw.clone();
        }
        if (raw == null || raw.length == 0) {
            return decodeKeyString(fallbackStr);
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(raw);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be supported by JVM", e);
        }
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM with authenticated associated data.
     * Generates a 12-byte random IV and encapsulates into:
     * [0x01][12-byte IV][Ciphertext + 16-byte Tag]
     */
    public byte[] encrypt(byte[] plaintext, byte[] associatedData) throws GeneralSecurityException {
        if (plaintext == null) {
            plaintext = new byte[0];
        }
        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, spec);

        if (associatedData != null && associatedData.length > 0) {
            cipher.updateAAD(associatedData);
        }

        byte[] ciphertextWithTag = cipher.doFinal(plaintext);

        return ByteBuffer.allocate(1 + IV_LENGTH_BYTES + ciphertextWithTag.length)
                .put(ENVELOPE_VERSION)
                .put(iv)
                .put(ciphertextWithTag)
                .array();
    }

    public byte[] encrypt(byte[] plaintext) throws GeneralSecurityException {
        return encrypt(plaintext, EMPTY_AAD);
    }

    public String encryptString(String plaintext) throws GeneralSecurityException {
        byte[] input = (plaintext == null) ? new byte[0] : plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] envelope = encrypt(input, EMPTY_AAD);
        return Base64.getEncoder().encodeToString(envelope);
    }

    /**
     * Decrypts an authenticated envelope using AES-256-GCM.
     * Validates version (0x01) and unpacks the 12-byte IV and ciphertext tag.
     */
    public byte[] decrypt(byte[] envelope, byte[] associatedData) throws GeneralSecurityException {
        if (envelope == null || envelope.length < MIN_ENVELOPE_LENGTH) {
            throw new GeneralSecurityException("Invalid vault envelope: Buffer too small");
        }
        if (envelope[0] != ENVELOPE_VERSION) {
            throw new GeneralSecurityException("Unsupported vault envelope version: " + envelope[0]);
        }

        byte[] iv = Arrays.copyOfRange(envelope, 1, HEADER_LENGTH_BYTES);
        int ciphertextLength = envelope.length - HEADER_LENGTH_BYTES;

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, aesKey, spec);

        if (associatedData != null && associatedData.length > 0) {
            cipher.updateAAD(associatedData);
        }

        return cipher.doFinal(envelope, HEADER_LENGTH_BYTES, ciphertextLength);
    }

    public byte[] decrypt(byte[] envelope) throws GeneralSecurityException {
        return decrypt(envelope, EMPTY_AAD);
    }

    public String decryptString(String base64Envelope) throws GeneralSecurityException {
        if (base64Envelope == null || base64Envelope.isBlank()) {
            throw new GeneralSecurityException("Ciphertext cannot be null or blank");
        }
        byte[] envelope = Base64.getDecoder().decode(base64Envelope.trim());
        byte[] decrypted = decrypt(envelope, EMPTY_AAD);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * Computes an HMAC-SHA256 blind index hash for searching identity records
     * without exposing plaintext identity numbers.
     */
    public byte[] blindIndex(String identity) {
        if (identity == null || identity.isBlank()) {
            throw new IllegalArgumentException("Identity cannot be empty for blind indexing");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKeyBytes, "HmacSHA256"));
            String normalized = identity.strip().toUpperCase(java.util.Locale.ROOT);
            return mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Blind index calculation failed", e);
        }
    }
}
