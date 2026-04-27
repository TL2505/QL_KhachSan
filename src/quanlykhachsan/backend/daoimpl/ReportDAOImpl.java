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
import java.sql.Date;
import quanlykhachsan.backend.model.DashboardData;
import quanlykhachsan.backend.model.DashboardFilter;
import java.util.LinkedHashMap;

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
    @Override
    public DashboardData getDashboardData(DashboardFilter filter) {
        DashboardData data = new DashboardData();
        DashboardData.KPI kpi = new DashboardData.KPI();
        Map<String, Integer> segmentData = new HashMap<>();
        List<DashboardData.RevenueChartItem> revList = new ArrayList<>();
        List<DashboardData.OccupancyChartItem> occList = new ArrayList<>();
        List<DashboardData.InvoiceDetail> invList = new ArrayList<>();

        StringBuilder baseWhere = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (filter != null) {
            if (filter.getFromDate() != null) {
                baseWhere.append(" AND b.check_in_date >= ? ");
                params.add(new Date(filter.getFromDate().getTime()));
            }
            if (filter.getToDate() != null) {
                baseWhere.append(" AND b.check_in_date <= ? ");
                params.add(new Date(filter.getToDate().getTime()));
            }
            if (filter.getRoomTypeId() != null && filter.getRoomTypeId() > 0) {
                baseWhere.append(" AND r.room_type_id = ? ");
                params.add(filter.getRoomTypeId());
            }
        }

        try (Connection con = DBconn.getConnection()) {
            // 1. Calculate KPIs & Overalls
            // RevPAR requires Total Rooms. Occupancy requires Total Rooms.
            // Notice: Total Rooms = total rooms active in DB.
            int totalRooms = 0;
            String qRooms = "SELECT COUNT(id) FROM rooms WHERE status != 'out_of_service'";
            try (PreparedStatement ps = con.prepareStatement(qRooms)) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) totalRooms = rs.getInt(1);
                }
            }

            int totalBookedRooms = 0;
            int totalBookings = 0;
            double totalRoomRev = 0;
            double totalServiceRev = 0;
            double totalGrossRev = 0;
            int totalNights = 0;

            String qBookingStats = "SELECT COUNT(b.id) as b_count, SUM(DATEDIFF(b.check_out_date, b.check_in_date)) as sum_nights, " +
                    "COUNT(DISTINCT b.room_id) as booked_rooms " +
                    "FROM bookings b JOIN rooms r ON b.room_id = r.id " + baseWhere.toString();
            try (PreparedStatement ps = con.prepareStatement(qBookingStats)) {
                for(int i=0; i<params.size(); i++) ps.setObject(i+1, params.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        totalBookings = rs.getInt("b_count");
                        totalNights = rs.getInt("sum_nights");
                        totalBookedRooms = rs.getInt("booked_rooms");
                    }
                }
            }

            String qRev = "SELECT SUM(i.total_room_fee) as r_rev, SUM(i.total_service_fee) as s_rev, SUM(i.final_total) as g_rev " +
                    "FROM invoices i JOIN bookings b ON i.booking_id = b.id JOIN rooms r ON b.room_id = r.id " +
                    baseWhere.toString() + " AND i.status = 'paid'";
            try (PreparedStatement ps = con.prepareStatement(qRev)) {
                for(int i=0; i<params.size(); i++) ps.setObject(i+1, params.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        totalRoomRev = rs.getDouble("r_rev");
                        totalServiceRev = rs.getDouble("s_rev");
                        totalGrossRev = rs.getDouble("g_rev");
                    }
                }
            }

            kpi.setRoomRevenue(totalRoomRev);
            kpi.setServiceRevenue(totalServiceRev);
            kpi.setTotalRevenue(totalGrossRev);
            
            // Occupancy Rate = (Số phòng đã đặt / Tổng số phòng) * 100
            if (totalRooms > 0) {
                kpi.setOccupancyRate((double) totalBookedRooms / totalRooms * 100.0);
            }
            
            // ADR = Tổng doanh thu phòng / Số phòng đã bán
            if (totalBookedRooms > 0) {
                kpi.setAdr(totalRoomRev / totalBookedRooms);
            }
            
            // RevPAR = Tổng doanh thu phòng / Tổng số phòng
            if (totalRooms > 0) {
                kpi.setRevPar(totalRoomRev / totalRooms);
            }
            
            // ALOS = Tổng số đêm lưu trú / Số booking
            if (totalBookings > 0) {
                kpi.setAlos((double) totalNights / totalBookings);
            }

            data.setKpi(kpi);

            // 2. Fetch Market Segments
            String qSegment = "SELECT b.customer_type, COUNT(b.id) " +
                    "FROM bookings b JOIN rooms r ON b.room_id = r.id " + baseWhere.toString() + " GROUP BY b.customer_type";
            try (PreparedStatement ps = con.prepareStatement(qSegment)) {
                for(int i=0; i<params.size(); i++) ps.setObject(i+1, params.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while(rs.next()) {
                        String type = rs.getString(1);
                        if(type == null) type = "Walk-in";
                        segmentData.put(type, rs.getInt(2));
                    }
                }
            }
            data.setMarketSegmentData(segmentData);

            // 3. Revenue Chart (Gruoped by Month for simplicity)
            String qRevChart = "SELECT DATE_FORMAT(b.check_in_date, '%Y-%m') as m, SUM(i.total_room_fee) as r, " +
                    "SUM(i.total_service_fee) as s, SUM(i.final_total) as t " +
                    "FROM invoices i JOIN bookings b ON i.booking_id = b.id JOIN rooms r ON b.room_id = r.id " +
                    baseWhere.toString() + " AND i.status = 'paid' GROUP BY DATE_FORMAT(b.check_in_date, '%Y-%m') ORDER BY m ASC";
            try (PreparedStatement ps = con.prepareStatement(qRevChart)) {
                for(int i=0; i<params.size(); i++) ps.setObject(i+1, params.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while(rs.next()) {
                        revList.add(new DashboardData.RevenueChartItem(
                                rs.getString("m"), rs.getDouble("r"), rs.getDouble("s"), rs.getDouble("t")
                        ));
                    }
                }
            }
            data.setRevenueChartData(revList);

            // 4. Invoices Table output
            String qInv = "SELECT i.id, b.check_in_date, i.total_room_fee, i.total_service_fee, i.final_total " +
                    "FROM invoices i JOIN bookings b ON i.booking_id = b.id JOIN rooms r ON b.room_id = r.id " +
                    baseWhere.toString() + " AND i.status = 'paid' ORDER BY b.check_in_date DESC LIMIT 100";
            try (PreparedStatement ps = con.prepareStatement(qInv)) {
                for(int i=0; i<params.size(); i++) ps.setObject(i+1, params.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while(rs.next()) {
                        invList.add(new DashboardData.InvoiceDetail(
                                String.valueOf(rs.getInt("id")),
                                rs.getString("check_in_date"),
                                rs.getDouble("total_room_fee"),
                                rs.getDouble("total_service_fee"),
                                rs.getDouble("final_total")
                        ));
                    }
                }
            }
            data.setInvoices(invList);
            
            // Occupancy chart can be derived from monthly groupings similarly
            String qOccChart = "SELECT DATE_FORMAT(b.check_in_date, '%Y-%m') as m, COUNT(DISTINCT b.room_id) as booked_rooms " +
                    "FROM bookings b JOIN rooms r ON b.room_id = r.id " + baseWhere.toString() + " GROUP BY DATE_FORMAT(b.check_in_date, '%Y-%m') ORDER BY m ASC";
            try (PreparedStatement ps = con.prepareStatement(qOccChart)) {
                for(int i=0; i<params.size(); i++) ps.setObject(i+1, params.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while(rs.next()) {
                        double rate = totalRooms > 0 ? ((double)rs.getInt("booked_rooms") / totalRooms * 100.0) : 0;
                        occList.add(new DashboardData.OccupancyChartItem(rs.getString("m"), rate));
                    }
                }
            }
            data.setOccupancyChartData(occList);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return data;
    }
}
