package quanlykhachsan.backend.service;

import quanlykhachsan.backend.dao.RoomDAO;
import quanlykhachsan.backend.daoimpl.RoomDAOImpl;
import quanlykhachsan.backend.model.Room;

import java.util.List;

public class RoomService {

    private RoomDAO roomDAO = new RoomDAOImpl();

    public List<Room> getAllRooms() {
        return roomDAO.findAll();
    }

    public Room getRoomById(int id) {
        return roomDAO.findById(id);
    }

    public boolean updateRoomStatus(int roomId, String status) {
        return roomDAO.updateStatus(roomId, status);
    }
}