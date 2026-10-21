package quanlykhachsan.ai;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import quanlykhachsan.backend.utils.AesGcmEncryptionUtil;
import static org.junit.jupiter.api.Assertions.*;

class AesGcmEncryptionUtilTest {
    @Test void freshIvRoundTripTamperAndRowBinding() throws Exception {
        byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
        var aes = new AesGcmEncryptionUtil(key);
        byte[] data = "SYNTHETIC-IDENTITY-NOT-A-CITIZEN-ID".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "customer:7:id".getBytes(StandardCharsets.UTF_8);
        var ivs = new HashSet<String>();
        for (int i = 0; i < 1000; i++) {
            byte[] encrypted = aes.encrypt(data, aad);
            assertTrue(ivs.add(java.util.HexFormat.of().formatHex(Arrays.copyOfRange(encrypted, 1, 13))));
            assertArrayEquals(data, aes.decrypt(encrypted, aad));
        }
        byte[] encrypted = aes.encrypt(data, aad);
        assertThrows(GeneralSecurityException.class, () -> aes.decrypt(encrypted, new byte[]{8}));
        encrypted[encrypted.length - 1] ^= 1;
        assertThrows(GeneralSecurityException.class, () -> aes.decrypt(encrypted, aad));
        assertThrows(GeneralSecurityException.class, () -> aes.decrypt(new byte[10], aad));
        assertDoesNotThrow(() -> new AesGcmEncryptionUtil(new byte[16]));
        String plainText = "Aurelia-Grand-Palace-Identity";
        String cipherB64 = aes.encrypt(plainText);
        assertEquals(plainText, aes.decrypt(cipherB64));
        byte[] other = new byte[32]; new SecureRandom().nextBytes(other);
        assertThrows(GeneralSecurityException.class, () -> new AesGcmEncryptionUtil(other).decrypt(aes.encrypt(data, aad), aad));
    }

    @Test
    void testResolveTo32BytesNormalization() {
        // 1. Standardized 32-byte Base64 key
        byte[] standard = AesGcmEncryptionUtil.resolveTo32Bytes(AesGcmEncryptionUtil.DEFAULT_FALLBACK_KEY_BASE64);
        assertNotNull(standard);
        assertEquals(32, standard.length);

        // 2. Null or blank key falls back to standard 32 bytes
        byte[] fromNull = AesGcmEncryptionUtil.resolveTo32Bytes(null);
        assertNotNull(fromNull);
        assertEquals(32, fromNull.length);
        assertArrayEquals(standard, fromNull);

        byte[] fromBlank = AesGcmEncryptionUtil.resolveTo32Bytes("   ");
        assertNotNull(fromBlank);
        assertEquals(32, fromBlank.length);
        assertArrayEquals(standard, fromBlank);

        // 3. Exact 32-character raw string
        String raw32 = "12345678901234567890123456789012";
        byte[] fromRaw32 = AesGcmEncryptionUtil.resolveTo32Bytes(raw32);
        assertNotNull(fromRaw32);
        assertEquals(32, fromRaw32.length);

        // 4. Arbitrary short or non-standard key normalized by SHA-256 to exactly 32 bytes
        byte[] fromShort = AesGcmEncryptionUtil.resolveTo32Bytes("short-secret");
        assertNotNull(fromShort);
        assertEquals(32, fromShort.length);

        byte[] fromLong = AesGcmEncryptionUtil.resolveTo32Bytes("super_long_key_exceeding_standard_thirty_two_bytes_length_limit");
        assertNotNull(fromLong);
        assertEquals(32, fromLong.length);
    }
}
