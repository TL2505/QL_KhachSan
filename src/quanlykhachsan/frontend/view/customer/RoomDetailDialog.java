package quanlykhachsan.frontend.view.customer;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import com.google.gson.JsonObject;

import quanlykhachsan.backend.room.Room;
import quanlykhachsan.backend.room.RoomType;
import quanlykhachsan.backend.user.User;
import quanlykhachsan.frontend.api.RoomAPI;
import quanlykhachsan.frontend.api.BookingAPI;
import quanlykhachsan.frontend.api.PromotionAPI;
import quanlykhachsan.frontend.api.PaymentAPI;
import quanlykhachsan.frontend.utils.ThemeManager;
import java.util.List;

public class RoomDetailDialog extends JDialog {

    private Room room;
    private User currentUser;
    private RoomType roomType;
    private JsonObject bestPromo;

    private JLabel lblTypeName, lblCapacity, lblDescription, lblPrice, lblStatus;
    private JLabel lblDiscountValue, lblTotalFinal;
    private JTextField txtCheckIn, txtCheckOut;
    private JButton btnPay;

    private final Color PRIMARY = ThemeManager.getPrimary();
    private final Color SUCCESS = ThemeManager.getSuccess();
    private final Color DANGER  = ThemeManager.getDanger();
    private final Color MUTED   = ThemeManager.getTextMuted();
    private final Color BG      = ThemeManager.getBgPanel();
    private final Color CARD_BG = ThemeManager.getCardBg();
    private final Color BORDER  = ThemeManager.getBorderColor();

    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

    public RoomDetailDialog(Frame owner, Room room, User user) {
        super(owner, "Chi Tiết Phòng " + room.getRoomNumber(), true);
        this.room = room;
        this.currentUser = user;
        
        setSize(980, 620);
        setMinimumSize(new Dimension(980, 620));
        setResizable(false);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG);

        initUI();
        loadData();
    }
    private void initUI() {
        // --- Header ---
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ThemeManager.getCardBg());
        header.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER),
            new EmptyBorder(20, 25, 20, 25)
        ));

        JLabel lblTitle = new JLabel("Phòng " + room.getRoomNumber());
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitle.setForeground(ThemeManager.getTextMain());
        header.add(lblTitle, BorderLayout.WEST);

        lblStatus = createStatusBadge(room.getStatus());
        header.add(lblStatus, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
        // --- Content (3-column layout): Image | Info | Booking ---
        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(BG);
        content.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Left: Image + thumbnails
        JPanel leftCol = new JPanel(new BorderLayout(0, 8));
        leftCol.setOpaque(false);
        leftCol.setPreferredSize(new Dimension(300, 300));
        leftCol.setMaximumSize(new Dimension(300, Integer.MAX_VALUE));

        JLabel mainImage = new JLabel("", SwingConstants.CENTER);
        mainImage.setPreferredSize(new Dimension(300, 300));
        mainImage.setOpaque(true);
        mainImage.setBackground(new Color(230, 230, 230));
        mainImage.setBorder(new LineBorder(BORDER, 1, true));
        mainImage.setText("HÌNH ẢNH PHÒNG");
        mainImage.setForeground(new Color(120, 120, 120));
        mainImage.setFont(new Font("Segoe UI", Font.BOLD, 18));
        leftCol.add(mainImage, BorderLayout.CENTER);

        JPanel thumbs = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        thumbs.setOpaque(false);
        for (int i = 0; i < 4; i++) {
            JLabel t = new JLabel("", SwingConstants.CENTER);
            t.setPreferredSize(new Dimension(60, 50));
            t.setOpaque(true);
            t.setBackground(new Color(245, 245, 245));
            t.setBorder(new LineBorder(BORDER, 1, true));
            thumbs.add(t);
        }
        leftCol.add(thumbs, BorderLayout.SOUTH);

        // Center: Details (type, amenities, description, specs)
        JPanel centerCol = new JPanel();
        centerCol.setLayout(new BoxLayout(centerCol, BoxLayout.Y_AXIS));
        centerCol.setOpaque(false);
        centerCol.setPreferredSize(new Dimension(340, 0));
        centerCol.setMaximumSize(new Dimension(340, Integer.MAX_VALUE));

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        JLabel roomTitle = new JLabel("Phòng " + room.getRoomNumber());
        roomTitle.setFont(new Font("Segoe UI", Font.BOLD, 26));
        roomTitle.setForeground(ThemeManager.getTextMain());
        titleRow.add(roomTitle, BorderLayout.WEST);
        titleRow.add(createStatusBadge(room.getStatus()), BorderLayout.EAST);
        centerCol.add(titleRow);
        centerCol.add(Box.createVerticalStrut(12));

        // Amenities grid
        JPanel am = new JPanel(new GridLayout(0, 2, 8, 6));
        am.setOpaque(false);
        String[] amenities = {"Wifi miễn phí", "TV màn hình phẳng", "Điều hòa", "Bình đun nước", "Tủ lạnh mini", "Máy sấy tóc", "Dép đi trong phòng", "Ban công"};
        for (String a : amenities) {
            JLabel la = new JLabel("• " + a);
            la.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            la.setForeground(ThemeManager.getTextMuted());
            am.add(la);
        }
        centerCol.add(am);
        centerCol.add(Box.createVerticalStrut(12));

        lblDescription = new JLabel("<html>Mô tả: Phòng rộng rãi, tiện nghi đầy đủ, phù hợp cho cặp đôi hoặc công tác.</html>");
        lblDescription.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblDescription.setForeground(ThemeManager.getTextMuted());
        centerCol.add(lblDescription);
        centerCol.add(Box.createVerticalStrut(12));

        // Ensure spec labels exist
        lblTypeName = new JLabel("Đang tải loại phòng...");
        lblTypeName.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblTypeName.setForeground(ThemeManager.getTextMain());
        lblCapacity = new JLabel("👥 Sức chứa: -- người");
        lblCapacity.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblCapacity.setForeground(ThemeManager.getTextMain());

        // Specs table
        JPanel specs = new JPanel(new GridLayout(0, 2, 6, 8));
        specs.setOpaque(false);
        specs.add(makeSpecLabel("Loại phòng:")); specs.add(makeSpecValue(lblTypeName));
        specs.add(makeSpecLabel("Sức chứa:")); specs.add(makeSpecValue(lblCapacity));
        specs.add(makeSpecLabel("Diện tích:")); specs.add(makeSpecValue(new JLabel("-- m²")));
        centerCol.add(specs);

        // Right: Booking box (sticky)
        JPanel rightCol = new JPanel();
        rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.Y_AXIS));
        rightCol.setOpaque(false);
        rightCol.setPreferredSize(new Dimension(220, 0));
        rightCol.setMaximumSize(new Dimension(240, Integer.MAX_VALUE));

        JPanel bookingBox = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(BORDER);
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 12, 12);
            }
        };
        bookingBox.setOpaque(false);
        bookingBox.setLayout(new BoxLayout(bookingBox, BoxLayout.Y_AXIS));
        bookingBox.setBorder(new EmptyBorder(16, 16, 16, 16));

        lblPrice = new JLabel(formatPrice(room.getPrice()));
        lblPrice.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblPrice.setForeground(ThemeManager.getTextMain());
        lblPrice.setAlignmentX(Component.LEFT_ALIGNMENT);
        bookingBox.add(lblPrice);
        bookingBox.add(Box.createVerticalStrut(6));

        // Dates
        JPanel dates = new JPanel();
        dates.setOpaque(false);
        dates.setLayout(new GridLayout(2, 1, 6, 6));
        JPanel pIn = new JPanel(new BorderLayout()); pIn.setOpaque(false); pIn.add(makeLabel("Ngày nhận"), BorderLayout.NORTH);
        txtCheckIn = new JTextField(sdf.format(new Date())); styleTextField(txtCheckIn); pIn.add(txtCheckIn, BorderLayout.CENTER);
        dates.add(pIn);
        JPanel pOut = new JPanel(new BorderLayout()); pOut.setOpaque(false); pOut.add(makeLabel("Ngày trả"), BorderLayout.NORTH);
        Calendar cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_MONTH, 1);
        txtCheckOut = new JTextField(sdf.format(cal.getTime())); styleTextField(txtCheckOut); pOut.add(txtCheckOut, BorderLayout.CENTER);
        dates.add(pOut);
        bookingBox.add(dates);
        bookingBox.add(Box.createVerticalStrut(8));

        // Guests selector
        bookingBox.add(makeLabel("Số lượng khách"));
        JComboBox<String> cbGuests = new JComboBox<>(new String[]{"1 người", "2 người", "3 người"});
        cbGuests.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        bookingBox.add(cbGuests);
        bookingBox.add(Box.createVerticalStrut(12));

        // Total and button
        JPanel totalRow = new JPanel(new BorderLayout()); totalRow.setOpaque(false);
        JLabel ttl = new JLabel("Tổng tạm tính:"); ttl.setForeground(ThemeManager.getTextMuted()); totalRow.add(ttl, BorderLayout.WEST);
        lblTotalFinal = new JLabel(formatPrice(room.getPrice())); lblTotalFinal.setFont(new Font("Segoe UI", Font.BOLD, 18)); lblTotalFinal.setForeground(PRIMARY); totalRow.add(lblTotalFinal, BorderLayout.EAST);
        bookingBox.add(totalRow);
        bookingBox.add(Box.createVerticalStrut(12));

        btnPay = new JButton("💳 Thanh toán & Đặt ngay");
        btnPay.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnPay.setBackground(PRIMARY);
        btnPay.setForeground(Color.WHITE);
        btnPay.setFocusPainted(false);
        btnPay.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnPay.putClientProperty("Button.arc", 12);
        btnPay.addActionListener(e -> processInstantBooking());
        btnPay.setAlignmentX(Component.CENTER_ALIGNMENT);
        bookingBox.add(btnPay);

        rightCol.add(bookingBox);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        content.add(leftCol, gbc);

        gbc.gridx = 1;
        gbc.insets = new Insets(0, 12, 0, 12);
        gbc.weightx = 0.6;
        content.add(centerCol, gbc);

        gbc.gridx = 2;
        gbc.insets = new Insets(0, 0, 0, 0);
        gbc.weightx = 0.4;
        content.add(rightCol, gbc);

        JScrollPane mainScroll = new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        mainScroll.setBorder(null);
        mainScroll.setOpaque(false);
        mainScroll.getViewport().setOpaque(false);
        add(mainScroll, BorderLayout.CENTER);

        // --- Actions ---
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 15));
        actions.setBackground(CARD_BG);
        actions.setBorder(new MatteBorder(1, 0, 0, 0, BORDER));

        JButton btnCancel = new JButton("Đóng");
        btnCancel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnCancel.setBackground(CARD_BG);
        btnCancel.setForeground(ThemeManager.getTextMain());
        btnCancel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnCancel.putClientProperty("Button.arc", 12);
        btnCancel.addActionListener(e -> dispose());

        actions.add(btnCancel);
        add(actions, BorderLayout.SOUTH);

        // Live update pricing when dates change
        FocusAdapter updateListener = new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) { updatePricing(); }
        };
        txtCheckIn.addFocusListener(updateListener);
        txtCheckOut.addFocusListener(updateListener);
    }

    private void loadData() {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                roomType = RoomAPI.getRoomType(room.getRoomTypeId());
                return null;
            }
            @Override
            protected void done() {
                if (roomType != null) {
                    lblTypeName.setText(roomType.getName());
                    lblCapacity.setText("👥 Sức chứa: " + roomType.getCapacity() + " người");
                    lblDescription.setText("<html>" + roomType.getDescription() + "</html>");
                }
                updatePricing();
            }
        };
        worker.execute();
    }

    private void updatePricing() {
        if (currentUser.getCustomerId() == null) {
            lblDiscountValue.setText("Chưa liên kết KH");
            btnPay.setEnabled(false);
            return;
        }

        SwingWorker<JsonObject, Void> worker = new SwingWorker<>() {
            @Override
            protected JsonObject doInBackground() {
                return PromotionAPI.getPromotionPreview(
                    room.getId(),
                    currentUser.getCustomerId(),
                    txtCheckIn.getText(),
                    txtCheckOut.getText()
                );
            }
            @Override
            protected void done() {
                try {
                    bestPromo = get();
                    if (bestPromo != null) {
                        double discount = bestPromo.get("calculatedDiscount").getAsDouble();
                        lblDiscountValue.setText("- " + formatPrice(discount));
                        
                        // Simple day calculation
                        Date d1 = sdf.parse(txtCheckIn.getText());
                        Date d2 = sdf.parse(txtCheckOut.getText());
                        long diff = d2.getTime() - d1.getTime();
                        long nights = Math.max(1, diff / (1000 * 60 * 60 * 24));
                        
                        double total = (room.getPrice() * nights) - discount;
                        lblTotalFinal.setText(formatPrice(total + (total * 0.1))); // Including 10% tax mock
                    }
                } catch (Exception ex) {
                    // Ignore errors during preview
                }
            }
        };
        worker.execute();
    }

    private void processInstantBooking() {
        if (currentUser.getCustomerId() == null) {
            JOptionPane.showMessageDialog(this, "Tài khoản của bạn chưa liên kết với thông tin khách hàng. Vui lòng cập nhật hồ sơ!");
            return;
        }

        btnPay.setEnabled(false);
        String in = txtCheckIn.getText();
        String out = txtCheckOut.getText();

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                // Pre-check current room status to give immediate, clear messages without relying on server-side text
                try {
                    // 1) Check active booking for this room
                    quanlykhachsan.backend.booking.Booking active = BookingAPI.getActiveBookingByRoom(room.getId());
                    if (active != null) {
                        return "Phòng đã được đặt hoặc đang có khách!";
                    }
                } catch (Exception ex) {
                    // ignore and continue to server-side booking attempt
                }

                try {
                    // 2) Refresh room list to inspect status
                    java.util.List<quanlykhachsan.backend.room.Room> all = RoomAPI.getAllRooms();
                    for (quanlykhachsan.backend.room.Room rr : all) {
                        if (rr.getId() == room.getId()) {
                            String st = rr.getStatus() != null ? rr.getStatus().toLowerCase() : "available";
                            if ("maintenance".equals(st)) return "Phòng đang được bảo trì!";
                            if ("cleaning".equals(st)) return "Phòng đang dọn dẹp!";
                            break;
                        }
                    }
                } catch (Exception ex) {
                    // ignore
                }

                // 3. Create Booking
                JsonObject res = BookingAPI.bookRoom(currentUser.getCustomerId(), room.getId(), in, out);
                System.out.println("[DEBUG] bookRoom response: " + (res != null ? res.toString() : "null"));
                if (res == null) return "Lỗi kết nối";

                String status = res.has("status") && !res.get("status").isJsonNull() ? res.get("status").getAsString() : "error";
                if ("success".equals(status)) {
                    // bookingId is inside data.bookingId
                    int bookingId = -1;
                    try {
                        if (res.has("data") && res.get("data").isJsonObject()) {
                            JsonObject data = res.getAsJsonObject("data");
                            if (data.has("bookingId") && !data.get("bookingId").isJsonNull()) {
                                bookingId = data.get("bookingId").getAsInt();
                            }
                        }
                    } catch (Exception ex) {
                        // parsing issue
                    }

                    if (bookingId <= 0) {
                        // Unexpected but booking may have been created; return informative message
                        return "Đã tạo đặt chỗ nhưng không lấy được ID booking. Vui lòng kiểm tra lịch sử đặt phòng.";
                    }

                    // 2. Immediate Payment (Fast flow)
                    String totalStr = lblTotalFinal.getText().replaceAll("[^\\d]", "");
                    double finalAmount = Double.parseDouble(totalStr);

                    String payResp = PaymentAPI.pay(bookingId, finalAmount, "Cash (Fast Pay)", currentUser.getCustomerId());
                    System.out.println("[DEBUG] payment response: " + payResp);
                    boolean payOk = payResp != null && payResp.startsWith("Success");
                    if (payOk) return "Success";
                    else return "Đặt phòng thành công nhưng lỗi thanh toán. Vui lòng liên hệ lễ tân.";
                } else {
                    // Return mapped error message from server if available
                    try {
                        String raw = null;
                        if (res.has("message") && !res.get("message").isJsonNull()) raw = res.get("message").getAsString();
                        else if (res.has("data") && res.get("data").isJsonObject()) {
                            JsonObject inner = res.getAsJsonObject("data");
                            if (inner.has("message") && !inner.get("message").isJsonNull()) raw = inner.get("message").getAsString();
                        }
                        if (raw != null) return mapServerBookingMessage(raw);
                    } catch (Exception ex) {}
                    return "Lỗi đặt phòng: server trả về trạng thái không thành công";
                }
            }
            @Override
            protected void done() {
                try {
                    String result = get();
                    if ("Success".equals(result)) {
                        JOptionPane.showMessageDialog(RoomDetailDialog.this, 
                            "Chúc mừng! Bạn đã đặt phòng " + room.getRoomNumber() + " thành công.\n" +
                            "Hệ thống đã ghi nhận thanh toán.", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                    } else {
                        JOptionPane.showMessageDialog(RoomDetailDialog.this, result, "Lỗi", JOptionPane.ERROR_MESSAGE);
                        btnPay.setEnabled(true);
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(RoomDetailDialog.this, "Lỗi: " + e.getMessage());
                    btnPay.setEnabled(true);
                }
            }
        }.execute();
    }

    // --- Utils ---

    private void styleTextField(JTextField f) {
        f.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        f.setBackground(ThemeManager.getCardBg());
        f.setForeground(ThemeManager.getTextMain());
        f.setCaretColor(ThemeManager.getTextMain());
        f.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(ThemeManager.getBorderColor(), 1, true),
            new EmptyBorder(8, 12, 8, 12)
        ));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
    }

    private JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 13));
        l.setForeground(ThemeManager.getTextMain());
        l.setBorder(new EmptyBorder(0, 0, 5, 0));
        return l;
    }

    private String formatPrice(double price) {
        return NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(price);
    }

    private String toVietnamese(String status) {
        if (status == null) return "N/A";
        switch (status.toLowerCase()) {
            case "available":      return "Trống - Sẵn sàng";
            case "booked":         return "Đã được đặt";
            case "occupied":       return "Đang có khách";
            case "maintenance":    return "Đang bảo trì";
            case "cleaning":       return "Đang dọn dẹp";
            default:               return status;
        }
    }

    private String mapServerBookingMessage(String raw) {
        if (raw == null) return "Lỗi đặt phòng";
        String lower = raw.toLowerCase();
        if (lower.contains("bảo trì") || lower.contains("maintenance")) return "Phòng đang được bảo trì!";
        if (lower.contains("dọn") || lower.contains("clean")) return "Phòng đang dọn dẹp!";
        if (lower.contains("đặt") || lower.contains("booked") || lower.contains("đã được đặt")) return "Phòng đã được đặt rồi";
        if (lower.contains("không tồn tại") || lower.contains("not exist") || lower.contains("not found")) return "Phòng không tồn tại!";
        // Default: return server message unchanged
        return raw;
    }

    private JLabel makeSpecLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l.setForeground(ThemeManager.getTextMuted());
        return l;
    }

    private JLabel makeSpecValue(JLabel v) {
        v.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        v.setForeground(ThemeManager.getTextMain());
        return v;
    }

    private BadgeLabel createStatusBadge(String status) {
        String text = toVietnamese(status).toUpperCase();
        Color fgColor;
        Color bgColor;
        if (status == null) status = "available";
        switch (status.toLowerCase()) {
            case "available":
                fgColor = SUCCESS;
                bgColor = new Color(SUCCESS.getRed(), SUCCESS.getGreen(), SUCCESS.getBlue(), 35);
                break;
            case "booked":
                fgColor = new Color(217, 119, 6);
                bgColor = new Color(245, 158, 11, 35);
                break;
            case "occupied":
                fgColor = DANGER;
                bgColor = new Color(DANGER.getRed(), DANGER.getGreen(), DANGER.getBlue(), 35);
                break;
            case "cleaning":
                Color cleaningColor = new Color(56, 189, 248);
                fgColor = new Color(12, 74, 110);
                bgColor = new Color(cleaningColor.getRed(), cleaningColor.getGreen(), cleaningColor.getBlue(), 35);
                break;
            case "maintenance":
            default:
                fgColor = MUTED;
                bgColor = new Color(MUTED.getRed(), MUTED.getGreen(), MUTED.getBlue(), 35);
                break;
        }
        return new BadgeLabel(text, fgColor, bgColor);
    }

    private static class BadgeLabel extends JLabel {
        private Color bg;
        public BadgeLabel(String text, Color fg, Color bg) {
            super(text, SwingConstants.CENTER);
            setForeground(fg);
            this.bg = bg;
            setFont(new Font("Segoe UI", Font.BOLD, 11));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
