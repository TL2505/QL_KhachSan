package quanlykhachsan.backend.customer;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.AesGcmEncryptionUtil;
import static org.junit.jupiter.api.Assertions.*;

class GuestIdentityVaultVerificationTest {
    private final byte[] aesKey = new byte[32];
    private final byte[] hmacKey = new byte[32];
    private final AesGcmEncryptionUtil aes = new AesGcmEncryptionUtil(aesKey);
    private final GuestIdentityVaultDao vault = new GuestIdentityVaultDao(aes, hmacKey);
    private final String identity = "SYNTHETIC-VERIFY-ONLY";

    private byte[] encrypted(String field) throws Exception {
        return aes.encrypt(identity.getBytes(StandardCharsets.UTF_8), ("identity-v1:7:" + field).getBytes(StandardCharsets.UTF_8));
    }
    @Test void correctKeysAuthenticateEveryStoredFieldAndBlindIndex() throws Exception {
        assertDoesNotThrow(() -> vault.verifyRecord(7, encrypted("id"), encrypted("raw"), encrypted("face"), vault.blindIndex(identity)));
    }
    @Test void differentAesKeyAndRowBindingFailWithoutRewritingCiphertext() throws Exception {
        byte[] other = new byte[32]; other[0] = 1;
        byte[] original = encrypted("id"), snapshot = original.clone();
        var wrong = new GuestIdentityVaultDao(new AesGcmEncryptionUtil(other), hmacKey);
        assertEquals("VLT01", assertThrows(SQLException.class, () -> wrong.verifyRecord(7, original, null, null, vault.blindIndex(identity))).getSQLState());
        assertArrayEquals(snapshot, original);
        assertEquals("VLT01", assertThrows(SQLException.class, () -> vault.verifyRecord(8, original, null, null, vault.blindIndex(identity))).getSQLState());
    }
    @Test void differentHmacKeyIsDetectedEvenWhenAesCanDecrypt() throws Exception {
        byte[] other = new byte[32]; other[0] = 2;
        var wrong = new GuestIdentityVaultDao(aes, other);
        assertEquals("VLT02", assertThrows(SQLException.class, () -> wrong.verifyRecord(7, encrypted("id"), null, null, vault.blindIndex(identity))).getSQLState());
    }
    @Test void missingIndexAndTamperedOptionalPayloadFail() throws Exception {
        assertEquals("VLT02", assertThrows(SQLException.class, () -> vault.verifyRecord(7, encrypted("id"), null, null, null)).getSQLState());
        byte[] damaged = encrypted("raw"); damaged[damaged.length-1] ^= 1;
        assertEquals("VLT01", assertThrows(SQLException.class, () -> vault.verifyRecord(7, encrypted("id"), damaged, null, vault.blindIndex(identity))).getSQLState());
    }
}
