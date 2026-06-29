package quanlykhachsan.backend.booking;

import java.util.Date;
import quanlykhachsan.backend.enums.BookingStatus;
import com.google.gson.annotations.SerializedName;

public class Booking {
    private int id;
    private int customerId;
    private int roomId;
    private Date checkInDate;
    private Date checkOutDate;
    private double totalPrice;
    
    @SerializedName("status")
    private BookingStatus statusEnum;
    private Date createdAt;

    public Booking() {
    }

    public Booking(int id, int customerId, int roomId, Date checkInDate, Date checkOutDate, double totalPrice, BookingStatus statusEnum) {
        this.id = id;
        this.customerId = customerId;
        this.roomId = roomId;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.totalPrice = totalPrice;
        this.statusEnum = statusEnum;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }
    public int getRoomId() { return roomId; }
    public void setRoomId(int roomId) { this.roomId = roomId; }
    public Date getCheckInDate() { return checkInDate; }
    public void setCheckInDate(Date checkInDate) { this.checkInDate = checkInDate; }
    public Date getCheckOutDate() { return checkOutDate; }
    public void setCheckOutDate(Date checkOutDate) { this.checkOutDate = checkOutDate; }
    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }
    
    // Core Enum Methods
    public BookingStatus getStatusEnum() { return statusEnum; }
    public void setStatusEnum(BookingStatus statusEnum) { this.statusEnum = statusEnum; }

    // Backward Compatibility for Swing UI (Returns String)
    public String getStatus() { return statusEnum != null ? statusEnum.name().toLowerCase() : null; }
    public void setStatus(String status) { this.statusEnum = BookingStatus.fromString(status); }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
