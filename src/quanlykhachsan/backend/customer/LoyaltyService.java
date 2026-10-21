package quanlykhachsan.backend.customer;


import java.util.List;
import java.util.NoSuchElementException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import quanlykhachsan.backend.utils.DBconn;

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

    /** Admin adjustments are serialized per member and committed with their audit entry. */
    public Customer adjustPoints(int customerId, int pointsChange, String reason, String actor) throws Exception {
        if (customerId <= 0 || pointsChange == 0 || reason == null || reason.isBlank() || reason.trim().length() > 200) {
            throw new IllegalArgumentException("Nhập thành viên, số điểm khác 0 và lý do từ 1 đến 200 ký tự.");
        }
        try (Connection con = DBconn.getConnection()) {
            con.setAutoCommit(false);
            try {
                Customer customer = new Customer();
                try (PreparedStatement select = con.prepareStatement("SELECT id, loyalty_points, total_loyalty_points FROM customers WHERE id = ? FOR UPDATE")) {
                    select.setInt(1, customerId);
                    try (ResultSet rs = select.executeQuery()) {
                        if (!rs.next()) throw new NoSuchElementException("Không tìm thấy thành viên.");
                        long balance = (long) rs.getInt("loyalty_points") + pointsChange;
                        long total = (long) rs.getInt("total_loyalty_points") + Math.max(0, pointsChange);
                        if (balance < 0) throw new IllegalStateException("Số điểm trừ vượt quá số dư hiện có.");
                        if (balance > Integer.MAX_VALUE || total > Integer.MAX_VALUE) throw new IllegalArgumentException("Số điểm vượt quá giới hạn cho phép.");
                        customer.setId(customerId);
                        customer.setLoyaltyPoints((int) balance);
                        customer.setTotalLoyaltyPoints((int) total);
                        customer.setLoyaltyLevel(calculateLevel((int) total));
                    }
                }
                try (PreparedStatement update = con.prepareStatement("UPDATE customers SET loyalty_points = ?, total_loyalty_points = ?, loyalty_level = ? WHERE id = ?")) {
                    update.setInt(1, customer.getLoyaltyPoints());
                    update.setInt(2, customer.getTotalLoyaltyPoints());
                    update.setString(3, customer.getLoyaltyLevel());
                    update.setInt(4, customerId);
                    update.executeUpdate();
                }
                try (PreparedStatement history = con.prepareStatement("INSERT INTO loyalty_histories (customer_id, points, type, description) VALUES (?, ?, ?, ?)")) {
                    history.setInt(1, customerId);
                    history.setInt(2, pointsChange);
                    history.setString(3, pointsChange > 0 ? "earn" : "redeem");
                    String audit = "Admin #" + actor + ": " + reason.trim();
                    history.setString(4, audit.substring(0, Math.min(255, audit.length())));
                    history.executeUpdate();
                }
                con.commit();
                return customer;
            } catch (Exception error) {
                con.rollback();
                throw error;
            }
        }
    }

    private String calculateLevel(int totalPoints) {
        if (totalPoints >= VIP_MIN)  return "VIP";
        if (totalPoints >= GOLD_MIN) return "Gold";
        if (totalPoints >= 500)      return "Silver";
        return "Member";
    }
}
