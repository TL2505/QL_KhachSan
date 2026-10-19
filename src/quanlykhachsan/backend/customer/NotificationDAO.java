package quanlykhachsan.backend.customer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import quanlykhachsan.backend.utils.DatabaseConnection;

/**
 * Data Access Object for customer notifications and lifecycle alerts.
 */
public class NotificationDAO {

    public List<Notification> getNotificationsByCustomer(int customerId, boolean unreadOnly, int limit) throws SQLException {
        List<Notification> list = new ArrayList<>();
        String sql = "SELECT id, customer_id, booking_id, notification_type, title, message, voucher_code, action_url, is_read, read_at, created_at " +
                     "FROM customer_notifications " +
                     "WHERE customer_id = ? " +
                     (unreadOnly ? "AND is_read = FALSE " : "") +
                     "ORDER BY created_at DESC LIMIT ?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement q = c.prepareStatement(sql)) {
            q.setInt(1, customerId);
            q.setInt(2, limit > 0 ? limit : 20);
            try (ResultSet r = q.executeQuery()) {
                while (r.next()) {
                    Notification n = new Notification();
                    n.setId(r.getLong("id"));
                    n.setCustomerId(r.getInt("customer_id"));
                    int bid = r.getInt("booking_id");
                    if (!r.wasNull()) n.setBookingId(bid);
                    n.setNotificationType(r.getString("notification_type"));
                    n.setTitle(r.getString("title"));
                    n.setMessage(r.getString("message"));
                    n.setVoucherCode(r.getString("voucher_code"));
                    n.setActionUrl(r.getString("action_url"));
                    n.setRead(r.getBoolean("is_read"));
                    n.setReadAt(r.getTimestamp("read_at"));
                    n.setCreatedAt(r.getTimestamp("created_at"));
                    list.add(n);
                }
            }
        }
        return list;
    }

    public int getUnreadCount(int customerId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM customer_notifications WHERE customer_id = ? AND is_read = FALSE";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement q = c.prepareStatement(sql)) {
            q.setInt(1, customerId);
            try (ResultSet r = q.executeQuery()) {
                return r.next() ? r.getInt(1) : 0;
            }
        }
    }

    public boolean markAsRead(long id, int customerId) throws SQLException {
        String sql = "UPDATE customer_notifications SET is_read = TRUE, read_at = CURRENT_TIMESTAMP WHERE id = ? AND customer_id = ?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement q = c.prepareStatement(sql)) {
            q.setLong(1, id);
            q.setInt(2, customerId);
            return q.executeUpdate() > 0;
        }
    }

    public long createNotification(Notification n) throws SQLException {
        String sql = "INSERT INTO customer_notifications (customer_id, booking_id, notification_type, title, message, voucher_code, action_url) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement q = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            q.setInt(1, n.getCustomerId());
            if (n.getBookingId() != null && n.getBookingId() > 0) q.setInt(2, n.getBookingId());
            else q.setNull(2, Types.INTEGER);
            q.setString(3, n.getNotificationType());
            q.setString(4, n.getTitle());
            q.setString(5, n.getMessage());
            q.setString(6, n.getVoucherCode());
            q.setString(7, n.getActionUrl());
            q.executeUpdate();
            try (ResultSet r = q.getGeneratedKeys()) {
                if (r.next()) {
                    long id = r.getLong(1);
                    n.setId(id);
                    return id;
                }
            }
        }
        return 0;
    }
}
