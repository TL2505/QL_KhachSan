package quanlykhachsan.backend.service;

import quanlykhachsan.backend.dao.BookingDAO;
import quanlykhachsan.backend.daoimpl.BookingDAOImpl;
import quanlykhachsan.backend.daoimpl.RoomDAOImpl;
import quanlykhachsan.backend.model.Booking;
import quanlykhachsan.backend.model.Invoice;
import quanlykhachsan.backend.daoimpl.InvoiceDAOImpl;

import java.util.List;

public class BookingService {

    private BookingDAO bookingDAO = new BookingDAOImpl();

    public List<Booking> getAllBookings() {
        return bookingDAO.selectBooking();
    }

    public boolean checkAvailable(int roomId, java.util.Date checkIn, java.util.Date checkOut) {
        List<Booking> bookings = bookingDAO.findByRoomId(roomId);

        for (Booking b : bookings) {
            boolean overlap = checkIn.getTime() <= b.getCheckOutDate().getTime() &&
                              checkOut.getTime() >= b.getCheckInDate().getTime();
            if (overlap) return false;
        }
        return true;
    }

    public boolean createBooking(Booking booking) {
        if (!checkAvailable(
                booking.getRoomId(),
                booking.getCheckInDate(),
                booking.getCheckOutDate())) {
            return false;
        }

        return bookingDAO.insert(booking);
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

    public boolean updateBookingStatus(int bookingId, String status) {
        Booking b = getBookingById(bookingId);
        if (b != null) {
            b.setStatus(status);
            bookingDAO.updateBooking(b);
            return true;
        }
        return false;
    }

    public boolean processPayment(int bookingId, double amount, String method) {
        Booking b = getBookingById(bookingId);
        if (b != null) {
            // 1. Cập nhật trạng thái Booking sang "paid"
            b.setStatus("paid");
            bookingDAO.updateBooking(b);
            
            // 2. Tạo bản ghi Thanh toán (Payment) và lưu vào DB
            quanlykhachsan.backend.model.Payment p = new quanlykhachsan.backend.model.Payment();
            p.setInvoiceId(bookingId); 
            p.setAmount(amount);
            
            // Ánh xạ phương thức từ giao diện sang DB (nếu cần gọn gàng)
            String dbMethod = method;
            if (method.contains("Cash")) dbMethod = "cash";
            else if (method.contains("Credit Card")) dbMethod = "credit_card";
            else if (method.contains("Chuyển khoản")) dbMethod = "bank_transfer";
            
            p.setPaymentMethod(dbMethod);
            p.setPaymentDate(new java.util.Date());
            new quanlykhachsan.backend.daoimpl.PaymentDAOImpl().addPayment(p);
            
            // 2.5 Tạo bản ghi Hóa Đơn (Invoice) và lưu vào DB
            Invoice inv = new Invoice();
            inv.setBookingId(bookingId);
            double subtotal = amount / 1.1; // Amount đã bao gồm 10% thuế lúc Lễ tân thanh toán
            double tax = amount - subtotal;
            inv.setTotalRoomFee(subtotal);
            inv.setTaxAmount(tax);
            inv.setFinalTotal(amount);
            inv.setStatus("paid");
            new InvoiceDAOImpl().addInvoice(inv);
            
            // 3. Cập nhật trạng thái Phòng sang "cleaning"
            new RoomDAOImpl().updateStatus(b.getRoomId(), "cleaning");
            
            return true;
        }
        return false;
    }
}