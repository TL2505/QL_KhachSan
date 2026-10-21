package quanlykhachsan.backend.customer;

import java.sql.*;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.PortalValidation;

/** A database-scoped phone lock prevents duplicates across all backend workers. */
public final class GuestIngestionService {
    @FunctionalInterface interface Connections { Connection open() throws SQLException; }
    private final Connections connections;
    public GuestIngestionService() { this(DatabaseConnection::getConnection); }
    GuestIngestionService(Connections connections) { this.connections = connections; }
    public int ingest(String name, String rawPhone, String email) throws SQLException {
        return ingest(name,rawPhone,email,null);
    }
    public int ingest(String name,String rawPhone,String email,String identity)throws SQLException {
        return capture(null, name, rawPhone, email, identity);
    }

    /** Booking under a verified account must never select another profile by phone. */
    public int ingestOwned(int customerId, String name, String rawPhone, String email, String identity) throws SQLException {
        if (customerId < 1) throw new SecurityException();
        return capture(customerId, name, rawPhone, email, identity);
    }

    private int capture(Integer owner, String name, String rawPhone, String email, String identity) throws SQLException {
        String phone = PortalValidation.phone(rawPhone);
        PortalValidation.email(email);
        if (name != null && (name.length() > 100 || name.chars().anyMatch(Character::isISOControl))) throw new IllegalArgumentException("Invalid name");
        try (Connection c = connections.open()) {
            lock(c, phone, true);
            try {
                c.setAutoCommit(false);
                int id;
                if (owner == null) id = ingest(c,name,phone,email);
                else {
                    try (PreparedStatement q = c.prepareStatement("SELECT phone FROM customers WHERE id=? FOR UPDATE")) {
                        q.setInt(1, owner);
                        try (ResultSet row = q.executeQuery()) {
                            if (!row.next() || !phone.equals(PortalValidation.phone(row.getString(1)))) throw new SecurityException();
                        }
                    }
                    id = owner;
                }
                if(identity!=null) {
                    if(!identity.matches("[A-Za-z0-9]{5,20}"))throw new IllegalArgumentException("Invalid identity");
                    storeIdentity(c, id, identity, GuestIdentityVaultDao.configured());
                }
                c.commit(); return id;
            } catch (SQLException | RuntimeException ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); lock(c, phone, false); }
        }
    }

    /** Respect the existing vault constraint; never replace an established identity during booking. */
    static void storeIdentity(Connection c, int customer, String identity, GuestIdentityVaultDao vault) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT identity_hash,identity_card FROM customers WHERE id=? FOR UPDATE")) {
            q.setInt(1, customer);
            try (ResultSet r = q.executeQuery()) {
                if (!r.next()) throw new SQLException("Customer not found during identity capture");
                byte[] existingHash = r.getBytes("identity_hash");
                String legacyIdentity = r.getString("identity_card");
                if ((existingHash != null && !java.security.MessageDigest.isEqual(existingHash, vault.blindIndex(identity)))
                        || (legacyIdentity != null && !legacyIdentity.strip().equalsIgnoreCase(identity.strip()))) {
                    throw new IdentityConflictException();
                }
                if (existingHash != null) return;
            }
        }
        // The caller holds the phone/customer lock and commits or rolls back this encrypted write.
        vault.save(c, customer, identity, null, null);
    }

    public static final class IdentityConflictException extends IllegalStateException {
        public IdentityConflictException() { super("Guest identity requires Front Desk review"); }
    }

    /** Caller must hold the phone lock through commit or rollback. */
    public int ingest(Connection c, String name, String phone, String email) throws SQLException {
                int id;
                try (PreparedStatement q = c.prepareStatement("SELECT id FROM customers WHERE phone IN (?,?) ORDER BY id LIMIT 1")) {
                    q.setString(1, phone); q.setString(2, "+84" + phone.substring(1));
                    try (ResultSet rows = q.executeQuery()) { id = rows.next() ? rows.getInt(1) : 0; }
                }
                if (id == 0) {
                    try (PreparedStatement q = c.prepareStatement("INSERT INTO customers(full_name,identity_card,phone,email,loyalty_level,total_loyalty_points) VALUES (?,NULL,?,?,'Member',0)", Statement.RETURN_GENERATED_KEYS)) {
                        q.setString(1, name == null ? "" : name); q.setString(2, phone); q.setString(3, email); q.executeUpdate();
                        try (ResultSet key = q.getGeneratedKeys()) { if (!key.next()) throw new SQLException("Guest insert returned no ID"); id = key.getInt(1); }
                    }
                } else if (name != null && !name.isBlank()) {
                    try (PreparedStatement q = c.prepareStatement("UPDATE customers SET full_name=? WHERE id=? AND full_name=''")) {
                        q.setString(1,name); q.setInt(2,id); q.executeUpdate();
                    }
                }
                return id;
    }
    public static void lock(Connection c, String phone, boolean acquire) throws SQLException {
        try (PreparedStatement q = c.prepareStatement(acquire ? "SELECT GET_LOCK(CONCAT('guest-phone:',?),10)" : "SELECT RELEASE_LOCK(CONCAT('guest-phone:',?))")) {
            q.setString(1,phone);
            try (ResultSet row = q.executeQuery()) {
                if (!row.next() || row.getInt(1) != 1) throw new SQLException("Guest phone lock unavailable");
            }
        }
    }
}
