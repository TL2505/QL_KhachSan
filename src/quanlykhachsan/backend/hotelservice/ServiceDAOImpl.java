package quanlykhachsan.backend.hotelservice;

import quanlykhachsan.backend.hotelservice.ServiceDAO;
import quanlykhachsan.backend.hotelservice.Service;
import quanlykhachsan.backend.utils.DBconn;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;

public class ServiceDAOImpl implements ServiceDAO {

    @Override
    public void addService(Service service) {
        String query = "INSERT INTO services (name, description, price, status) VALUES (?, ?, ?, ?)";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, service.getName());
            ps.setString(2, service.getDescription());
            ps.setDouble(3, service.getPrice());
            ps.setString(4, service.getStatus() != null ? service.getStatus() : "active");
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updateService(Service service) {
        String query = "UPDATE services SET name=?, description=?, price=?, status=? WHERE id=?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, service.getName());
            ps.setString(2, service.getDescription());
            ps.setDouble(3, service.getPrice());
            ps.setString(4, service.getStatus());
            ps.setInt(5, service.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteService(int id) {
        String query = "DELETE FROM services WHERE id=?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public ArrayList<Service> selectAllServices() {
        ArrayList<Service> list = new ArrayList<>();
        String query = "SELECT * FROM services";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Service s = new Service();
                s.setId(rs.getInt("id"));
                s.setName(rs.getString("name"));
                s.setDescription(rs.getString("description"));
                s.setPrice(rs.getDouble("price"));
                s.setStatus(rs.getString("status"));
                s.setCreatedAt(rs.getTimestamp("created_at"));
                s.setUpdatedAt(rs.getTimestamp("updated_at"));
                list.add(s);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public Service findById(int id) {
        String query = "SELECT * FROM services WHERE id=?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Service s = new Service();
                    s.setId(rs.getInt("id"));
                    s.setName(rs.getString("name"));
                    s.setDescription(rs.getString("description"));
                    s.setPrice(rs.getDouble("price"));
                    s.setStatus(rs.getString("status"));
                    return s;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
