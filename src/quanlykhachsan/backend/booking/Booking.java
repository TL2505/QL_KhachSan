package quanlykhachsan.backend.booking;

import java.util.Date;

public class Booking {
    private int id;
    private int customerId;
    private int roomId;
    private Date checkInDate;
    private Date checkOutDate;
    private double totalPrice;
    private String status;
    private String paymentChoice;
    private String customerType;
    private Date createdAt;
    private Date checkoutOverdueAt;

    public Booking() {
    }

    public Booking(int id, int customerId, int roomId, Date checkInDate, Date checkOutDate, double totalPrice, String status) {
        this.id = id;
        this.customerId = customerId;
        this.roomId = roomId;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.totalPrice = totalPrice;
        this.status = status;
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPaymentChoice() { return paymentChoice; }
    public void setPaymentChoice(String paymentChoice) { this.paymentChoice = paymentChoice; }
    public String getCustomerType() { return customerType; }
    public void setCustomerType(String customerType) { this.customerType = customerType; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getCheckoutOverdueAt() { return checkoutOverdueAt; }
    public void setCheckoutOverdueAt(Date checkoutOverdueAt) { this.checkoutOverdueAt = checkoutOverdueAt; }
}
