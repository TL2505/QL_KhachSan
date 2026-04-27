package quanlykhachsan.frontend.view.admin;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.Map;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import com.toedter.calendar.JDateChooser;

import quanlykhachsan.backend.report.DashboardData;
import quanlykhachsan.backend.report.DashboardFilter;
import quanlykhachsan.frontend.api.ReportAPI;
import quanlykhachsan.frontend.utils.ReportPDFExporter;
import quanlykhachsan.frontend.utils.ThemeManager;

public class ReportForm extends JPanel {

    private JLabel lblStatus;
    
    // Components
    private JDateChooser dpFrom;
    private JDateChooser dpTo;
    private JComboBox<String> cboRoomType; // Mock options for now
    
    // KPIs
    private JLabel lblADR;
    private JLabel lblRevPAR;
    private JLabel lblALOS;
    private JLabel lblOcc;

    // Charts
    private JPanel chartBarContainer;
    private JPanel chartPieContainer;
    private ChartPanel barChartPanel;
    private ChartPanel pieChartPanel;

    // Table
    private DefaultTableModel tableModel;
    private JTable reportTable;

    // Colors
    private final Color PRIMARY   = ThemeManager.getPrimary();
    private final Color SUCCESS   = ThemeManager.getSuccess();
    private final Color WARNING   = ThemeManager.getWarning();
    private final Color DANGER    = ThemeManager.getDanger();
    private final Color BG_PANEL  = ThemeManager.getBgPanel();
    private final Color BORDER_CLR = ThemeManager.getBorderColor();
    private final Color MUTED     = ThemeManager.getTextMuted();
    private final Color CARD_BG   = ThemeManager.getCardBg();
    private final Color TEXT_MAIN = ThemeManager.getTextMain();
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("#,###");

    public ReportForm() {
        setLayout(new BorderLayout());
        setBackground(BG_PANEL);
        initUI();
        applyFilter(); // Initial load
    }

    private void initUI() {
        // Status bar
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(CARD_BG);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER_CLR),
            new EmptyBorder(10, 16, 10, 16)
        ));
        JLabel pageTitle = new JLabel("Báo cáo Phân tích Dữ liệu (Dashboard)");
        pageTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        pageTitle.setForeground(TEXT_MAIN);
        
        lblStatus = new JLabel(" ");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        
        statusBar.add(pageTitle, BorderLayout.WEST);
        statusBar.add(lblStatus, BorderLayout.EAST);
        add(statusBar, BorderLayout.NORTH);

        // Center Content Scrollable
        JPanel mainContent = new JPanel();
        mainContent.setLayout(new BoxLayout(mainContent, BoxLayout.Y_AXIS));
        mainContent.setBackground(BG_PANEL);
        mainContent.setBorder(new EmptyBorder(15, 15, 15, 15));

        // 1. FILTER PANEL
        mainContent.add(buildFilterPanel());
        mainContent.add(Box.createVerticalStrut(15));

        // 2. KPI CARDS
        mainContent.add(buildKpiPanel());
        mainContent.add(Box.createVerticalStrut(15));

        // 3. CHARTS AREA
        mainContent.add(buildChartsPanel());
        mainContent.add(Box.createVerticalStrut(15));

        // 4. TABLE AREA
        mainContent.add(buildTablePanel());

        JScrollPane mainScroll = new JScrollPane(mainContent);
        mainScroll.setBorder(null);
        mainScroll.getVerticalScrollBar().setUnitIncrement(16);
        add(mainScroll, BorderLayout.CENTER);
    }

    private JPanel buildFilterPanel() {
        JPanel pnl = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        pnl.setBackground(CARD_BG);
        pnl.setBorder(new RoundBorder(BORDER_CLR, 10));

        JLabel lblStart = new JLabel("Từ ngày:");
        lblStart.setForeground(TEXT_MAIN);
        dpFrom = new JDateChooser();
        dpFrom.setPreferredSize(new Dimension(130, 30));
        
        JLabel lblEnd = new JLabel("Đến ngày:");
        lblEnd.setForeground(TEXT_MAIN);
        dpTo = new JDateChooser();
        dpTo.setPreferredSize(new Dimension(130, 30));

        JLabel lblType = new JLabel("Loại phòng:");
        lblType.setForeground(TEXT_MAIN);
        // Map: All=0, Standard=1, Deluxe=2, Family=3
        cboRoomType = new JComboBox<>(new String[]{"Tất cả", "Standard", "Deluxe", "Family"});
        cboRoomType.setPreferredSize(new Dimension(120, 30));

        JButton btnApply = new JButton("Áp Dụng Lọc");
        btnApply.setBackground(PRIMARY);
        btnApply.setForeground(Color.WHITE);
        btnApply.setFocusPainted(false);
        btnApply.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnApply.addActionListener(e -> applyFilter());

        JButton btnExport = new JButton("Xuất PDF");
        btnExport.setBackground(DANGER);
        btnExport.setForeground(Color.WHITE);
        btnExport.setFocusPainted(false);
        btnExport.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnExport.addActionListener(e -> ReportPDFExporter.exportPDF(reportTable, "Báo Cáo Tùy Chỉnh"));

        pnl.add(lblStart); pnl.add(dpFrom);
        pnl.add(lblEnd); pnl.add(dpTo);
        pnl.add(lblType); pnl.add(cboRoomType);
        pnl.add(btnApply);
        pnl.add(Box.createHorizontalStrut(50));
        pnl.add(btnExport);

        return pnl;
    }

    private JPanel buildKpiPanel() {
        JPanel pnl = new JPanel(new GridLayout(1, 4, 15, 0));
        pnl.setBackground(BG_PANEL);
        pnl.setMaximumSize(new Dimension(4000, 110));

        lblADR = new JLabel("0 đ");
        lblRevPAR = new JLabel("0 đ");
        lblALOS = new JLabel("0 ngày");
        lblOcc = new JLabel("0 %");

        pnl.add(createCard("ADR (Giá trị trung bình/phòng)", lblADR, PRIMARY));
        pnl.add(createCard("RevPAR (Doanh thu/phòng sẵn có)", lblRevPAR, SUCCESS));
        pnl.add(createCard("ALOS (Độ dài lưu trú TB)", lblALOS, WARNING));
        pnl.add(createCard("Occupancy Rate (Lấp phòng)", lblOcc, new Color(139, 92, 246))); // Purple

        return pnl;
    }

    private JPanel buildChartsPanel() {
        JPanel pnl = new JPanel(new GridLayout(1, 2, 15, 0));
        pnl.setBackground(BG_PANEL);
        pnl.setPreferredSize(new Dimension(1200, 350));
        pnl.setMaximumSize(new Dimension(4000, 400));

        // LEFT: Bar Chart container
        chartBarContainer = new JPanel(new BorderLayout());
        chartBarContainer.setBackground(CARD_BG);
        chartBarContainer.setBorder(BorderFactory.createCompoundBorder(
            new RoundBorder(BORDER_CLR, 10), new EmptyBorder(10, 10, 10, 10)
        ));
        JLabel lblBarTitle = new JLabel("Doanh thu Tổng quát theo Kỳ");
        lblBarTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblBarTitle.setForeground(TEXT_MAIN);
        lblBarTitle.setBorder(new EmptyBorder(0, 0, 10, 0));
        chartBarContainer.add(lblBarTitle, BorderLayout.NORTH);

        // RIGHT: Pie Chart container
        chartPieContainer = new JPanel(new BorderLayout());
        chartPieContainer.setBackground(CARD_BG);
        chartPieContainer.setBorder(BorderFactory.createCompoundBorder(
            new RoundBorder(BORDER_CLR, 10), new EmptyBorder(10, 10, 10, 10)
        ));
        JLabel lblPieTitle = new JLabel("Phân khúc Thị trường Khách hàng (Market Segment)");
        lblPieTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblPieTitle.setForeground(TEXT_MAIN);
        lblPieTitle.setBorder(new EmptyBorder(0, 0, 10, 0));
        chartPieContainer.add(lblPieTitle, BorderLayout.NORTH);

        pnl.add(chartBarContainer);
        pnl.add(chartPieContainer);

        return pnl;
    }

    private JPanel buildTablePanel() {
        JPanel pnl = new JPanel(new BorderLayout());
        pnl.setBackground(CARD_BG);
        pnl.setBorder(new RoundBorder(BORDER_CLR, 10));
        pnl.setPreferredSize(new Dimension(1200, 300));
        
        JLabel lblTableTitle = new JLabel("Danh sách Hóa Đơn Chi Tiết");
        lblTableTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTableTitle.setForeground(TEXT_MAIN);
        lblTableTitle.setBorder(new EmptyBorder(10, 10, 10, 10));
        pnl.add(lblTableTitle, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(
            new String[]{"ID Hóa đơn", "Ngày Check-in", "Doanh thu Phòng", "Doanh thu Dịch vụ", "Tổng Kết"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        reportTable = new JTable(tableModel);
        
        // Styling table
        reportTable.setRowHeight(35);
        reportTable.setBackground(CARD_BG);
        reportTable.setForeground(TEXT_MAIN);
        reportTable.setSelectionBackground(ThemeManager.isDarkMode() ? new Color(51, 65, 85) : new Color(226, 232, 240));
        reportTable.setSelectionForeground(TEXT_MAIN);
        
        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer();
        headerRenderer.setBackground(ThemeManager.isDarkMode() ? new Color(30, 41, 59) : new Color(241, 245, 249));
        headerRenderer.setForeground(ThemeManager.isDarkMode() ? new Color(148, 163, 184) : new Color(71, 85, 105));
        headerRenderer.setFont(new Font("Segoe UI", Font.BOLD, 12));
        reportTable.getTableHeader().setDefaultRenderer(headerRenderer);
        
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        reportTable.getColumnModel().getColumn(2).setCellRenderer(rightRenderer);
        reportTable.getColumnModel().getColumn(3).setCellRenderer(rightRenderer);
        reportTable.getColumnModel().getColumn(4).setCellRenderer(rightRenderer);

        JScrollPane scroll = new JScrollPane(reportTable);
        scroll.setBorder(new MatteBorder(1, 0, 0, 0, BORDER_CLR));
        scroll.getViewport().setBackground(CARD_BG);
        pnl.add(scroll, BorderLayout.CENTER);

        return pnl;
    }

    private JPanel createCard(String title, JLabel valueLabel, Color color) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 30));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(color);
                g2.fillRoundRect(0, 15, 4, getHeight() - 30, 4, 4);
                
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            }
        };
        card.setOpaque(false);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(15, 20, 15, 20));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblTitle.setForeground(MUTED);
        
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        valueLabel.setForeground(TEXT_MAIN);

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private void applyFilter() {
        lblStatus.setText("Đang tải xử lý dữ liệu...");
        lblStatus.setForeground(MUTED);
        
        Date from = dpFrom.getDate();
        Date to = dpTo.getDate();
        int rtId = cboRoomType.getSelectedIndex(); 
        
        DashboardFilter filter = new DashboardFilter(from, to, rtId == 0 ? null : rtId);

        SwingWorker<DashboardData, Void> worker = new SwingWorker<>() {
            @Override
            protected DashboardData doInBackground() throws Exception {
                return ReportAPI.getDashboardData(filter);
            }

            @Override
            protected void done() {
                try {
                    DashboardData data = get();
                    if (data == null) {
                        lblStatus.setText("Không có dữ liệu!");
                        lblStatus.setForeground(DANGER);
                        return;
                    }

                    // 1. Update KPIs
                    DashboardData.KPI kpi = data.getKpi();
                    if (kpi != null) {
                        lblADR.setText(CURRENCY_FORMAT.format(kpi.getAdr()) + " đ");
                        lblRevPAR.setText(CURRENCY_FORMAT.format(kpi.getRevPar()) + " đ");
                        lblALOS.setText(String.format("%.1f", kpi.getAlos()) + " ngày");
                        lblOcc.setText(String.format("%.1f", kpi.getOccupancyRate()) + " %");
                    }

                    // 2. Bar Chart
                    DefaultCategoryDataset barDataset = new DefaultCategoryDataset();
                    if (data.getRevenueChartData() != null) {
                        for (DashboardData.RevenueChartItem item : data.getRevenueChartData()) {
                            barDataset.addValue(item.getTotalRevenue(), "Tổng Nguồn", item.getLabel());
                            barDataset.addValue(item.getRoomRevenue(), "Phòng", item.getLabel());
                            barDataset.addValue(item.getServiceRevenue(), "Dịch vụ", item.getLabel());
                        }
                    }

                    JFreeChart barChart = ChartFactory.createBarChart(
                        null, "Kỳ xuất HĐ", "VND", barDataset, PlotOrientation.VERTICAL, true, true, false);
                    barChart.setBackgroundPaint(CARD_BG);
                    if (barChart.getLegend() != null) {
                        barChart.getLegend().setBackgroundPaint(CARD_BG);
                        barChart.getLegend().setItemPaint(TEXT_MAIN);
                    }
                    org.jfree.chart.plot.CategoryPlot plot = barChart.getCategoryPlot();
                    plot.setBackgroundPaint(CARD_BG);
                    plot.setOutlineVisible(false);
                    plot.getDomainAxis().setTickLabelPaint(TEXT_MAIN);
                    plot.getDomainAxis().setLabelPaint(TEXT_MAIN);
                    plot.getDomainAxis().setAxisLinePaint(BORDER_CLR);
                    plot.getRangeAxis().setTickLabelPaint(TEXT_MAIN);
                    plot.getRangeAxis().setLabelPaint(TEXT_MAIN);
                    plot.getRangeAxis().setAxisLinePaint(BORDER_CLR);
                    
                    org.jfree.chart.renderer.category.BarRenderer renderer = (org.jfree.chart.renderer.category.BarRenderer) plot.getRenderer();
                    renderer.setBarPainter(new org.jfree.chart.renderer.category.StandardBarPainter());
                    renderer.setDrawBarOutline(false);
                    renderer.setShadowVisible(false);
                    renderer.setSeriesPaint(0, PRIMARY);
                    renderer.setSeriesPaint(1, SUCCESS);
                    renderer.setSeriesPaint(2, WARNING);

                    if (barChartPanel != null) chartBarContainer.remove(barChartPanel);
                    barChartPanel = new ChartPanel(barChart);
                    chartBarContainer.add(barChartPanel, BorderLayout.CENTER);
                    chartBarContainer.revalidate();
                    chartBarContainer.repaint();

                    // 3. Pie Chart
                    DefaultPieDataset pieDataset = new DefaultPieDataset();
                    if (data.getMarketSegmentData() != null) {
                        for (Map.Entry<String, Integer> entry : data.getMarketSegmentData().entrySet()) {
                            pieDataset.setValue(entry.getKey(), entry.getValue());
                        }
                    }

                    JFreeChart pieChart = ChartFactory.createPieChart(
                        null, pieDataset, true, true, false);
                    pieChart.setBackgroundPaint(CARD_BG);
                    if (pieChart.getLegend() != null) {
                        pieChart.getLegend().setBackgroundPaint(CARD_BG);
                        pieChart.getLegend().setItemPaint(TEXT_MAIN);
                    }
                    PiePlot piePlot = (PiePlot) pieChart.getPlot();
                    piePlot.setBackgroundPaint(CARD_BG);
                    piePlot.setOutlineVisible(false);
                    piePlot.setShadowXOffset(0);
                    piePlot.setShadowYOffset(0);
                    piePlot.setLabelBackgroundPaint(CARD_BG);
                    piePlot.setLabelOutlinePaint(BORDER_CLR);
                    piePlot.setLabelShadowPaint(null);
                    piePlot.setLabelPaint(TEXT_MAIN);
                    piePlot.setLabelLinkPaint(TEXT_MAIN);
                    piePlot.setSectionOutlinesVisible(false);

                    if (pieChartPanel != null) chartPieContainer.remove(pieChartPanel);
                    pieChartPanel = new ChartPanel(pieChart);
                    chartPieContainer.add(pieChartPanel, BorderLayout.CENTER);
                    chartPieContainer.revalidate();
                    chartPieContainer.repaint();

                    // 4. Update Table
                    tableModel.setRowCount(0);
                    if (data.getInvoices() != null) {
                        for (DashboardData.InvoiceDetail inv : data.getInvoices()) {
                            tableModel.addRow(new Object[]{
                                "INV-" + inv.getId(),
                                inv.getDate(),
                                CURRENCY_FORMAT.format(inv.getRoomFee()) + " đ",
                                CURRENCY_FORMAT.format(inv.getServiceFee()) + " đ",
                                CURRENCY_FORMAT.format(inv.getTotal()) + " đ"
                            });
                        }
                    }

                    lblStatus.setText("Dữ liệu phân tích đã cập nhật!");
                    lblStatus.setForeground(SUCCESS);

                } catch (Exception e) {
                    e.printStackTrace();
                    lblStatus.setText("Lỗi mạng hiển thị báo cáo!");
                    lblStatus.setForeground(DANGER);
                }
            }
        };
        worker.execute();
    }

    private static class RoundBorder extends javax.swing.border.AbstractBorder {
        private final Color color;
        private final int radius;
        public RoundBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
        }
        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(1, 1, 1, 1);
        }
    }
}
