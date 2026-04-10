package quanlykhachsan.backend.dao;

import quanlykhachsan.backend.model.MonthlyRevenue;
import java.util.List;

public interface ReportDAO {
    List<MonthlyRevenue> getMonthlyRevenue();
}
