package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.general.DefaultPieDataset;

import quanlykhachsan.backend.model.DailyStats;
import quanlykhachsan.frontend.api.ReportAPI;

public class AdminDashboard extends JPanel {

    private JLabel lblRevenue, lblOccupancy, lblCheckIn, lblCheckOut;
    private JPanel chartContainer;
    private ChartPanel pieChartPanel;

    private static final Color PRIMARY   = new Color(37, 99, 235); // Blue 600
    private static final Color SUCCESS   = new Color(34, 197, 94); // Green 500
    private static final Color WARNING   = new Color(234, 179, 8);  // Yellow 500
    private static final Color DANGER    = new Color(239, 68, 68);  // Red 500
    private static final Color BG        = new Color(248, 250, 252); // Slate 50
    private static final Color CARD_BG   = Color.WHITE;
    private static final Color BORDER_C  = new Color(226, 232, 240); // Slate 200

    private java.util.function.Consumer<Integer> tabSwitcher;

    public AdminDashboard(java.util.function.Consumer<Integer> tabSwitcher) {
        this.tabSwitcher = tabSwitcher;
        setLayout(new BorderLayout(20, 20));
        setBackground(BG);
        setBorder(new EmptyBorder(30, 30, 30, 30));

        initUI();
        loadData();
    }

    private void initUI() {
        // --- Header ---
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel lblTitle = new JLabel("TỔNG QUAN VẬN HÀNH HÔM NAY");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitle.setForeground(new Color(15, 23, 42));
        header.add(lblTitle, BorderLayout.WEST);
        
        JButton btnRefresh = new JButton("🔄 Làm mới");
        btnRefresh.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRefresh.addActionListener(e -> loadData());
        header.add(btnRefresh, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // --- KPI Cards Grid ---
        JPanel cardGrid = new JPanel(new GridLayout(1, 4, 15, 0));
        cardGrid.setOpaque(false);
        cardGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        lblRevenue   = new JLabel("0 đ");
        lblOccupancy = new JLabel("0%");
        lblCheckIn   = new JLabel("0 lượt");
        lblCheckOut  = new JLabel("0 lượt");

        cardGrid.add(createKPICard("Doanh thu", lblRevenue, SUCCESS));
        cardGrid.add(createKPICard("Công suất phòng", lblOccupancy, PRIMARY));
        cardGrid.add(createKPICard("Lượt nhận phòng", lblCheckIn, WARNING));
        cardGrid.add(createKPICard("Lượt trả phòng", lblCheckOut, DANGER));

        // --- Main Content (Chart + Recent) ---
        JPanel body = new JPanel(new BorderLayout(25, 0));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(25, 0, 0, 0));

        // Left: Room Chart
        chartContainer = new JPanel(new BorderLayout());
        chartContainer.setBackground(CARD_BG);
        chartContainer.setBorder(new RoundBorder(BORDER_C, 15));
        chartContainer.setPreferredSize(new Dimension(500, 400));
        
        JLabel chartTitle = new JLabel(" Phân bổ trạng thái phòng");
        chartTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        chartTitle.setBorder(new EmptyBorder(15, 20, 10, 0));
        chartContainer.add(chartTitle, BorderLayout.NORTH);
        
        body.add(chartContainer, BorderLayout.CENTER);

        // Right: Quick Shortcuts
        JPanel quickActions = new JPanel();
        quickActions.setLayout(new BoxLayout(quickActions, BoxLayout.Y_AXIS));
        quickActions.setBackground(CARD_BG);
        quickActions.setBorder(new RoundBorder(BORDER_C, 15));
        quickActions.setPreferredSize(new Dimension(350, 400));

        JLabel actionTitle = new JLabel("Lối tắt nhanh");
        actionTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        actionTitle.setBorder(new EmptyBorder(15, 20, 15, 0));
        actionTitle.setAlignmentX(LEFT_ALIGNMENT);
        quickActions.add(actionTitle);

        JButton btnBooking = createActionBtn("Quản lý Đặt phòng");
        // For Admin, mapping "Booking" to "Room Layout" (index 1) since Booking is Staff-only in this version, 
        // or we could add Booking back for Admin if preferred.
        btnBooking.addActionListener(e -> { if(tabSwitcher != null) tabSwitcher.accept(1); });
        quickActions.add(btnBooking);

        JButton btnRooms = createActionBtn("Xem Sơ đồ phòng");
        btnRooms.addActionListener(e -> { if(tabSwitcher != null) tabSwitcher.accept(1); });
        quickActions.add(btnRooms);

        JButton btnCustomers = createActionBtn("Quản lý Khách hàng");
        btnCustomers.addActionListener(e -> { if(tabSwitcher != null) tabSwitcher.accept(2); });
        quickActions.add(btnCustomers);

        JButton btnReports = createActionBtn("Báo cáo Doanh thu");
        btnReports.addActionListener(e -> { if(tabSwitcher != null) tabSwitcher.accept(8); });
        quickActions.add(btnReports);

        body.add(quickActions, BorderLayout.EAST);

        // Assembler
        JPanel centerWrapper = new JPanel(new BorderLayout(0, 0));
        centerWrapper.setOpaque(false);
        centerWrapper.add(cardGrid, BorderLayout.NORTH);
        centerWrapper.add(body, BorderLayout.CENTER);

        add(centerWrapper, BorderLayout.CENTER);
    }

    private JPanel createKPICard(String title, JLabel valLabel, Color color) {
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
        lblTitle.setForeground(new Color(100, 116, 139));
        
        valLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        valLabel.setForeground(color);

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(valLabel, BorderLayout.CENTER);
        return card;
    }

    private JButton createActionBtn(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setForeground(new Color(51, 65, 85));
        
        btn.setBackground(CARD_BG);
        btn.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(5, 10, 5, 10),
            BorderFactory.createCompoundBorder(
                new RoundBorder(new Color(241, 245, 249), 10),
                new EmptyBorder(10, 20, 10, 20)
            )
        ));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(true);
        
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) { 
                btn.setBackground(new Color(241, 245, 249));
                btn.setForeground(PRIMARY);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) { 
                btn.setBackground(CARD_BG);
                btn.setForeground(new Color(51, 65, 85));
            }
        });
        return btn;
    }

    private void loadData() {
        new SwingWorker<DailyStats, Void>() {
            @Override protected DailyStats doInBackground() throws Exception {
                return ReportAPI.getTodayStats();
            }

            @Override protected void done() {
                try {
                    DailyStats stats = get();
                    NumberFormat vnFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
                    
                    lblRevenue.setText(vnFormat.format(stats.getRevenueToday()));
                    int occupancy = stats.getTotalRooms() > 0 ? (stats.getOccupiedRooms() * 100 / stats.getTotalRooms()) : 0;
                    lblOccupancy.setText(occupancy + "%");
                    lblCheckIn.setText(stats.getPendingCheckIns() + " lượt");
                    lblCheckOut.setText(stats.getPendingCheckOuts() + " lượt");

                    updateChart(stats.getRoomStatusCounts());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.execute();
    }

    private void updateChart(Map<String, Integer> data) {
        DefaultPieDataset dataset = new DefaultPieDataset();
        if (data != null) {
            data.forEach((k, v) -> {
                String label;
                switch(k.toLowerCase()) {
                    case "available": label = "Trống"; break;
                    case "occupied": label = "Có khách"; break;
                    case "cleaning": label = "Dọn dẹp"; break;
                    case "maintenance": label = "Bảo trì"; break;
                    case "booked": label = "Đã đặt"; break;
                    default: label = k; break;
                }
                dataset.setValue(label, v);
            });
        }

        JFreeChart pieChart = ChartFactory.createPieChart(null, dataset, true, true, false);
        pieChart.setBackgroundPaint(CARD_BG);
        pieChart.setBorderVisible(false);
        
        org.jfree.chart.plot.PiePlot plot = (org.jfree.chart.plot.PiePlot) pieChart.getPlot();
        plot.setBackgroundPaint(CARD_BG);
        plot.setOutlineVisible(false);
        plot.setLabelGenerator(null); // Hide labels on chart for cleaner look
        
        // Custom colors for slices
        plot.setSectionPaint("Trống", new Color(34, 197, 94));
        plot.setSectionPaint("Có khách", new Color(37, 99, 235));
        plot.setSectionPaint("Dọn dẹp", new Color(234, 179, 8));
        plot.setSectionPaint("Bảo trì", new Color(239, 68, 68));
        plot.setSectionPaint("Đã đặt", new Color(168, 85, 247));

        if (pieChartPanel != null) chartContainer.remove(pieChartPanel);
        pieChartPanel = new ChartPanel(pieChart);
        pieChartPanel.setBackground(CARD_BG);
        pieChartPanel.setPreferredSize(new Dimension(400, 300));
        chartContainer.add(pieChartPanel, BorderLayout.CENTER);
        chartContainer.revalidate();
        chartContainer.repaint();
    }

    // Helper class for rounded borders
    private static class RoundBorder extends AbstractBorder {
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
