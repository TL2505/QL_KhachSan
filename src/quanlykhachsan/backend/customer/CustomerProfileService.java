package quanlykhachsan.backend.customer;

import com.google.gson.JsonObject;
import java.sql.*;
import java.time.LocalDate;
import java.time.ZoneId;
import quanlykhachsan.backend.utils.PortalValidation;

/** Own-profile access only: the authenticated user ID selects the customer. */
public final class CustomerProfileService {
    public record Update(String fullName, String phone, String email, String identityCard, LocalDate dob, String address) { }

    public static Update validate(JsonObject body) {
        String fullName = PortalValidation.text(body, "fullName", 100, true);
        String phone = PortalValidation.phone(PortalValidation.text(body, "phone", 20, true));
        String email = PortalValidation.email(PortalValidation.text(body, "email", 100, true));
        String identity = PortalValidation.text(body, "identityCard", 20, false);
        if (identity != null && !identity.matches("(?:[0-9]{12}|[A-Za-z][A-Za-z0-9]{4,19})"))
            throw new IllegalArgumentException("Invalid identity number");
        String date = PortalValidation.text(body, "dateOfBirth", 10, false);
        LocalDate dob = null;
        if (date != null) {
            try { dob = LocalDate.parse(date); }
            catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid birth date"); }
            if (dob.isBefore(LocalDate.of(1900, 1, 1)) || dob.isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))))
                throw new IllegalArgumentException("Invalid birth date");
        }
        return new Update(fullName, phone, email, identity, dob, PortalValidation.text(body, "address", 255, false));
    }

    public static JsonObject read(Connection c, int userId) throws SQLException {
        try (var q = c.prepareStatement("SELECT c.id,COALESCE(NULLIF(u.full_name,''),c.full_name),COALESCE(NULLIF(u.phone,''),c.phone),COALESCE(NULLIF(u.email,''),c.email),c.address,c.date_of_birth,EXISTS(SELECT 1 FROM guest_identity_vault v WHERE v.customer_id=c.id) FROM users u JOIN customers c ON c.id=u.customer_id WHERE u.id=?")) {
            q.setInt(1, userId);
            try (var r = q.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("Customer profile is not linked");
                var result = new JsonObject();
                result.addProperty("customerId", r.getInt(1));
                result.addProperty("fullName", r.getString(2)); result.addProperty("phone", r.getString(3));
                result.addProperty("email", r.getString(4)); result.addProperty("address", r.getString(5));
                result.addProperty("dateOfBirth", r.getString(6)); result.addProperty("identitySaved", r.getBoolean(7));
                return result;
            }
        }
    }

    public static JsonObject save(Connection c, int userId, Update data, GuestIdentityVaultDao vault) throws SQLException {
        if (!c.getAutoCommit()) throw new SQLException("Profile update must own its transaction");
        c.setAutoCommit(false);
        try {
            int customerId;
            try (var q = c.prepareStatement("SELECT customer_id FROM users WHERE id=? FOR UPDATE")) {
                q.setInt(1, userId);
                try (var r = q.executeQuery()) {
                    if (!r.next() || r.getObject(1) == null) throw new IllegalArgumentException("Customer profile is not linked");
                    customerId = r.getInt(1);
                }
            }
            try (var q = c.prepareStatement("UPDATE customers SET full_name=?,phone=?,email=?,address=?,date_of_birth=? WHERE id=?")) {
                q.setString(1, data.fullName()); q.setString(2, data.phone()); q.setString(3, data.email());
                q.setString(4, data.address()); q.setObject(5, data.dob()); q.setInt(6, customerId); q.executeUpdate();
            }
            try (var q = c.prepareStatement("UPDATE users SET full_name=?,phone=?,email=? WHERE id=?")) {
                q.setString(1, data.fullName()); q.setString(2, data.phone()); q.setString(3, data.email()); q.setInt(4, userId); q.executeUpdate();
            }
            if (data.identityCard() != null) {
                try (var q = c.prepareStatement("UPDATE guest_identity_vault SET encrypted_raw_payload=NULL,face_embedding_blob=NULL WHERE customer_id=?")) {
                    q.setInt(1, customerId); q.executeUpdate();
                }
                vault.save(c, customerId, data.identityCard(), null, null);
            }
            JsonObject result = read(c, userId);
            c.commit(); return result;
        } catch (SQLException | RuntimeException ex) { c.rollback(); throw ex; }
        finally { c.setAutoCommit(true); }
    }
}
