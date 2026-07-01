package quanlykhachsan.frontend.view.customer;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import quanlykhachsan.frontend.api.BookingAPI;
import quanlykhachsan.frontend.api.CustomerAPI;
import quanlykhachsan.frontend.api.PromotionAPI;
import quanlykhachsan.backend.promotion.Promotion;
import quanlykhachsan.backend.booking.Booking;
import quanlykhachsan.backend.customer.Customer;
import quanlykhachsan.backend.user.User;
import quanlykhachsan.frontend.view.ChatDialog;
import quanlykhachsan.frontend.view.customer.CustomerNotificationDialog;

public class CustomerDashboard extends JPanel {

    private User currentUser;
    private Customer customerData;

    private JLabel lblName, lblTier, lblPoints, lblNextTierMsg;
    private JProgressBar progressTier;
    private JButton btnNotifications;
    private JLabel lblBookedCount, lblStayedCount, lblSpendTotal, lblPointsSummary;
    private JPanel promoPanel;
    private JPanel activeBookingPanel;
    private Booking activeBooking;

    private final Color PRIMARY    = new Color(37, 99, 235);
    private final Color BG         = quanlykhachsan.frontend.utils.ThemeManager.getBgPanel();
    private final Color CARD_BG    = quanlykhachsan.frontend.utils.ThemeManager.getCardBg();
    private final Color BORDER_C   = quanlykhachsan.frontend.utils.ThemeManager.getBorderColor();
    private final Color SUCCESS    = new Color(34, 197, 94);
    private final Color SILVER_C   = new Color(148, 163, 184);
    private final Color GOLD_C     = new Color(234, 179, 8);
    private final Color VIP_C      = new Color(139, 92, 246);

    private SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
    private SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm");
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
        JPanel headerPanel = new JPanel(new BorderLayout(20, 20));
        headerPanel.setOpaque(false);

        JPanel welcomeBox = new JPanel(new GridLayout(2, 1));
        welcomeBox.setOpaque(false);
        JLabel lblWelcome = new JLabel("Khách hàng thân thiết,");
        lblWelcome.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        lblWelcome.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMuted());
        lblName = new JLabel("Đang tải...");
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 32));
        lblName.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        welcomeBox.add(lblWelcome);
        welcomeBox.add(lblName);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        btnNotifications = createNotificationButton();
        JButton btnReload = new JButton("🔄 Tải lại");
        btnReload.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnReload.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getBorderColor());
        btnReload.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        btnReload.setFocusPainted(false);
        btnReload.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnReload.addActionListener(e -> loadData());

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

        actions.add(btnNotifications);
        actions.add(btnReload);
        actions.add(btnChat);

        headerPanel.add(welcomeBox, BorderLayout.WEST);
        headerPanel.add(actions, BorderLayout.EAST);

        JPanel headerCard = new JPanel(new BorderLayout(0, 10));
        headerCard.setBackground(CARD_BG);
        headerCard.setBorder(new CompoundBorder(new LineBorder(BORDER_C, 1, true), new EmptyBorder(20, 20, 20, 20)));
        headerCard.add(headerPanel, BorderLayout.NORTH);

        JLabel headerDescription = new JLabel("Theo dõi đặt phòng, điểm thành viên và ưu đãi mới nhất ngay tại bảng điều khiển của bạn.");
        headerDescription.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        headerDescription.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMuted());
        headerCard.add(headerDescription, BorderLayout.SOUTH);

        add(headerCard, BorderLayout.NORTH);

        JPanel mainGrid = new JPanel(new BorderLayout(20, 20));
        mainGrid.setOpaque(false);

        mainGrid.add(buildLoyaltyCard(), BorderLayout.WEST);

        JPanel centerCol = new JPanel(new BorderLayout(0, 20));
        centerCol.setOpaque(false);
        centerCol.add(buildStatsPanel(), BorderLayout.NORTH);
        centerCol.add(buildCurrentBookingSection(), BorderLayout.CENTER);
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
        t.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMuted());
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
        progressTier.setBackground(quanlykhachsan.frontend.utils.ThemeManager.isDarkMode() ? new Color(30, 41, 59) : new Color(241, 245, 249));
        progressTier.setBorder(null);
        card.add(progressTier);
        card.add(Box.createVerticalStrut(8));

        lblNextTierMsg = new JLabel("Bạn cần thêm 1,000đ để lên hạng Gold");
        lblNextTierMsg.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblNextTierMsg.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMuted());
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

    private JPanel buildStatsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 18, 12));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(0, 0, 0, 0));

        lblBookedCount = new JLabel("0 lần", SwingConstants.CENTER);
        lblStayedCount = new JLabel("0 lần", SwingConstants.CENTER);
        lblSpendTotal = new JLabel("0 đ", SwingConstants.CENTER);
        lblPointsSummary = new JLabel("0", SwingConstants.CENTER);

        panel.add(makeStatCard("🛎️ Đã đặt phòng", lblBookedCount));
        panel.add(makeStatCard("🏨 Đã lưu trú", lblStayedCount));
        panel.add(makeStatCard("💰 Tổng chi tiêu", lblSpendTotal));
        panel.add(makeStatCard("⭐ Điểm tích lũy", lblPointsSummary));

        return panel;
    }

    private JPanel makeStatCard(String title, JLabel valueLabel) {
        JPanel card = new JPanel();
        card.setLayout(new BorderLayout(0, 14));
        card.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        card.setPreferredSize(new Dimension(180, 120));
        card.setBorder(new CompoundBorder(new LineBorder(BORDER_C, 1, true), new EmptyBorder(18, 18, 18, 18)));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblTitle.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMuted());

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        valueLabel.setForeground(PRIMARY);

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);

        return card;
    }

    private JButton createNotificationButton() {
        JButton btn = new JButton("🔔 Thông báo");
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getBorderColor());
        btn.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> {
            Window owner = SwingUtilities.getWindowAncestor(this);
            CustomerNotificationDialog dialog = new CustomerNotificationDialog(owner);
            dialog.setVisible(true);
        });
        return btn;
    }

    private JPanel buildCurrentBookingSection() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 15));
        wrapper.setOpaque(false);

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JLabel title = new JLabel("ĐẶT PHÒNG HIỆN TẠI");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        JLabel subtitle = new JLabel("Xem nhanh đơn đang xử lý và trạng thái đặt phòng mới nhất.");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMuted());

        heading.add(title, BorderLayout.NORTH);
        heading.add(subtitle, BorderLayout.SOUTH);
        wrapper.add(heading, BorderLayout.NORTH);

        activeBookingPanel = new JPanel(new BorderLayout());
        activeBookingPanel.setOpaque(false);
        updateActiveBooking(null);
        wrapper.add(activeBookingPanel, BorderLayout.CENTER);

        return wrapper;
    }

    private void updateActiveBooking(Booking booking) {
        activeBooking = booking;
        activeBookingPanel.removeAll();
        if (booking == null) {
            JPanel empty = new JPanel();
            empty.setLayout(new BorderLayout());
            empty.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
            empty.setBorder(new CompoundBorder(new LineBorder(BORDER_C, 1, true), new EmptyBorder(20, 20, 20, 20)));
            JLabel lblEmpty = new JLabel("Chưa có đơn đặt phòng đang hoạt động.", SwingConstants.CENTER);
            lblEmpty.setFont(new Font("Segoe UI", Font.BOLD, 14));
            lblEmpty.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMuted());
            empty.add(lblEmpty, BorderLayout.CENTER);
            activeBookingPanel.add(empty, BorderLayout.CENTER);
        } else {
            activeBookingPanel.add(makeCurrentBookingCard(booking), BorderLayout.CENTER);
        }
        activeBookingPanel.revalidate();
        activeBookingPanel.repaint();
    }

    private JPanel makeCurrentBookingCard(Booking booking) {
        JPanel card = new JPanel(new BorderLayout(18, 18));
        card.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        card.setBorder(new CompoundBorder(new LineBorder(BORDER_C, 1, true), new EmptyBorder(22, 22, 22, 22)));

        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        JLabel lblRoom = new JLabel("Phòng #" + booking.getRoomId());
        lblRoom.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblRoom.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());

        JLabel lblStatus = new JLabel(renderStatusLabel(booking.getStatus()), SwingConstants.CENTER);
        lblStatus.setOpaque(true);
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblStatus.setBorder(new EmptyBorder(8, 16, 8, 16));
        lblStatus.setBackground(getStatusBackground(booking.getStatus()));
        lblStatus.setForeground(Color.WHITE);

        header.add(lblRoom, BorderLayout.WEST);
        header.add(lblStatus, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        JPanel details = new JPanel(new GridLayout(2, 2, 16, 16));
        details.setOpaque(false);

        details.add(createInfoBlock("Check-in", booking.getCheckInDate() != null ? sdf.format(booking.getCheckInDate()) : "---"));
        details.add(createInfoBlock("Check-out", booking.getCheckOutDate() != null ? sdf.format(booking.getCheckOutDate()) : "---"));
        details.add(createInfoBlock("Ngày đặt", booking.getCreatedAt() != null ? sdf.format(booking.getCreatedAt()) : "---"));
        details.add(createInfoBlock("Tổng tiền", nf.format(booking.getTotalPrice())));

        card.add(details, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        actions.setOpaque(false);

        JButton btnView = new JButton("Xem chi tiết");
        btnView.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnView.setBackground(PRIMARY);
        btnView.setForeground(Color.WHITE);
        btnView.setFocusPainted(false);
        btnView.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnView.addActionListener(e -> {
            Window owner = SwingUtilities.getWindowAncestor(this);
            BookingDetailDialog dialog = new BookingDetailDialog(owner, booking);
            dialog.setVisible(true);
        });
        actions.add(btnView);

        JButton btnCancel = new JButton("Hủy đơn");
        btnCancel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnCancel.setBackground(new Color(239, 68, 68));
        btnCancel.setForeground(Color.WHITE);
        btnCancel.setFocusPainted(false);
        btnCancel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnCancel.addActionListener(e -> cancelCurrentBooking());
        actions.add(btnCancel);

        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private String renderStatusLabel(String status) {
        if (status == null) return "---";
        switch (status.toLowerCase()) {
            case "pending": return "🟡 Chờ xác nhận";
            case "booked": return "🟡 Đã đặt";
            case "checked_in": return "🟢 Đang lưu trú";
            case "checked_out": return "⚪ Đã trả phòng";
            case "paid": return "🟢 Đã thanh toán";
            case "cancelled": return "🔴 Đã hủy";
            default: return status;
        }
    }

    private Color getStatusBackground(String status) {
        if (status == null) return new Color(107, 114, 128);
        switch (status.toLowerCase()) {
            case "pending":
            case "booked":
                return new Color(245, 158, 11);
            case "checked_in":
                return new Color(37, 99, 235);
            case "checked_out":
            case "paid":
                return SUCCESS;
            case "cancelled":
                return new Color(239, 68, 68);
            default:
                return new Color(107, 114, 128);
        }
    }

    private JPanel createInfoBlock(String label, String value) {
        JPanel block = new JPanel(new BorderLayout(0, 6));
        block.setOpaque(false);

        JLabel lblTitle = new JLabel(label);
        lblTitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblTitle.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMuted());

        JLabel lblValue = new JLabel(value);
        lblValue.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblValue.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());

        block.add(lblTitle, BorderLayout.NORTH);
        block.add(lblValue, BorderLayout.CENTER);
        return block;
    }

    private void cancelCurrentBooking() {
        if (activeBooking == null) return;
        String status = activeBooking.getStatus();
        if (status == null || !(status.equalsIgnoreCase("pending") || status.equalsIgnoreCase("booked"))) {
            JOptionPane.showMessageDialog(this, "Không thể hủy đơn này vì đơn đã được xử lý hoặc đang lưu trú.", "Không thể hủy", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn hủy đơn đặt phòng này?", "Xác nhận hủy", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        boolean success = BookingAPI.cancelBooking(activeBooking.getId());
        if (success) {
            JOptionPane.showMessageDialog(this, "Đã hủy đơn đặt phòng.");
            loadData();
        } else {
            JOptionPane.showMessageDialog(this, "Hủy đơn thất bại, vui lòng thử lại.", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel buildPromotionsSection() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(0, 240));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel t = new JLabel("Ưu đãi hấp dẫn dành cho bạn");
        t.setFont(new Font("Segoe UI", Font.BOLD, 16));
        t.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        JLabel subtitle = new JLabel("Các ưu đãi riêng cho khách hàng thân thiết sẽ hiển thị tại đây.");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMuted());

        header.add(t, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.SOUTH);
        panel.add(header, BorderLayout.NORTH);

        promoPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 18, 18));
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
            updateStats(java.util.Collections.emptyList());
            updateActiveBooking(null);
            loadPromotions();
            return;
        }

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private List<Booking> bookings;
            @Override
            protected Void doInBackground() {
                customerData = CustomerAPI.getCustomerById(currentUser.getCustomerId());
                bookings = BookingAPI.getBookingsByCustomer(currentUser.getCustomerId());
                return null;
            }

            @Override
            protected void done() {
                if (customerData != null) {
                    lblName.setText(customerData.getFullName());
                    updateLoyaltyUI(customerData);
                }
                updateStats(bookings);
                updateActiveBooking(findCurrentBooking(bookings));
            }
        };
        worker.execute();
        loadPromotions();
    }

    private Booking findCurrentBooking(List<Booking> bookings) {
        if (bookings == null || bookings.isEmpty()) return null;

        for (Booking b : bookings) {
            if (b.getStatus() != null && (b.getStatus().equalsIgnoreCase("pending") || b.getStatus().equalsIgnoreCase("booked") || b.getStatus().equalsIgnoreCase("checked_in"))) {
                return b;
            }
        }

        Date now = new Date();
        Booking upcoming = null;
        for (Booking b : bookings) {
            if (b.getCheckInDate() != null && b.getCheckInDate().after(now)) {
                if (upcoming == null || b.getCheckInDate().before(upcoming.getCheckInDate())) {
                    upcoming = b;
                }
            }
        }
        if (upcoming != null) return upcoming;

        Booking latest = null;
        for (Booking b : bookings) {
            if (b.getCheckInDate() != null) {
                if (latest == null || b.getCheckInDate().after(latest.getCheckInDate())) {
                    latest = b;
                }
            }
        }
        return latest;
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

    private void updateStats(List<Booking> bookings) {
        int booked = bookings != null ? bookings.size() : 0;
        int stayed = 0;
        double total = 0;
        if (bookings != null) {
            for (Booking b : bookings) {
                if (b.getStatus() != null && (b.getStatus().equalsIgnoreCase("checked_out") || b.getStatus().equalsIgnoreCase("paid"))) {
                    stayed++;
                }
                total += b.getTotalPrice();
            }
        }
        lblBookedCount.setText(booked + " lần");
        lblStayedCount.setText(stayed + " lần");
        lblSpendTotal.setText(nf.format(total));
        lblPointsSummary.setText(customerData != null ? String.format("%,d", customerData.getLoyaltyPoints()) : "0");
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
                label.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMuted());
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
                setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
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
