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
    private Long electronicInvoiceId;
    private String electronicInvoiceStatus;
    private String electronicInvoiceNumber;
    private String electronicInvoiceViewUrl;
    private String electronicInvoiceDocumentType;
    private String electronicInvoiceProviderMode;
    private String electronicInvoiceTaxStatus;

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

    public Long getElectronicInvoiceId() { return electronicInvoiceId; }
    public void setElectronicInvoiceId(Long electronicInvoiceId) { this.electronicInvoiceId = electronicInvoiceId; }
    public String getElectronicInvoiceStatus() { return electronicInvoiceStatus; }
    public void setElectronicInvoiceStatus(String electronicInvoiceStatus) { this.electronicInvoiceStatus = electronicInvoiceStatus; }
    public String getElectronicInvoiceNumber() { return electronicInvoiceNumber; }
    public void setElectronicInvoiceNumber(String electronicInvoiceNumber) { this.electronicInvoiceNumber = electronicInvoiceNumber; }
    public String getElectronicInvoiceViewUrl() { return electronicInvoiceViewUrl; }
    public void setElectronicInvoiceViewUrl(String electronicInvoiceViewUrl) { this.electronicInvoiceViewUrl = electronicInvoiceViewUrl; }
    public String getElectronicInvoiceDocumentType() { return electronicInvoiceDocumentType; }
    public void setElectronicInvoiceDocumentType(String electronicInvoiceDocumentType) { this.electronicInvoiceDocumentType = electronicInvoiceDocumentType; }
    public String getElectronicInvoiceProviderMode() { return electronicInvoiceProviderMode; }
    public void setElectronicInvoiceProviderMode(String electronicInvoiceProviderMode) { this.electronicInvoiceProviderMode = electronicInvoiceProviderMode; }
    public String getElectronicInvoiceTaxStatus() { return electronicInvoiceTaxStatus; }
    public void setElectronicInvoiceTaxStatus(String electronicInvoiceTaxStatus) { this.electronicInvoiceTaxStatus = electronicInvoiceTaxStatus; }
}
