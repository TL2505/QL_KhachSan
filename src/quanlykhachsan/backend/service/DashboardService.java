package quanlykhachsan.backend.service;

import quanlykhachsan.backend.dao.RoomDAO;
import quanlykhachsan.backend.dao.BookingDAO;
import quanlykhachsan.backend.dao.PaymentDAO;
import quanlykhachsan.backend.dao.CustomerDAO;
import quanlykhachsan.backend.daoimpl.RoomDAOImpl;
import quanlykhachsan.backend.daoimpl.BookingDAOImpl;
import quanlykhachsan.backend.daoimpl.PaymentDAOImpl;
import quanlykhachsan.backend.daoimpl.CustomerDAOImpl;
import quanlykhachsan.backend.model.Room;
import quanlykhachsan.backend.model.Booking;
import quanlykhachsan.backend.model.Payment;
import quanlykhachsan.backend.model.Customer;

import java.util.*;
import java.text.SimpleDateFormat;

public class DashboardService {

    private RoomDAO roomDAO = new RoomDAOImpl();
    private BookingDAO bookingDAO = new BookingDAOImpl();
    private PaymentDAO paymentDAO = new PaymentDAOImpl();
    private CustomerDAO customerDAO = new CustomerDAOImpl();

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // 1. Doanh thu hôm nay
        double revenueToday = 0;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String todayStr = sdf.format(new Date());
        
        List<Payment> payments = paymentDAO.selectPayment();
        for (Payment p : payments) {
            if (p.getPaymentDate() != null && sdf.format(p.getPaymentDate()).equals(todayStr)) {
                revenueToday += p.getAmount();
            }
        }
        stats.put("revenueToday", revenueToday);

        // 2. Trạng thái phòng
        int available = 0, occupied = 0, maintenance = 0, dirty = 0;
        List<Room> rooms = roomDAO.selectRoom();
        for (Room r : rooms) {
            String s = r.getStatus().toLowerCase();
            if (s.equals("available")) available++;
            else if (s.equals("occupied")) occupied++;
            else if (s.equals("maintenance")) maintenance++;
            else if (s.equals("dirty")) dirty++;
        }
        stats.put("totalRooms", rooms.size());
        stats.put("roomsAvailable", available);
        stats.put("roomsOccupied", occupied);
        stats.put("roomsMaintenance", maintenance);
        stats.put("roomsDirty", dirty);

        // 3. Tổng khách hàng
        List<Customer> customers = customerDAO.selectCustomer();
        stats.put("totalCustomers", customers.size());

        // 4. Lượt đặt phòng hôm nay
        int bookingsToday = 0;
        List<Booking> bookings = bookingDAO.selectBooking();
        for (Booking b : bookings) {
            if (b.getCheckInDate() != null && sdf.format(b.getCheckInDate()).equals(todayStr)) {
                bookingsToday++;
            }
        }
        stats.put("bookingsToday", bookingsToday);

        // 5. Hoạt động gần đây (5 booking gần nhất)
        List<Booking> recentBookings = new ArrayList<>(bookings);
        recentBookings.sort((b1, b2) -> Integer.compare(b2.getId(), b1.getId()));
        List<Booking> top5 = recentBookings.subList(0, Math.min(5, recentBookings.size()));
        stats.put("recentActivity", top5);

        return stats;
    }
}
