package quanlykhachsan.backend.interaction;

import java.util.List;

public class ReviewService {
    private ReviewDAO reviewDAO = new ReviewDAOImpl();

    public List<Review> getAllReviews() {
        return reviewDAO.findAll();
    }

    public List<Review> getReviewsByRoom(int roomId) {
        return reviewDAO.findByRoomId(roomId);
    }

    public boolean addReview(Review review) {
        return reviewDAO.insert(review);
    }

    public boolean deleteReview(int id) {
        return reviewDAO.delete(id);
    }
}
