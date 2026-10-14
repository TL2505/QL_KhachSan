package quanlykhachsan.backend.customer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import quanlykhachsan.backend.utils.DBconn;
import quanlykhachsan.backend.utils.DatabaseConnection;

public class CustomerDAOImpl implements CustomerDAO {

    @Override
    public void addCustomer(Customer customer) throws Exception {
        String query = "INSERT INTO customers(full_name, identity_card, phone, email, address, is_vip) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, customer.getFullName());
            ps.setString(2, customer.getIdentityCard());
            ps.setString(3, customer.getPhone());
            ps.setString(4, customer.getEmail());
            ps.setString(5, customer.getAddress());
            ps.setBoolean(6, customer.isVip());
            // Loyalty fields ignored for addCustomer, usually default to 0/Silver in DB
            ps.executeUpdate();
        }
    }

    @Override
    public void updateCustomer(Customer customer) throws Exception {
        String query = "UPDATE customers SET full_name=?, identity_card=?, phone=?, email=?, address=?, is_vip=?, loyalty_points=?, total_loyalty_points=?, loyalty_level=? WHERE id=?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, customer.getFullName());
            ps.setString(2, customer.getIdentityCard());
            ps.setString(3, customer.getPhone());
            ps.setString(4, customer.getEmail());
            ps.setString(5, customer.getAddress());
            ps.setBoolean(6, customer.isVip());
            ps.setInt(7, customer.getLoyaltyPoints());
            ps.setInt(8, customer.getTotalLoyaltyPoints());
            ps.setString(9, customer.getLoyaltyLevel());
            ps.setInt(10, customer.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void deleteCustomer(Customer customer) throws Exception {
        String query = "DELETE FROM customers WHERE id=?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, customer.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public ArrayList<Customer> selectCustomer() {
        ArrayList<Customer> list = new ArrayList<>();
        String query = "SELECT * FROM customers";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Customer c = new Customer();
                c.setId(rs.getInt("id"));
                c.setFullName(rs.getString("full_name"));
                c.setIdentityCard(rs.getString("identity_card"));
                c.setPhone(rs.getString("phone"));
                c.setEmail(rs.getString("email"));
                c.setAddress(rs.getString("address"));
                c.setVip(rs.getBoolean("is_vip"));
                c.setLoyaltyPoints(rs.getInt("loyalty_points"));
                c.setTotalLoyaltyPoints(rs.getInt("total_loyalty_points"));
                c.setLoyaltyLevel(rs.getString("loyalty_level"));
                list.add(c);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public void comboBoxCustomer() {
        
    }
    @Override
    public java.util.List<Customer> findAll() {
        return selectCustomer();
    }

    @Override
    public Customer findById(int id) {
        for (Customer c : selectCustomer()) {
            if (c.getId() == id) return c;
        }
        return null;
    }

    @Override
    public boolean insert(Customer customer) throws Exception {
        addCustomer(customer);
        return true;
    }

    @Override
    public int addAndReturnId(Customer customer) throws Exception {
        String query = "INSERT INTO customers(full_name, identity_card, phone, email, address, is_vip) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, customer.getFullName());
            ps.setString(2, customer.getIdentityCard());
            ps.setString(3, customer.getPhone());
            ps.setString(4, customer.getEmail());
            ps.setString(5, customer.getAddress());
            ps.setBoolean(6, customer.isVip());
            ps.executeUpdate();
            
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return -1;
    }

    @Override
    public void updateLoyaltyPoints(int customerId, int currentPointsChange, int totalPointsChange, String newLevel) throws Exception {
        String sql = "UPDATE customers SET loyalty_points = loyalty_points + ?, total_loyalty_points = total_loyalty_points + ?, loyalty_level = ? WHERE id = ?";
        try (Connection con = DBconn.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, currentPointsChange);
            ps.setInt(2, totalPointsChange);
            ps.setString(3, newLevel);
            ps.setInt(4, customerId);
            ps.executeUpdate();
        }
    }

    @Override
    public int createCustomer(String fullName, String phone, String identityCard, String email, String loyaltyLevel) throws SQLException {
        // Check for duplicate ID/Passport or phone numbers
        String checkSql = "SELECT id FROM customers WHERE identity_card = ? OR phone = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(checkSql)) {
            ps.setString(1, identityCard != null ? identityCard.trim() : "");
            ps.setString(2, phone != null ? phone.trim() : "");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalStateException("Customer with this ID/Passport or phone number already exists in the system");
                }
            }
        }

        String insertSql = "INSERT INTO customers(full_name, phone, identity_card, email, loyalty_level) VALUES (?, ?, ?, ?, ?)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, fullName != null ? fullName.trim() : "");
            ps.setString(2, phone != null ? phone.trim() : "");
            ps.setString(3, identityCard != null ? identityCard.trim() : "");
            ps.setString(4, email != null ? email.trim() : "");
            ps.setString(5, (loyaltyLevel != null && !loyaltyLevel.isBlank()) ? loyaltyLevel.trim() : "Member");
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062 || "23000".equals(e.getSQLState())
                    || (e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate"))) {
                throw new IllegalStateException("A customer with this ID card/Citizen ID or phone number already exists in the system (Duplicate Key)");
            }
            throw e;
        }
        throw new SQLException("Failed to retrieve generated customer ID");
    }

    @Override
    public List<Map<String, Object>> searchCustomers(String keyword) {
        List<Map<String, Object>> list = new ArrayList<>();
        boolean hasKeyword = (keyword != null && !keyword.isBlank());
        String sql = hasKeyword
                ? "SELECT id, full_name, phone, identity_card, loyalty_level, created_at FROM customers WHERE full_name LIKE ? OR phone LIKE ? OR identity_card LIKE ? ORDER BY id DESC LIMIT 50"
                : "SELECT id, full_name, phone, identity_card, loyalty_level, created_at FROM customers ORDER BY id DESC LIMIT 50";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (hasKeyword) {
                String pattern = "%" + keyword.trim() + "%";
                ps.setString(1, pattern);
                ps.setString(2, pattern);
                ps.setString(3, pattern);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", rs.getInt("id"));
                    map.put("full_name", rs.getString("full_name"));
                    map.put("phone", rs.getString("phone"));
                    map.put("identity_card", rs.getString("identity_card"));
                    map.put("loyalty_level", rs.getString("loyalty_level"));
                    map.put("created_at", rs.getString("created_at"));
                    list.add(map);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error searching customers: " + e.getMessage(), e);
        }
        return list;
    }
}
