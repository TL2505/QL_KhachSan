package quanlykhachsan.backend.interaction;

import quanlykhachsan.backend.interaction.ReviewDAO;
import quanlykhachsan.backend.interaction.Review;
import quanlykhachsan.backend.utils.DBconn;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReviewDAOImpl implements ReviewDAO {

    @Override
    public List<Review> findAll() {
        List<Review> list = new ArrayList<>();
        String sql = "SELECT r.*, c.full_name FROM reviews r JOIN customers c ON r.customer_id = c.id ORDER BY r.created_at DESC";
        try (Connection con = DBconn.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public List<Review> findByRoomId(int roomId) {
        List<Review> list = new ArrayList<>();
        String sql = "SELECT r.*, c.full_name FROM reviews r JOIN customers c ON r.customer_id = c.id WHERE r.room_id = ? ORDER BY r.created_at DESC";
        try (Connection con = DBconn.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public boolean insert(Review review) {
        String sql = "INSERT INTO reviews (customer_id, room_id, rating, comment) VALUES (?, ?, ?, ?)";
        try (Connection con = DBconn.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, review.getCustomerId());
            ps.setInt(2, review.getRoomId());
            ps.setInt(3, review.getRating());
            ps.setString(4, review.getComment());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean delete(int id) {
        String sql = "DELETE FROM reviews WHERE id = ?";
        try (Connection con = DBconn.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private Review mapResultSet(ResultSet rs) throws SQLException {
        Review r = new Review();
        r.setId(rs.getInt("id"));
        r.setCustomerId(rs.getInt("customer_id"));
        r.setRoomId(rs.getInt("room_id"));
        r.setRating(rs.getInt("rating"));
        r.setComment(rs.getString("comment"));
        r.setCreatedAt(rs.getTimestamp("created_at"));
        r.setCustomerName(rs.getString("full_name"));
        return r;
    }
}
