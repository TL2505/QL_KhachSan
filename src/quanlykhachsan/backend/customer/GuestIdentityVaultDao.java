package quanlykhachsan.backend.customer;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.*;
import java.util.Arrays;
import java.util.Base64;
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
        String encoded = quanlykhachsan.backend.utils.SecretConfiguration.required("HMAC_IDENTITY_KEY");
        byte[] bytes = Base64.getDecoder().decode(encoded);
        try { return new GuestIdentityVaultDao(AesGcmEncryptionUtil.fromEnvironment(), bytes); }
        finally { Arrays.fill(bytes, (byte)0); }
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
