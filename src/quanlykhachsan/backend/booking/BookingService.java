package quanlykhachsan.backend.booking;

import quanlykhachsan.backend.room.RoomDAOImpl;
import quanlykhachsan.backend.promotion.Promotion;
import quanlykhachsan.backend.customer.Customer;
import quanlykhachsan.backend.room.Room;
import quanlykhachsan.backend.customer.CustomerDAOImpl;
import quanlykhachsan.backend.promotion.PromotionService;
import quanlykhachsan.backend.room.OptimisticLockException;

import java.util.List;

public class BookingService {

    private BookingDAO bookingDAO = new BookingDAOImpl();
    private PromotionService promotionService = new PromotionService();
    private CustomerDAOImpl customerDAO = new CustomerDAOImpl();
    private RoomDAOImpl roomDAO = new RoomDAOImpl();

    public List<Booking> getAllBookings() {
        return bookingDAO.selectBooking();
    }

    public boolean checkAvailable(int roomId, java.util.Date checkIn, java.util.Date checkOut) {
        java.time.ZoneId zone=java.time.ZoneId.of("Asia/Ho_Chi_Minh");
        java.time.LocalDateTime in=java.time.Instant.ofEpochMilli(checkIn.getTime()).atZone(zone).toLocalDate().atTime(14,0);
        java.time.LocalDateTime out=java.time.Instant.ofEpochMilli(checkOut.getTime()).atZone(zone).toLocalDate().atTime(12,0);
        try(java.sql.Connection c=quanlykhachsan.backend.utils.DatabaseConnection.getConnection()) {
            int buffer;
            try(java.sql.PreparedStatement q=c.prepareStatement("SELECT buffer_daily FROM rooms WHERE id=?")) {
                q.setInt(1,roomId);try(java.sql.ResultSet r=q.executeQuery()){if(!r.next())return false;buffer=r.getInt(1);}
            }
            java.time.LocalDateTime release=out.plusMinutes(buffer);
            try(java.sql.PreparedStatement q=c.prepareStatement(
                    "SELECT id FROM bookings WHERE room_id=? AND status IN ('pending','confirmed','checked_in','checked_out') AND check_in_date<? AND room_release_at>? LIMIT 1")) {
                q.setInt(1,roomId);q.setTimestamp(2,java.sql.Timestamp.valueOf(release));q.setTimestamp(3,java.sql.Timestamp.valueOf(in));
                try(java.sql.ResultSet r=q.executeQuery()){return !r.next();}
            }
        } catch(java.sql.SQLException ex) { return false; }
    }

    public int addBooking(Booking booking) {
        if (!checkAvailable(
                booking.getRoomId(),
                booking.getCheckInDate(),
                booking.getCheckOutDate())) {
            return -1;
        }
        return bookingDAO.addBooking(booking);
    }

    public int addBooking(Booking booking, int expectedRoomVersion) throws OptimisticLockException {
        if (!checkAvailable(booking.getRoomId(), booking.getCheckInDate(), booking.getCheckOutDate())) {
            return -1;
        }
        return ((BookingDAOImpl) bookingDAO).addBooking(booking, expectedRoomVersion);
    }

    public List<Booking> getBookingsByCustomer(int customerId) {
        return bookingDAO.findByCustomerId(customerId);
    }

    public Booking getBookingById(int id) {
        for (Booking b : bookingDAO.selectBooking()) {
            if (b.getId() == id) return b;
        }
        return null;
    }

    public boolean updateBookingStatus(int bookingId,String status){
        try{return new AdminBookingStatusService().decide(bookingId,status).httpStatus()==200;}
        catch(java.sql.SQLException ex){throw new IllegalStateException("Booking state change was not confirmed",ex);}
    }
    public boolean cancelBooking(int bookingId){return updateBookingStatus(bookingId,"cancelled");}

    /** All payments must use the transactional folio ledger. */
    public boolean processPayment(int bookingId, double amount, String method) {
        throw new IllegalStateException("Use FolioService with an authorized collection and Idempotency-Key");
    }
    public com.google.gson.JsonObject getBookingFolioDetails(Booking booking) throws java.sql.SQLException {
        com.google.gson.JsonObject result = quanlykhachsan.backend.utils.JsonUtil.getGson()
            .toJsonTree(booking).getAsJsonObject();
        com.google.gson.JsonArray folioRows = new com.google.gson.JsonArray();
        String sql = "SELECT id, folio_type, balance, status FROM folios WHERE booking_id = ? ORDER BY id";
        try (java.sql.Connection connection = quanlykhachsan.backend.utils.DatabaseConnection.getConnection();
             java.sql.PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, booking.getId());
            try (java.sql.ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    com.google.gson.JsonObject folio = new com.google.gson.JsonObject();
                    folio.addProperty("id", rows.getLong("id"));
                    folio.addProperty("folio_type", rows.getString("folio_type"));
                    folio.addProperty("balance", rows.getBigDecimal("balance"));
                    folio.addProperty("status", rows.getString("status"));
                    folioRows.add(folio);
                }
            }
            var payment=quanlykhachsan.backend.payment.PaymentStatus.forBooking(connection,booking.getId());result.add("payment",payment);result.addProperty("paymentStatus",payment.get("payment_status").getAsString());
        }
        result.add("folios", folioRows);
        return result;
    }
}
