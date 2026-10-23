package quanlykhachsan.backend.notification;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Broadcast staff-facing operational alerts with per-user read state. */
public final class StaffNotifications {
    private static final DateTimeFormatter EVENT_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");
    private StaffNotifications() { }

    public static void checkoutOverdue(Connection c, int bookingId, LocalDateTime scheduledCheckout) throws SQLException {
        if (c.getAutoCommit()) throw new SQLException("Staff notification events require a business transaction");
        String room; String guest; String phone; java.math.BigDecimal balance;
        try (PreparedStatement q = c.prepareStatement(
                "SELECT r.room_number,c.full_name,c.phone,COALESCE((SELECT SUM(f.balance) FROM folios f WHERE f.booking_id=b.id),0.00) "
              + "FROM bookings b JOIN rooms r ON r.id=b.room_id JOIN customers c ON c.id=b.customer_id WHERE b.id=?")) {
            q.setInt(1, bookingId);
            try (ResultSet r = q.executeQuery()) {
                if (!r.next()) throw new SQLException("Overdue booking missing");
                room = r.getString(1); guest = r.getString(2); phone = r.getString(3); balance = r.getBigDecimal(4);
            }
        }
        String message = "Đơn #" + bookingId + " · phòng " + room + " · khách " + (guest == null ? "chưa có tên" : guest)
                + (phone == null || phone.isBlank() ? " · chưa có số điện thoại" : " · gọi " + phone)
                + " · còn phải thu " + balance.toPlainString() + " VND. Vui lòng liên hệ khách và thu tiền tại quầy trước khi hoàn tất trả phòng.";
        String eventKey = "checkout-overdue:" + bookingId + ":" + scheduledCheckout.format(EVENT_TIME);
        try (PreparedStatement q = c.prepareStatement(
                "INSERT INTO staff_notifications(event_key,booking_id,title,message,action_url) VALUES(?,?,?,?,'portal.html?view=checkout') "
              + "ON DUPLICATE KEY UPDATE event_key=event_key")) {
            q.setString(1, eventKey); q.setInt(2, bookingId); q.setString(3, "Khách quá hạn trả phòng"); q.setString(4, message); q.executeUpdate();
        }
    }
}
