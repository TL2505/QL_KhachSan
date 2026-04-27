package quanlykhachsan.backend.booking;

import java.util.Date;

public class Invoice {
    private int id;
    private int bookingId;
    private double totalRoomFee;
    private double totalServiceFee;
    private double discount;
    private double taxAmount;
    private double finalTotal;
    private Date issueDate;
    private String status;

    // Transient fields for UI Display
    private String customerName;
    private String roomNumber;

    public Invoice() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getBookingId() { return bookingId; }
    public void setBookingId(int bookingId) { this.bookingId = bookingId; }

    public double getTotalRoomFee() { return totalRoomFee; }
    public void setTotalRoomFee(double totalRoomFee) { this.totalRoomFee = totalRoomFee; }

    public double getTotalServiceFee() { return totalServiceFee; }
    public void setTotalServiceFee(double totalServiceFee) { this.totalServiceFee = totalServiceFee; }

    public double getDiscount() { return discount; }
    public void setDiscount(double discount) { this.discount = discount; }

    public double getTaxAmount() { return taxAmount; }
    public void setTaxAmount(double taxAmount) { this.taxAmount = taxAmount; }

    public double getFinalTotal() { return finalTotal; }
    public void setFinalTotal(double finalTotal) { this.finalTotal = finalTotal; }

    public Date getIssueDate() { return issueDate; }
    public void setIssueDate(Date issueDate) { this.issueDate = issueDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
}
