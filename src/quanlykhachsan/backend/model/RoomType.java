package quanlykhachsan.backend.model;

public class RoomType {
    private int id;
    private String name;
    private String description;
    private double basePrice;
    private int capacity;

    public RoomType() {
    }

    public RoomType(int id, String name, String description, double basePrice, int capacity) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
        this.capacity = capacity;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getBasePrice() { return basePrice; }
    public void setBasePrice(double basePrice) { this.basePrice = basePrice; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    
    @Override
    public String toString() {
        return name;
    }
}
