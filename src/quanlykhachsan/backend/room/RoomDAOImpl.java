package quanlykhachsan.backend.room;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import quanlykhachsan.backend.utils.DBconn;

public class RoomDAOImpl implements RoomDAO {

    @Override
    public void addRoom(Room room) {
        String query = "INSERT INTO rooms(room_number, room_type_id, price, status) VALUES (?, ?, ?, ?)";
        try (Connection con = DBconn.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement check = con.prepareStatement(
                        "SELECT id FROM rooms WHERE room_number=? FOR UPDATE")) {
                    check.setString(1, room.getRoomNumber());
                    try (ResultSet rows = check.executeQuery()) {
                        if (rows.next()) throw new RoomConflictException("Số phòng đã tồn tại.");
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(query)) {
                    ps.setString(1, room.getRoomNumber());
                    ps.setInt(2, room.getRoomTypeId());
                    ps.setBigDecimal(3, java.math.BigDecimal.valueOf(room.getPrice()));
                    ps.setString(4, room.getStatus());
                    ps.executeUpdate();
                }
                con.commit();
            } catch (Exception ex) {
                con.rollback();
                if (ex instanceof SQLException sql && isConstraintViolation(sql)) {
                    throw new RoomConflictException("Số phòng đã tồn tại hoặc loại phòng không hợp lệ.");
                }
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (RoomConflictException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể thêm phòng do lỗi cơ sở dữ liệu.", ex);
        }
    }

    @Override
    public boolean updateRoom(Room room) {
        String query = "UPDATE rooms SET room_number=?, room_type_id=?, price=?, version=version+1 WHERE id=?";
        try (Connection con = DBconn.getConnection()) {
            con.setAutoCommit(false);
            try {
                String currentStatus;
                try (PreparedStatement lock = con.prepareStatement(
                        "SELECT status FROM rooms WHERE id=? FOR UPDATE")) {
                    lock.setInt(1, room.getId());
                    try (ResultSet rows = lock.executeQuery()) {
                        if (!rows.next()) {
                            con.rollback();
                            return false;
                        }
                        currentStatus = rows.getString("status");
                    }
                }
                if (hasCheckedInBooking(con, room.getId())) {
                    throw new RoomConflictException("Không thể sửa thông tin phòng đang có khách lưu trú.");
                }
                try (PreparedStatement check = con.prepareStatement(
                        "SELECT id FROM rooms WHERE room_number=? AND id<>? FOR UPDATE")) {
                    check.setString(1, room.getRoomNumber());
                    check.setInt(2, room.getId());
                    try (ResultSet rows = check.executeQuery()) {
                        if (rows.next()) throw new RoomConflictException("Số phòng đã tồn tại.");
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(query)) {
                    ps.setString(1, room.getRoomNumber());
                    ps.setInt(2, room.getRoomTypeId());
                    ps.setBigDecimal(3, java.math.BigDecimal.valueOf(room.getPrice()));
                    ps.setInt(4, room.getId());
                    boolean updated = ps.executeUpdate() == 1;
                    con.commit();
                    return updated;
                }
            } catch (Exception ex) {
                con.rollback();
                if (ex instanceof SQLException sql && isConstraintViolation(sql)) {
                    throw new RoomConflictException("Số phòng đã tồn tại hoặc loại phòng không hợp lệ.");
                }
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (RoomConflictException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể cập nhật phòng do lỗi cơ sở dữ liệu.", ex);
        }
    }

    @Override
    public boolean deleteRoom(Room room) {
        try (Connection con = DBconn.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement lock = con.prepareStatement("SELECT id FROM rooms WHERE id=? FOR UPDATE")) {
                    lock.setInt(1, room.getId());
                    try (ResultSet rows = lock.executeQuery()) {
                        if (!rows.next()) {
                            con.rollback();
                            return false;
                        }
                    }
                }
                try (PreparedStatement bookings = con.prepareStatement(
                        "SELECT id FROM bookings WHERE room_id=? LIMIT 1 FOR UPDATE")) {
                    bookings.setInt(1, room.getId());
                    try (ResultSet rows = bookings.executeQuery()) {
                        if (rows.next()) {
                            throw new RoomConflictException(
                                "Không thể xóa phòng đã có đặt phòng hoặc lịch sử lưu trú.");
                        }
                    }
                }
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM rooms WHERE id=?")) {
                    ps.setInt(1, room.getId());
                    boolean deleted = ps.executeUpdate() == 1;
                    con.commit();
                    return deleted;
                }
            } catch (Exception ex) {
                con.rollback();
                if (ex instanceof SQLException sql && isConstraintViolation(sql)) {
                    throw new RoomConflictException("Không thể xóa phòng đang được dữ liệu khác tham chiếu.");
                }
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (RoomConflictException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể xóa phòng do lỗi cơ sở dữ liệu.", ex);
        }
    }

    @Override
    public ArrayList<Room> selectRoom() {
        ArrayList<Room> list = new ArrayList<>();
        String query = "SELECT r.*, t.name AS type_name FROM rooms r "
                + "JOIN room_types t ON t.id=r.room_type_id ORDER BY r.id";
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
                r.setVersion(rs.getInt("version"));
                r.setTypeName(rs.getString("type_name"));
                list.add(r);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tải danh sách phòng từ cơ sở dữ liệu.", e);
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
        String sql = "UPDATE rooms SET status=?, version=version+1 WHERE id=?";
        try (Connection con = quanlykhachsan.backend.utils.DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, roomId);
            return ps.executeUpdate() == 1;
        } catch (Exception e) {
            return false;
        }
    }
    @Override
    public java.util.List<Room> findAvailableRooms(java.util.Date checkIn, java.util.Date checkOut) {
        java.util.List<Room> list = new java.util.ArrayList<>();
        String query = "SELECT r.*, t.name AS type_name FROM rooms r JOIN room_types t ON t.id=r.room_type_id " +
                       "WHERE r.status NOT IN ('out_of_order','out_of_service') " +
                       "AND NOT EXISTS (SELECT 1 FROM bookings b WHERE b.room_id=r.id " +
                       "AND b.status IN ('confirmed','checked_in','pending','checked_out') " +
                       "AND b.check_in_date<DATE_ADD(?, INTERVAL r.buffer_daily MINUTE) AND b.room_release_at>?)";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setTimestamp(1, new java.sql.Timestamp(checkOut.getTime()));
            ps.setTimestamp(2, new java.sql.Timestamp(checkIn.getTime()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Room r = new Room();
                    r.setId(rs.getInt("id"));
                    r.setRoomNumber(rs.getString("room_number"));
                    r.setRoomTypeId(rs.getInt("room_type_id"));
                    r.setPrice(rs.getDouble("price"));
                    r.setStatus(rs.getString("status"));
                    r.setTypeName(rs.getString("type_name"));
                    list.add(r);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tìm phòng trống trong cơ sở dữ liệu.", e);
        }
        return list;
    }

    private static boolean hasCheckedInBooking(Connection connection, int roomId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT id FROM bookings WHERE room_id=? AND status='checked_in' LIMIT 1 FOR UPDATE")) {
            query.setInt(1, roomId);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static boolean isConstraintViolation(SQLException exception) {
        return exception.getSQLState() != null && exception.getSQLState().startsWith("23");
    }
}
