package quanlykhachsan.backend.customer;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import quanlykhachsan.backend.utils.AesGcmEncryptionUtil;
import static org.junit.jupiter.api.Assertions.*;

/** JDBC boundary tests only; never connect to or seed an application database. */
class GuestBookingIdentityTest {
    private final GuestIdentityVaultDao vault = new GuestIdentityVaultDao(new AesGcmEncryptionUtil(new byte[32]), new byte[32]);
    private static final String IDENTITY = "TEST123456789";
    private static final class Database {
        byte[] hash, encrypted;
        String legacy;
        int writes;
        boolean fail;
        final List<String> sql = new ArrayList<>();
        Connection open() {
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Connection.class}, (p,m,a) -> {
                if (m.getName().equals("getAutoCommit")) return false;
                if (m.getName().equals("prepareStatement")) {
                    String query = (String)a[0]; sql.add(query); Map<Integer,Object> params = new HashMap<>();
                    return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{PreparedStatement.class}, (s,method,args) -> {
                        if (method.getName().startsWith("set")) { params.put((Integer)args[0], args[1]); return null; }
                        if (method.getName().equals("executeQuery")) {
                            int[] row = {0};
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ResultSet.class}, (r,rm,ra) -> switch (rm.getName()) {
                                case "next" -> ++row[0] == 1;
                                case "getBytes" -> hash;
                                case "getString" -> legacy;
                                case "close" -> null;
                                default -> throw new AssertionError(rm.getName());
                            });
                        }
                        if (method.getName().equals("executeUpdate")) {
                            if (fail) throw new SQLException("Simulated vault failure", "23000", 1062);
                            writes++;
                            if (query.startsWith("INSERT INTO guest_identity_vault")) encrypted = (byte[])params.get(2);
                            else if (query.startsWith("UPDATE customers SET identity_hash=?")) { hash = (byte[])params.get(1); legacy = null; }
                            else throw new AssertionError("Unexpected write: " + query);
                            return 1;
                        }
                        if (method.getName().equals("close")) return null;
                        throw new AssertionError(method.getName());
                    });
                }
                if (m.getName().equals("close")) return null;
                throw new AssertionError(m.getName());
            });
        }
    }
    @Test void capturesIdentityUsingExistingVaultWithoutViolatingNoPlainIdentityConstraint() throws Exception {
        Database db = new Database();
        GuestIngestionService.storeIdentity(db.open(), 7, IDENTITY, vault);
        assertEquals(2, db.writes); assertNull(db.legacy);
        assertArrayEquals(vault.blindIndex(IDENTITY), db.hash);
        assertFalse(new String(db.encrypted, StandardCharsets.UTF_8).contains(IDENTITY));
        assertEquals(IDENTITY, new String(new AesGcmEncryptionUtil(new byte[32]).decrypt(db.encrypted,
            "identity-v1:7:id".getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        assertTrue(db.sql.stream().noneMatch(sql -> sql.contains("identity_card=?")));
    }
    @Test void matchingExistingHashIsNotOverwrittenOrRecaptured() throws Exception {
        Database db = new Database(); db.hash = vault.blindIndex(IDENTITY);
        GuestIngestionService.storeIdentity(db.open(), 7, IDENTITY.toLowerCase(Locale.ROOT), vault);
        assertEquals(0, db.writes);
    }
    @Test void conflictingExistingIdentityStillRequiresFrontDeskReview() {
        Database db = new Database(); db.hash = vault.blindIndex("OTHER12345");
        assertThrows(IllegalStateException.class, () -> GuestIngestionService.storeIdentity(db.open(), 7, IDENTITY, vault));
        assertEquals(0, db.writes);
        db.hash = null; db.legacy = "OTHER12345";
        assertThrows(IllegalStateException.class, () -> GuestIngestionService.storeIdentity(db.open(), 7, IDENTITY, vault));
        assertEquals(0, db.writes);
    }
    @Test void databaseFailurePropagatesToCallerInsteadOfPretendingIdentityWasSaved() {
        Database db = new Database(); db.fail = true;
        assertThrows(SQLException.class, () -> GuestIngestionService.storeIdentity(db.open(), 7, IDENTITY, vault));
        assertEquals(0, db.writes); assertNull(db.hash);
    }
}
