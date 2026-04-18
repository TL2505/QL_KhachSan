package quanlykhachsan.backend.dao;

import java.util.List;
import quanlykhachsan.backend.model.LoyaltyHistory;

public interface LoyaltyHistoryDAO {
    public void addHistory(LoyaltyHistory history);
    public List<LoyaltyHistory> findByCustomerId(int customerId);
    public List<LoyaltyHistory> findAll();
}
