package quanlykhachsan.backend.customer;

import java.sql.Timestamp;

/**
 * Model representing customer lifecycle notifications, vouchers, and alerts.
 */
public class Notification {
    private long id;
    private int customerId;
    private Integer bookingId;
    private String notificationType; // booking_confirmed, checkin_welcome, checkout_gratitude, service_update, promo_alert
    private String title;
    private String message;
    private String voucherCode;
    private String actionUrl;
    private boolean isRead;
    private Timestamp readAt;
    private Timestamp createdAt;

    public Notification() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public Integer getBookingId() { return bookingId; }
    public void setBookingId(Integer bookingId) { this.bookingId = bookingId; }

    public String getNotificationType() { return notificationType; }
    public void setNotificationType(String notificationType) { this.notificationType = notificationType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getVoucherCode() { return voucherCode; }
    public void setVoucherCode(String voucherCode) { this.voucherCode = voucherCode; }

    public String getActionUrl() { return actionUrl; }
    public void setActionUrl(String actionUrl) { this.actionUrl = actionUrl; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public Timestamp getReadAt() { return readAt; }
    public void setReadAt(Timestamp readAt) { this.readAt = readAt; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
