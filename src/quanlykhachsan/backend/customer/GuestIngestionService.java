package quanlykhachsan.backend.customer;

import java.sql.*;
import quanlykhachsan.backend.utils.DatabaseConnection;
import quanlykhachsan.backend.utils.PortalValidation;

/** A database-scoped phone lock prevents duplicates across all backend workers. */
public final class GuestIngestionService {
    public int ingest(String name, String rawPhone, String email) throws SQLException {
        return ingest(name,rawPhone,email,null);
    }
    public int ingest(String name,String rawPhone,String email,String identity)throws SQLException {
        String phone = PortalValidation.phone(rawPhone);
        PortalValidation.email(email);
        if (name != null && (name.length() > 100 || name.chars().anyMatch(Character::isISOControl))) throw new IllegalArgumentException("Invalid name");
        try (Connection c = DatabaseConnection.getConnection()) {
            lock(c, phone, true);
            try {
                c.setAutoCommit(false);
                int id = ingest(c,name,phone,email);
                if(identity!=null) {
                    if(!identity.matches("[A-Za-z0-9]{5,20}"))throw new IllegalArgumentException("Invalid identity");
                    try(PreparedStatement q=c.prepareStatement("SELECT identity_card FROM customers WHERE id=? FOR UPDATE")) {
                        q.setInt(1,id);try(ResultSet r=q.executeQuery()){r.next();String existing=r.getString(1);if(existing!=null && !existing.equals(identity))throw new IllegalStateException("Guest identity requires Front Desk review");}
                    }
                    try(PreparedStatement q=c.prepareStatement("UPDATE customers SET identity_card=? WHERE id=? AND identity_card IS NULL")){q.setString(1,identity);q.setInt(2,id);q.executeUpdate();}
                }
                c.commit(); return id;
            } catch (SQLException | RuntimeException ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); lock(c, phone, false); }
        }
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
