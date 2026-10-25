package quanlykhachsan.backend.report;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Operational and cash-flow indicators for the administrator dashboard. */
public class AdminOverviewStats {
    private String businessDate;
    private BigDecimal collectionsToday = BigDecimal.ZERO;
    private int totalRooms;
    private int sellableRooms;
    private int occupiedRooms;
    private int roomsNeedCleaning;
    private int pendingBookings;
    private int arrivalsToday;
    private int departuresToday;
    private BigDecimal collectionsThisMonth = BigDecimal.ZERO;
    private BigDecimal refundsThisMonth = BigDecimal.ZERO;
    private BigDecimal refundRate = BigDecimal.ZERO;
    private BigDecimal outstandingManualRefundAmount = BigDecimal.ZERO;
    private int outstandingManualRefundCount;
    private int bookingsCreatedThisMonth;
    private int cancelledBookingsThisMonth;
    private BigDecimal cancellationRate = BigDecimal.ZERO;
    private List<MonthlyRevenue> revenueByMonth = new ArrayList<>();

    public String getBusinessDate() { return businessDate; }
    public void setBusinessDate(String businessDate) { this.businessDate = businessDate; }
    public BigDecimal getCollectionsToday() { return collectionsToday; }
    public void setCollectionsToday(BigDecimal collectionsToday) { this.collectionsToday = collectionsToday; }
    public int getTotalRooms() { return totalRooms; }
    public void setTotalRooms(int totalRooms) { this.totalRooms = totalRooms; }
    public int getSellableRooms() { return sellableRooms; }
    public void setSellableRooms(int sellableRooms) { this.sellableRooms = sellableRooms; }
    public int getOccupiedRooms() { return occupiedRooms; }
    public void setOccupiedRooms(int occupiedRooms) { this.occupiedRooms = occupiedRooms; }
    public int getRoomsNeedCleaning() { return roomsNeedCleaning; }
    public void setRoomsNeedCleaning(int roomsNeedCleaning) { this.roomsNeedCleaning = roomsNeedCleaning; }
    public int getPendingBookings() { return pendingBookings; }
    public void setPendingBookings(int pendingBookings) { this.pendingBookings = pendingBookings; }
    public int getArrivalsToday() { return arrivalsToday; }
    public void setArrivalsToday(int arrivalsToday) { this.arrivalsToday = arrivalsToday; }
    public int getDeparturesToday() { return departuresToday; }
    public void setDeparturesToday(int departuresToday) { this.departuresToday = departuresToday; }
    public BigDecimal getCollectionsThisMonth() { return collectionsThisMonth; }
    public void setCollectionsThisMonth(BigDecimal collectionsThisMonth) { this.collectionsThisMonth = collectionsThisMonth; }
    public BigDecimal getRefundsThisMonth() { return refundsThisMonth; }
    public void setRefundsThisMonth(BigDecimal refundsThisMonth) { this.refundsThisMonth = refundsThisMonth; }
    public BigDecimal getRefundRate() { return refundRate; }
    public void setRefundRate(BigDecimal refundRate) { this.refundRate = refundRate; }
    public BigDecimal getOutstandingManualRefundAmount() { return outstandingManualRefundAmount; }
    public void setOutstandingManualRefundAmount(BigDecimal amount) { this.outstandingManualRefundAmount = amount; }
    public int getOutstandingManualRefundCount() { return outstandingManualRefundCount; }
    public void setOutstandingManualRefundCount(int count) { this.outstandingManualRefundCount = count; }
    public int getBookingsCreatedThisMonth() { return bookingsCreatedThisMonth; }
    public void setBookingsCreatedThisMonth(int count) { this.bookingsCreatedThisMonth = count; }
    public int getCancelledBookingsThisMonth() { return cancelledBookingsThisMonth; }
    public void setCancelledBookingsThisMonth(int count) { this.cancelledBookingsThisMonth = count; }
    public BigDecimal getCancellationRate() { return cancellationRate; }
    public void setCancellationRate(BigDecimal cancellationRate) { this.cancellationRate = cancellationRate; }
    public List<MonthlyRevenue> getRevenueByMonth() { return revenueByMonth; }
    public void setRevenueByMonth(List<MonthlyRevenue> revenueByMonth) { this.revenueByMonth = revenueByMonth; }
}
