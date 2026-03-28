package quanlykhachsan.backend.dao;

import quanlykhachsan.backend.model.RoomType;
import java.util.List;

public interface RoomTypeDAO {
    public List<RoomType> findAll();
    public RoomType findById(int id);
}
