package quanlykhachsan.backend.service;

import quanlykhachsan.backend.dao.RoomTypeDAO;
import quanlykhachsan.backend.daoimpl.RoomTypeDAOImpl;
import quanlykhachsan.backend.model.RoomType;
import java.util.List;

public class RoomTypeService {
    private RoomTypeDAO roomTypeDAO = new RoomTypeDAOImpl();

    public List<RoomType> getAllRoomTypes() {
        return roomTypeDAO.findAll();
    }

    public RoomType getRoomTypeById(int id) {
        return roomTypeDAO.findById(id);
    }
}
