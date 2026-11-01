package quanlykhachsan.backend.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import quanlykhachsan.backend.utils.DBconn;

public class UserDAOImpl implements UserDAO {

    @Override
    public void addUser(User user) throws Exception {
        String query = "INSERT INTO users(username, password, role_id, status, full_name, email, phone, customer_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setInt(3, user.getRoleId());
            ps.setString(4, user.getStatus());
            ps.setString(5, user.getFullName());
            ps.setString(6, user.getEmail());
            ps.setString(7, user.getPhone());
            if (user.getCustomerId() != null) ps.setInt(8, user.getCustomerId());
            else ps.setNull(8, java.sql.Types.INTEGER);
            ps.executeUpdate();
        }
    }

    @Override
    public void updateUser(User user) throws Exception {
        String query = "UPDATE users SET username=?, password=?, role_id=?, status=?, full_name=?, email=?, phone=?, customer_id=? WHERE id=?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setInt(3, user.getRoleId());
            ps.setString(4, user.getStatus());
            ps.setString(5, user.getFullName());
            ps.setString(6, user.getEmail());
            ps.setString(7, user.getPhone());
            if (user.getCustomerId() != null) ps.setInt(8, user.getCustomerId());
            else ps.setNull(8, java.sql.Types.INTEGER);
            ps.setInt(9, user.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public boolean updateRole(int userId, int roleId) throws Exception {
        String query = "UPDATE users SET role_id=? WHERE id=?";
        try (Connection con = DBconn.getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, roleId);
            ps.setInt(2, userId);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean isRoleCurrent(int userId, String tokenRole) throws java.sql.SQLException {
        String query = "SELECT u.status, r.id AS role_id, r.name AS role_name "
                + "FROM users u JOIN roles r ON r.id=u.role_id WHERE u.id=?";
        try (Connection con = DBconn.getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                String status = rs.getString("status");
                if (status != null && java.util.Set.of("inactive", "disabled", "locked", "suspended", "deleted")
                        .contains(status.toLowerCase(java.util.Locale.ROOT))) return false;
                String normalizedTokenRole = tokenRole == null ? "" : tokenRole.trim();
                return normalizedTokenRole.equalsIgnoreCase(rs.getString("role_name"))
                        || normalizedTokenRole.equals(Integer.toString(rs.getInt("role_id")));
            }
        }
    }

    @Override
    public void deleteUser(User user) throws Exception {
        String query = "DELETE FROM users WHERE id=?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, user.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public ArrayList<User> selectUser() {
        ArrayList<User> list = new ArrayList<>();
        String query = "SELECT * FROM users";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                User u = new User();
                u.setId(rs.getInt("id"));
                u.setUsername(rs.getString("username"));
                u.setPassword(rs.getString("password"));
                u.setRoleId(rs.getInt("role_id"));
                u.setStatus(rs.getString("status"));
                u.setFullName(rs.getString("full_name"));
                u.setEmail(rs.getString("email"));
                u.setPhone(rs.getString("phone"));
                int cid = rs.getInt("customer_id");
                if (!rs.wasNull()) u.setCustomerId(cid);
                list.add(u);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public void comboBoxUser() {
    }
    @Override
    public User findByUsername(String username) {
        String query = "SELECT u.*, COALESCE(NULLIF(u.phone,''), c.phone) AS account_phone, "
                + "COALESCE(NULLIF(u.email,''), c.email) AS account_email "
                + "FROM users u LEFT JOIN customers c ON c.id=u.customer_id WHERE u.username = ?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User u = new User();
                    u.setId(rs.getInt("id"));
                    u.setUsername(rs.getString("username"));
                    u.setPassword(rs.getString("password"));
                    u.setRoleId(rs.getInt("role_id"));
                    u.setStatus(rs.getString("status"));
                    u.setFullName(rs.getString("full_name"));
                    u.setEmail(rs.getString("account_email"));
                    u.setPhone(rs.getString("account_phone"));
                    int cid = rs.getInt("customer_id");
                    if (!rs.wasNull()) u.setCustomerId(cid);
                    return u;
                }
            }
        } catch (java.sql.SQLException e) {
            org.slf4j.LoggerFactory.getLogger(UserDAOImpl.class).error(
                "[LOGIN DB_FAILURE] SQLState={} code={}", e.getSQLState(), e.getErrorCode());
            throw new IllegalStateException("Account lookup unavailable", e);
        }
        return null;
    }

    @Override
    public boolean insert(User user) throws Exception {
        addUser(user);
        return true;
    }

    @Override
    public boolean insert(Connection con, User user) throws Exception {
        if (con.getAutoCommit()) throw new java.sql.SQLException("User insert requires a caller-owned transaction");
        String query = "INSERT INTO users(username, password, role_id, status, full_name, email, phone, customer_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setInt(3, user.getRoleId());
            ps.setString(4, user.getStatus());
            ps.setString(5, user.getFullName());
            ps.setString(6, user.getEmail());
            ps.setString(7, user.getPhone());
            if (user.getCustomerId() != null) ps.setInt(8, user.getCustomerId());
            else ps.setNull(8, java.sql.Types.INTEGER);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public int getRoleIdByName(String roleName) {
        String query = "SELECT id FROM roles WHERE LOWER(name) = ?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, roleName.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1; // Not found
    }

    @Override
    public java.util.List<quanlykhachsan.backend.user.Role> selectAllRoles() {
        java.util.List<quanlykhachsan.backend.user.Role> list = new ArrayList<>();
        String query = "SELECT * FROM roles";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                quanlykhachsan.backend.user.Role r = new quanlykhachsan.backend.user.Role();
                r.setId(rs.getInt("id"));
                r.setName(rs.getString("name"));
                r.setDescription(rs.getString("description"));
                list.add(r);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}
