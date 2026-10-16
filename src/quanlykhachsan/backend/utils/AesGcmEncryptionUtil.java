package quanlykhachsan.backend.utils;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Versioned envelopes contain a fresh 96-bit IV and an authenticated 128-bit tag. */
public final class AesGcmEncryptionUtil {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmEncryptionUtil(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != 32) throw new IllegalArgumentException("AES-256 key must contain exactly 32 bytes");
        key = new SecretKeySpec(keyBytes.clone(), "AES");
    }

    public static AesGcmEncryptionUtil fromEnvironment() {
        String encoded = SecretConfiguration.required("AES_VAULT_KEY");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("AES_VAULT_KEY must be Base64 encoded"); }
        try { return new AesGcmEncryptionUtil(bytes); }
        finally { Arrays.fill(bytes, (byte) 0); }
    }

    public byte[] encrypt(byte[] plaintext, byte[] associatedData) throws GeneralSecurityException {
        byte[] iv = new byte[12]; random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        cipher.updateAAD(associatedData);
        byte[] encrypted = cipher.doFinal(plaintext);
        return java.nio.ByteBuffer.allocate(13 + encrypted.length).put((byte) 1).put(iv).put(encrypted).array();
    }

    public byte[] decrypt(byte[] envelope, byte[] associatedData) throws GeneralSecurityException {
        if (envelope == null || envelope.length < 29 || envelope[0] != 1) throw new GeneralSecurityException("Invalid vault envelope");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOfRange(envelope, 1, 13)));
        cipher.updateAAD(associatedData);
        return cipher.doFinal(envelope, 13, envelope.length - 13);
    }
}
