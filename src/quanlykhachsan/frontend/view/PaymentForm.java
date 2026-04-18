package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import quanlykhachsan.backend.model.Booking;
import quanlykhachsan.backend.model.Customer;
import quanlykhachsan.backend.model.Room;
import quanlykhachsan.frontend.api.BookingAPI;
import quanlykhachsan.frontend.api.CustomerAPI;
import quanlykhachsan.frontend.api.RoomAPI;
import quanlykhachsan.frontend.api.PaymentAPI;
import quanlykhachsan.frontend.api.PromotionAPI;
import quanlykhachsan.frontend.api.LoyaltyAPI;
import quanlykhachsan.frontend.utils.InvoicePDFExporter;
import java.net.URL;
import java.awt.Image;
import javax.imageio.ImageIO;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class PaymentForm extends JPanel {

    // ─── Constants ────────────────────────────────────────────────────────
    private static final Color PRIMARY = new Color(37, 99, 235);
    private static final Color SUCCESS = new Color(5, 150, 105);
    private static final Color DANGER = new Color(220, 38, 38);
    private static final Color MUTED = new Color(107, 114, 128);
    private static final Color BG_PANEL = new Color(248, 250, 252);
    private static final Color BORDER_CLR = new Color(226, 232, 240);
    private static final Color ROW_SELECT = new Color(219, 234, 254);

    private final SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm");
    private final DecimalFormat nf = new DecimalFormat("#,###");

    // ─── UI Components ────────────────────────────────────────────────────
    private JTable bookingTable;
    private DefaultTableModel tableModel;
    private JLabel lblStatus;

    // Invoice Panel Components
    private JLabel lblCustName, lblRoomNum, lblDuration, lblSubtotal, lblPromo, lblDiscount, lblTax, lblTotal;
    private JComboBox<String> cbMethod;
    private JButton btnPay, btnShowQR;

    // ─── State ────────────────────────────────────────────────────────────
    private List<Booking> bookingsList = new ArrayList<>();
    private List<Customer> customersList = new ArrayList<>();
    private List<Room> roomsList = new ArrayList<>();
    private Booking selectedBooking = null;
    private double currentCalculatedDiscount = 0;
    private int currentLoyaltyPoints = 0;
    private double redeemDiscount = 0;
    private int pointsToRedeem = 0;

    private JPanel loyaltyPanel;
    private JLabel lblLoyaltyPoints, lblLoyaltyTier, lblRedeemInfo;
    private JButton btnRedeem100, btnRedeem500, btnCancelRedeem;

    public PaymentForm() {
        setLayout(new BorderLayout());
        setBackground(BG_PANEL);
        initUI();
        loadInitialData();
    }

    private void initUI() {
        // ── Header ──────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, BORDER_CLR),
                new EmptyBorder(12, 20, 12, 20)));

        JLabel title = new JLabel("Thanh toán & Hóa đơn");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(new Color(15, 23, 42));

        lblStatus = new JLabel("Đang tải...");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblStatus.setForeground(MUTED);

        header.add(title, BorderLayout.WEST);
        header.add(lblStatus, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ── Main Content ────────────────────────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setDividerLocation(550);
        split.setDividerSize(1);
        split.setBorder(null);

        split.setLeftComponent(buildListPanel());
        split.setRightComponent(buildInvoicePanel());
        add(split, BorderLayout.CENTER);
    }

    private JPanel buildListPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(new MatteBorder(0, 0, 0, 1, BORDER_CLR));

        JPanel toolBar = new JPanel(new BorderLayout());
        toolBar.setBackground(Color.WHITE);
        toolBar.setBorder(new EmptyBorder(10, 15, 10, 15));
        JLabel sub = new JLabel("Danh sách phòng chờ thanh toán");
        sub.setFont(new Font("Segoe UI", Font.BOLD, 14));
        sub.setForeground(new Color(55, 65, 81));

        JButton btnRefresh = new JButton("Tải lại");
        btnRefresh.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRefresh.addActionListener(e -> loadInitialData());

        toolBar.add(sub, BorderLayout.WEST);
        toolBar.add(btnRefresh, BorderLayout.EAST);
        panel.add(toolBar, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(new String[] { "ID", "Khách Hàng", "Phòng", "Check-in", "Trạng Thái" }, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        bookingTable = new JTable(tableModel);
        bookingTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        bookingTable.setRowHeight(40);
        bookingTable.setShowGrid(false);
        bookingTable.setSelectionBackground(ROW_SELECT);
        bookingTable.setSelectionForeground(new Color(17, 24, 39));

        // Header
        bookingTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        bookingTable.getTableHeader().setReorderingAllowed(false);
        bookingTable.getTableHeader().setPreferredSize(new Dimension(0, 35));

        // Event
        bookingTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting())
                onBookingSelected();
        });

        panel.add(new JScrollPane(bookingTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildInvoicePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_PANEL);
        panel.setBorder(new EmptyBorder(25, 30, 25, 30));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        JLabel title = new JLabel("CHI TIẾT HÓA ĐƠN");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(PRIMARY);
        title.setAlignmentX(LEFT_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(20));

        // Invoice Rows
        lblCustName = createInvoiceRow(content, "Khách hàng:", "---");
        lblRoomNum = createInvoiceRow(content, "Phòng:", "---");
        lblDuration = createInvoiceRow(content, "Số ngày ở:", "---");

        JSeparator s1 = new JSeparator();
        s1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        content.add(Box.createVerticalStrut(15));
        content.add(s1);
        content.add(Box.createVerticalStrut(15));

        lblSubtotal = createInvoiceRow(content, "Tiền phòng:", "0 VNĐ");
        lblPromo = createInvoiceRow(content, "Khuyến mãi:", "Không có");
        lblDiscount = createInvoiceRow(content, "Giảm giá:", "0 VNĐ");
        lblTax = createInvoiceRow(content, "Thuế (10%):", "0 VNĐ");
        lblTax.setVisible(false);

        content.add(Box.createVerticalStrut(10));

        // Loyalty Rewards Panel
        loyaltyPanel = new JPanel();
        loyaltyPanel.setLayout(new BoxLayout(loyaltyPanel, BoxLayout.Y_AXIS));
        loyaltyPanel.setBackground(new Color(239, 246, 255));
        loyaltyPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(191, 219, 254), 1, true),
                new EmptyBorder(12, 12, 12, 12)));
        loyaltyPanel.setAlignmentX(LEFT_ALIGNMENT);
        loyaltyPanel.setVisible(false);

        JPanel tierRow = new JPanel(new BorderLayout());
        tierRow.setOpaque(false);
        lblLoyaltyTier = new JLabel("SILVER");
        lblLoyaltyTier.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblLoyaltyTier.setOpaque(true);
        lblLoyaltyTier.setBackground(new Color(241, 245, 249));
        lblLoyaltyTier.setForeground(new Color(100, 116, 139));
        lblLoyaltyTier.setBorder(new EmptyBorder(2, 8, 2, 8));

        lblLoyaltyPoints = new JLabel("0 điểm");
        lblLoyaltyPoints.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblLoyaltyPoints.setForeground(PRIMARY);

        tierRow.add(lblLoyaltyTier, BorderLayout.WEST);
        tierRow.add(lblLoyaltyPoints, BorderLayout.EAST);
        loyaltyPanel.add(tierRow);
        loyaltyPanel.add(Box.createVerticalStrut(10));

        lblRedeemInfo = new JLabel("Đổi điểm tích lũy để nhận voucher giảm giá:");
        lblRedeemInfo.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        loyaltyPanel.add(lblRedeemInfo);
        loyaltyPanel.add(Box.createVerticalStrut(8));

        JPanel redeemButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        redeemButtons.setOpaque(false);
        btnRedeem100 = createRedeemButton("100đ (-50k)");
        btnRedeem500 = createRedeemButton("500đ (-300k)");
        btnCancelRedeem = new JButton("Hủy");
        btnCancelRedeem.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnCancelRedeem.setVisible(false);

        btnRedeem100.addActionListener(e -> applyRedeem(100, 50000));
        btnRedeem500.addActionListener(e -> applyRedeem(500, 300000));
        btnCancelRedeem.addActionListener(e -> applyRedeem(0, 0));

        redeemButtons.add(btnRedeem100);
        redeemButtons.add(btnRedeem500);
        redeemButtons.add(btnCancelRedeem);
        loyaltyPanel.add(redeemButtons);

        content.add(loyaltyPanel);
        content.add(Box.createVerticalStrut(20));

        lblTotal = new JLabel("TỔNG CỘNG: 0 VNĐ");
        lblTotal.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblTotal.setForeground(DANGER);
        lblTotal.setAlignmentX(LEFT_ALIGNMENT);
        content.add(lblTotal);

        content.add(Box.createVerticalStrut(30));

        JLabel lblM = new JLabel("Phương thức thanh toán:");
        lblM.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblM.setAlignmentX(LEFT_ALIGNMENT);
        content.add(lblM);
        content.add(Box.createVerticalStrut(8));

        cbMethod = new JComboBox<>(new String[] { "Tiền mặt (Cash)", "Chuyển khoản", "Thẻ tín dụng (Credit Card)" });
        cbMethod.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        cbMethod.setAlignmentX(LEFT_ALIGNMENT);
        cbMethod.setBorder(BorderFactory.createEmptyBorder());
        content.add(cbMethod);

        btnShowQR = new JButton("HIỂN THỊ MÃ QR");
        btnShowQR.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnShowQR.setForeground(Color.WHITE);
        btnShowQR.setBackground(new Color(14, 165, 233));
        btnShowQR.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnShowQR.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        btnShowQR.setAlignmentX(LEFT_ALIGNMENT);
        btnShowQR.setVisible(false); // Ẩn mặc định
        btnShowQR.addActionListener(e -> actionShowQR());

        cbMethod.addActionListener(e -> {
            if ("Chuyển khoản".equals(cbMethod.getSelectedItem()) && selectedBooking != null) {
                btnShowQR.setVisible(true);
            } else {
                btnShowQR.setVisible(false);
            }
        });

        content.add(Box.createVerticalStrut(8));
        content.add(btnShowQR);
        content.add(Box.createVerticalGlue());

        btnPay = new JButton("XÁC NHẬN THANH TOÁN") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? SUCCESS : MUTED);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                super.paintComponent(g);
            }
        };
        btnPay.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnPay.setForeground(Color.WHITE);
        btnPay.setContentAreaFilled(false);
        btnPay.setBorderPainted(false);
        btnPay.setFocusPainted(false);
        btnPay.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnPay.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        btnPay.setAlignmentX(LEFT_ALIGNMENT);
        btnPay.setEnabled(false);
        btnPay.addActionListener(e -> actionProcessPayment());

        content.add(btnPay);

        panel.add(content, BorderLayout.NORTH);
        return panel;
    }

    private JLabel createInvoiceRow(JPanel parent, String label, String value) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JLabel l = new JLabel(label);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        l.setForeground(MUTED);

        JLabel v = new JLabel(value);
        v.setFont(new Font("Segoe UI", Font.BOLD, 14));
        v.setForeground(new Color(17, 24, 39));

        row.add(l, BorderLayout.WEST);
        row.add(v, BorderLayout.EAST);
        parent.add(row);
        parent.add(Box.createVerticalStrut(8));
        return v;
    }

    private JButton createRedeemButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(new Color(30, 64, 175));
        btn.setBackground(Color.WHITE);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // ─── Logic ────────────────────────────────────────────────────────────

    private void loadInitialData() {
        lblStatus.setText("Đang tải dữ liệu...");
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                customersList = CustomerAPI.getAllCustomers();
                roomsList = RoomAPI.getAllRooms();
                bookingsList = BookingAPI.getAllBookings();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    refreshTable();
                    lblStatus.setText("Sẵn sàng. " + tableModel.getRowCount() + " đơn cần thanh toán.");
                    lblStatus.setForeground(SUCCESS);
                } catch (Exception e) {
                    lblStatus.setText("Lỗi tải dữ liệu!");
                    lblStatus.setForeground(DANGER);
                }
            }
        };
        worker.execute();
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        for (Booking b : bookingsList) {
            if ("checked_in".equals(b.getStatus()) || "checked_out".equals(b.getStatus())) {
                String custName = getCustomerName(b.getCustomerId());
                String roomNum = getRoomNumber(b.getRoomId());

                String statusVn = b.getStatus().equals("checked_in") ? "Đang ở" : "Đã trả phòng";

                tableModel.addRow(new Object[] {
                        b.getId(), custName, roomNum, df.format(b.getCheckInDate()), statusVn
                });
            }
        }
        resetInvoice();
    }

    private void onBookingSelected() {
        int row = bookingTable.getSelectedRow();
        if (row < 0) {
            resetInvoice();
            return;
        }

        int bookingId = (int) tableModel.getValueAt(row, 0);
        selectedBooking = null;
        for (Booking b : bookingsList) {
            if (b.getId() == bookingId) {
                selectedBooking = b;
                break;
            }
        }

        if (selectedBooking != null) {
            updateInvoiceDisplay();
            fetchLoyaltyInfo();
        }
    }

    private void fetchLoyaltyInfo() {
        if (selectedBooking == null)
            return;

        SwingWorker<Customer, Void> worker = new SwingWorker<>() {
            @Override
            protected Customer doInBackground() {
                return LoyaltyAPI.getCustomerLoyalty(selectedBooking.getCustomerId());
            }

            @Override
            protected void done() {
                try {
                    Customer c = get();
                    if (c != null) {
                        currentLoyaltyPoints = c.getLoyaltyPoints();
                        lblLoyaltyPoints.setText(nf.format(currentLoyaltyPoints) + " điểm");
                        String tier = c.getLoyaltyLevel() != null ? c.getLoyaltyLevel().toUpperCase() : "SILVER";
                        lblLoyaltyTier.setText(tier);

                        // Style badge
                        if ("VIP".equals(tier)) {
                            lblLoyaltyTier.setBackground(new Color(245, 243, 255));
                            lblLoyaltyTier.setForeground(new Color(109, 40, 217));
                        } else if ("GOLD".equals(tier)) {
                            lblLoyaltyTier.setBackground(new Color(255, 251, 235));
                            lblLoyaltyTier.setForeground(new Color(180, 83, 9));
                        } else {
                            lblLoyaltyTier.setBackground(new Color(241, 245, 249));
                            lblLoyaltyTier.setForeground(new Color(100, 116, 139));
                        }

                        btnRedeem100.setEnabled(currentLoyaltyPoints >= 100);
                        btnRedeem500.setEnabled(currentLoyaltyPoints >= 500);
                        applyRedeem(0, 0); // Reset redeem on new selection
                        loyaltyPanel.setVisible(true);
                    }
                } catch (Exception e) {
                    loyaltyPanel.setVisible(false);
                }
            }
        };
        worker.execute();
    }

    private void applyRedeem(int points, double discount) {
        this.pointsToRedeem = points;
        this.redeemDiscount = discount;

        if (points > 0) {
            btnRedeem100.setEnabled(false);
            btnRedeem500.setEnabled(false);
            btnCancelRedeem.setVisible(true);
            lblRedeemInfo.setText("Đã áp dụng đổi " + points + " điểm (Giảm " + nf.format(discount) + " VNĐ)");
            lblRedeemInfo.setForeground(SUCCESS);
        } else {
            btnRedeem100.setEnabled(currentLoyaltyPoints >= 100);
            btnRedeem500.setEnabled(currentLoyaltyPoints >= 500);
            btnCancelRedeem.setVisible(false);
            lblRedeemInfo.setText("Đổi điểm tích lũy để nhận voucher giảm giá:");
            lblRedeemInfo.setForeground(MUTED);
        }
        recalculateTotal();
    }

    private void updateInvoiceDisplay() {
        lblCustName.setText(getCustomerName(selectedBooking.getCustomerId()));
        String roomNum = getRoomNumber(selectedBooking.getRoomId());
        double roomPrice = getRoomPrice(selectedBooking.getRoomId());
        lblRoomNum.setText(roomNum + " (" + nf.format(roomPrice) + " VNĐ/đêm)");

        // Tính số ngày
        long diff = selectedBooking.getCheckOutDate().getTime() - selectedBooking.getCheckInDate().getTime();
        long days = (diff / (1000 * 60 * 60 * 24));
        if (days == 0)
            days = 1;

        lblDuration.setText(days + " đêm");

        double subtotal = days * roomPrice;

        // Fetch promotion preview
        lblPromo.setText("Đang kiểm tra...");
        lblDiscount.setText("...");

        final long finalDays = days;
        final double finalSubtotal = subtotal;

        SwingWorker<JsonObject, Void> promoWorker = new SwingWorker<>() {
            @Override
            protected JsonObject doInBackground() {
                return PromotionAPI.getBestPromotionPreview(selectedBooking.getId());
            }

            @Override
            protected void done() {
                try {
                    JsonObject data = get();
                    currentCalculatedDiscount = 0;
                    if (data != null && data.has("calculatedDiscount")) {
                        currentCalculatedDiscount = data.get("calculatedDiscount").getAsDouble();
                        if (data.has("promotion") && !data.get("promotion").isJsonNull()) {
                            JsonObject p = data.getAsJsonObject("promotion");
                            lblPromo.setText(p.get("name").getAsString());
                            lblPromo.setForeground(SUCCESS);
                        } else {
                            lblPromo.setText("Không có");
                            lblPromo.setForeground(MUTED);
                        }
                    } else {
                        lblPromo.setText("Không có");
                        lblPromo.setForeground(MUTED);
                    }

                    lblDiscount.setText("- " + nf.format(currentCalculatedDiscount) + " VNĐ");
                    lblDiscount.setForeground(DANGER);

                    double taxable = finalSubtotal - currentCalculatedDiscount;
                    double tax = 0;
                    double total = taxable + tax;

                    lblSubtotal.setText(nf.format(finalSubtotal) + " VNĐ");
                    lblTax.setText(nf.format(tax) + " VNĐ");
                    recalculateTotal();

                } catch (Exception e) {
                    lblPromo.setText("Lỗi kiểm tra");
                }
            }
        };
        promoWorker.execute();

        btnPay.setEnabled(true);
        if ("Chuyển khoản".equals(cbMethod.getSelectedItem())) {
            btnShowQR.setVisible(true);
        }
    }

    private void recalculateTotal() {
        if (selectedBooking == null)
            return;

        long diff = selectedBooking.getCheckOutDate().getTime() - selectedBooking.getCheckInDate().getTime();
        long days = Math.max(1, diff / (1000 * 60 * 60 * 24));
        double roomPrice = getRoomPrice(selectedBooking.getRoomId());
        double subtotal = days * roomPrice;

        double taxable = subtotal - currentCalculatedDiscount - redeemDiscount;
        if (taxable < 0)
            taxable = 0;

        double tax = 0;
        double total = taxable + tax;

        lblDiscount.setText("- " + nf.format(currentCalculatedDiscount + redeemDiscount) + " VNĐ");
        lblTotal.setText("TỔNG CỘNG: " + nf.format(total) + " VNĐ");
    }

    private void resetInvoice() {
        selectedBooking = null;
        lblCustName.setText("---");
        lblRoomNum.setText("---");
        lblDuration.setText("---");
        lblSubtotal.setText("0 VNĐ");
        lblServiceTotal.setText("0 VNĐ");
        lblTax.setText("0 VNĐ");
        lblDiscount.setText("0 VNĐ");
        lblTotal.setText("TỔNG CỘNG: 0 VNĐ");
        btnPay.setEnabled(false);
        btnShowQR.setVisible(false);
        loyaltyPanel.setVisible(false);
        redeemDiscount = 0;
        pointsToRedeem = 0;
    }

    private void actionProcessPayment() {
        if (selectedBooking == null)
            return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc chắn muốn xác nhận thanh toán cho Đơn đặt #" + selectedBooking.getId() + "?",
                "Xác nhận", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            btnPay.setEnabled(false);
            lblStatus.setText("Đang xử lý thanh toán...");

            // Lấy thông tin phòng và khách hàng trước
            Customer currentCustomer = null;
            for (Customer c : customersList)
                if (c.getId() == selectedBooking.getCustomerId())
                    currentCustomer = c;
            Room currentRoom = null;
            for (Room r : roomsList)
                if (r.getId() == selectedBooking.getRoomId())
                    currentRoom = r;

            long diff = selectedBooking.getCheckOutDate().getTime() - selectedBooking.getCheckInDate().getTime();
            long days = Math.max(1, diff / (1000 * 60 * 60 * 24));
            double subtotal = days * getRoomPrice(selectedBooking.getRoomId());
            double amount = (subtotal - currentCalculatedDiscount - redeemDiscount);
            if (amount < 0)
                amount = 0;

            final double finalAmount = amount;
            final long finalDays = days;
            Booking bKeep = selectedBooking;
            Customer cKeep = currentCustomer;
            Room rKeep = currentRoom;
            int finalPointsRedeem = pointsToRedeem;
            double finalLoyaltyDiscount = redeemDiscount;

            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override
                protected String doInBackground() {
                    // Nếu có đổi điểm, thực hiện đổi trước
                    if (finalPointsRedeem > 0) {
                        boolean ok = LoyaltyAPI.redeemPoints(bKeep.getCustomerId(), finalPointsRedeem,
                                finalLoyaltyDiscount, "Đổi điểm tại hóa đơn #" + bKeep.getId());
                        if (!ok)
                            return "Error: Không thể đổi điểm Loyalty!";
                    }

                    String method = (String) cbMethod.getSelectedItem();
                    return PaymentAPI.pay(bKeep.getId(), finalAmount, method, bKeep.getCustomerId());
                }

                @Override
                protected void done() {
                    try {
                        String res = get();
                        if (res.startsWith("Success")) {
                            JOptionPane.showMessageDialog(PaymentForm.this,
                                    "Thanh toán thành công!\nĐơn hàng đã hoàn tất và Phòng đang được dọn dẹp.",
                                    "Thành Công", JOptionPane.INFORMATION_MESSAGE);

                            // Gọi Export PDF
                            InvoicePDFExporter.exportPDF(bKeep, cKeep, rKeep, (int) finalDays, finalAmount);

                            loadInitialData();
                        } else {
                            JOptionPane.showMessageDialog(PaymentForm.this, "Lỗi: " + res, "Thất Bại",
                                    JOptionPane.ERROR_MESSAGE);
                            btnPay.setEnabled(true);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            worker.execute();
        }
    }

    private void actionShowQR() {
        if (selectedBooking == null)
            return;
        try {
            long diff = selectedBooking.getCheckOutDate().getTime() - selectedBooking.getCheckInDate().getTime();
            long days = Math.max(1, diff / (1000 * 60 * 60 * 24));
            double subtotal = days * getRoomPrice(selectedBooking.getRoomId());
            double amount = (subtotal - currentCalculatedDiscount - redeemDiscount) * 1.1;
            if (amount < 0)
                amount = 0;

            // Chốt thông tin VietQR
            // Mã NH MBBank: 970422. Tk demo: 123456789. Thay bằng mã và TK thật của bạn!
            String bankBin = "970422";
            String accountNo = "0987654321"; // TK Demo
            String accountName = "KHACH SAN NGOC MAI";
            String addInfo = "Thanh Toan Phong " + getRoomNumber(selectedBooking.getRoomId());

            String amountStr = String.valueOf((long) amount);

            String qrUrl = "https://img.vietqr.io/image/" + bankBin + "-" + accountNo + "-compact.png"
                    + "?amount=" + amountStr
                    + "&addInfo=" + java.net.URLEncoder.encode(addInfo, "UTF-8")
                    + "&accountName=" + java.net.URLEncoder.encode(accountName, "UTF-8");

            JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "QR Code Thanh Toán", true);
            dialog.setSize(400, 500);
            dialog.setLocationRelativeTo(this);
            dialog.setLayout(new BorderLayout());

            JLabel lblWait = new JLabel("Đang tạo mã QR, vui lòng chờ...", SwingConstants.CENTER);
            dialog.add(lblWait, BorderLayout.CENTER);

            // Fetch image in background so we don't freeze UI
            SwingWorker<Image, Void> loadQR = new SwingWorker<>() {
                @Override
                protected Image doInBackground() throws Exception {
                    return ImageIO.read(new URL(qrUrl));
                }

                @Override
                protected void done() {
                    try {
                        Image img = get();
                        lblWait.setIcon(new ImageIcon(img.getScaledInstance(350, -1, Image.SCALE_SMOOTH)));
                        lblWait.setText("");
                    } catch (Exception ex) {
                        lblWait.setText("Lỗi kết nối bộ tải QR!");
                    }
                }
            };
            loadQR.execute();

            dialog.setVisible(true);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi tạo QR: " + ex.getMessage());
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private String getCustomerName(int id) {
        for (Customer c : customersList)
            if (c.getId() == id)
                return c.getFullName();
        return "Không rõ";
    }

    private String getRoomNumber(int id) {
        for (Room r : roomsList)
            if (r.getId() == id)
                return r.getRoomNumber();
        return "Không rõ";
    }

    private double getRoomPrice(int id) {
        for (Room r : roomsList)
            if (r.getId() == id)
                return r.getPrice();
        return 0;
    }
}
