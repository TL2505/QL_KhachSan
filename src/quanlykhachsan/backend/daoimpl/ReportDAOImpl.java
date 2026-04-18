package quanlykhachsan.backend.daoimpl;

import quanlykhachsan.backend.dao.ReportDAO;
import quanlykhachsan.backend.model.DailyStats;
import quanlykhachsan.backend.model.MonthlyRevenue;
import quanlykhachsan.backend.utils.DBconn;
import java.util.HashMap;
import java.util.Map;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class ReportDAOImpl implements ReportDAO {

    @Override
    public List<MonthlyRevenue> getMonthlyRevenue() {
        List<MonthlyRevenue> list = new ArrayList<>();
        String query = "SELECT month, total_invoices, total_room_revenue, total_service_revenue, gross_revenue FROM v_monthly_revenue";
        
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

    @Override
    public DailyStats getDailyStats() {
        DailyStats stats = new DailyStats();
        try (Connection con = DBconn.getConnection()) {
            // 1. Revenue Today
            String q1 = "SELECT SUM(amount) FROM payments WHERE DATE(payment_date) = CURDATE()";
            try (PreparedStatement ps = con.prepareStatement(q1); ResultSet rs = ps.executeQuery()) {
                if (rs.next()) stats.setRevenueToday(rs.getDouble(1));
            }

            // 2. Room Counts
            String q2 = "SELECT status, COUNT(*) FROM rooms GROUP BY status";
            Map<String, Integer> counts = new HashMap<>();
            int total = 0;
            int occupied = 0;
            try (PreparedStatement ps = con.prepareStatement(q2); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString(1);
                    int count = rs.getInt(2);
                    counts.put(status, count);
                    total += count;
                    if ("occupied".equals(status)) occupied = count;
                }
            }
            stats.setRoomStatusCounts(counts);
            stats.setTotalRooms(total);
            stats.setOccupiedRooms(occupied);

            // 3. Pending Check-ins
            String q3 = "SELECT COUNT(*) FROM bookings WHERE DATE(check_in_date) = CURDATE() AND status IN ('pending', 'confirmed')";
            try (PreparedStatement ps = con.prepareStatement(q3); ResultSet rs = ps.executeQuery()) {
                if (rs.next()) stats.setPendingCheckIns(rs.getInt(1));
            }

            // 4. Pending Check-outs
            String q4 = "SELECT COUNT(*) FROM bookings WHERE DATE(check_out_date) = CURDATE() AND status = 'checked_in'";
            try (PreparedStatement ps = con.prepareStatement(q4); ResultSet rs = ps.executeQuery()) {
                if (rs.next()) stats.setPendingCheckOuts(rs.getInt(1));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return stats;
    }

    @Override
    public int getActiveAccountCount() {
        int count = 0;
        String query = "SELECT COUNT(*) FROM users WHERE LOWER(status) IN ('active', '1', 'kích hoạt')";
        try (Connection con = DBconn.getConnection();
             PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                count = rs.getInt(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return count;
    }
}
