package quanlykhachsan.backend.daoimpl;

import quanlykhachsan.backend.dao.RoomDAO;
import quanlykhachsan.backend.model.Room;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import quanlykhachsan.backend.utils.DBconn;

public class RoomDAOImpl implements RoomDAO {

    @Override
    public void addRoom(Room room) {
        String query = "INSERT INTO rooms(room_number, room_type_id, price, status) VALUES (?, ?, ?, ?)";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, room.getRoomNumber());
            ps.setInt(2, room.getRoomTypeId());
            ps.setDouble(3, room.getPrice());
            ps.setString(4, room.getStatus());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updateRoom(Room room) {
        String query = "UPDATE rooms SET room_number=?, room_type_id=?, price=?, status=? WHERE id=?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, room.getRoomNumber());
            ps.setInt(2, room.getRoomTypeId());
            ps.setDouble(3, room.getPrice());
            ps.setString(4, room.getStatus());
            ps.setInt(5, room.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteRoom(Room room) {
        String query = "DELETE FROM rooms WHERE id=?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, room.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public ArrayList<Room> selectRoom() {
        ArrayList<Room> list = new ArrayList<>();
        String query = "SELECT * FROM rooms";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Room r = new Room();
                r.setId(rs.getInt("id"));
                r.setRoomNumber(rs.getString("room_number"));
                r.setRoomTypeId(rs.getInt("room_type_id"));
                r.setPrice(rs.getDouble("price"));
                r.setStatus(rs.getString("status"));
                list.add(r);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public void comboBoxRoom() {
    }
    @Override
    public java.util.List<Room> findAll() {
        return selectRoom();
    }

    @Override
    public Room findById(int id) {
        for (Room r : selectRoom()) {
            if (r.getId() == id) return r;
        }
        return null;
    }

    @Override
    public boolean updateStatus(int roomId, String status) {
        try {
            Room r = findById(roomId);
            if (r != null) {
                r.setStatus(status);
                updateRoom(r);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
