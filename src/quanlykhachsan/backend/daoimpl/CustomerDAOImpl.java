package quanlykhachsan.backend.daoimpl;

import quanlykhachsan.backend.dao.CustomerDAO;
import quanlykhachsan.backend.model.Customer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import quanlykhachsan.backend.utils.DBconn;

public class CustomerDAOImpl implements CustomerDAO {

    @Override
    public void addCustomer(Customer customer) {
        String query = "INSERT INTO customers(full_name, identity_card, phone, email, address) VALUES (?, ?, ?, ?, ?)";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, customer.getFullName());
            ps.setString(2, customer.getIdentityCard());
            ps.setString(3, customer.getPhone());
            ps.setString(4, customer.getEmail());
            ps.setString(5, customer.getAddress());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updateCustomer(Customer customer) {
        String query = "UPDATE customers SET full_name=?, identity_card=?, phone=?, email=?, address=? WHERE id=?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, customer.getFullName());
            ps.setString(2, customer.getIdentityCard());
            ps.setString(3, customer.getPhone());
            ps.setString(4, customer.getEmail());
            ps.setString(5, customer.getAddress());
            ps.setInt(6, customer.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteCustomer(Customer customer) {
        String query = "DELETE FROM customers WHERE id=?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, customer.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
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
                list.add(c);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public void comboBoxCustomer() {
        // Äá»ƒ trá»‘ng nhÆ° yÃªu cáº§u (sáº½ triá»ƒn khai theo nhu cáº§u UI sau nÃ y)
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
    public boolean insert(Customer customer) {
        try {
            addCustomer(customer);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
