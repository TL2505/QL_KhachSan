package quanlykhachsan.backend.hotelservice;

import java.util.Date;

public class ServiceUsage {
    private int id;
    private int bookingId;
    private int serviceId;
    private int quantity;
    private double unitPrice;
    private double totalPrice;
    private Date usageDate;

    public ServiceUsage() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getBookingId() { return bookingId; }
    public void setBookingId(int bookingId) { this.bookingId = bookingId; }
    public int getServiceId() { return serviceId; }
    public void setServiceId(int serviceId) { this.serviceId = serviceId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }
    public Date getUsageDate() { return usageDate; }
    public void setUsageDate(Date usageDate) { this.usageDate = usageDate; }
}
