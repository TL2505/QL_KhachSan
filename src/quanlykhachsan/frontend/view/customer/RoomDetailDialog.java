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
        
        setSize(460, 500);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG);

        initUI();
        loadData();
    }    private void initUI() {
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

        // --- Content ---
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(BG);
        content.setBorder(new EmptyBorder(20, 25, 20, 25));

        // Info Section (Static Info Card)
        JPanel infoCard = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(BORDER);
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 16, 16);
            }
        };
        infoCard.setLayout(new BoxLayout(infoCard, BoxLayout.Y_AXIS));
        infoCard.setOpaque(false);
        infoCard.setBorder(new EmptyBorder(16, 20, 16, 20));

        lblTypeName = new JLabel("Đang tải loại phòng...");
        lblTypeName.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTypeName.setForeground(ThemeManager.getTextMain());
        infoCard.add(lblTypeName);
        infoCard.add(Box.createVerticalStrut(6));

        lblCapacity = new JLabel("👥 Sức chứa: -- người");
        lblCapacity.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblCapacity.setForeground(MUTED);
        infoCard.add(lblCapacity);
        infoCard.add(Box.createVerticalStrut(10));

        lblDescription = new JLabel("<html><i>Đang tải mô tả chi tiết...</i></html>");
        lblDescription.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblDescription.setForeground(ThemeManager.getTextMuted());
        infoCard.add(lblDescription);

        content.add(infoCard);
        content.add(Box.createVerticalStrut(20));

        // Booking Controls (Side-by-Side Date Fields)
        JPanel datePanel = new JPanel(new GridLayout(1, 2, 15, 0));
        datePanel.setOpaque(false);

        JPanel pIn = new JPanel();
        pIn.setLayout(new BoxLayout(pIn, BoxLayout.Y_AXIS));
        pIn.setOpaque(false);
        pIn.add(makeLabel("📅 Ngày Nhận"));
        txtCheckIn = new JTextField(sdf.format(new Date()));
        styleTextField(txtCheckIn);
        pIn.add(txtCheckIn);
        datePanel.add(pIn);

        JPanel pOut = new JPanel();
        pOut.setLayout(new BoxLayout(pOut, BoxLayout.Y_AXIS));
        pOut.setOpaque(false);
        pOut.add(makeLabel("📅 Ngày Trả"));
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        txtCheckOut = new JTextField(sdf.format(cal.getTime()));
        styleTextField(txtCheckOut);
        pOut.add(txtCheckOut);
        datePanel.add(pOut);

        content.add(datePanel);
        content.add(Box.createVerticalStrut(20));

        // Pricing Section (Invoice ticket style)
        JPanel priceCard = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(BORDER);
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 16, 16);
                // Dash separator line above total payment
                g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4}, 0));
                g2.setColor(BORDER);
                g2.drawLine(15, getHeight() - 40, getWidth() - 15, getHeight() - 40);
            }
        };
        priceCard.setLayout(new BorderLayout());
        priceCard.setOpaque(false);
        priceCard.setBorder(new EmptyBorder(16, 20, 16, 20));

        JPanel pricePane = new JPanel(new GridLayout(3, 2, 0, 8));
        pricePane.setOpaque(false);

        JLabel lblUnit = new JLabel("Đơn giá / đêm:");
        lblUnit.setForeground(ThemeManager.getTextMuted());
        pricePane.add(lblUnit);
        
        lblPrice = new JLabel(formatPrice(room.getPrice()));
        lblPrice.setForeground(ThemeManager.getTextMain());
        lblPrice.setHorizontalAlignment(SwingConstants.RIGHT);
        pricePane.add(lblPrice);

        JLabel lblPromo = new JLabel("Khuyến mãi áp dụng:");
        lblPromo.setForeground(ThemeManager.getTextMuted());
        pricePane.add(lblPromo);
        
        lblDiscountValue = new JLabel("- " + formatPrice(0));
        lblDiscountValue.setForeground(DANGER);
        lblDiscountValue.setHorizontalAlignment(SwingConstants.RIGHT);
        pricePane.add(lblDiscountValue);

        JLabel lblTotalText = new JLabel("💰 TỔNG THANH TOÁN (10% VAT):");
        lblTotalText.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblTotalText.setForeground(ThemeManager.getTextMain());
        pricePane.add(lblTotalText);

        lblTotalFinal = new JLabel(formatPrice(room.getPrice()));
        lblTotalFinal.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblTotalFinal.setForeground(PRIMARY);
        lblTotalFinal.setHorizontalAlignment(SwingConstants.RIGHT);
        pricePane.add(lblTotalFinal);

        priceCard.add(pricePane, BorderLayout.CENTER);
        content.add(priceCard);

        // Wrap everything in a main scroll pane if it gets too long
        JScrollPane mainScroll = new JScrollPane(content);
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

        btnPay = new JButton("💳 Thanh toán & Đặt ngay");
        btnPay.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnPay.setBackground(PRIMARY);
        btnPay.setForeground(Color.WHITE);
        btnPay.setFocusPainted(false);
        btnPay.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnPay.putClientProperty("Button.arc", 12);
        btnPay.addActionListener(e -> processInstantBooking());

        actions.add(btnCancel);
        actions.add(btnPay);
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
                // 1. Create Booking
                JsonObject res = BookingAPI.bookRoom(currentUser.getCustomerId(), room.getId(), in, out);
                if (res != null && "success".equals(res.get("status").getAsString())) {
                    int bookingId = res.get("bookingId").getAsInt();
                    
                    // 2. Immediate Payment (Fast flow)
                    String totalStr = lblTotalFinal.getText().replaceAll("[^\\d]", "");
                    double finalAmount = Double.parseDouble(totalStr);
                    
                    boolean payOk = PaymentAPI.processPayment(bookingId, finalAmount, "Cash (Fast Pay)");
                    if (payOk) return "Success";
                    else return "Đặt phòng thành công nhưng lỗi thanh toán. Vui lòng liên hệ lễ tân.";
                }
                return res != null ? res.get("message").getAsString() : "Lỗi kết nối";
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
