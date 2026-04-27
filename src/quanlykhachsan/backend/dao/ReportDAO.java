package quanlykhachsan.backend.dao;

import quanlykhachsan.backend.model.DailyStats;
import quanlykhachsan.backend.model.MonthlyRevenue;
import quanlykhachsan.backend.model.DashboardData;
import quanlykhachsan.backend.model.DashboardFilter;
import java.util.List;

public interface ReportDAO {
    List<MonthlyRevenue> getMonthlyRevenue();
    DailyStats getDailyStats();
    int getActiveAccountCount();
    DashboardData getDashboardData(DashboardFilter filter);
}
