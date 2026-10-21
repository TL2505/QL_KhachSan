package quanlykhachsan.backend.customer;

import quanlykhachsan.backend.utils.DBconn;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class LoyaltyHistoryDAOImpl implements LoyaltyHistoryDAO {

    @Override
    public void addHistory(LoyaltyHistory history) {
        String query = "INSERT INTO loyalty_histories (customer_id, points, type, description) VALUES (?, ?, ?, ?)";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, history.getCustomerId());
            ps.setInt(2, history.getPointsChange());
            ps.setString(3, history.getType());
            ps.setString(4, history.getDescription());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Không thể lưu lịch sử điểm.", e);
        }
    }

    @Override
    public List<LoyaltyHistory> findByCustomerId(int customerId) {
        List<LoyaltyHistory> list = new ArrayList<>();
        String query = "SELECT * FROM loyalty_histories WHERE customer_id = ? ORDER BY created_at DESC, id DESC";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LoyaltyHistory h = new LoyaltyHistory();
                    h.setId(rs.getInt("id"));
                    h.setCustomerId(rs.getInt("customer_id"));
                    h.setPointsChange(rs.getInt("points"));
                    h.setType(rs.getString("type"));
                    h.setDescription(rs.getString("description"));
                    h.setCreatedAt(rs.getTimestamp("created_at"));
                    list.add(h);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tải lịch sử điểm.", e);
        }
        return list;
    }

    @Override
    public List<LoyaltyHistory> findAll() {
        List<LoyaltyHistory> list = new ArrayList<>();
        String query = "SELECT * FROM loyalty_histories ORDER BY created_at DESC, id DESC";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                LoyaltyHistory h = new LoyaltyHistory();
                h.setId(rs.getInt("id"));
                h.setCustomerId(rs.getInt("customer_id"));
                h.setPointsChange(rs.getInt("points"));
                h.setType(rs.getString("type"));
                h.setDescription(rs.getString("description"));
                h.setCreatedAt(rs.getTimestamp("created_at"));
                list.add(h);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tải lịch sử điểm.", e);
        }
        return list;
    }
}
