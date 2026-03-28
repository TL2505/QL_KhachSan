package quanlykhachsan.backend.service;

import quanlykhachsan.backend.dao.BookingDAO;
import quanlykhachsan.backend.daoimpl.BookingDAOImpl;
import quanlykhachsan.backend.model.Booking;

import java.util.List;

public class BookingService {

    private BookingDAO bookingDAO = new BookingDAOImpl();

    public boolean checkAvailable(int roomId, java.util.Date checkIn, java.util.Date checkOut) {
        List<Booking> bookings = bookingDAO.findByRoomId(roomId);

        for (Booking b : bookings) {
            // Chỉ kiểm tra trùng lặp với các booking chưa hoàn tất
            if ("checked_out".equals(b.getStatus())) continue;

            boolean overlap = checkIn.getTime() < b.getCheckOutDate().getTime() &&
                              checkOut.getTime() > b.getCheckInDate().getTime();
            if (overlap) return false;
        }
        return true;
    }

    public int createBooking(Booking booking) {
        if (!checkAvailable(
                booking.getRoomId(),
                booking.getCheckInDate(),
                booking.getCheckOutDate())) {
            return -1;
        }

        return bookingDAO.addBooking(booking);
    }

    public List<Booking> getBookingsByCustomer(int customerId) {
        return bookingDAO.findByCustomerId(customerId);
    }

    public List<Booking> getAllBookings() {
        return bookingDAO.selectBooking();
    }

    public Booking getBookingById(int id) {
        for (Booking b : bookingDAO.selectBooking()) {
            if (b.getId() == id) return b;
        }
        return null;
    }

    public boolean updateBookingStatus(int bookingId, String status) {
        Booking b = getBookingById(bookingId);
        if (b != null) {
            b.setStatus(status);
            bookingDAO.updateBooking(b);
            return true;
        }
        return false;
    }
}