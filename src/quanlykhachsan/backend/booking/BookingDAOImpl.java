package quanlykhachsan.backend.booking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.ZoneId;
import quanlykhachsan.backend.utils.DBconn;
import quanlykhachsan.backend.room.RoomAllocationService;
import quanlykhachsan.backend.room.OptimisticLockException;
import quanlykhachsan.backend.room.RentalType;
import quanlykhachsan.backend.room.StayWindow;

public class BookingDAOImpl implements BookingDAO {

    @Override
    public int addBooking(Booking booking) {
        try {
            return addBooking(booking, RoomAllocationService.currentVersion(booking.getRoomId()));
        } catch (OptimisticLockException ex) {
            return -1;
        } catch (java.sql.SQLException ex) {
            ex.printStackTrace();
            return -1;
        }
    }

    public int addBooking(Booking booking, int expectedRoomVersion) throws OptimisticLockException {
        String query = "INSERT INTO bookings(customer_id,room_id,rental_type,check_in_date,check_out_date,buffer_minutes,total_price,status) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection con = quanlykhachsan.backend.utils.DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            ZoneId zone=ZoneId.of("Asia/Ho_Chi_Minh");
            LocalDateTime checkInDay=java.time.Instant.ofEpochMilli(booking.getCheckInDate().getTime()).atZone(zone).toLocalDate().atTime(14,0);
            LocalDateTime checkOutDay=java.time.Instant.ofEpochMilli(booking.getCheckOutDate().getTime()).atZone(zone).toLocalDate().atTime(12,0);
            StayWindow stay=StayWindow.resolve(RentalType.DAILY,checkInDay,checkOutDay);
            int buffer=RoomAllocationService.reserveForStay(con,booking.getRoomId(),expectedRoomVersion,1,stay);
            try (PreparedStatement ps = con.prepareStatement(query, PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, booking.getCustomerId());
                ps.setInt(2, booking.getRoomId());
                ps.setString(3,RentalType.DAILY.databaseValue());
                ps.setTimestamp(4,java.sql.Timestamp.valueOf(stay.checkIn()));
                ps.setTimestamp(5,java.sql.Timestamp.valueOf(stay.checkOut()));
                ps.setInt(6,buffer);
                ps.setBigDecimal(7, BigDecimal.valueOf(booking.getTotalPrice()).setScale(2, RoundingMode.HALF_UP));
                ps.setString(8, booking.getStatus());
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) throw new java.sql.SQLException("Booking insert did not return an ID");
                    int id = rs.getInt(1);
                    booking.setId(id);
                    try (PreparedStatement master = con.prepareStatement(
                            "INSERT INTO folios (booking_id, folio_type, balance, status,code) VALUES (?, 'master', 0.00, 'open',?)")) {
                        master.setInt(1, id);
                        master.setString(2,"FOLIO-MASTER-B"+id);
                        master.executeUpdate();
                    }
                    try (PreparedStatement guest = con.prepareStatement(
                            "INSERT INTO folios (booking_id, folio_type, balance, status,code) VALUES (?, 'guest', 0.00, 'open',?)")) {
                        guest.setInt(1, id);
                        guest.setString(2,"FOLIO-GUEST-B"+id);
                        guest.executeUpdate();
                    }
                    con.commit();
                    return id;
                }
            } catch (Exception ex) {
                con.rollback();
                if (ex instanceof OptimisticLockException) throw (OptimisticLockException) ex;
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    @Override
    public void updateBooking(Booking booking) {
        throw new UnsupportedOperationException("Booking schedule and status changes must use the booking or folio workflow");
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
        String query = "SELECT * FROM bookings ORDER BY created_at DESC, id DESC";
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
                b.setCreatedAt(rs.getTimestamp("created_at"));
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
