package quanlykhachsan.backend.interaction;

import quanlykhachsan.backend.interaction.Review;
import java.util.List;

public interface ReviewDAO {
    List<Review> findAll();
    List<Review> findByRoomId(int roomId);
    boolean insert(Review review);
    boolean delete(int id);
}
