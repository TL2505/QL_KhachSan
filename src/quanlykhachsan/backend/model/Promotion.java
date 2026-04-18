package quanlykhachsan.backend.model;

import java.util.Date;

public class Promotion {
    private int id;
    private String name;
    private String description;
    private String discountType; // 'percentage' or 'fixed_amount'
    private double discountValue;
    private Date startDate;
    private Date endDate;
    private String conditionType; // 'none', 'room_type', 'min_stay', 'vip_only'
    private String conditionValue;
    private String status; // 'active' or 'inactive'

    public Promotion() {
    }

    public Promotion(int id, String name, String description, String discountType, double discountValue, Date startDate, Date endDate, String conditionType, String conditionValue, String status) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.startDate = startDate;
        this.endDate = endDate;
        this.conditionType = conditionType;
        this.conditionValue = conditionValue;
        this.status = status;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }

    public double getDiscountValue() { return discountValue; }
    public void setDiscountValue(double discountValue) { this.discountValue = discountValue; }

    public Date getStartDate() { return startDate; }
    public void setStartDate(Date startDate) { this.startDate = startDate; }

    public Date getEndDate() { return endDate; }
    public void setEndDate(Date endDate) { this.endDate = endDate; }

    public String getConditionType() { return conditionType; }
    public void setConditionType(String conditionType) { this.conditionType = conditionType; }

    public String getConditionValue() { return conditionValue; }
    public void setConditionValue(String conditionValue) { this.conditionValue = conditionValue; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
