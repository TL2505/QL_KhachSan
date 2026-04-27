package quanlykhachsan.backend.customer;

import java.util.Date;

public class LoyaltyHistory {
    private int id;
    private int customerId;
    private int pointsChange;
    private String type; // earn, redeem
    private String description;
    private Date createdAt;

    public LoyaltyHistory() {
    }

    public LoyaltyHistory(int id, int customerId, int pointsChange, String type, String description, Date createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.pointsChange = pointsChange;
        this.type = type;
        this.description = description;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }
    public int getPointsChange() { return pointsChange; }
    public void setPointsChange(int pointsChange) { this.pointsChange = pointsChange; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
