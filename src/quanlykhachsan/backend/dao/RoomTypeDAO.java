package quanlykhachsan.backend.dao;

import quanlykhachsan.backend.model.RoomType;
import java.util.List;

public interface RoomTypeDAO {
    List<RoomType> findAll();
    RoomType findById(int id);
}
