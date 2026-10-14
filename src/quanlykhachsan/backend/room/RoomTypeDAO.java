package quanlykhachsan.backend.room;

import java.util.List;

public interface RoomTypeDAO {
    List<RoomType> findAll();
    RoomType findById(int id);
}
