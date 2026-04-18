package quanlykhachsan.backend.daoimpl;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import quanlykhachsan.backend.dao.InvoiceDAO;
import quanlykhachsan.backend.model.Invoice;
import quanlykhachsan.backend.utils.DBconn;

public class InvoiceDAOImpl implements InvoiceDAO {

    @Override
    public List<Invoice> getAllInvoices() {
        return searchInvoices("");
    }

    @Override
    public List<Invoice> searchInvoices(String keyword) {
        List<Invoice> list = new ArrayList<>();
        String sql = "SELECT i.*, c.full_name AS customer_name, r.room_number FROM invoices i " +
                     "JOIN bookings b ON i.booking_id = b.id " +
                     "JOIN customers c ON b.customer_id = c.id " +
                     "JOIN rooms r ON b.room_id = r.id ";

        if (keyword != null && !keyword.trim().isEmpty()) {
            sql += "WHERE c.full_name LIKE ? OR r.room_number LIKE ? ";
        }
        sql += "ORDER BY i.issue_date DESC";

        try (Connection con = DBconn.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            if (keyword != null && !keyword.trim().isEmpty()) {
                String search = "%" + keyword.trim() + "%";
                ps.setString(1, search);
                ps.setString(2, search);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Invoice inv = new Invoice();
                    inv.setId(rs.getInt("id"));
                    inv.setBookingId(rs.getInt("booking_id"));
                    inv.setTotalRoomFee(rs.getDouble("total_room_fee"));
                    inv.setTotalServiceFee(rs.getDouble("total_service_fee"));
                    inv.setDiscount(rs.getDouble("discount"));
                    inv.setTaxAmount(rs.getDouble("tax_amount"));
                    inv.setFinalTotal(rs.getDouble("final_total"));
                    inv.setIssueDate(rs.getTimestamp("issue_date"));
                    inv.setStatus(rs.getString("status"));
                    
                    inv.setCustomerName(rs.getString("customer_name"));
                    inv.setRoomNumber(rs.getString("room_number"));
                    
                    list.add(inv);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public boolean addInvoice(Invoice invoice) {
        String sql = "INSERT INTO invoices(booking_id, total_room_fee, total_service_fee, discount, tax_amount, final_total, status) " +
                     "VALUES (?, ?, 0, ?, ?, ?, ?)";
        try (Connection con = DBconn.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
             
            ps.setInt(1, invoice.getBookingId());
            ps.setDouble(2, invoice.getTotalRoomFee());
            ps.setDouble(3, invoice.getDiscount());
            ps.setDouble(4, invoice.getTaxAmount());
            ps.setDouble(5, invoice.getFinalTotal());
            ps.setString(6, invoice.getStatus());

            int affectedRows = ps.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        invoice.setId(rs.getInt(1)); // Return inserted ID if needed later
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}
