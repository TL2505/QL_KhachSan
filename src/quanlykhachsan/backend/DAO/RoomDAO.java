package quanlykhachsan.backend.DAO;

import quanlykhachsan.backend.MODEL.Room;
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

}
