package quanlykhachsan.backend.model;

import java.time.LocalDateTime;

public class Booking {
    private int id;
    private Customer customer;
    private Room room;
    private LocalDateTime checkInDate;
    private LocalDateTime checkOutDate;
    private double totalPrice;
    private String status;

    public Booking() {}

    public Booking(int id, Customer customer, Room room,
                   LocalDateTime checkInDate, LocalDateTime checkOutDate,
                   double totalPrice, String status) {
        this.id = id;
        this.customer = customer;
        this.room = room;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.totalPrice = totalPrice;
        this.status = status;
    }

    // ===== GETTER & SETTER =====
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }

    public LocalDateTime getCheckInDate() { return checkInDate; }
    public void setCheckInDate(LocalDateTime checkInDate) { this.checkInDate = checkInDate; }

    public LocalDateTime getCheckOutDate() { return checkOutDate; }
    public void setCheckOutDate(LocalDateTime checkOutDate) { this.checkOutDate = checkOutDate; }

    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}