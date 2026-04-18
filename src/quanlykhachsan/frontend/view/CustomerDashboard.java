package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import quanlykhachsan.frontend.api.BookingAPI;
import quanlykhachsan.frontend.api.CustomerAPI;
import quanlykhachsan.frontend.api.LoyaltyAPI;
import quanlykhachsan.frontend.api.PromotionAPI;
import quanlykhachsan.backend.model.Promotion;
import quanlykhachsan.backend.model.Booking;
import quanlykhachsan.backend.model.Customer;
import quanlykhachsan.backend.model.User;

public class CustomerDashboard extends JPanel {

    private User currentUser;
    private Customer customerData;

    private JLabel lblName, lblTier, lblPoints, lblNextTierMsg;
    private JProgressBar progressTier;
    private JTable tblBookings;
    private DefaultTableModel tblModel;
    private JPanel promoPanel;

    private static final Color PRIMARY    = new Color(37, 99, 235);
    private static final Color BG         = new Color(248, 250, 252);
    private static final Color CARD_BG    = Color.WHITE;
    private static final Color BORDER_C   = new Color(226, 232, 240);
    private static final Color SUCCESS    = new Color(34, 197, 94);
    private static final Color SILVER_C   = new Color(148, 163, 184);
    private static final Color GOLD_C     = new Color(234, 179, 8);
    private static final Color VIP_C      = new Color(139, 92, 246);

    private SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
    private NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

    public CustomerDashboard(User user) {
        this.currentUser = user;
        setLayout(new BorderLayout(20, 20));
        setBackground(BG);
        setBorder(new EmptyBorder(25, 25, 25, 25));

        initUI();
        loadData();
    }

    private void initUI() {
        // --- Top: Welcome & Cards ---
        JPanel topPanel = new JPanel(new BorderLayout(20, 0));
        topPanel.setOpaque(false);

        JPanel welcomeBox = new JPanel(new GridLayout(2, 1));
        welcomeBox.setOpaque(false);
        JLabel lblWelcome = new JLabel("Chào mừng trở lại,");
        lblWelcome.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        lblWelcome.setForeground(new Color(100, 116, 139));
        lblName = new JLabel("Đang tải...");
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 30));
        lblName.setForeground(new Color(15, 23, 42));
        welcomeBox.add(lblWelcome);
        welcomeBox.add(lblName);
        topPanel.add(welcomeBox, BorderLayout.WEST);

        // Chat Button
        JButton btnChat = new JButton("Chat hỗ trợ trực tuyến");
        btnChat.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnChat.setBackground(PRIMARY);
        btnChat.setForeground(Color.WHITE);
        btnChat.setFocusPainted(false);
        btnChat.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnChat.addActionListener(e -> {
            ChatDialog dialog = new ChatDialog((Frame) SwingUtilities.getWindowAncestor(this), currentUser);
            dialog.setVisible(true);
        });
        
        JButton btnReload = new JButton("🔄 Tải lại");
        btnReload.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnReload.setBackground(new Color(226, 232, 240));
        btnReload.setForeground(new Color(15, 23, 42));
        btnReload.setFocusPainted(false);
        btnReload.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnReload.addActionListener(e -> loadData());

        JPanel chatWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        chatWrapper.setOpaque(false);
        chatWrapper.add(btnReload);
        chatWrapper.add(btnChat);
        topPanel.add(chatWrapper, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        // --- Center: Grid of Info ---
        JPanel mainGrid = new JPanel(new BorderLayout(20, 20));
        mainGrid.setOpaque(false);

        // Left Col: Loyalty Card
        mainGrid.add(buildLoyaltyCard(), BorderLayout.WEST);

        // Center Col: Booking History
        JPanel centerCol = new JPanel(new BorderLayout(0, 20));
        centerCol.setOpaque(false);
        centerCol.add(buildBookingHistoryPanel(), BorderLayout.CENTER);
        
        // Bottom Section: Promotions
        centerCol.add(buildPromotionsSection(), BorderLayout.SOUTH);

        mainGrid.add(centerCol, BorderLayout.CENTER);

        add(mainGrid, BorderLayout.CENTER);
    }

    private JPanel buildLoyaltyCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD_BG);
        card.setPreferredSize(new Dimension(320, 0));
        card.setBorder(new CompoundBorder(
            new LineBorder(BORDER_C, 1, true),
            new EmptyBorder(25, 25, 25, 25)
        ));

        JLabel t = new JLabel("Thẻ Thành Viên");
        t.setFont(new Font("Segoe UI", Font.BOLD, 14));
        t.setForeground(new Color(100, 116, 139));
        card.add(t);
        card.add(Box.createVerticalStrut(20));

        lblTier = new JLabel("THÀNH VIÊN SILVER");
        lblTier.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTier.setForeground(SILVER_C);
        card.add(lblTier);
        card.add(Box.createVerticalStrut(10));

        lblPoints = new JLabel("0 Điểm");
        lblPoints.setFont(new Font("Segoe UI", Font.BOLD, 32));
        lblPoints.setForeground(PRIMARY);
        card.add(lblPoints);
        card.add(Box.createVerticalStrut(25));

        progressTier = new JProgressBar(0, 1000);
        progressTier.setValue(0);
        progressTier.setPreferredSize(new Dimension(0, 8));
        progressTier.setForeground(PRIMARY);
        progressTier.setBackground(new Color(241, 245, 249));
        progressTier.setBorder(null);
        card.add(progressTier);
        card.add(Box.createVerticalStrut(8));

        lblNextTierMsg = new JLabel("Bạn cần thêm 1,000đ để lên hạng Gold");
        lblNextTierMsg.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblNextTierMsg.setForeground(new Color(100, 116, 139));
        card.add(lblNextTierMsg);

        card.add(Box.createVerticalStrut(30));
        card.add(new JSeparator());
        card.add(Box.createVerticalStrut(20));

        JLabel bTitle = new JLabel("Quyền lợi của bạn:");
        bTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        card.add(bTitle);
        card.add(Box.createVerticalStrut(10));

        String[] perks = {
            "• Tích lũy 1 điểm mỗi 1.000đ chi tiêu",
            "• Ưu đãi giảm giá 5% tại nhà hàng",
            "• Check-in sớm nếu còn phòng trống"
        };
        for (String p : perks) {
            JLabel lp = new JLabel(p);
            lp.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            lp.setForeground(new Color(51, 65, 85));
            lp.setBorder(new EmptyBorder(6, 0, 6, 0));
            card.add(lp);
        }

        card.add(Box.createVerticalGlue());
        return card;
    }

    private JPanel buildBookingHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 15));
        panel.setOpaque(false);

        JLabel t = new JLabel("Lịch sử Đặt phòng của bạn");
        t.setFont(new Font("Segoe UI", Font.BOLD, 18));
        t.setForeground(new Color(15, 23, 42));
        panel.add(t, BorderLayout.NORTH);

        tblModel = new DefaultTableModel(new String[]{"Ngày đặt", "Phòng", "Thời gian", "Tổng tiền", "Trạng thái", "Hành động"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 5; }
        };
        tblBookings = new JTable(tblModel);
        styleTable(tblBookings);

        JScrollPane scroll = new JScrollPane(tblBookings);
        scroll.setBorder(new LineBorder(BORDER_C, 1, true));
        scroll.getViewport().setBackground(Color.WHITE);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private void styleTable(JTable table) {
        table.setRowHeight(45);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.getTableHeader().setPreferredSize(new Dimension(0, 40));
        table.getTableHeader().setBackground(new Color(248, 250, 252));
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        table.getColumnModel().getColumn(4).setCellRenderer(new StatusRenderer());
        
        table.getColumnModel().getColumn(5).setCellRenderer(new ActionBtnRenderer());
        table.getColumnModel().getColumn(5).setCellEditor(new ActionBtnEditor());
    }

    private JPanel buildPromotionsSection() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(0, 200));

        JLabel t = new JLabel("Ưu đãi hấp dẫn dành cho bạn");
        t.setFont(new Font("Segoe UI", Font.BOLD, 16));
        t.setForeground(new Color(15, 23, 42));
        panel.add(t, BorderLayout.NORTH);

        promoPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 15, 0));
        promoPanel.setOpaque(false);

        JScrollPane scroll = new JScrollPane(promoPanel);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private void loadData() {
        if (currentUser.getCustomerId() == null) {
            lblName.setText(currentUser.getFullName());
            lblTier.setText("CHƯA LIÊN KẾT KH");
            return;
        }

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                customerData = CustomerAPI.getCustomerById(currentUser.getCustomerId());
                List<Booking> bookings = BookingAPI.getBookingsByCustomer(currentUser.getCustomerId());
                
                SwingUtilities.invokeLater(() -> {
                    if (customerData != null) {
                        lblName.setText(customerData.getFullName());
                        updateLoyaltyUI(customerData);
                    }
                    
                    tblModel.setRowCount(0);
                    for (Booking b : bookings) {
                        tblModel.addRow(new Object[]{
                            b.getCreatedAt() != null ? sdf.format(b.getCreatedAt()) : "---",
                            "Phòng #" + b.getRoomId(), 
                            sdf.format(b.getCheckInDate()) + " - " + sdf.format(b.getCheckOutDate()),
                            nf.format(b.getTotalPrice()),
                            b.getStatus(),
                            b // Passing the whole object for the editor
                        });
                    }
                });
                return null;
            }
        };
        worker.execute();
        
        loadPromotions();
    }

    private void loadPromotions() {
        promoPanel.removeAll();
        new SwingWorker<List<Promotion>, Void>() {
            @Override protected List<Promotion> doInBackground() { return PromotionAPI.getActivePromotions(); }
            @Override protected void done() {
                try {
                    List<Promotion> list = get();
                    if (list != null) {
                        for (Promotion p : list) {
                            promoPanel.add(makePromotionCard(p));
                        }
                    }
                } catch (Exception e) {}
                promoPanel.revalidate();
                promoPanel.repaint();
            }
        }.execute();
    }

    private JPanel makePromotionCard(Promotion p) {
        JPanel card = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                g2.setColor(BORDER_C);
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 15, 15);
                // Dash line
                g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3}, 0));
                g2.drawLine(60, 10, 60, getHeight()-10);
            }
        };
        card.setPreferredSize(new Dimension(280, 80));
        card.setLayout(new BorderLayout());
        card.setOpaque(false);

        JLabel lblIcon = new JLabel(p.getDiscountType().equals("percentage") ? "🏷️" : "💵");
        lblIcon.setFont(new Font("Segoe UI", Font.PLAIN, 24));
        lblIcon.setHorizontalAlignment(JLabel.CENTER);
        lblIcon.setPreferredSize(new Dimension(60, 80));
        card.add(lblIcon, BorderLayout.WEST);

        JPanel right = new JPanel(new GridLayout(2, 1));
        right.setOpaque(false);
        right.setBorder(new EmptyBorder(10, 15, 10, 10));

        JLabel name = new JLabel(p.getName());
        name.setFont(new Font("Segoe UI", Font.BOLD, 13));
        
        String val = p.getDiscountType().equals("percentage") ? p.getDiscountValue() + "% OFF" : String.format("-%,.0fđ", p.getDiscountValue());
        JLabel disc = new JLabel(val);
        disc.setFont(new Font("Segoe UI", Font.BOLD, 15));
        disc.setForeground(PRIMARY);

        right.add(name);
        right.add(disc);
        card.add(right, BorderLayout.CENTER);

        return card;
    }

    private void updateLoyaltyUI(Customer c) {
        String tier = c.getLoyaltyLevel() != null ? c.getLoyaltyLevel().toUpperCase() : "SILVER";
        lblTier.setText(tier + " MEMBER");
        lblPoints.setText(String.format("%,d Điểm", c.getLoyaltyPoints()));

        if ("VIP".equals(tier)) {
            lblTier.setText("💎 VIP MEMBER");
            lblTier.setForeground(VIP_C);
            progressTier.setValue(100);
            lblNextTierMsg.setText("🏆 Bạn đã đạt hạng cao nhất!");
        } else if ("GOLD".equals(tier)) {
            lblTier.setText("🥇 GOLD MEMBER");
            lblTier.setForeground(GOLD_C);
            int next = 5000 - c.getLoyaltyPoints();
            progressTier.setMaximum(5000);
            progressTier.setValue(c.getLoyaltyPoints());
            lblNextTierMsg.setText("🚀 Còn " + next + " điểm để lên hạng VIP");
        } else {
            lblTier.setText("🥈 SILVER MEMBER");
            lblTier.setForeground(SILVER_C);
            int next = 1000 - c.getLoyaltyPoints();
            progressTier.setMaximum(1000);
            progressTier.setValue(c.getLoyaltyPoints());
            lblNextTierMsg.setText("⚡ Còn " + next + " điểm để lên hạng GOLD");
        }
    }

    // --- Renderers & Editors ---

    class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            label.setHorizontalAlignment(CENTER);
            label.setFont(new Font("Segoe UI", Font.BOLD, 12));
            
            String status = String.valueOf(value).toLowerCase();
            if (status.contains("pending")) {
                label.setText("CHỜ XÁC NHẬN");
                label.setForeground(new Color(234, 179, 8));
            } else if (status.contains("checked_in")) {
                label.setText("ĐANG Ở");
                label.setForeground(new Color(37, 99, 235));
            } else if (status.contains("checked_out") || "paid".equals(status)) {
                label.setText("ĐÃ TRẢ PHÒNG");
                label.setForeground(SUCCESS);
            } else if (status.contains("cancelled")) {
                label.setText("ĐÃ HỦY");
                label.setForeground(new Color(239, 68, 68));
            } else {
                label.setForeground(new Color(100, 116, 139));
            }
            return label;
        }
    }

    class ActionBtnRenderer extends JButton implements javax.swing.table.TableCellRenderer {
        public ActionBtnRenderer() {
            setOpaque(true);
            setFont(new Font("Segoe UI", Font.BOLD, 11));
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Booking b = (Booking) value;
            String status = b.getStatus();
            if ("checked_in".equals(status)) {
                setText("Thanh toán & Trả phòng");
                setBackground(SUCCESS);
                setForeground(Color.WHITE);
                setEnabled(true);
            } else if ("checked_out".equals(status) || "paid".equals(status)) {
                setText("Gửi Đánh giá");
                setBackground(PRIMARY);
                setForeground(Color.WHITE);
                setEnabled(true);
            } else {
                setText("---");
                setBackground(Color.WHITE);
                setForeground(new Color(203, 213, 225));
                setEnabled(false);
            }
            return this;
        }
    }

    class ActionBtnEditor extends DefaultCellEditor {
        private JButton btn;
        private Booking currentBooking;

        public ActionBtnEditor() {
            super(new JCheckBox());
            btn = new JButton();
            btn.setOpaque(true);
            btn.addActionListener(e -> {
                if (currentBooking != null) {
                    if ("checked_in".equals(currentBooking.getStatus())) {
                        Window owner = SwingUtilities.getWindowAncestor(btn);
                        CustomerPaymentDialog dialog = new CustomerPaymentDialog(owner, currentBooking, () -> {
                            loadData();
                        });
                        dialog.setVisible(true);
                    } else if ("checked_out".equals(currentBooking.getStatus()) || "paid".equals(currentBooking.getStatus())) {
                        Window owner = SwingUtilities.getWindowAncestor(btn);
                        ReviewSubmissionDialog dialog = new ReviewSubmissionDialog(owner, currentBooking);
                        dialog.setVisible(true);
                    }
                }
                fireEditingStopped();
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            currentBooking = (Booking) value;
            String status = currentBooking.getStatus();
            if ("checked_in".equals(status)) {
                btn.setText("Thanh toán & Trả phòng");
                btn.setBackground(SUCCESS);
                btn.setForeground(Color.WHITE);
            } else if ("checked_out".equals(status) || "paid".equals(status)) {
                btn.setText("Gửi Đánh giá");
                btn.setBackground(PRIMARY);
                btn.setForeground(Color.WHITE);
            } else {
                btn.setText("---");
            }
            return btn;
        }

        @Override public Object getCellEditorValue() { return currentBooking; }
    }
}
