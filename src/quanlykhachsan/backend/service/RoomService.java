package quanlykhachsan.backend.service;

import quanlykhachsan.backend.dao.RoomTypeDAO;
import quanlykhachsan.backend.daoimpl.RoomTypeDAOImpl;
import quanlykhachsan.backend.model.Room;
import quanlykhachsan.backend.model.RoomType;

import java.util.List;
import quanlykhachsan.backend.dao.RoomDAO;
import quanlykhachsan.backend.daoimpl.RoomDAOImpl;

public class RoomService {

    private RoomDAO roomDAO = new RoomDAOImpl();
    private RoomTypeDAO roomTypeDAO = new RoomTypeDAOImpl();

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

    public List<RoomType> getAllRoomTypes() {
        return roomTypeDAO.findAll();
    }

    public RoomType getRoomTypeById(int id) {
        return roomTypeDAO.findById(id);
    }

    public List<Room> findAvailableRooms(java.util.Date checkIn, java.util.Date checkOut) {
        return roomDAO.findAvailableRooms(checkIn, checkOut);
    }
}