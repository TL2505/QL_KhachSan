package quanlykhachsan.backend.booking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import quanlykhachsan.backend.utils.DBconn;
import quanlykhachsan.backend.room.RoomAllocationService;
import quanlykhachsan.backend.room.OptimisticLockException;

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
        String query = "INSERT INTO bookings(customer_id, room_id, check_in_date, check_out_date, total_price, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection con = quanlykhachsan.backend.utils.DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            RoomAllocationService.reserve(con, booking.getRoomId(), expectedRoomVersion);
            try (PreparedStatement ps = con.prepareStatement(query, PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, booking.getCustomerId());
                ps.setInt(2, booking.getRoomId());
                ps.setTimestamp(3, new java.sql.Timestamp(booking.getCheckInDate().getTime()));
                ps.setTimestamp(4, new java.sql.Timestamp(booking.getCheckOutDate().getTime()));
                ps.setBigDecimal(5, BigDecimal.valueOf(booking.getTotalPrice()).setScale(2, RoundingMode.HALF_UP));
                ps.setString(6, booking.getStatus());
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) throw new java.sql.SQLException("Booking insert did not return an ID");
                    int id = rs.getInt(1);
                    booking.setId(id);
                    java.math.BigDecimal roomBalance = java.math.BigDecimal.valueOf(booking.getTotalPrice())
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                    try (PreparedStatement master = con.prepareStatement(
                            "INSERT INTO folios (booking_id, folio_type, balance, status) VALUES (?, 'master', ?, 'open')",
                         PreparedStatement.RETURN_GENERATED_KEYS)) {
                        master.setInt(1, id);
                        master.setBigDecimal(2, roomBalance);
                        master.executeUpdate();
                        try (ResultSet masterId = master.getGeneratedKeys()) {
                            if (!masterId.next()) throw new java.sql.SQLException("Master folio insert did not return an ID");
                            try (PreparedStatement transaction = con.prepareStatement(
                                    "INSERT INTO folio_transactions (folio_id, transaction_type, charge_type, description, amount, business_date, reference_id)"
                                  + " VALUES (?, 'debit', 'room_rate', 'Room charge', ?, ?, ?)")) {
                                transaction.setInt(1, masterId.getInt(1));
                                transaction.setBigDecimal(2, roomBalance);
                                transaction.setDate(3, java.sql.Date.valueOf(java.time.LocalDate.now(
                                    java.time.ZoneId.of("Asia/Ho_Chi_Minh"))));
                                transaction.setString(4, "BOOKING-" + id + "-ROOM-RATE");
                                transaction.executeUpdate();
                            }
                        }
                    }
                    try (PreparedStatement guest = con.prepareStatement(
                            "INSERT INTO folios (booking_id, folio_type, balance, status) VALUES (?, 'guest', 0.00, 'open')")) {
                        guest.setInt(1, id);
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
        String query = "UPDATE bookings SET customer_id=?, room_id=?, check_in_date=?, check_out_date=?, total_price=?, status=? WHERE id=?";
        try (Connection con = DBconn.getConnection(); 
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, booking.getCustomerId());
            ps.setInt(2, booking.getRoomId());
            ps.setTimestamp(3, new java.sql.Timestamp(booking.getCheckInDate().getTime()));
            ps.setTimestamp(4, new java.sql.Timestamp(booking.getCheckOutDate().getTime()));
            ps.setBigDecimal(5, BigDecimal.valueOf(booking.getTotalPrice()).setScale(2, RoundingMode.HALF_UP));
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
