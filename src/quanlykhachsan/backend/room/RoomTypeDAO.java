package quanlykhachsan.backend.room;

import quanlykhachsan.backend.room.RoomType;
import java.util.List;

public interface RoomTypeDAO {
    List<RoomType> findAll();
    RoomType findById(int id);
}
