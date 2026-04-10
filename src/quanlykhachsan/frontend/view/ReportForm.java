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

public class ReportForm extends JPanel {

    private JLabel lblTotalRevenue;
    private JLabel lblTotalInvoices;
    private JLabel lblStatus;
    
    private DefaultTableModel tableModel;
    private JTable reportTable;
    
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
        pageTitle.setForeground(new Color(17, 24, 39));
        
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
        
        // Placeholder cho thẻ mới sau này
        JPanel placeholderCard = createCard("Tính năng mở rộng (Tương lai)", new JLabel("..."), new Color(156, 163, 175));
        pnlCards.add(placeholderCard);

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

        // Placeholder Area ở dưới cùng bên phải
        JPanel rightPlaceholder = new JPanel(new BorderLayout());
        rightPlaceholder.setBackground(Color.WHITE);
        rightPlaceholder.setBorder(new LineBorder(new Color(229, 231, 235), 1, true));
        rightPlaceholder.setPreferredSize(new Dimension(0, 100));
        JLabel lblPlaceholderDesc = new JLabel("Khu vực phát triển tính năng lọc & xuất PDF (Phase sau)", SwingConstants.CENTER);
        lblPlaceholderDesc.setForeground(MUTED);
        rightPlaceholder.add(lblPlaceholderDesc);

        pnlRight.add(rightPlaceholder, BorderLayout.SOUTH);

        pnlBody.add(pnlRight, BorderLayout.EAST);
        
        mainContent.add(pnlBody);
        
        JScrollPane mainScroll = new JScrollPane(mainContent);
        mainScroll.setBorder(null);
        add(mainScroll, BorderLayout.CENTER);
    }

    private JPanel createCard(String title, JLabel valueLabel, Color accentColor) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(4, 1, 1, 1, accentColor),
            new EmptyBorder(16, 16, 16, 16)
        ));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTitle.setForeground(MUTED);
        
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
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
