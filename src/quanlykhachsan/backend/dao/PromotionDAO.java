package quanlykhachsan.backend.dao;

import quanlykhachsan.backend.model.Promotion;
import java.util.List;

public interface PromotionDAO {
    List<Promotion> selectAll();
    Promotion findById(int id);
    boolean insert(Promotion promotion);
    boolean update(Promotion promotion);
    boolean delete(int id);
}
