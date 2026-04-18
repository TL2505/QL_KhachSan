package quanlykhachsan.backend.daoimpl;

import quanlykhachsan.backend.dao.BookingDAO;
import quanlykhachsan.backend.model.Booking;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import quanlykhachsan.backend.utils.DBconn;

public class BookingDAOImpl implements BookingDAO {

    @Override
    public int addBooking(Booking booking) {
        String query = "INSERT INTO bookings(customer_id, room_id, check_in_date, check_out_date, total_price, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, booking.getCustomerId());
            ps.setInt(2, booking.getRoomId());
            ps.setTimestamp(3, new java.sql.Timestamp(booking.getCheckInDate().getTime()));
            ps.setTimestamp(4, new java.sql.Timestamp(booking.getCheckOutDate().getTime()));
            ps.setDouble(5, booking.getTotalPrice());
            ps.setString(6, booking.getStatus());
            ps.executeUpdate();
            
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                int id = rs.getInt(1);
                booking.setId(id);
                return id;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    @Override
    public void updateBooking(Booking booking) {
        String query = "UPDATE bookings SET customer_id=?, room_id=?, check_in_date=?, check_out_date=?, total_price=?, status=? WHERE id=?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, booking.getCustomerId());
            ps.setInt(2, booking.getRoomId());
            ps.setTimestamp(3, new java.sql.Timestamp(booking.getCheckInDate().getTime()));
            ps.setTimestamp(4, new java.sql.Timestamp(booking.getCheckOutDate().getTime()));
            ps.setDouble(5, booking.getTotalPrice());
            ps.setString(6, booking.getStatus());
            ps.setInt(7, booking.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteBooking(Booking booking) {
        String query = "DELETE FROM bookings WHERE id=?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, booking.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public ArrayList<Booking> selectBooking() {
        ArrayList<Booking> list = new ArrayList<>();
        String query = "SELECT * FROM bookings";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Booking b = new Booking();
                b.setId(rs.getInt("id"));
                b.setCustomerId(rs.getInt("customer_id"));
                b.setRoomId(rs.getInt("room_id"));
                b.setCheckInDate(rs.getTimestamp("check_in_date"));
                b.setCheckOutDate(rs.getTimestamp("check_out_date"));
                b.setTotalPrice(rs.getDouble("total_price"));
                b.setStatus(rs.getString("status"));
                list.add(b);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public void comboBoxBooking() {
    }
    @Override
    public java.util.List<Booking> findByRoomId(int roomId) {
        java.util.List<Booking> list = new java.util.ArrayList<>();
        for (Booking b : selectBooking()) {
            if (b.getRoomId() == roomId) list.add(b);
        }
        return list;
    }

    @Override
    public java.util.List<Booking> findByCustomerId(int customerId) {
        java.util.List<Booking> list = new java.util.ArrayList<>();
        for (Booking b : selectBooking()) {
            if (b.getCustomerId() == customerId) list.add(b);
        }
        return list;
    }

    @Override
    public boolean insert(Booking booking) {
        return addBooking(booking) > 0;
    }
}
