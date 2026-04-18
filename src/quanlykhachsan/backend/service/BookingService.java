package quanlykhachsan.backend.service;

import quanlykhachsan.backend.dao.BookingDAO;
import quanlykhachsan.backend.daoimpl.BookingDAOImpl;
import quanlykhachsan.backend.daoimpl.RoomDAOImpl;
import quanlykhachsan.backend.model.Booking;
import quanlykhachsan.backend.model.Invoice;
import quanlykhachsan.backend.daoimpl.InvoiceDAOImpl;
import quanlykhachsan.backend.model.Promotion;
import quanlykhachsan.backend.model.Customer;
import quanlykhachsan.backend.model.Room;
import quanlykhachsan.backend.daoimpl.CustomerDAOImpl;
import quanlykhachsan.backend.daoimpl.RoomDAOImpl;
import quanlykhachsan.backend.daoimpl.PaymentDAOImpl;

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
        List<Booking> bookings = bookingDAO.findByRoomId(roomId);

        for (Booking b : bookings) {
            boolean overlap = checkIn.getTime() <= b.getCheckOutDate().getTime() &&
                              checkOut.getTime() >= b.getCheckInDate().getTime();
            if (overlap) return false;
        }
        return true;
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
            // 1. Fetch info for promotion calculation
            Customer customer = customerDAO.findById(b.getCustomerId());
            Room room = roomDAO.findById(b.getRoomId());
            
            // 2. Calculate promotion
            Promotion bestPromo = promotionService.calculateBestDiscount(b, customer, room);
            double discount = 0;
            if (bestPromo != null) {
                long diff = b.getCheckOutDate().getTime() - b.getCheckInDate().getTime();
                long nights = Math.max(1, diff / (1000 * 60 * 60 * 24));
                if ("percentage".equals(bestPromo.getDiscountType())) {
                    discount = (room.getPrice() * nights) * (bestPromo.getDiscountValue() / 100.0);
                } else {
                    discount = bestPromo.getDiscountValue();
                }
            }

            // 3. Update Booking status to "paid"
            b.setStatus("paid");
            bookingDAO.updateBooking(b);
            
            // 4. Create Payment record
            quanlykhachsan.backend.model.Payment p = new quanlykhachsan.backend.model.Payment();
            p.setInvoiceId(bookingId); 
            p.setAmount(amount); // This 'amount' from UI is expected to be final price
            
            String dbMethod = method;
            if (method.contains("Cash")) dbMethod = "cash";
            else if (method.contains("Credit Card")) dbMethod = "credit_card";
            else if (method.contains("Chuyển khoản")) dbMethod = "bank_transfer";
            
            p.setPaymentMethod(dbMethod);
            p.setPaymentDate(new java.util.Date());
            new PaymentDAOImpl().addPayment(p);
            
            // 5. Create Invoice record
            Invoice inv = new Invoice();
            inv.setBookingId(bookingId);
            
            long diff = b.getCheckOutDate().getTime() - b.getCheckInDate().getTime();
            long nights = Math.max(1, diff / (1000 * 60 * 60 * 24));
            double subtotal = room.getPrice() * nights;
            
            inv.setTotalRoomFee(subtotal);
            inv.setDiscount(discount);
            double taxableAmount = subtotal - discount;
            double tax = 0.0;
            inv.setTaxAmount(tax);
            inv.setFinalTotal(taxableAmount + tax);
            inv.setStatus("paid");
            new InvoiceDAOImpl().addInvoice(inv);
            
            // 6. Update Room status to "cleaning"
            roomDAO.updateStatus(b.getRoomId(), "cleaning");
            
            return true;
        }
        return false;
    }
}