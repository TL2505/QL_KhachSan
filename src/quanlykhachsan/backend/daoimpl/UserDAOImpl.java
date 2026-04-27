package quanlykhachsan.backend.daoimpl;

import quanlykhachsan.backend.dao.UserDAO;
import quanlykhachsan.backend.model.User;
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
        String query = "SELECT * FROM users WHERE username = ?";
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
                    u.setEmail(rs.getString("email"));
                    u.setPhone(rs.getString("phone"));
                    int cid = rs.getInt("customer_id");
                    if (!rs.wasNull()) u.setCustomerId(cid);
                    return u;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public User findByEmail(String email) {
        String query = "SELECT * FROM users WHERE email = ?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
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
                    return u;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean insert(User user) throws Exception {
        addUser(user);
        return true;
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
    public java.util.List<quanlykhachsan.backend.model.Role> selectAllRoles() {
        java.util.List<quanlykhachsan.backend.model.Role> list = new ArrayList<>();
        String query = "SELECT * FROM roles";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                quanlykhachsan.backend.model.Role r = new quanlykhachsan.backend.model.Role();
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
