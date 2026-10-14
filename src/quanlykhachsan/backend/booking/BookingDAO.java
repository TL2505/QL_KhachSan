package quanlykhachsan.backend.booking;

import java.util.ArrayList;

public interface BookingDAO {

//    add Booking
    public int addBooking(Booking booking);

//    update Booking
    public void updateBooking(Booking booking);

//    delete Booking
    public void deleteBooking(Booking booking);

//    list of Booking
    public ArrayList<Booking> selectBooking();

    public void comboBoxBooking();

    public java.util.List<Booking> findByRoomId(int roomId);
    public java.util.List<Booking> findByCustomerId(int customerId);
    public boolean insert(Booking booking);

    // TASK-A2.3 & TASK-A2.6: Booking Lifecycle & Optimistic Locking Integration
    public int createBookingAtomic(int customerId, int roomId, int roomVersion, String checkInDate, String checkOutDate, double totalAmount) throws java.sql.SQLException, quanlykhachsan.backend.utils.OptimisticLockException;
    public boolean updateBookingAtomic(int bookingId, int roomVersion, String newCheckInDate, String newCheckOutDate, Double newTotalAmount) throws java.sql.SQLException, quanlykhachsan.backend.utils.OptimisticLockException;
    public boolean cancelBooking(int bookingId) throws java.sql.SQLException;
    public java.util.List<java.util.Map<String, Object>> getAllBookings();
    public java.util.Map<String, Object> getBookingById(int id);
}
