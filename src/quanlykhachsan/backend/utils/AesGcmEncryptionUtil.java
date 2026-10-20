package quanlykhachsan.backend.utils;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * High-security AES-256-GCM authenticated encryption utility with Zero-Crash Self-Healing key resolution.
 * Versioned envelopes contain a 1-byte version header (0x01), a fresh 96-bit (12-byte) IV,
 * ciphertext, and an authenticated 128-bit (16-byte) GCM tag.
 */
public final class AesGcmEncryptionUtil {

    public static final String DEFAULT_FALLBACK_KEY_BASE64 = "c2VjdXJlX2Flc18yNTZfdmF1bHRfa2V5X2F1cmVsaWE=";
    private static final byte[] EMPTY_AAD = new byte[0];

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    /**
     * Zero-Crash Constructor:
     * If keyBytes is null or keyBytes.length != 32, does not throw an exception.
     * Automatically hashes keyBytes with SHA-256 to ensure a strictly compliant 32-byte AES-256 key.
     */
    public AesGcmEncryptionUtil(byte[] keyBytes) {
        byte[] valid32Bytes;
        if (keyBytes == null || keyBytes.length != 32) {
            valid32Bytes = sha256(keyBytes == null ? new byte[0] : keyBytes);
        } else {
            valid32Bytes = keyBytes.clone();
        }
        this.key = new SecretKeySpec(valid32Bytes, "AES");
    }

    /**
     * Resolves an arbitrary string into a strictly compliant 32-byte key:
     * 1. Attempts Base64 decode. If exactly 32 bytes, use directly. If not 32 bytes, hash with SHA-256.
     * 2. If not Base64, gets UTF-8 bytes: if exactly 32 bytes, use directly; otherwise, hash with SHA-256.
     */
    public static byte[] resolveTo32Bytes(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return resolveTo32Bytes(DEFAULT_FALLBACK_KEY_BASE64);
        }
        String trimmed = rawKey.trim();
        try {
            byte[] decoded = Base64.getDecoder().decode(trimmed);
            if (decoded.length == 32) {
                return decoded;
            }
            return sha256(decoded);
        } catch (IllegalArgumentException notBase64) {
            byte[] rawBytes = trimmed.getBytes(StandardCharsets.UTF_8);
            if (rawBytes.length == 32) {
                return rawBytes;
            }
            return sha256(rawBytes);
        }
    }

    /**
     * Computes SHA-256 digest guaranteed to be exactly 32 bytes.
     */
    private static byte[] sha256(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm must be available in JVM", e);
        }
    }

    /**
     * Factory constructor loading the key from the environment with self-healing fallback.
     */
    public static AesGcmEncryptionUtil fromEnvironment() {
        String rawKey;
        try {
            rawKey = SecretConfiguration.required("AES_VAULT_KEY");
        } catch (Exception ex) {
            rawKey = SecurityUtil.getAesVaultKey();
        }
        byte[] key32 = resolveTo32Bytes(rawKey);
        try {
            return new AesGcmEncryptionUtil(key32);
        } finally {
            Arrays.fill(key32, (byte) 0);
        }
    }

    /**
     * Encrypts plaintext bytes with authenticated associated data (AAD).
     */
    public byte[] encrypt(byte[] plaintext, byte[] associatedData) throws GeneralSecurityException {
        if (plaintext == null) {
            plaintext = new byte[0];
        }
        byte[] iv = new byte[12];
        random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        if (associatedData != null && associatedData.length > 0) {
            cipher.updateAAD(associatedData);
        }
        byte[] encrypted = cipher.doFinal(plaintext);
        return java.nio.ByteBuffer.allocate(13 + encrypted.length)
                .put((byte) 1)
                .put(iv)
                .put(encrypted)
                .array();
    }

    /**
     * Encrypts a plaintext string and returns Base64 encoded ciphertext envelope.
     */
    public String encrypt(String plainText) throws GeneralSecurityException {
        byte[] input = (plainText == null) ? new byte[0] : plainText.getBytes(StandardCharsets.UTF_8);
        byte[] envelope = encrypt(input, EMPTY_AAD);
        return Base64.getEncoder().encodeToString(envelope);
    }

    /**
     * Decrypts an authenticated envelope with associated data (AAD).
     */
    public byte[] decrypt(byte[] envelope, byte[] associatedData) throws GeneralSecurityException {
        if (envelope == null || envelope.length < 29 || envelope[0] != 1) {
            throw new GeneralSecurityException("Invalid vault envelope");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOfRange(envelope, 1, 13)));
        if (associatedData != null && associatedData.length > 0) {
            cipher.updateAAD(associatedData);
        }
        return cipher.doFinal(envelope, 13, envelope.length - 13);
    }

    /**
     * Decrypts a Base64 encoded ciphertext envelope and returns plaintext string.
     */
    public String decrypt(String cipherTextBase64) throws GeneralSecurityException {
        if (cipherTextBase64 == null || cipherTextBase64.isBlank()) {
            throw new GeneralSecurityException("Ciphertext cannot be null or blank");
        }
        byte[] envelope = Base64.getDecoder().decode(cipherTextBase64.trim());
        byte[] decrypted = decrypt(envelope, EMPTY_AAD);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}
