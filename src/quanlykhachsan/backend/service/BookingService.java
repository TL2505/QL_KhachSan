package quanlykhachsan.backend.service;

import quanlykhachsan.backend.dao.BookingDAO;
import quanlykhachsan.backend.model.Booking;

import java.time.LocalDateTime;
import java.util.List;

public class BookingService {

    private BookingDAO bookingDAO = new BookingDAO();

    // kiểm tra phòng trống
    public boolean checkAvailable(int roomId, LocalDateTime checkIn, LocalDateTime checkOut) {
        List<Booking> bookings = bookingDAO.findByRoomId(roomId);

        for (Booking b : bookings) {
            boolean overlap = checkIn.isBefore(b.getCheckOutDate()) &&
                              checkOut.isAfter(b.getCheckInDate());
            if (overlap) return false;
        }
        return true;
    }

    // tạo booking
    public boolean createBooking(Booking booking) {
        if (!checkAvailable(
                booking.getRoom().getId(),
                booking.getCheckInDate(),
                booking.getCheckOutDate())) {
            return false;
        }

        return bookingDAO.insert(booking);
    }

    // lịch sử khách hàng (CRM)
    public List<Booking> getBookingsByCustomer(int customerId) {
        return bookingDAO.findByCustomerId(customerId);
    }
}