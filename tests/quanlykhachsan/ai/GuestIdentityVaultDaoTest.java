package quanlykhachsan.ai;

import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import quanlykhachsan.support.Phase2TestDatabase;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.customer.*;
import static org.junit.jupiter.api.Assertions.*;

class GuestIdentityVaultDaoTest {
    @Test void migrationIsRepeatableVaultIsCiphertextAndBlindIndexIsStable() throws Exception {
        Phase2TestDatabase.require();
        var vault=GuestIdentityVaultDao.configured();
        String identity="TEST"+UUID.randomUUID().toString().replace("-", "").substring(0,12);
        int customer;
        try(var c=DatabaseConnection.getConnection()){
            try(var q=c.prepareStatement("INSERT INTO customers(full_name,identity_card,phone) VALUES ('Synthetic Vault',?,'VAULT-TEST')",Statement.RETURN_GENERATED_KEYS)){
                q.setString(1,identity);q.executeUpdate();try(var r=q.getGeneratedKeys()){r.next();customer=r.getInt(1);}
            }
            try{
                assertTrue(IdentityVaultMigration.backfill(c,vault)>=1);
                assertEquals(0,IdentityVaultMigration.backfill(c,vault));
                assertEquals(identity,vault.readIdentity(c,customer));
                IdentityVaultMigration.finalizeSchema(c);
                try {
                    assertThrows(SQLException.class,()->{
                        try(var q=c.prepareStatement("UPDATE customers SET identity_card='SYNTHETIC-REJECT' WHERE id=?")){q.setInt(1,customer);q.executeUpdate();}
                    });
                } finally {
                    // Restore this disposable schema for legacy regression fixtures only.
                    try(var q=c.createStatement()){q.execute("ALTER TABLE customers DROP CONSTRAINT chk_no_plain_identity");}
                }
                assertArrayEquals(vault.blindIndex(identity),vault.blindIndex(identity.toLowerCase(java.util.Locale.ROOT)));
                try(var q=c.prepareStatement("SELECT identity_card,identity_hash,v.encrypted_id_number FROM customers c JOIN guest_identity_vault v ON v.customer_id=c.id WHERE c.id=?")){
                    q.setInt(1,customer);try(var r=q.executeQuery()){assertTrue(r.next());assertNull(r.getString(1));
                        assertArrayEquals(vault.blindIndex(identity),r.getBytes(2));
                        assertFalse(new String(r.getBytes(3),java.nio.charset.StandardCharsets.UTF_8).contains(identity));}
                }
                c.setAutoCommit(false);vault.save(c,customer,identity,new byte[]{1,2},new byte[]{3,4});c.commit();c.setAutoCommit(true);
                try(var q=c.prepareStatement("SELECT encrypted_id_number,encrypted_raw_payload,face_embedding_blob FROM guest_identity_vault WHERE customer_id=?")){
                    q.setInt(1,customer);try(var r=q.executeQuery()){r.next();
                        assertFalse(java.util.Arrays.equals(java.util.Arrays.copyOfRange(r.getBytes(1),1,13),java.util.Arrays.copyOfRange(r.getBytes(2),1,13)));
                        assertFalse(java.util.Arrays.equals(java.util.Arrays.copyOfRange(r.getBytes(2),1,13),java.util.Arrays.copyOfRange(r.getBytes(3),1,13)));}
                }
            }finally{c.setAutoCommit(true);try(var q=c.prepareStatement("DELETE FROM customers WHERE id=?")){q.setInt(1,customer);q.executeUpdate();}}
        }
    }
}
