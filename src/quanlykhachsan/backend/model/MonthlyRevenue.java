package quanlykhachsan.backend.model;

public class MonthlyRevenue {
    private String month;
    private int totalInvoices;
    private double totalRoomRevenue;
    private double totalServiceRevenue;
    private double grossRevenue;

    public MonthlyRevenue() {}

    public MonthlyRevenue(String month, int totalInvoices, double totalRoomRevenue, double totalServiceRevenue, double grossRevenue) {
        this.month = month;
        this.totalInvoices = totalInvoices;
        this.totalRoomRevenue = totalRoomRevenue;
        this.totalServiceRevenue = totalServiceRevenue;
        this.grossRevenue = grossRevenue;
    }

    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }

    public int getTotalInvoices() { return totalInvoices; }
    public void setTotalInvoices(int totalInvoices) { this.totalInvoices = totalInvoices; }

    public double getTotalRoomRevenue() { return totalRoomRevenue; }
    public void setTotalRoomRevenue(double totalRoomRevenue) { this.totalRoomRevenue = totalRoomRevenue; }

    public double getTotalServiceRevenue() { return totalServiceRevenue; }
    public void setTotalServiceRevenue(double totalServiceRevenue) { this.totalServiceRevenue = totalServiceRevenue; }

    public double getGrossRevenue() { return grossRevenue; }
    public void setGrossRevenue(double grossRevenue) { this.grossRevenue = grossRevenue; }
}
