package quanlykhachsan.backend.customer;

import java.util.List;
import quanlykhachsan.backend.customer.LoyaltyHistory;

public interface LoyaltyHistoryDAO {
    public void addHistory(LoyaltyHistory history);
    public List<LoyaltyHistory> findByCustomerId(int customerId);
    public List<LoyaltyHistory> findAll();
}
