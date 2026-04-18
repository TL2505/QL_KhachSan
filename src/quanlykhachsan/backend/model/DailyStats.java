package quanlykhachsan.backend.model;

import java.util.Map;

public class DailyStats {
    private double revenueToday;
    private int occupiedRooms;
    private int totalRooms;
    private int pendingCheckIns;
    private int pendingCheckOuts;
    private Map<String, Integer> roomStatusCounts;

    // Getters and Setters
    public double getRevenueToday() { return revenueToday; }
    public void setRevenueToday(double revenueToday) { this.revenueToday = revenueToday; }

    public int getOccupiedRooms() { return occupiedRooms; }
    public void setOccupiedRooms(int occupiedRooms) { this.occupiedRooms = occupiedRooms; }

    public int getTotalRooms() { return totalRooms; }
    public void setTotalRooms(int totalRooms) { this.totalRooms = totalRooms; }

    public int getPendingCheckIns() { return pendingCheckIns; }
    public void setPendingCheckIns(int pendingCheckIns) { this.pendingCheckIns = pendingCheckIns; }

    public int getPendingCheckOuts() { return pendingCheckOuts; }
    public void setPendingCheckOuts(int pendingCheckOuts) { this.pendingCheckOuts = pendingCheckOuts; }

    public Map<String, Integer> getRoomStatusCounts() { return roomStatusCounts; }
    public void setRoomStatusCounts(Map<String, Integer> roomStatusCounts) { this.roomStatusCounts = roomStatusCounts; }
}
