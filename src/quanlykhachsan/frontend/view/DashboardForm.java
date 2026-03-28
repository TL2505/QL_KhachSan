package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.DecimalFormat;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import quanlykhachsan.frontend.api.DashboardAPI;

public class DashboardForm extends JPanel {

    private JLabel lblRevenue, lblOccupied, lblCustomers, lblBookings;
    private JProgressBar barAvailable, barOccupied, barDirty, barMaintenance;
    private JTable recentActivityTable;
    private DefaultTableModel recentActivityModel;
    private Timer autoRefreshTimer;

    public DashboardForm() {
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(new Color(245, 245, 250));

        initUI();
        initTimer();
        refreshDashboard();
    }

    private void initTimer() {
        // Tự động làm mới mỗi 10 giây
        autoRefreshTimer = new Timer(10000, e -> {
            if (isShowing()) {
                refreshDashboard();
            }
        });
        autoRefreshTimer.start();
    }

    private void initUI() {
        // --- TOP: SUMMARY CARDS ---
        JPanel cardPanel = new JPanel(new GridLayout(1, 4, 15, 0));
        cardPanel.setOpaque(false);

        lblRevenue = createStatCard(cardPanel, "💰 Doanh thu hôm nay", "0 VNĐ", new Color(46, 204, 113));
        lblOccupied = createStatCard(cardPanel, "🏨 Phòng đang có khách", "0", new Color(52, 152, 219));
        lblCustomers = createStatCard(cardPanel, "👥 Tổng khách hàng", "0", new Color(155, 89, 182));
        lblBookings = createStatCard(cardPanel, "📅 Lượt đặt hôm nay", "0", new Color(230, 126, 34));

        add(cardPanel, BorderLayout.NORTH);

        // --- CENTER: MAIN CONTENT ---
        JPanel mainContent = new JPanel(new GridLayout(1, 2, 20, 0));
        mainContent.setOpaque(false);

        // LEFT: Room Status Breakdown
        JPanel roomStatusPanel = new JPanel();
        roomStatusPanel.setLayout(new BoxLayout(roomStatusPanel, BoxLayout.Y_AXIS));
        roomStatusPanel.setBorder(BorderFactory.createTitledBorder("📊 Phân bổ trạng thái phòng"));
        roomStatusPanel.setBackground(Color.WHITE);

        barAvailable = createProgressBar(roomStatusPanel, "Phòng Trống (Available)", Color.GREEN);
        barOccupied = createProgressBar(roomStatusPanel, "Đang ở (Occupied)", Color.RED);
        barDirty = createProgressBar(roomStatusPanel, "Cần dọn dẹp (Dirty)", Color.ORANGE);
        barMaintenance = createProgressBar(roomStatusPanel, "Bảo trì (Maintenance)", Color.GRAY);

        mainContent.add(roomStatusPanel);

        // RIGHT: Recent Activity
        JPanel activityPanel = new JPanel(new BorderLayout());
        activityPanel.setBorder(BorderFactory.createTitledBorder("🕒 Hoạt động gần đây"));
        activityPanel.setBackground(Color.WHITE);

        recentActivityModel = new DefaultTableModel(new String[]{"ID", "Phòng ID", "Khách ID", "Trạng thái"}, 0);
        recentActivityTable = new JTable(recentActivityModel);
        activityPanel.add(new JScrollPane(recentActivityTable), BorderLayout.CENTER);

        mainContent.add(activityPanel);

        add(mainContent, BorderLayout.CENTER);
    }

    private JLabel createStatCard(JPanel parent, String title, String value, Color color) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(230, 230, 230), 1),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Arial", Font.PLAIN, 14));
        lblTitle.setForeground(Color.GRAY);
        card.add(lblTitle, BorderLayout.NORTH);

        JLabel lblValue = new JLabel(value);
        lblValue.setFont(new Font("Arial", Font.BOLD, 22));
        lblValue.setForeground(color);
        card.add(lblValue, BorderLayout.CENTER);

        parent.add(card);
        return lblValue;
    }

    private JProgressBar createProgressBar(JPanel parent, String label, Color color) {
        parent.add(Box.createVerticalStrut(10));
        parent.add(new JLabel(label));
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        bar.setForeground(color);
        parent.add(bar);
        return bar;
    }

    private void refreshDashboard() {
        new SwingWorker<JsonObject, Void>() {
            @Override
            protected JsonObject doInBackground() throws Exception {
                return DashboardAPI.getDashboardStats();
            }

            @Override
            protected void done() {
                try {
                    JsonObject response = get();
                    if ("success".equals(response.get("status").getAsString())) {
                        JsonObject data = response.getAsJsonObject("data");
                        
                        // Update Cards
                        DecimalFormat df = new DecimalFormat("#,###");
                        lblRevenue.setText(df.format(data.get("revenueToday").getAsDouble()) + " VNĐ");
                        lblOccupied.setText(data.get("roomsOccupied").getAsString());
                        lblCustomers.setText(data.get("totalCustomers").getAsString());
                        lblBookings.setText(data.get("bookingsToday").getAsString());

                        // Update Progress Bars
                        int total = data.get("totalRooms").getAsInt();
                        if (total > 0) {
                            barAvailable.setMaximum(total); barAvailable.setValue(data.get("roomsAvailable").getAsInt());
                            barOccupied.setMaximum(total); barOccupied.setValue(data.get("roomsOccupied").getAsInt());
                            barDirty.setMaximum(total); barDirty.setValue(data.get("roomsDirty").getAsInt());
                            barMaintenance.setMaximum(total); barMaintenance.setValue(data.get("roomsMaintenance").getAsInt());
                        }

                        // Update Recent Activity
                        recentActivityModel.setRowCount(0);
                        JsonArray activity = data.getAsJsonArray("recentActivity");
                        for (JsonElement e : activity) {
                            JsonObject item = e.getAsJsonObject();
                            recentActivityModel.addRow(new Object[]{
                                item.get("id").getAsInt(),
                                item.get("roomId").getAsInt(),
                                item.get("customerId").getAsInt(),
                                item.get("status").getAsString()
                            });
                        }

                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }.execute();
    }
}
