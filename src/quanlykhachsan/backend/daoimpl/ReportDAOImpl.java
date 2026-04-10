package quanlykhachsan.backend.daoimpl;

import quanlykhachsan.backend.dao.ReportDAO;
import quanlykhachsan.backend.model.MonthlyRevenue;
import quanlykhachsan.backend.utils.DBconn;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class ReportDAOImpl implements ReportDAO {

    @Override
    public List<MonthlyRevenue> getMonthlyRevenue() {
        List<MonthlyRevenue> list = new ArrayList<>();
        String query = "SELECT month, total_invoices, total_room_revenue, total_service_revenue, gross_revenue FROM view_monthly_revenue";
        
        try (Connection con = DBconn.getConnection();
             PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                MonthlyRevenue mr = new MonthlyRevenue();
                mr.setMonth(rs.getString("month"));
                mr.setTotalInvoices(rs.getInt("total_invoices"));
                mr.setTotalRoomRevenue(rs.getDouble("total_room_revenue"));
                mr.setTotalServiceRevenue(rs.getDouble("total_service_revenue"));
                mr.setGrossRevenue(rs.getDouble("gross_revenue"));
                list.add(mr);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}
