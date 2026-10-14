package quanlykhachsan.backend.customer;

import java.util.List;

public interface LoyaltyHistoryDAO {
    public void addHistory(LoyaltyHistory history);
    public List<LoyaltyHistory> findByCustomerId(int customerId);
    public List<LoyaltyHistory> findAll();
}
