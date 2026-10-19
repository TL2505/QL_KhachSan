package quanlykhachsan.backend.interaction;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Model representing customer stay reviews, rating breakdowns,
 * and official management responses.
 */
public class Review {
    private int id;
    private Integer bookingId;
    private int customerId;
    private int roomId;
    private int rating = 5; // legacy overall rating (1-5)
    private int ratingRoom = 5;
    private int ratingFnb = 5;
    private int ratingService = 5;
    private int ratingCleanliness = 5;
    private BigDecimal ratingOverall = new BigDecimal("5.0");
    private String comment;
    private String managementResponse;
    private Integer respondedByUserId;
    private Timestamp respondedAt;
    private boolean rewardGranted = false;
    private int rewardPoints = 200;
    private Timestamp createdAt;

    // Joint / display fields
    private String customerName;
    private String customerDisplayName;
    private String roomNumber;
    private String roomTypeName;
    private String responderName;

    public Review() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Integer getBookingId() { return bookingId; }
    public void setBookingId(Integer bookingId) { this.bookingId = bookingId; }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public int getRoomId() { return roomId; }
    public void setRoomId(int roomId) { this.roomId = roomId; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public int getRatingRoom() { return ratingRoom; }
    public void setRatingRoom(int ratingRoom) { this.ratingRoom = ratingRoom; }

    public int getRatingFnb() { return ratingFnb; }
    public void setRatingFnb(int ratingFnb) { this.ratingFnb = ratingFnb; }

    public int getRatingService() { return ratingService; }
    public void setRatingService(int ratingService) { this.ratingService = ratingService; }

    public int getRatingCleanliness() { return ratingCleanliness; }
    public void setRatingCleanliness(int ratingCleanliness) { this.ratingCleanliness = ratingCleanliness; }

    public BigDecimal getRatingOverall() { return ratingOverall; }
    public void setRatingOverall(BigDecimal ratingOverall) { this.ratingOverall = ratingOverall; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public String getManagementResponse() { return managementResponse; }
    public void setManagementResponse(String managementResponse) { this.managementResponse = managementResponse; }

    public Integer getRespondedByUserId() { return respondedByUserId; }
    public void setRespondedByUserId(Integer respondedByUserId) { this.respondedByUserId = respondedByUserId; }

    public Timestamp getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Timestamp respondedAt) { this.respondedAt = respondedAt; }

    public boolean isRewardGranted() { return rewardGranted; }
    public void setRewardGranted(boolean rewardGranted) { this.rewardGranted = rewardGranted; }

    public int getRewardPoints() { return rewardPoints; }
    public void setRewardPoints(int rewardPoints) { this.rewardPoints = rewardPoints; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerDisplayName() { return customerDisplayName; }
    public void setCustomerDisplayName(String customerDisplayName) { this.customerDisplayName = customerDisplayName; }

    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }

    public String getRoomTypeName() { return roomTypeName; }
    public void setRoomTypeName(String roomTypeName) { this.roomTypeName = roomTypeName; }

    public String getResponderName() { return responderName; }
    public void setResponderName(String responderName) { this.responderName = responderName; }
}
