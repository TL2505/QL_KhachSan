package quanlykhachsan.backend.customer;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.*;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import quanlykhachsan.backend.utils.AesGcmEncryptionUtil;

/** Caller owns the transaction; each field has its own IV and is bound to customer + purpose. */
public final class GuestIdentityVaultDao {
    private final AesGcmEncryptionUtil encryption;
    private final byte[] blindKey;
    public GuestIdentityVaultDao(AesGcmEncryptionUtil encryption, byte[] blindKey) {
        if (blindKey == null || blindKey.length != 32) throw new IllegalArgumentException("32-byte HMAC key required");
        this.encryption = encryption; this.blindKey = blindKey.clone();
    }
    public static GuestIdentityVaultDao configured() {
        byte[] bytes = quanlykhachsan.backend.utils.SecretConfiguration.key256("HMAC_IDENTITY_KEY");
        try { return new GuestIdentityVaultDao(AesGcmEncryptionUtil.fromEnvironment(), bytes); }
        finally { Arrays.fill(bytes, (byte)0); }
    }

    /** Read-only proof against stored ciphertext and blind indexes; never logs identity data. */
    public long verifyExistingRecords(Connection c) throws SQLException {
        long count = 0;
        try (var q = c.prepareStatement("SELECT v.customer_id,v.encrypted_id_number,v.encrypted_raw_payload,v.face_embedding_blob,c.identity_hash FROM guest_identity_vault v LEFT JOIN customers c ON c.id=v.customer_id");
             var r = q.executeQuery()) {
            while (r.next()) {
                verifyRecord(r.getInt(1), r.getBytes(2), r.getBytes(3), r.getBytes(4), r.getBytes(5));
                count++;
            }
        }
        try (var q = c.prepareStatement("SELECT COUNT(*) FROM customers c LEFT JOIN guest_identity_vault v ON v.customer_id=c.id WHERE c.identity_hash IS NOT NULL AND v.customer_id IS NULL"); var r = q.executeQuery()) {
            if (r.next() && r.getLong(1) != 0) throw new SQLException("Identity hash has no vault record", "VLT03");
        }
        return count;
    }

    void verifyRecord(int customer, byte[] encryptedId, byte[] raw, byte[] face, byte[] hash) throws SQLException {
        byte[] clear = null;
        try {
            clear = encryption.decrypt(encryptedId, aad(customer, "id"));
            byte[] actual = blindIndex(new String(clear, StandardCharsets.UTF_8));
            try {
                if (hash == null || !java.security.MessageDigest.isEqual(actual, hash))
                    throw new SQLException("HMAC_IDENTITY_KEY does not match the stored identity index, or index is damaged; restore original key, do not rewrite data", "VLT02");
            } finally { Arrays.fill(actual, (byte) 0); }
            for (String field : new String[]{"raw", "face"}) {
                byte[] encrypted = "raw".equals(field) ? raw : face;
                if (encrypted != null) {
                    byte[] payload = encryption.decrypt(encrypted, aad(customer, field));
                    Arrays.fill(payload, (byte) 0);
                }
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new SQLException("AES_VAULT_KEY cannot authenticate stored vault data; restore original key and verify backup integrity", "VLT01");
        } finally { if (clear != null) Arrays.fill(clear, (byte) 0); }
    }
    public byte[] blindIndex(String identity) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(blindKey,"HmacSHA256"));
            return mac.doFinal(normalize(identity).getBytes(StandardCharsets.UTF_8));
        } catch(GeneralSecurityException e) { throw new IllegalStateException("Identity indexing unavailable",e); }
    }
    private static String normalize(String identity) {
        if(identity==null || identity.isBlank() || identity.length()>100)throw new IllegalArgumentException("Invalid identity");
        return identity.strip().toUpperCase(java.util.Locale.ROOT);
    }
    private static byte[] aad(int customer,String field) {return ("identity-v1:"+customer+":"+field).getBytes(StandardCharsets.UTF_8);}
    public void save(Connection c,int customer,String identity,byte[] raw,byte[] face) throws SQLException {
        if(c.getAutoCommit())throw new SQLException("Vault writes require a caller-owned transaction");
        try {
            normalize(identity);
            byte[] ciphertext=encryption.encrypt(identity.strip().getBytes(StandardCharsets.UTF_8),aad(customer,"id"));
            byte[] encryptedRaw=raw==null?null:encryption.encrypt(raw,aad(customer,"raw"));
            byte[] encryptedFace=face==null?null:encryption.encrypt(face,aad(customer,"face"));
            try(var q=c.prepareStatement("INSERT INTO guest_identity_vault(customer_id,encrypted_id_number,encrypted_raw_payload,face_embedding_blob) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE encrypted_id_number=VALUES(encrypted_id_number),encrypted_raw_payload=COALESCE(VALUES(encrypted_raw_payload),encrypted_raw_payload),face_embedding_blob=COALESCE(VALUES(face_embedding_blob),face_embedding_blob),captured_at=CURRENT_TIMESTAMP")){
                q.setInt(1,customer);q.setBytes(2,ciphertext);q.setBytes(3,encryptedRaw);q.setBytes(4,encryptedFace);q.executeUpdate();
            }
            try(var q=c.prepareStatement("UPDATE customers SET identity_hash=?,identity_card=NULL WHERE id=?")){
                q.setBytes(1,blindIndex(identity));q.setInt(2,customer);
                if(q.executeUpdate()!=1)throw new SQLException("Customer not found");
            }
        }catch(GeneralSecurityException e){throw new SQLException("Vault encryption unavailable",e);}
    }
    /** No public HTTP decrypt endpoint. Caller must enforce Admin/Front Desk access. */
    public String readIdentity(Connection c,int customer) throws SQLException {
        try(var q=c.prepareStatement("SELECT encrypted_id_number FROM guest_identity_vault WHERE customer_id=?")){
            q.setInt(1,customer);try(var r=q.executeQuery()){
                if(!r.next())return null;
                byte[] clear=encryption.decrypt(r.getBytes(1),aad(customer,"id"));
                try{return new String(clear,StandardCharsets.UTF_8);}finally{Arrays.fill(clear,(byte)0);}
            }
        }catch(GeneralSecurityException e){throw new SQLException("Vault authentication failed",e);}
    }
}
