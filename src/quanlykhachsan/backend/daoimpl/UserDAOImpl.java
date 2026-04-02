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
    public void addUser(User user) {
        String query = "INSERT INTO users(username, password, role_id, status, full_name, email, phone) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setInt(3, user.getRoleId());
            ps.setString(4, user.getStatus());
            ps.setString(5, user.getFullName());
            ps.setString(6, user.getEmail());
            ps.setString(7, user.getPhone());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updateUser(User user) {
        String query = "UPDATE users SET username=?, password=?, role_id=?, status=?, full_name=?, email=?, phone=? WHERE id=?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setInt(3, user.getRoleId());
            ps.setString(4, user.getStatus());
            ps.setString(5, user.getFullName());
            ps.setString(6, user.getEmail());
            ps.setString(7, user.getPhone());
            ps.setInt(8, user.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteUser(User user) {
        String query = "DELETE FROM users WHERE id=?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, user.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
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
                    return u;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean insert(User user) {
        try {
            addUser(user);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
