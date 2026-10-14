package quanlykhachsan.backend.room;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import quanlykhachsan.backend.system.SystemStateHolder;
import quanlykhachsan.backend.utils.DBconn;
import quanlykhachsan.backend.utils.DatabaseConnection;

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
    @Override
    public java.util.List<Room> findAvailableRooms(java.util.Date checkIn, java.util.Date checkOut) {
        java.util.List<Room> list = new java.util.ArrayList<>();
        String query = "SELECT * FROM rooms r " +
                       "WHERE r.status != 'out_of_order' " +
                       "AND r.id NOT IN (" +
                       "  SELECT b.room_id FROM bookings b " +
                       "  WHERE b.status IN ('confirmed', 'checked_in', 'pending') " +
                       "  AND NOT (b.check_out_date <= ? OR b.check_in_date >= ?)" +
                       ")";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setTimestamp(1, new java.sql.Timestamp(checkIn.getTime()));
            ps.setTimestamp(2, new java.sql.Timestamp(checkOut.getTime()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Room r = new Room();
                    r.setId(rs.getInt("id"));
                    r.setRoomNumber(rs.getString("room_number"));
                    r.setRoomTypeId(rs.getInt("room_type_id"));
                    r.setPrice(rs.getDouble("price"));
                    r.setStatus(rs.getString("status"));
                    list.add(r);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public List<Map<String, Object>> getAllRooms() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT r.id, r.room_number, r.status, r.version, r.room_type_id, rt.name AS room_type_name, rt.base_price, rt.capacity " +
                     "FROM rooms r " +
                     "JOIN room_types rt ON r.room_type_id = rt.id " +
                     "ORDER BY r.room_number ASC";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", rs.getInt("id"));
                map.put("room_number", rs.getString("room_number"));
                map.put("status", rs.getString("status"));
                map.put("version", rs.getInt("version"));
                map.put("room_type_id", rs.getInt("room_type_id"));
                map.put("room_type_name", rs.getString("room_type_name"));
                map.put("base_price", rs.getDouble("base_price"));
                map.put("capacity", rs.getInt("capacity"));
                list.add(map);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error fetching all rooms: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public List<Map<String, Object>> getAvailableRooms(String checkIn, String checkOut) {
        if (checkIn == null || checkOut == null || checkIn.isBlank() || checkOut.isBlank()) {
            throw new IllegalArgumentException("check_in and check_out are required");
        }
        String ci = checkIn.trim();
        String co = checkOut.trim();
        if (ci.compareTo(co) >= 0) {
            throw new IllegalArgumentException("Check-in date must be before check-out date");
        }

        LocalDate today = SystemStateHolder.getCurrentBusinessDate();
        LocalDate checkInDate;
        try {
            checkInDate = LocalDate.parse(ci.length() >= 10 ? ci.substring(0, 10) : ci);
        } catch (Exception e) {
            checkInDate = today;
        }

        // Standard:
        // - If check-in date is today (or past/same-day): Require r.status = 'vacant_clean'.
        // - If check-in date is in the future: Only require r.status != 'out_of_order'
        //   and ensure no schedule conflict in the bookings table.
        boolean isFuture = checkInDate.isAfter(today);
        String statusFilter = isFuture
                ? "r.status != 'out_of_order'"
                : "r.status = 'vacant_clean'";

        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT r.id, r.room_number, r.status, r.version, r.room_type_id, rt.name AS room_type_name, rt.base_price, rt.capacity " +
                     "FROM rooms r " +
                     "JOIN room_types rt ON r.room_type_id = rt.id " +
                     "WHERE " + statusFilter + " " +
                     "AND NOT EXISTS ( " +
                     "    SELECT 1 FROM bookings b " +
                     "    WHERE b.room_id = r.id " +
                     "    AND b.status IN ('confirmed', 'checked_in', 'reserved') " +
                     "    AND NOT (b.check_out_date <= ? OR b.check_in_date >= ?) " +
                     ") " +
                     "ORDER BY r.room_number ASC";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            String formattedCheckIn = ci.length() == 10 ? ci + " 14:00:00" : ci;
            String formattedCheckOut = co.length() == 10 ? co + " 12:00:00" : co;
            ps.setString(1, formattedCheckIn);
            ps.setString(2, formattedCheckOut);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", rs.getInt("id"));
                    map.put("room_number", rs.getString("room_number"));
                    map.put("status", rs.getString("status"));
                    map.put("version", rs.getInt("version"));
                    map.put("room_type_id", rs.getInt("room_type_id"));
                    map.put("room_type_name", rs.getString("room_type_name"));
                    map.put("base_price", rs.getDouble("base_price"));
                    map.put("capacity", rs.getInt("capacity"));
                    list.add(map);
                }
            }
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new RuntimeException("Error fetching available rooms: " + e.getMessage(), e);
        }
        return list;
    }
}
