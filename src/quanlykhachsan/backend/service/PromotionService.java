package quanlykhachsan.backend.service;

import quanlykhachsan.backend.dao.PromotionDAO;
import quanlykhachsan.backend.daoimpl.PromotionDAOImpl;
import quanlykhachsan.backend.model.Promotion;
import quanlykhachsan.backend.model.Booking;
import quanlykhachsan.backend.model.Customer;
import quanlykhachsan.backend.model.Room;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class PromotionService {

    private PromotionDAO promotionDAO = new PromotionDAOImpl();

    public List<Promotion> getAllPromotions() {
        return promotionDAO.selectAll();
    }

    public List<Promotion> getActivePromotions() {
        Date now = new Date();
        return promotionDAO.selectAll().stream()
                .filter(p -> "active".equals(p.getStatus()))
                .filter(p -> p.getStartDate().before(now) && p.getEndDate().after(now))
                .collect(Collectors.toList());
    }

    public Promotion getPromotionById(int id) {
        return promotionDAO.findById(id);
    }

    public boolean createPromotion(Promotion p) {
        if (p.getStartDate().after(p.getEndDate())) {
            return false; // Invalid dates
        }
        return promotionDAO.insert(p);
    }

    public boolean updatePromotion(Promotion p) {
        if (p.getStartDate().after(p.getEndDate())) {
            return false; // Invalid dates
        }
        return promotionDAO.update(p);
    }

    public boolean deletePromotion(int id) {
        return promotionDAO.delete(id);
    }

    public Promotion calculateBestDiscount(Booking booking, Customer customer, Room room) {
        List<Promotion> active = getActivePromotions();
        Promotion best = null;
        double maxDiscount = 0;

        // Calculate nights
        long diff = booking.getCheckOutDate().getTime() - booking.getCheckInDate().getTime();
        long nights = Math.max(1, diff / (1000 * 60 * 60 * 24));
        double totalRoomPrice = room.getPrice() * nights;

        for (Promotion p : active) {
            if (isEligible(p, booking, customer, room, nights)) {
                double discount = 0;
                if ("percentage".equals(p.getDiscountType())) {
                    discount = totalRoomPrice * (p.getDiscountValue() / 100.0);
                } else if ("fixed_amount".equals(p.getDiscountType())) {
                    discount = p.getDiscountValue();
                }

                if (discount > maxDiscount) {
                    maxDiscount = discount;
                    best = p;
                }
            }
        }
        return best;
    }

    private boolean isEligible(Promotion p, Booking booking, Customer customer, Room room, long nights) {
        String type = p.getConditionType();
        String value = p.getConditionValue();

        if (type == null || "none".equals(type) || type.isEmpty()) return true;

        try {
            switch (type) {
                case "room_type":
                    // If value is numeric, check roomTypeId. Otherwise, we might need RoomType name (omitted for simplicity)
                    return String.valueOf(room.getRoomTypeId()).equals(value);
                case "min_stay":
                    return nights >= Integer.parseInt(value);
                case "vip_only":
                    return customer != null && customer.isVip();
                default:
                    return true;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
