package quanlykhachsan.backend.DAOimpl;

import quanlykhachsan.backend.DAO.BookingDAO;
import quanlykhachsan.backend.MODEL.Booking;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import quanlykhachsan.DBconn;

public class BookingDAOImpl implements BookingDAO {

    @Override
    public void addBooking(Booking booking) {
        String query = "INSERT INTO bookings(customer_id, room_id, check_in_date, check_out_date, total_price, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, booking.getCustomerId());
            ps.setInt(2, booking.getRoomId());
            ps.setTimestamp(3, new java.sql.Timestamp(booking.getCheckInDate().getTime()));
            ps.setTimestamp(4, new java.sql.Timestamp(booking.getCheckOutDate().getTime()));
            ps.setDouble(5, booking.getTotalPrice());
            ps.setString(6, booking.getStatus());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
}
