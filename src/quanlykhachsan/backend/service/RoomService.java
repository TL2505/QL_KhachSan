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

    public boolean addRoom(Room room) {
        try {
            roomDAO.addRoom(room);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteRoom(int roomId) {
        try {
            Room r = roomDAO.findById(roomId);
            if (r != null) {
                roomDAO.deleteRoom(r);
                return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}