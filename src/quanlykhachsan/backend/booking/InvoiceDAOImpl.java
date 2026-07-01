package quanlykhachsan.backend.booking;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import quanlykhachsan.backend.booking.InvoiceDAO;
import quanlykhachsan.backend.booking.Invoice;
import quanlykhachsan.backend.utils.DBconn;

public class InvoiceDAOImpl implements InvoiceDAO {

    @Override
    public List<Invoice> getAllInvoices() {
        return searchInvoices("", 0, 100);
    }

    @Override
    public List<Invoice> searchInvoices(String keyword) {
        return searchInvoices(keyword, 0, 100);
    }

    @Override
    public List<Invoice> searchInvoices(String keyword, int offset, int limit) {
        return searchInvoices(keyword, "", "", "", offset, limit);
    }

    @Override
    public List<Invoice> searchInvoices(String keyword, String status, String fromDate, String toDate, int offset, int limit) {
        List<Invoice> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT i.*, c.full_name AS customer_name, r.room_number FROM invoices i " +
            "JOIN bookings b ON i.booking_id = b.id " +
            "LEFT JOIN customers c ON b.customer_id = c.id " +
            "JOIN rooms r ON b.room_id = r.id WHERE 1=1 "
        );

        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append("AND (c.full_name LIKE ? OR r.room_number LIKE ? OR CAST(i.id AS CHAR) = ?) ");
        }
        if (status != null && !status.trim().isEmpty() && !status.equals("all")) {
            sql.append("AND i.status = ? ");
        }
        if (fromDate != null && !fromDate.trim().isEmpty()) {
            sql.append("AND i.issue_date >= ? ");
        }
        if (toDate != null && !toDate.trim().isEmpty()) {
            sql.append("AND i.issue_date <= ? ");
        }
        
        sql.append("ORDER BY i.issue_date DESC LIMIT ? OFFSET ?");

        try (Connection con = DBconn.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            
            int paramIndex = 1;
            if (keyword != null && !keyword.trim().isEmpty()) {
                String search = "%" + keyword.trim() + "%";
                ps.setString(paramIndex++, search);
                ps.setString(paramIndex++, search);
                ps.setString(paramIndex++, keyword.trim());
            }
            if (status != null && !status.trim().isEmpty() && !status.equals("all")) {
                ps.setString(paramIndex++, status.trim());
            }
            if (fromDate != null && !fromDate.trim().isEmpty()) {
                ps.setString(paramIndex++, fromDate.trim() + " 00:00:00");
            }
            if (toDate != null && !toDate.trim().isEmpty()) {
                ps.setString(paramIndex++, toDate.trim() + " 23:59:59");
            }
            
            ps.setInt(paramIndex++, limit);
            ps.setInt(paramIndex++, offset);

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
    public Invoice getInvoiceById(int id) {
        String sql = "SELECT i.*, c.full_name AS customer_name, r.room_number FROM invoices i " +
                     "JOIN bookings b ON i.booking_id = b.id " +
                     "LEFT JOIN customers c ON b.customer_id = c.id " +
                     "JOIN rooms r ON b.room_id = r.id WHERE i.id = ?";
        try (Connection con = DBconn.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
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
                    return inv;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
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
