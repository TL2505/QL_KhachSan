package quanlykhachsan.backend.room;

import quanlykhachsan.backend.room.Room;
import java.util.ArrayList;

public interface RoomDAO {

//    add Room
    public void addRoom(Room room);

//    update Room
    public void updateRoom(Room room);

//    delete Room
    public void deleteRoom(Room room);

//    list of Room
    public ArrayList<Room> selectRoom();

    public void comboBoxRoom();

    public java.util.List<Room> findAll();
    public Room findById(int id);
    public boolean updateStatus(int roomId, String status);
    public java.util.List<Room> findAvailableRooms(java.util.Date checkIn, java.util.Date checkOut);
}
