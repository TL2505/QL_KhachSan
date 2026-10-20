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
}
