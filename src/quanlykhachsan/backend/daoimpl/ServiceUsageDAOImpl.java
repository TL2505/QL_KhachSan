package quanlykhachsan.backend.daoimpl;

import quanlykhachsan.backend.dao.ServiceUsageDAO;
import quanlykhachsan.backend.model.ServiceUsage;
import quanlykhachsan.backend.utils.DBconn;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;

public class ServiceUsageDAOImpl implements ServiceUsageDAO {

    @Override
    public void addServiceUsage(ServiceUsage usage) {
        String query = "INSERT INTO service_usage (booking_id, service_id, quantity, unit_price, total_price) VALUES (?, ?, ?, ?, ?)";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, usage.getBookingId());
            ps.setInt(2, usage.getServiceId());
            ps.setInt(3, usage.getQuantity());
            ps.setDouble(4, usage.getUnitPrice());
            ps.setDouble(5, usage.getQuantity() * usage.getUnitPrice());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteServiceUsage(int id) {
        String query = "DELETE FROM service_usage WHERE id=?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public ArrayList<ServiceUsage> getUsageByBookingId(int bookingId) {
        ArrayList<ServiceUsage> list = new ArrayList<>();
        String query = "SELECT * FROM service_usage WHERE booking_id=?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ServiceUsage u = new ServiceUsage();
                    u.setId(rs.getInt("id"));
                    u.setBookingId(rs.getInt("booking_id"));
                    u.setServiceId(rs.getInt("service_id"));
                    u.setQuantity(rs.getInt("quantity"));
                    u.setUnitPrice(rs.getDouble("unit_price"));
                    u.setTotalPrice(rs.getDouble("total_price"));
                    u.setUsageDate(rs.getTimestamp("usage_date"));
                    list.add(u);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}
