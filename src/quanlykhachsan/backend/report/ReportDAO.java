package quanlykhachsan.backend.report;

import java.sql.SQLException;
import java.util.List;

public interface ReportDAO {
    List<MonthlyRevenue> getMonthlyRevenue();
    DailyStats getDailyStats();
    AdminOverviewStats getAdminOverview() throws SQLException;
    int getActiveAccountCount();
    DashboardData getDashboardData(DashboardFilter filter);
}
