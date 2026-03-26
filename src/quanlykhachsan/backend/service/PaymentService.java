package quanlykhachsan.backend.service;

public class PaymentService {

    // chỉ xử lý logic, chưa lưu DB

    // tính tiền phòng
    public double calculateRoomCost(double pricePerNight, int days) {
        return pricePerNight * days;
    }

    // tổng tiền (phase 1 đơn giản)
    public double calculateTotal(double roomCost) {
        return roomCost;
    }
}