package quanlykhachsan.backend.room.dto;

public class RoomResponse {
    private int id;
    private String roomNumber;
    private int roomTypeId;
    private String typeName;
    private double price;
    private String status;
    private int version;
    private String createdAt;
    public String getCreatedAt(){return createdAt;}
    public void setCreatedAt(String value){createdAt=value;}
    public int getVersion(){return version;}
    public void setVersion(int value){version=value;}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    public int getRoomTypeId() { return roomTypeId; }
    public void setRoomTypeId(int roomTypeId) { this.roomTypeId = roomTypeId; }
    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
