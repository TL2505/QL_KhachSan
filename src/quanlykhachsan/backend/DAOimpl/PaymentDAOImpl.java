package quanlykhachsan.backend.DAOimpl;

import quanlykhachsan.backend.DAO.PaymentDAO;
import quanlykhachsan.backend.MODEL.Payment;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import quanlykhachsan.DBconn;

public class PaymentDAOImpl implements PaymentDAO {

    @Override
    public void addPayment(Payment payment) {
        String query = "INSERT INTO payments(invoice_id, amount, payment_method, payment_date) VALUES (?, ?, ?, ?)";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, payment.getInvoiceId());
            ps.setDouble(2, payment.getAmount());
            ps.setString(3, payment.getPaymentMethod());
            ps.setTimestamp(4, new java.sql.Timestamp(payment.getPaymentDate().getTime()));
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updatePayment(Payment payment) {
        String query = "UPDATE payments SET invoice_id=?, amount=?, payment_method=?, payment_date=? WHERE id=?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, payment.getInvoiceId());
            ps.setDouble(2, payment.getAmount());
            ps.setString(3, payment.getPaymentMethod());
            ps.setTimestamp(4, new java.sql.Timestamp(payment.getPaymentDate().getTime()));
            ps.setInt(5, payment.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deletePayment(Payment payment) {
        String query = "DELETE FROM payments WHERE id=?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, payment.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public ArrayList<Payment> selectPayment() {
        ArrayList<Payment> list = new ArrayList<>();
        String query = "SELECT * FROM payments";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Payment p = new Payment();
                p.setId(rs.getInt("id"));
                p.setInvoiceId(rs.getInt("invoice_id"));
                p.setAmount(rs.getDouble("amount"));
                p.setPaymentMethod(rs.getString("payment_method"));
                p.setPaymentDate(rs.getTimestamp("payment_date"));
                list.add(p);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public void comboBoxPayment() {
    }
}
