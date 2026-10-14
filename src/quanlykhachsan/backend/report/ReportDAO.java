package quanlykhachsan.backend.report;

import java.util.List;

public interface ReportDAO {
    List<MonthlyRevenue> getMonthlyRevenue();
    DailyStats getDailyStats();
    int getActiveAccountCount();
    DashboardData getDashboardData(DashboardFilter filter);
}
