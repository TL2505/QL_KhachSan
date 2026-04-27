package quanlykhachsan.backend.report;

import quanlykhachsan.backend.report.DailyStats;
import quanlykhachsan.backend.report.MonthlyRevenue;
import quanlykhachsan.backend.report.DashboardData;
import quanlykhachsan.backend.report.DashboardFilter;
import java.util.List;

public interface ReportDAO {
    List<MonthlyRevenue> getMonthlyRevenue();
    DailyStats getDailyStats();
    int getActiveAccountCount();
    DashboardData getDashboardData(DashboardFilter filter);
}
