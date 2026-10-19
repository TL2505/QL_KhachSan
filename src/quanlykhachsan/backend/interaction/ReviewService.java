package quanlykhachsan.backend.interaction;

import com.google.gson.JsonObject;
import java.sql.SQLException;
import java.util.List;

public class ReviewService {
    private final ReviewDAO reviewDAO = new ReviewDAOImpl();

    public List<Review> getAllReviews() {
        return reviewDAO.findAll();
    }

    public List<Review> getReviewsByRoom(int roomId) {
        return reviewDAO.findByRoomId(roomId);
    }

    public Review getReviewById(int id) {
        return reviewDAO.findById(id);
    }

    public boolean addReview(Review review) {
        return reviewDAO.insert(review);
    }

    public boolean deleteReview(int id) {
        return reviewDAO.delete(id);
    }

    public Review submitVerifiedReview(Review review) throws SQLException {
        return reviewDAO.submitVerifiedReview(review);
    }

    public boolean saveManagementReply(int reviewId, String reply, int userId) throws SQLException {
        return reviewDAO.saveManagementReply(reviewId, reply, userId);
    }

    public JsonObject getPublicReputationSummary(int page, int limit) throws SQLException {
        return reviewDAO.getPublicReputationSummary(page, limit);
    }
}
