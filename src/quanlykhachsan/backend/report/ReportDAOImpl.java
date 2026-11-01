package quanlykhachsan.backend.report;

import quanlykhachsan.backend.utils.DBconn;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.TreeMap;

public class ReportDAOImpl implements ReportDAO {

    @Override
    public AdminOverviewStats getAdminOverview() throws SQLException {
        AdminOverviewStats data = new AdminOverviewStats();
        try (Connection con = DBconn.getConnection()) {
            LocalDate businessDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
            try (PreparedStatement ps = con.prepareStatement("SELECT business_date FROM hotel_business_state WHERE id=1");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) businessDate = rs.getDate(1).toLocalDate();
            }

            LocalDate monthStart = businessDate.withDayOfMonth(1);
            LocalDate nextMonth = monthStart.plusMonths(1);
            LocalDate chartStart = monthStart.minusMonths(5);
            data.setBusinessDate(businessDate.toString());

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT COUNT(*), "
                    + "COALESCE(SUM(status NOT IN ('out_of_order','out_of_service')),0), "
                    + "COALESCE(SUM(status IN ('occupied_clean','occupied_dirty')),0), "
                    + "COALESCE(SUM(status='vacant_dirty'),0) FROM rooms");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    data.setTotalRooms(rs.getInt(1));
                    data.setSellableRooms(rs.getInt(2));
                    data.setOccupiedRooms(rs.getInt(3));
                    data.setRoomsNeedCleaning(rs.getInt(4));
                }
            }

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT COALESCE(SUM(status='pending'),0), "
                    + "COALESCE(SUM(DATE(check_in_date)=? AND status IN ('pending','confirmed')),0), "
                    + "COALESCE(SUM(DATE(check_out_date)=? AND status='checked_in'),0) FROM bookings")) {
                ps.setDate(1, Date.valueOf(businessDate));
                ps.setDate(2, Date.valueOf(businessDate));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        data.setPendingBookings(rs.getInt(1));
                        data.setArrivalsToday(rs.getInt(2));
                        data.setDeparturesToday(rs.getInt(3));
                    }
                }
            }

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT COALESCE(SUM(CASE WHEN business_date=? THEN amount ELSE 0 END),0), "
                    + "COALESCE(SUM(CASE WHEN business_date>=? AND business_date<? THEN amount ELSE 0 END),0) "
                    + "FROM folio_transactions WHERE transaction_type='credit' AND charge_type='payment'")) {
                ps.setDate(1, Date.valueOf(businessDate));
                ps.setDate(2, Date.valueOf(monthStart));
                ps.setDate(3, Date.valueOf(nextMonth));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        data.setCollectionsToday(rs.getBigDecimal(1));
                        data.setCollectionsThisMonth(rs.getBigDecimal(2));
                    }
                }
            }

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT COALESCE(SUM(CASE WHEN status='REFUNDED' AND resolved_at>=? AND resolved_at<? THEN amount ELSE 0 END),0), "
                    + "COALESCE(SUM(CASE WHEN status='MANUAL_REQUIRED' THEN amount ELSE 0 END),0), "
                    + "COALESCE(SUM(status='MANUAL_REQUIRED'),0) FROM booking_refunds")) {
                ps.setTimestamp(1, Timestamp.valueOf(monthStart.atStartOfDay()));
                ps.setTimestamp(2, Timestamp.valueOf(nextMonth.atStartOfDay()));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        data.setRefundsThisMonth(rs.getBigDecimal(1));
                        data.setOutstandingManualRefundAmount(rs.getBigDecimal(2));
                        data.setOutstandingManualRefundCount(rs.getInt(3));
                    }
                }
            }
            data.setRefundRate(percentage(data.getRefundsThisMonth(), data.getCollectionsThisMonth()));

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT COUNT(*), COALESCE(SUM(status='cancelled'),0) FROM bookings WHERE created_at>=? AND created_at<?")) {
                ps.setTimestamp(1, Timestamp.valueOf(monthStart.atStartOfDay()));
                ps.setTimestamp(2, Timestamp.valueOf(nextMonth.atStartOfDay()));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        data.setBookingsCreatedThisMonth(rs.getInt(1));
                        data.setCancelledBookingsThisMonth(rs.getInt(2));
                    }
                }
            }
            data.setCancellationRate(percentage(
                    BigDecimal.valueOf(data.getCancelledBookingsThisMonth()),
                    BigDecimal.valueOf(data.getBookingsCreatedThisMonth())));

            Map<String, BigDecimal> revenueByMonth = new TreeMap<>();
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT DATE_FORMAT(business_date,'%Y-%m') AS month, "
                    + "COALESCE(SUM(CASE WHEN transaction_type='debit' THEN amount "
                    + "WHEN transaction_type='credit' AND charge_type IN ('adjustment','refund') THEN -amount ELSE 0 END),0) AS revenue "
                    + "FROM folio_transactions WHERE business_date>=? AND business_date<? "
                    + "GROUP BY DATE_FORMAT(business_date,'%Y-%m') ORDER BY month")) {
                ps.setDate(1, Date.valueOf(chartStart));
                ps.setDate(2, Date.valueOf(nextMonth));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) revenueByMonth.put(rs.getString("month"), rs.getBigDecimal("revenue"));
                }
            }

            List<MonthlyRevenue> monthly = new ArrayList<>();
            DateTimeFormatter monthFormat = DateTimeFormatter.ofPattern("yyyy-MM");
            for (LocalDate month = chartStart; month.isBefore(nextMonth); month = month.plusMonths(1)) {
                String key = month.format(monthFormat);
                MonthlyRevenue item = new MonthlyRevenue();
                item.setMonth(key);
                item.setGrossRevenue(revenueByMonth.getOrDefault(key, BigDecimal.ZERO).doubleValue());
                monthly.add(item);
            }
            data.setRevenueByMonth(monthly);
        }
        return data;
    }

    private static BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() <= 0) return BigDecimal.ZERO;
        return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 1, RoundingMode.HALF_UP);
    }

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
                    if (status != null && status.toLowerCase(java.util.Locale.ROOT).startsWith("occupied")) occupied += count;
                }
            }
            stats.setRoomStatusCounts(counts);
            stats.setTotalRooms(total);
            stats.setOccupiedRooms(occupied);

            // 3. Pending Check-ins
            String q3 = "SELECT COUNT(*) FROM bookings WHERE DATE(check_in_date) = CURDATE() AND LOWER(status) IN ('pending', 'confirmed')";
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
