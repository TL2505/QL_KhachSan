package quanlykhachsan.backend.interaction;

import com.google.gson.JsonObject;
import java.sql.SQLException;
import java.util.List;

public interface ReviewDAO {
    List<Review> findAll();
    List<Review> findByRoomId(int roomId);
    boolean insert(Review review);
    boolean delete(int id);

    // Phase 2.1 All-In-One Reputation methods
    Review submitVerifiedReview(Review review) throws SQLException;
    boolean saveManagementReply(int reviewId, String reply, int userId) throws SQLException;
    JsonObject getPublicReputationSummary(int page, int limit) throws SQLException;
    Review findById(int id);
}
