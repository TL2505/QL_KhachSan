package quanlykhachsan.backend.model;

import java.util.List;
import java.util.Map;

public class DashboardData {
    private KPI kpi;
    private KPI comparisonKpi;
    
    private List<RevenueChartItem> revenueChartData;
    private List<OccupancyChartItem> occupancyChartData;
    private Map<String, Integer> marketSegmentData;
    
    // Bảng chi tiết hóa đơn
    private List<InvoiceDetail> invoices;

    public DashboardData() {
    }

    public KPI getKpi() { return kpi; }
    public void setKpi(KPI kpi) { this.kpi = kpi; }

    public KPI getComparisonKpi() { return comparisonKpi; }
    public void setComparisonKpi(KPI comparisonKpi) { this.comparisonKpi = comparisonKpi; }

    public List<RevenueChartItem> getRevenueChartData() { return revenueChartData; }
    public void setRevenueChartData(List<RevenueChartItem> revenueChartData) { this.revenueChartData = revenueChartData; }

    public List<OccupancyChartItem> getOccupancyChartData() { return occupancyChartData; }
    public void setOccupancyChartData(List<OccupancyChartItem> occupancyChartData) { this.occupancyChartData = occupancyChartData; }

    public Map<String, Integer> getMarketSegmentData() { return marketSegmentData; }
    public void setMarketSegmentData(Map<String, Integer> marketSegmentData) { this.marketSegmentData = marketSegmentData; }

    public List<InvoiceDetail> getInvoices() { return invoices; }
    public void setInvoices(List<InvoiceDetail> invoices) { this.invoices = invoices; }

    // Nested structures
    public static class KPI {
        private double adr;
        private double revPar;
        private double alos;
        private double occupancyRate;
        
        // Cần thêm doanh thu tổng vào kpi để tiện hiển thị
        private double totalRevenue;
        private double roomRevenue;
        private double serviceRevenue;

        public double getAdr() { return adr; }
        public void setAdr(double adr) { this.adr = adr; }

        public double getRevPar() { return revPar; }
        public void setRevPar(double revPar) { this.revPar = revPar; }

        public double getAlos() { return alos; }
        public void setAlos(double alos) { this.alos = alos; }

        public double getOccupancyRate() { return occupancyRate; }
        public void setOccupancyRate(double occupancyRate) { this.occupancyRate = occupancyRate; }

        public double getTotalRevenue() { return totalRevenue; }
        public void setTotalRevenue(double totalRevenue) { this.totalRevenue = totalRevenue; }

        public double getRoomRevenue() { return roomRevenue; }
        public void setRoomRevenue(double roomRevenue) { this.roomRevenue = roomRevenue; }

        public double getServiceRevenue() { return serviceRevenue; }
        public void setServiceRevenue(double serviceRevenue) { this.serviceRevenue = serviceRevenue; }
    }

    public static class RevenueChartItem {
        private String label; // Ngày hoặc Tháng
        private double roomRevenue;
        private double serviceRevenue;
        private double totalRevenue;

        public RevenueChartItem(String label, double roomRevenue, double serviceRevenue, double totalRevenue) {
            this.label = label;
            this.roomRevenue = roomRevenue;
            this.serviceRevenue = serviceRevenue;
            this.totalRevenue = totalRevenue;
        }

        public String getLabel() { return label; }
        public double getRoomRevenue() { return roomRevenue; }
        public double getServiceRevenue() { return serviceRevenue; }
        public double getTotalRevenue() { return totalRevenue; }
    }

    public static class OccupancyChartItem {
        private String label;
        private double rate;

        public OccupancyChartItem(String label, double rate) {
            this.label = label;
            this.rate = rate;
        }

        public String getLabel() { return label; }
        public double getRate() { return rate; }
    }

    public static class InvoiceDetail {
        private String id;
        private String date;
        private double roomFee;
        private double serviceFee;
        private double total;

        public InvoiceDetail(String id, String date, double roomFee, double serviceFee, double total) {
            this.id = id;
            this.date = date;
            this.roomFee = roomFee;
            this.serviceFee = serviceFee;
            this.total = total;
        }

        public String getId() { return id; }
        public String getDate() { return date; }
        public double getRoomFee() { return roomFee; }
        public double getServiceFee() { return serviceFee; }
        public double getTotal() { return total; }
    }
}
