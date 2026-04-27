package quanlykhachsan.backend.customer;

import quanlykhachsan.backend.customer.CustomerDAO;
import quanlykhachsan.backend.customer.LoyaltyHistoryDAO;
import quanlykhachsan.backend.customer.CustomerDAOImpl;
import quanlykhachsan.backend.customer.LoyaltyHistoryDAOImpl;
import quanlykhachsan.backend.customer.Customer;
import quanlykhachsan.backend.customer.LoyaltyHistory;

import java.util.List;

public class LoyaltyService {
    private CustomerDAO customerDAO = new CustomerDAOImpl();
    private LoyaltyHistoryDAO historyDAO = new LoyaltyHistoryDAOImpl();

    private static final int GOLD_MIN = 1000;
    private static final int VIP_MIN = 5000;

    public boolean addPoints(int customerId, double amount, String description) throws Exception {
        int points = (int) (amount / 1000);
        if (points <= 0) return true;

        Customer customer = customerDAO.findById(customerId);
        if (customer == null) return false;

        int newTotalPoints = customer.getTotalLoyaltyPoints() + points;
        String newLevel = calculateLevel(newTotalPoints);

        customerDAO.updateLoyaltyPoints(customerId, points, points, newLevel);

        LoyaltyHistory history = new LoyaltyHistory();
        history.setCustomerId(customerId);
        history.setPointsChange(points);
        history.setType("earn");
        history.setDescription(description + " (Earned " + points + " points)");
        historyDAO.addHistory(history);

        return true;
    }

    public boolean redeemPoints(int customerId, int pointsToRedeem, double discountAmount, String description) throws Exception {
        Customer customer = customerDAO.findById(customerId);
        if (customer == null || customer.getLoyaltyPoints() < pointsToRedeem) return false;

        // updateLoyaltyPoints(id, current_pts_change, total_pts_change, newLevel)
        // Redeem reduces current points but total points (for tier) stays the same.
        customerDAO.updateLoyaltyPoints(customerId, -pointsToRedeem, 0, customer.getLoyaltyLevel());

        LoyaltyHistory history = new LoyaltyHistory();
        history.setCustomerId(customerId);
        history.setPointsChange(-pointsToRedeem);
        history.setType("redeem");
        history.setDescription(description + " (Redeemed " + pointsToRedeem + " points for " + discountAmount + " VND discount)");
        historyDAO.addHistory(history);

        return true;
    }

    public List<LoyaltyHistory> getHistory(int customerId) {
        return historyDAO.findByCustomerId(customerId);
    }

    private String calculateLevel(int totalPoints) {
        if (totalPoints >= VIP_MIN)  return "VIP";
        if (totalPoints >= GOLD_MIN) return "Gold";
        if (totalPoints >= 500)      return "Silver";
        return "Member";
    }
}
