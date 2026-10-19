package quanlykhachsan.backend.hotelservice;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Model representing in-stay room service orders (F&B, Spa, Housekeeping).
 */
public class ServiceOrder {
    private long id;
    @com.google.gson.annotations.SerializedName(value="booking_id",alternate={"bookingId"})
    private int bookingId;
    @com.google.gson.annotations.SerializedName(value="customer_id",alternate={"customerId"})
    private int customerId;
    @com.google.gson.annotations.SerializedName(value="room_id",alternate={"roomId"})
    private int roomId;
    @com.google.gson.annotations.SerializedName(value="room_number",alternate={"roomNumber"})
    private String roomNumber;
    @com.google.gson.annotations.SerializedName(value="customer_name",alternate={"customerName"})
    private String customerName;
    private String category; // FNB, SPA, HOUSEKEEPING
    @com.google.gson.annotations.SerializedName(value="service_id",alternate={"serviceId"})
    private Integer serviceId;
    @com.google.gson.annotations.SerializedName(value="item_name",alternate={"itemName"})
    private String itemName;
    private int quantity = 1;
    @com.google.gson.annotations.SerializedName(value="unit_price",alternate={"unitPrice"})
    private BigDecimal unitPrice = BigDecimal.ZERO;
    private BigDecimal subtotal = BigDecimal.ZERO;
    @com.google.gson.annotations.SerializedName(value="discount_amount",alternate={"discountAmount"})
    private BigDecimal discountAmount = BigDecimal.ZERO;
    @com.google.gson.annotations.SerializedName(value="final_price",alternate={"finalPrice"})
    private BigDecimal finalPrice = BigDecimal.ZERO;
    @com.google.gson.annotations.SerializedName(value="voucher_code",alternate={"voucherCode"})
    private String voucherCode;
    @com.google.gson.annotations.SerializedName(value="special_instructions",alternate={"specialInstructions"})
    private String specialInstructions;
    @com.google.gson.annotations.SerializedName(value="scheduled_time",alternate={"scheduledTime"})
    private Timestamp scheduledTime;
    @com.google.gson.annotations.SerializedName(value="order_status",alternate={"orderStatus"})
    private String orderStatus = "PENDING"; // PENDING, ACCEPTED, DELIVERED, COMPLETED, CANCELLED
    @com.google.gson.annotations.SerializedName(value="folio_id",alternate={"folioId"})
    private Long folioId;
    @com.google.gson.annotations.SerializedName(value="folio_transaction_id",alternate={"folioTransactionId"})
    private Long folioTransactionId;
    @com.google.gson.annotations.SerializedName(value="created_at",alternate={"createdAt"})
    private Timestamp createdAt;
    @com.google.gson.annotations.SerializedName(value="updated_at",alternate={"updatedAt"})
    private Timestamp updatedAt;

    public ServiceOrder() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public int getBookingId() { return bookingId; }
    public void setBookingId(int bookingId) { this.bookingId = bookingId; }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public int getRoomId() { return roomId; }
    public void setRoomId(int roomId) { this.roomId = roomId; }

    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Integer getServiceId() { return serviceId; }
    public void setServiceId(Integer serviceId) { this.serviceId = serviceId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public BigDecimal getFinalPrice() { return finalPrice; }
    public void setFinalPrice(BigDecimal finalPrice) { this.finalPrice = finalPrice; }

    public String getVoucherCode() { return voucherCode; }
    public void setVoucherCode(String voucherCode) { this.voucherCode = voucherCode; }

    public String getSpecialInstructions() { return specialInstructions; }
    public void setSpecialInstructions(String specialInstructions) { this.specialInstructions = specialInstructions; }

    public Timestamp getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(Timestamp scheduledTime) { this.scheduledTime = scheduledTime; }

    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }

    public Long getFolioId() { return folioId; }
    public void setFolioId(Long folioId) { this.folioId = folioId; }

    public Long getFolioTransactionId() { return folioTransactionId; }
    public void setFolioTransactionId(Long folioTransactionId) { this.folioTransactionId = folioTransactionId; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
