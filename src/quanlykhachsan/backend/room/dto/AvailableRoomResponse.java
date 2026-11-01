package quanlykhachsan.backend.room.dto;

import java.math.BigDecimal;

/**
 * High-velocity room availability response projection.
 * Contains spatial, pricing, physical, and back-to-back turnover indicators.
 */
public class AvailableRoomResponse {
    private int roomId;
    private String roomNumber;
    private BigDecimal basePrice;
    private String physicalStatus;
    private int optimisticVersion;
    private int roomTypeId;
    private String roomTypeName;
    private int capacity;
    private String description;
    private String imageUrl;
    private String amenities;
    private boolean backToBackArrival;

    public AvailableRoomResponse() {}

    public int getRoomId() { return roomId; }
    public void setRoomId(int roomId) { this.roomId = roomId; }

    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }

    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }

    public String getPhysicalStatus() { return physicalStatus; }
    public void setPhysicalStatus(String physicalStatus) { this.physicalStatus = physicalStatus; }

    public int getOptimisticVersion() { return optimisticVersion; }
    public void setOptimisticVersion(int optimisticVersion) { this.optimisticVersion = optimisticVersion; }

    public int getRoomTypeId() { return roomTypeId; }
    public void setRoomTypeId(int roomTypeId) { this.roomTypeId = roomTypeId; }

    public String getRoomTypeName() { return roomTypeName; }
    public void setRoomTypeName(String roomTypeName) { this.roomTypeName = roomTypeName; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getAmenities() { return amenities; }
    public void setAmenities(String amenities) { this.amenities = amenities; }

    public boolean isBackToBackArrival() { return backToBackArrival; }
    public void setBackToBackArrival(boolean backToBackArrival) { this.backToBackArrival = backToBackArrival; }
}
