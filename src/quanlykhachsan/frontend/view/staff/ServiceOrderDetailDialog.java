package quanlykhachsan.frontend.view.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import quanlykhachsan.frontend.api.Phase2UiApi;
import quanlykhachsan.frontend.utils.HttpUtil;
import quanlykhachsan.frontend.utils.ServerConfig;
import quanlykhachsan.frontend.utils.ThemeManager;
import quanlykhachsan.frontend.utils.UIThemeTokens;

/**
 * Detailed view dialog for room service orders.
 * Allows PMS staff to review pending orders and accept or complete them.
 * Thread-safe off-EDT execution with FlatLaf luxury styling.
 */
public class ServiceOrderDetailDialog extends JDialog {

    private JTable table;
    private DefaultTableModel tableModel;
    private JLabel lblStatus;
    private JButton btnAccept;
    private JButton btnComplete;
    private JButton btnRefresh;
    private JButton btnClose;

    private final DecimalFormat currencyFmt = new DecimalFormat("#,### ₫");
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("dd/MM/yyyy HH:mm");
    private final Runnable onOrdersUpdatedCallback;
    private final quanlykhachsan.frontend.MainUI mainUI;

    private final Color COLOR_GOLD = new Color(212, 175, 55);
    private final Color COLOR_PENDING_BG = new Color(245, 158, 11, 40);
    private final Color COLOR_PENDING_TEXT = new Color(217, 119, 6);
    private final Color COLOR_ACCEPTED_BG = new Color(59, 130, 246, 40);
    private final Color COLOR_ACCEPTED_TEXT = new Color(37, 99, 235);
    private final Color COLOR_COMPLETED_BG = new Color(16, 185, 129, 40);
    private final Color COLOR_COMPLETED_TEXT = new Color(5, 150, 105);

    public ServiceOrderDetailDialog(Frame owner, Runnable onOrdersUpdatedCallback) {
        super(owner, "Chi Tiết Yêu Cầu Dịch Vụ Phòng - Aurelia Room Service", true);
        this.mainUI = (owner instanceof quanlykhachsan.frontend.MainUI ui) ? ui : null;
        this.onOrdersUpdatedCallback = onOrdersUpdatedCallback;
        setSize(980, 560);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        initUI();
        loadOrders();
    }

    private void initUI() {
        JPanel contentPane = new JPanel(new BorderLayout(15, 15));
        contentPane.setBackground(ThemeManager.getBgPanel());
        contentPane.setBorder(new EmptyBorder(20, 20, 20, 20));

        // --- Header Panel ---
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setOpaque(false);

        JLabel lblTitle = new JLabel("🛎️ Danh Sách Yêu Cầu Dịch Vụ Phòng (Room Service)");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(ThemeManager.getTextMain());
        titleBox.add(lblTitle);

        JLabel lblSub = new JLabel("Tiếp nhận và điều phối đơn gọi món, spa và dịch vụ buồng phòng của khách đang lưu trú");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(ThemeManager.getTextMuted());
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(lblSub);

        headerPanel.add(titleBox, BorderLayout.WEST);

        lblStatus = new JLabel("Đang tải dữ liệu...");
        lblStatus.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblStatus.setForeground(ThemeManager.getTextMuted());
        headerPanel.add(lblStatus, BorderLayout.EAST);

        contentPane.add(headerPanel, BorderLayout.NORTH);

        // --- Table ---
        String[] columnNames = {
            "ID", "Phòng", "Danh mục", "Món / Dịch vụ", "SL", "Thành tiền", "Voucher", "Trạng thái", "Ghi chú", "Thời gian"
        };
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(tableModel);
        table.setRowHeight(UIThemeTokens.TABLE_ROW_HEIGHT);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.getTableHeader().setBackground(ThemeManager.getSidebarBg());
        table.getTableHeader().setForeground(ThemeManager.getTextMain());

        // Custom column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(70);
        table.getColumnModel().getColumn(2).setPreferredWidth(70);
        table.getColumnModel().getColumn(3).setPreferredWidth(180);
        table.getColumnModel().getColumn(4).setPreferredWidth(45);
        table.getColumnModel().getColumn(5).setPreferredWidth(105);
        table.getColumnModel().getColumn(6).setPreferredWidth(80);
        table.getColumnModel().getColumn(7).setPreferredWidth(100);
        table.getColumnModel().getColumn(8).setPreferredWidth(140);
        table.getColumnModel().getColumn(9).setPreferredWidth(125);

        // Custom cell renderer for Status column (column 7)
        table.getColumnModel().getColumn(7).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
                String st = value != null ? value.toString() : "";
                if ("PENDING".equalsIgnoreCase(st)) {
                    lbl.setText("● CHỜ XỬ LÝ");
                    lbl.setForeground(COLOR_PENDING_TEXT);
                    if (!isSelected) lbl.setBackground(COLOR_PENDING_BG);
                } else if ("ACCEPTED".equalsIgnoreCase(st)) {
                    lbl.setText("✓ ĐÃ TIẾP NHẬN");
                    lbl.setForeground(COLOR_ACCEPTED_TEXT);
                    if (!isSelected) lbl.setBackground(COLOR_ACCEPTED_BG);
                } else if ("COMPLETED".equalsIgnoreCase(st)) {
                    lbl.setText("★ HOÀN THÀNH");
                    lbl.setForeground(COLOR_COMPLETED_TEXT);
                    if (!isSelected) lbl.setBackground(COLOR_COMPLETED_BG);
                } else {
                    lbl.setText(st);
                }
                return lbl;
            }
        });

        table.getSelectionModel().addListSelectionListener(e -> updateButtonStates());

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(ThemeManager.getBorderColor()));
        contentPane.add(scrollPane, BorderLayout.CENTER);

        // --- Action Buttons ---
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setOpaque(false);

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftActions.setOpaque(false);

        btnRefresh = new JButton("🔄 Làm mới");
        btnRefresh.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btnRefresh.addActionListener(e -> loadOrders());
        leftActions.add(btnRefresh);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightActions.setOpaque(false);

        btnAccept = new JButton("🛎️ Tiếp nhận đơn (Accept)");
        btnAccept.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnAccept.setBackground(new Color(37, 99, 235));
        btnAccept.setForeground(Color.WHITE);
        btnAccept.setEnabled(false);
        btnAccept.addActionListener(e -> updateSelectedOrderStatus("ACCEPTED", "Tiếp nhận đơn thành công! Đang chuyển thông tin sang Bếp / Spa."));
        rightActions.add(btnAccept);

        btnComplete = new JButton("✅ Hoàn thành (Complete)");
        btnComplete.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnComplete.setBackground(new Color(16, 185, 129));
        btnComplete.setForeground(Color.WHITE);
        btnComplete.setEnabled(false);
        btnComplete.addActionListener(e -> updateSelectedOrderStatus("COMPLETED", "Đã đánh dấu hoàn tất phục vụ đơn phòng."));
        rightActions.add(btnComplete);

        btnClose = new JButton("Đóng");
        btnClose.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btnClose.addActionListener(e -> dispose());
        rightActions.add(btnClose);

        bottomBar.add(leftActions, BorderLayout.WEST);
        bottomBar.add(rightActions, BorderLayout.EAST);
        contentPane.add(bottomBar, BorderLayout.SOUTH);

        setContentPane(contentPane);
    }

    private void updateButtonStates() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow >= 0) {
            String status = String.valueOf(tableModel.getValueAt(selectedRow, 7));
            btnAccept.setEnabled("PENDING".equalsIgnoreCase(status));
            btnComplete.setEnabled("ACCEPTED".equalsIgnoreCase(status) || "PENDING".equalsIgnoreCase(status));
        } else {
            btnAccept.setEnabled(false);
            btnComplete.setEnabled(false);
        }
    }

    public void loadOrders() {
        lblStatus.setText("Đang tải danh sách đơn dịch vụ...");
        btnRefresh.setEnabled(false);

        new SwingWorker<List<Object[]>, Void>() {
            @Override
            protected List<Object[]> doInBackground() throws Exception {
                List<Object[]> rows = new ArrayList<>();
                try {
                    String jsonStr = HttpUtil.sendGet(ServerConfig.getBaseUrl() + "/api/v1/service-orders/active");
                    if (jsonStr != null && !jsonStr.isBlank()) {
                        JsonObject resp = JsonParser.parseString(jsonStr).getAsJsonObject();
                        JsonArray data = resp.has("data") && resp.get("data").isJsonArray()
                                ? resp.getAsJsonArray("data") : new JsonArray();

                        for (JsonElement el : data) {
                            if (el.isJsonObject()) {
                                JsonObject o = el.getAsJsonObject();
                                long id = o.has("id") ? o.get("id").getAsLong() : 0;
                                String room = o.has("room_number") && !o.get("room_number").isJsonNull()
                                        ? o.get("room_number").getAsString() : "-";
                                String cat = o.has("category") && !o.get("category").isJsonNull()
                                        ? o.get("category").getAsString().toUpperCase() : "FNB";
                                String name = o.has("item_name") && !o.get("item_name").isJsonNull()
                                        ? o.get("item_name").getAsString() : "-";
                                int qty = o.has("quantity") ? o.get("quantity").getAsInt() : 1;
                                double price = o.has("final_price") && !o.get("final_price").isJsonNull()
                                        ? o.get("final_price").getAsDouble() : 0.0;
                                String voucher = o.has("voucher_code") && !o.get("voucher_code").isJsonNull()
                                        ? o.get("voucher_code").getAsString() : "-";
                                String status = o.has("order_status") && !o.get("order_status").isJsonNull()
                                        ? o.get("order_status").getAsString() : "PENDING";
                                String notes = o.has("special_instructions") && !o.get("special_instructions").isJsonNull()
                                        ? o.get("special_instructions").getAsString() : "";
                                String createdStr = "-";
                                if (o.has("created_at") && !o.get("created_at").isJsonNull()) {
                                    try {
                                        long ms = o.get("created_at").getAsLong();
                                        createdStr = timeFmt.format(new Date(ms));
                                    } catch (Exception ex) {
                                        createdStr = o.get("created_at").getAsString();
                                    }
                                }

                                rows.add(new Object[]{
                                    id, room, cat, name, qty, currencyFmt.format(price), voucher, status, notes, createdStr
                                });
                            }
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("[ServiceOrderDetailDialog] Load orders error: " + ex.getMessage());
                }
                return rows;
            }

            @Override
            protected void done() {
                btnRefresh.setEnabled(true);
                try {
                    List<Object[]> rows = get();
                    tableModel.setRowCount(0);
                    int pending = 0;
                    for (Object[] r : rows) {
                        tableModel.addRow(r);
                        if ("PENDING".equalsIgnoreCase(String.valueOf(r[7]))) {
                            pending++;
                        }
                    }
                    lblStatus.setText("Tổng cộng: " + rows.size() + " đơn (" + pending + " đang chờ xử lý)");
                    updateButtonStates();
                } catch (Exception ex) {
                    lblStatus.setText("Lỗi kết nối máy chủ");
                }
            }
        }.execute();
    }

    private void updateSelectedOrderStatus(String newStatus, String successMsg) {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) return;

        long orderId = (long) tableModel.getValueAt(selectedRow, 0);
        String reqBody = "{\"order_status\":\"" + newStatus + "\"}";

        final JButton targetBtn = "ACCEPTED".equalsIgnoreCase(newStatus) ? btnAccept : btnComplete;
        final String origText = targetBtn.getText();

        btnAccept.setEnabled(false);
        btnComplete.setEnabled(false);
        btnRefresh.setEnabled(false);
        targetBtn.setText("Đang xử lý...");
        lblStatus.setText("Đang cập nhật trạng thái đơn #" + orderId + "...");

        new SwingWorker<Boolean, Void>() {
            private String errorDetail = null;

            @Override
            protected Boolean doInBackground() throws Exception {
                try {
                    String respStr = HttpUtil.sendPut(ServerConfig.getBaseUrl() + "/api/v1/service-orders/" + orderId + "/status", reqBody);
                    if (respStr != null && !respStr.isBlank()) {
                        JsonObject obj = JsonParser.parseString(respStr).getAsJsonObject();
                        if (obj.has("success") && !obj.get("success").getAsBoolean()) {
                            errorDetail = obj.has("message") ? obj.get("message").getAsString() : "Máy chủ từ chối yêu cầu";
                            return false;
                        }
                    }
                    return true;
                } catch (Exception ex) {
                    errorDetail = ex.getMessage();
                    return false;
                }
            }

            @Override
            protected void done() {
                targetBtn.setText(origText);
                btnRefresh.setEnabled(true);
                try {
                    boolean success = get();
                    if (success) {
                        lblStatus.setText("Cập nhật đơn #" + orderId + " thành công!");
                        JOptionPane.showMessageDialog(ServiceOrderDetailDialog.this, successMsg, "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                        loadOrders();
                        if (onOrdersUpdatedCallback != null) {
                            onOrdersUpdatedCallback.run();
                        }
                        // Trigger an immediate re-scan instead of waiting for the 15-second interval:
                        if (mainUI != null && mainUI.getAlertWorker() != null) {
                            mainUI.getAlertWorker().triggerImmediateCheck();
                        }
                    } else {
                        lblStatus.setText("Lỗi cập nhật: " + (errorDetail != null ? errorDetail : "Không xác định"));
                        JOptionPane.showMessageDialog(ServiceOrderDetailDialog.this,
                                "Không thể cập nhật trạng thái: " + (errorDetail != null ? errorDetail : "Lỗi kết nối máy chủ"),
                                "Lỗi", JOptionPane.ERROR_MESSAGE);
                        updateButtonStates();
                    }
                } catch (Exception ex) {
                    lblStatus.setText("Lỗi: " + ex.getMessage());
                    updateButtonStates();
                }
            }
        }.execute();
    }
}
