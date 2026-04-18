package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.List;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

import quanlykhachsan.backend.model.MonthlyRevenue;
import quanlykhachsan.frontend.api.ReportAPI;
import quanlykhachsan.frontend.utils.ReportPDFExporter;
import quanlykhachsan.frontend.utils.ThemeManager;

public class ReportForm extends JPanel {

    private JLabel lblTotalRevenue;
    private JLabel lblTotalInvoices;
    private JLabel lblActiveAccounts;
    private JLabel lblStatus;
    
    private DefaultTableModel tableModel;
    private JTable reportTable;
    private javax.swing.table.TableRowSorter<DefaultTableModel> rowSorter;
    private JComboBox<String> cboYearFilter;
    
    private int activeAccountsCount = 0;
    
    private JPanel chartContainer;
    private ChartPanel chartPanel;

    private final Color PRIMARY   = ThemeManager.getPrimary();
    private final Color SUCCESS   = ThemeManager.getSuccess();
    private final Color WARNING   = ThemeManager.getWarning();
    private final Color DANGER    = ThemeManager.getDanger();
    private final Color BG_PANEL  = ThemeManager.getBgPanel();
    private final Color BORDER_CLR = ThemeManager.getBorderColor();
    private final Color MUTED = ThemeManager.getTextMuted();
    private final Color CARD_BG   = ThemeManager.getCardBg();
    private final Color TEXT_MAIN = ThemeManager.getTextMain();
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("#,###");

    public ReportForm() {
        setLayout(new BorderLayout(0, 0));
        setBackground(BG_PANEL);
        initUI();
        loadReport();
    }

    private void initUI() {
        // Status bar
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(CARD_BG);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER_CLR),
            new EmptyBorder(10, 16, 10, 16)
        ));
        JLabel pageTitle = new JLabel("Quản trị & Báo cáo Doanh Thu");
        pageTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        pageTitle.setForeground(TEXT_MAIN);
        
        lblStatus = new JLabel(" ");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        
        statusBar.add(pageTitle, BorderLayout.WEST);
        statusBar.add(lblStatus, BorderLayout.EAST);
        add(statusBar, BorderLayout.NORTH);

        // Center Content
        JPanel mainContent = new JPanel();
        mainContent.setLayout(new BoxLayout(mainContent, BoxLayout.Y_AXIS));
        mainContent.setBackground(BG_PANEL);
        mainContent.setBorder(new EmptyBorder(20, 20, 20, 20));

        // 1. Cards (Summary)
        JPanel pnlCards = new JPanel(new GridLayout(1, 3, 20, 0));
        pnlCards.setBackground(BG_PANEL);
        pnlCards.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        
        lblTotalRevenue = new JLabel("0 đ");
        lblTotalInvoices = new JLabel("0");
        
        pnlCards.add(createCard("Tổng Doanh Thu Gần Nhất", lblTotalRevenue, SUCCESS));
        pnlCards.add(createCard("Tổng Hóa Đơn", lblTotalInvoices, PRIMARY));
        
        lblActiveAccounts = new JLabel("0");
        pnlCards.add(createCard("Tài Khoản Kích Hoạt", lblActiveAccounts, WARNING));

        mainContent.add(pnlCards);
        mainContent.add(Box.createVerticalStrut(20));

        // 2. Body - Layout: Trái (Chart) - Phải (Table & Placeholder)
        JPanel pnlBody = new JPanel(new BorderLayout(20, 0));
        pnlBody.setBackground(BG_PANEL);

        // --- Chart Area ---
        chartContainer = new JPanel(new BorderLayout());
        chartContainer.setBackground(CARD_BG);
        chartContainer.setBorder(BorderFactory.createCompoundBorder(
            new RoundBorder(BORDER_CLR, 15),
            new EmptyBorder(10, 10, 10, 10)
        ));
        chartContainer.setPreferredSize(new Dimension(600, 400));
        
        String selectedYear = (cboYearFilter != null) ? (String) cboYearFilter.getSelectedItem() : "Tất cả";
        JLabel lblChartTitle = new JLabel("Biểu đồ Doanh thu " + selectedYear);
        lblChartTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblChartTitle.setForeground(TEXT_MAIN);
        lblChartTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        chartContainer.add(lblChartTitle, BorderLayout.NORTH);
        
        pnlBody.add(chartContainer, BorderLayout.CENTER);

        // --- Right Area (Table + Placeholder) ---
        JPanel pnlRight = new JPanel(new BorderLayout(0, 20));
        pnlRight.setBackground(BG_PANEL);
        pnlRight.setPreferredSize(new Dimension(500, 400));

        // Bảng
        tableModel = new DefaultTableModel(
            new String[]{"Tháng", "Số HĐ", "Tiền T.Phòng", "Tiền Dịch vụ", "Tổng Cộng"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        reportTable = new JTable(tableModel);
        
        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer();
        headerRenderer.setBackground(ThemeManager.isDarkMode() ? new Color(30, 41, 59) : new Color(241, 245, 249));
        headerRenderer.setForeground(ThemeManager.isDarkMode() ? new Color(148, 163, 184) : new Color(71, 85, 105));
        headerRenderer.setFont(new Font("Segoe UI", Font.BOLD, 12));
        headerRenderer.setHorizontalAlignment(JLabel.LEFT);
        headerRenderer.setBorder(new EmptyBorder(12, 16, 12, 16));
        
        reportTable.getTableHeader().setDefaultRenderer(headerRenderer);
        reportTable.setRowHeight(45);
        reportTable.setBackground(CARD_BG);
        reportTable.setSelectionBackground(ThemeManager.isDarkMode() ? new Color(51, 65, 85) : new Color(226, 232, 240));
        reportTable.setSelectionForeground(TEXT_MAIN);
        reportTable.setGridColor(BORDER_CLR);
        
        DefaultTableCellRenderer rightRender = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? CARD_BG : ThemeManager.isDarkMode() ? new Color(15, 23, 42) : new Color(250, 250, 250));
                    c.setForeground(TEXT_MAIN);
                }
                ((JComponent) c).setBorder(new EmptyBorder(0, 16, 0, 16));
                setHorizontalAlignment(SwingConstants.RIGHT);
                return c;
            }
        };
        
        reportTable.getColumnModel().getColumn(1).setCellRenderer(rightRender);
        reportTable.getColumnModel().getColumn(2).setCellRenderer(rightRender);
        reportTable.getColumnModel().getColumn(3).setCellRenderer(rightRender);
        reportTable.getColumnModel().getColumn(4).setCellRenderer(rightRender);

        JScrollPane scrollTable = new JScrollPane(reportTable);
        scrollTable.setBorder(BorderFactory.createCompoundBorder(
            new RoundBorder(BORDER_CLR, 15),
            new EmptyBorder(5, 5, 5, 5)
        ));
        scrollTable.getViewport().setBackground(CARD_BG);
        
        pnlRight.add(scrollTable, BorderLayout.CENTER);

        // Filter & Export Area ở dưới cùng bên phải
        JPanel rightActionArea = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 15));
        rightActionArea.setBackground(CARD_BG);
        rightActionArea.setBorder(new RoundBorder(BORDER_CLR, 15));
        
        JLabel lblFilter = new JLabel("Lọc theo năm:");
        cboYearFilter = new JComboBox<>(new String[]{"Tất cả", "2026", "2025", "2024", "2023"});
        cboYearFilter.setPreferredSize(new Dimension(100, 30));
        
        JButton btnExportPdf = new JButton("Xuất PDF");
        btnExportPdf.setBackground(DANGER); // Red
        btnExportPdf.setForeground(Color.WHITE);
        btnExportPdf.setFocusPainted(false);
        btnExportPdf.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnExportPdf.setPreferredSize(new Dimension(100, 30));
        
        rightActionArea.add(lblFilter);
        rightActionArea.add(cboYearFilter);
        rightActionArea.add(btnExportPdf);

        pnlRight.add(rightActionArea, BorderLayout.SOUTH);
        
        // Sorter logic
        rowSorter = new javax.swing.table.TableRowSorter<>(tableModel);
        reportTable.setRowSorter(rowSorter);

        cboYearFilter.addActionListener(e -> {
            String selectedYearVal = (String) cboYearFilter.getSelectedItem();
            if ("Tất cả".equals(selectedYearVal)) {
                rowSorter.setRowFilter(null);
            } else {
                rowSorter.setRowFilter(javax.swing.RowFilter.regexFilter("^" + selectedYearVal, 0)); // Cột tháng là cột 0
            }
        });
        
        btnExportPdf.addActionListener(e -> {
            String filterInfo = (String) cboYearFilter.getSelectedItem();
            ReportPDFExporter.exportPDF(reportTable, filterInfo);
        });

        pnlBody.add(pnlRight, BorderLayout.EAST);
        
        mainContent.add(pnlBody);
        
        JScrollPane mainScroll = new JScrollPane(mainContent);
        mainScroll.setBorder(null);
        add(mainScroll, BorderLayout.CENTER);
    }

    private JPanel createCard(String title, JLabel valueLabel, Color color) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Color Background with extreme soft tint (alpha 25)
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 25));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                
                // Border
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);
                
                // Side bar accent (thinner and full height)
                g2.setColor(color);
                g2.fillRoundRect(0, 15, 4, getHeight() - 30, 4, 4);
            }
        };
        card.setOpaque(false);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(15, 20, 15, 20));

        JLabel lblTitle = new JLabel(title.toUpperCase());
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblTitle.setForeground(MUTED);
        
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        valueLabel.setForeground(TEXT_MAIN);

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private void loadReport() {
        lblStatus.setText("Đang tải dữ liệu...");
        SwingWorker<List<MonthlyRevenue>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<MonthlyRevenue> doInBackground() throws Exception {
                try { activeAccountsCount = ReportAPI.getActiveAccountCount(); } catch (Exception ignore) {}
                return ReportAPI.getMonthlyRevenue();
            }

            @Override
            protected void done() {
                try {
                    List<MonthlyRevenue> list = get();
                    tableModel.setRowCount(0);
                    
                    if (list == null || list.isEmpty()) {
                        lblStatus.setText("Không có dữ liệu báo cáo.");
                        return;
                    }

                    // Cập nhật thẻ Summary (tháng đầu tiên trong list - tháng gần nhất)
                    MonthlyRevenue latest = list.get(0);
                    lblTotalRevenue.setText(CURRENCY_FORMAT.format(latest.getGrossRevenue()) + " VNĐ");
                    lblTotalInvoices.setText(String.valueOf(latest.getTotalInvoices()));
                    lblActiveAccounts.setText(String.valueOf(activeAccountsCount));

                    DefaultCategoryDataset dataset = new DefaultCategoryDataset();

                    // Cập nhật Data cho Bảng và Biểu đồ
                    // Lặp ngược để biểu đồ hiển thị từ cũ đến mới
                    for (int i = list.size() - 1; i >= 0; i--) {
                        MonthlyRevenue mr = list.get(i);
                        tableModel.insertRow(0, new Object[]{
                            mr.getMonth(),
                            mr.getTotalInvoices(),
                            CURRENCY_FORMAT.format(mr.getTotalRoomRevenue()),
                            CURRENCY_FORMAT.format(mr.getTotalServiceRevenue()),
                            CURRENCY_FORMAT.format(mr.getGrossRevenue())
                        });

                        dataset.addValue(mr.getGrossRevenue(), "Tổng Doanh Thu", mr.getMonth());
                        dataset.addValue(mr.getTotalRoomRevenue(), "Tiền Phòng", mr.getMonth());
                        dataset.addValue(mr.getTotalServiceRevenue(), "Tiền Dịch vụ", mr.getMonth());
                    }

                    // Tạo biểu đồ JFreeChart
                    JFreeChart barChart = ChartFactory.createBarChart(
                        null, "Tháng", "VND", dataset, PlotOrientation.VERTICAL, false, true, false);
                    
                    barChart.setBackgroundPaint(CARD_BG);
                    
                    org.jfree.chart.plot.CategoryPlot plot = barChart.getCategoryPlot();
                    plot.setBackgroundPaint(CARD_BG);
                    plot.setOutlineVisible(false);
                    plot.setRangeGridlinePaint(BORDER_CLR);
                    plot.setDomainGridlinesVisible(false);

                    org.jfree.chart.axis.CategoryAxis domainAxis = plot.getDomainAxis();
                    domainAxis.setTickLabelPaint(MUTED);
                    domainAxis.setAxisLinePaint(BORDER_CLR);
                    
                    org.jfree.chart.axis.NumberAxis rangeAxis = (org.jfree.chart.axis.NumberAxis) plot.getRangeAxis();
                    rangeAxis.setTickLabelPaint(MUTED);
                    rangeAxis.setAxisLinePaint(BORDER_CLR);

                    org.jfree.chart.renderer.category.BarRenderer renderer = (org.jfree.chart.renderer.category.BarRenderer) plot.getRenderer();
                    renderer.setSeriesPaint(0, SUCCESS); // Tổng Doanh Thu
                    renderer.setSeriesPaint(1, PRIMARY); // Tiền Phòng
                    renderer.setSeriesPaint(2, WARNING); // Tiền Dịch Vụ
                    renderer.setBarPainter(new org.jfree.chart.renderer.category.StandardBarPainter()); // Remove gloss/shadow
                    renderer.setDrawBarOutline(false);

                    if (chartPanel != null) {
                        chartContainer.remove(chartPanel);
                    }
                    chartPanel = new ChartPanel(barChart);
                    chartPanel.setPreferredSize(new Dimension(500, 300));
                    chartContainer.add(chartPanel, BorderLayout.CENTER);
                    chartContainer.revalidate();
                    chartContainer.repaint();

                    lblStatus.setText("Dữ liệu đã được cập nhật.");

                } catch (Exception e) {
                    lblStatus.setText("Lỗi kết nối hoặc tải báo cáo!");
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }
    // Helper class for rounded borders
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
            return new Insets(radius / 2, radius / 2, radius / 2, radius / 2);
        }
    }
}
