package quanlykhachsan.backend.daoimpl;

import quanlykhachsan.backend.dao.PromotionDAO;
import quanlykhachsan.backend.model.Promotion;
import quanlykhachsan.backend.utils.DBconn;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PromotionDAOImpl implements PromotionDAO {

    @Override
    public List<Promotion> selectAll() {
        List<Promotion> list = new ArrayList<>();
        String sql = "SELECT * FROM promotions ORDER BY created_at DESC";
        try (Connection con = DBconn.getConnection();
             PreparedStatement pst = con.prepareStatement(sql);
             ResultSet rs = pst.executeQuery()) {
            
            while (rs.next()) {
                Promotion p = new Promotion(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("discount_type"),
                        rs.getDouble("discount_value"),
                        rs.getTimestamp("start_date"),
                        rs.getTimestamp("end_date"),
                        rs.getString("condition_type"),
                        rs.getString("condition_value"),
                        rs.getString("status")
                );
                list.add(p);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public Promotion findById(int id) {
        String sql = "SELECT * FROM promotions WHERE id = ?";
        try (Connection con = DBconn.getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {
            
            pst.setInt(1, id);
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    return new Promotion(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getString("discount_type"),
                            rs.getDouble("discount_value"),
                            rs.getTimestamp("start_date"),
                            rs.getTimestamp("end_date"),
                            rs.getString("condition_type"),
                            rs.getString("condition_value"),
                            rs.getString("status")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean insert(Promotion p) {
        String sql = "INSERT INTO promotions (name, description, discount_type, discount_value, start_date, end_date, condition_type, condition_value, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = DBconn.getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {
            
            pst.setString(1, p.getName());
            pst.setString(2, p.getDescription());
            pst.setString(3, p.getDiscountType());
            pst.setDouble(4, p.getDiscountValue());
            pst.setTimestamp(5, new Timestamp(p.getStartDate().getTime()));
            pst.setTimestamp(6, new Timestamp(p.getEndDate().getTime()));
            pst.setString(7, p.getConditionType());
            pst.setString(8, p.getConditionValue());
            pst.setString(9, p.getStatus());
            
            return pst.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean update(Promotion p) {
        String sql = "UPDATE promotions SET name=?, description=?, discount_type=?, discount_value=?, start_date=?, end_date=?, condition_type=?, condition_value=?, status=? WHERE id=?";
        try (Connection con = DBconn.getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {
            
            pst.setString(1, p.getName());
            pst.setString(2, p.getDescription());
            pst.setString(3, p.getDiscountType());
            pst.setDouble(4, p.getDiscountValue());
            pst.setTimestamp(5, new Timestamp(p.getStartDate().getTime()));
            pst.setTimestamp(6, new Timestamp(p.getEndDate().getTime()));
            pst.setString(7, p.getConditionType());
            pst.setString(8, p.getConditionValue());
            pst.setString(9, p.getStatus());
            pst.setInt(10, p.getId());
            
            return pst.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean delete(int id) {
        String sql = "DELETE FROM promotions WHERE id=?";
        try (Connection con = DBconn.getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {
            
            pst.setInt(1, id);
            return pst.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
