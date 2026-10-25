package quanlykhachsan.backend.room;

import java.util.List;

public interface RoomTypeDAO {
    List<RoomType> findAll();
    RoomType findById(int id);
    boolean create(RoomType roomType);
    boolean update(RoomType roomType);
    boolean delete(int id);
}
