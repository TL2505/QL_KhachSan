package quanlykhachsan.backend.customer;

import quanlykhachsan.backend.customer.CustomerDAO;
import quanlykhachsan.backend.customer.Customer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import quanlykhachsan.backend.utils.DBconn;

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
}
