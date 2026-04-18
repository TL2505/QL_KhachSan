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

    private static final Color BG_PANEL = new Color(248, 250, 252);
    private static final Color BORDER_CLR = new Color(226, 232, 240);
    private static final Color MUTED = new Color(107, 114, 128);
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
        statusBar.setBackground(Color.WHITE);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER_CLR),
            new EmptyBorder(10, 16, 10, 16)
        ));
        JLabel pageTitle = new JLabel("Quản trị & Báo cáo Doanh Thu");
        pageTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        pageTitle.setForeground(new Color(15, 23, 42));
        
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
        
        pnlCards.add(createCard("Tổng Doanh Thu Gần Nhất", lblTotalRevenue, new Color(16, 185, 129)));
        pnlCards.add(createCard("Tổng Hóa Đơn", lblTotalInvoices, new Color(59, 130, 246)));
        
        lblActiveAccounts = new JLabel("0");
        pnlCards.add(createCard("Tài Khoản Kích Hoạt", lblActiveAccounts, new Color(245, 158, 11)));

        mainContent.add(pnlCards);
        mainContent.add(Box.createVerticalStrut(20));

        // 2. Body - Layout: Trái (Chart) - Phải (Table & Placeholder)
        JPanel pnlBody = new JPanel(new BorderLayout(20, 0));
        pnlBody.setBackground(BG_PANEL);

        // --- Chart Area ---
        chartContainer = new JPanel(new BorderLayout());
        chartContainer.setBackground(Color.WHITE);
        chartContainer.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_CLR, 1, true),
            new EmptyBorder(10, 10, 10, 10)
        ));
        chartContainer.setPreferredSize(new Dimension(600, 400));
        
        JLabel chartTitle = new JLabel("Biểu Đồ Doanh Thu");
        chartTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        chartContainer.add(chartTitle, BorderLayout.NORTH);
        
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
        reportTable.setRowHeight(30);
        reportTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        reportTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        
        DefaultTableCellRenderer rightRender = new DefaultTableCellRenderer();
        rightRender.setHorizontalAlignment(SwingConstants.RIGHT);
        reportTable.getColumnModel().getColumn(1).setCellRenderer(rightRender);
        reportTable.getColumnModel().getColumn(2).setCellRenderer(rightRender);
        reportTable.getColumnModel().getColumn(3).setCellRenderer(rightRender);
        reportTable.getColumnModel().getColumn(4).setCellRenderer(rightRender);

        JScrollPane scrollTable = new JScrollPane(reportTable);
        scrollTable.setBorder(BorderFactory.createLineBorder(BORDER_CLR));
        scrollTable.getViewport().setBackground(Color.WHITE);
        
        pnlRight.add(scrollTable, BorderLayout.CENTER);

        // Filter & Export Area ở dưới cùng bên phải
        JPanel rightActionArea = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 15));
        rightActionArea.setBackground(Color.WHITE);
        rightActionArea.setBorder(new LineBorder(BORDER_CLR, 1, true));
        
        JLabel lblFilter = new JLabel("Lọc theo năm:");
        cboYearFilter = new JComboBox<>(new String[]{"Tất cả", "2026", "2025", "2024", "2023"});
        cboYearFilter.setPreferredSize(new Dimension(100, 30));
        
        JButton btnExportPdf = new JButton("Xuất PDF");
        btnExportPdf.setBackground(new Color(239, 68, 68)); // Red
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
            String selectedYear = (String) cboYearFilter.getSelectedItem();
            if ("Tất cả".equals(selectedYear)) {
                rowSorter.setRowFilter(null);
            } else {
                rowSorter.setRowFilter(javax.swing.RowFilter.regexFilter("^" + selectedYear, 0)); // Cột tháng là cột 0
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

    private JPanel createCard(String title, JLabel valueLabel, Color accentColor) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(BORDER_CLR);
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 20, 20);
                
                g2.setColor(accentColor);
                g2.fillRoundRect(0, 20, 5, 40, 5, 5);
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(16, 25, 16, 16));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTitle.setForeground(MUTED);
        
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        valueLabel.setForeground(new Color(17, 24, 39));

        card.add(lblTitle);
        card.add(Box.createVerticalStrut(8));
        card.add(valueLabel);
        
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
                        "Tổng Quan Doanh Thu Các Tháng",
                        "Tháng",
                        "VNĐ",
                        dataset,
                        PlotOrientation.VERTICAL,
                        true, true, false
                    );
                    
                    barChart.setBackgroundPaint(Color.WHITE);
                    barChart.getPlot().setBackgroundPaint(new Color(248, 250, 252));

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
}
