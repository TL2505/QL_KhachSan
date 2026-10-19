package quanlykhachsan.backend.customer;

public class Customer {
    private int id;
    private String fullName;
    private String identityCard;
    private String phone;
    private String email;
    private String address;
    private boolean isVip;
    private int loyaltyPoints;
    private int totalLoyaltyPoints;
    private String loyaltyLevel;

    public Customer() {
    }

    public Customer(int id, String fullName, String identityCard, String phone, String email, String address, boolean isVip) {
        this.id = id;
        this.fullName = fullName;
        this.identityCard = identityCard;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.isVip = isVip;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getIdentityCard() { return identityCard; }
    public void setIdentityCard(String identityCard) { this.identityCard = identityCard; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public boolean isVip() { return isVip; }
    public void setVip(boolean isVip) { this.isVip = isVip; }

    public int getLoyaltyPoints() { return loyaltyPoints; }
    public void setLoyaltyPoints(int loyaltyPoints) { this.loyaltyPoints = loyaltyPoints; }
    public int getTotalLoyaltyPoints() { return totalLoyaltyPoints; }
    public void setTotalLoyaltyPoints(int totalLoyaltyPoints) { this.totalLoyaltyPoints = totalLoyaltyPoints; }
    public String getLoyaltyLevel() { return loyaltyLevel; }
    public void setLoyaltyLevel(String loyaltyLevel) { this.loyaltyLevel = loyaltyLevel; }
}
