package quanlykhachsan.backend.promotion;

import quanlykhachsan.backend.promotion.Promotion;
import java.util.List;

public interface PromotionDAO {
    List<Promotion> selectAll();
    Promotion findById(int id);
    boolean insert(Promotion promotion);
    boolean update(Promotion promotion);
    boolean delete(int id);
}
