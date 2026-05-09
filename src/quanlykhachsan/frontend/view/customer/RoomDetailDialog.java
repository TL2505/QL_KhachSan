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
import quanlykhachsan.frontend.api.ReviewAPI;
import quanlykhachsan.backend.interaction.Review;
import java.util.List;

public class RoomDetailDialog extends JDialog {

    private Room room;
    private User currentUser;
    private RoomType roomType;
    private JsonObject bestPromo;

    private JLabel lblTypeName, lblCapacity, lblDescription, lblPrice, lblStatus;
    private JLabel lblDiscountValue, lblTotalFinal;
    private JTextField txtCheckIn, txtCheckOut;
    private JTextArea txtComment;
    private JComboBox<Integer> cbRating;
    private JPanel reviewListPanel;
    private JButton btnPay, btnSubmitReview;

    private final Color PRIMARY = new Color(37, 99, 235);
    private final Color SUCCESS = new Color(34, 197, 94);
    private final Color DANGER  = new Color(239, 68, 68);
    private final Color MUTED   = new Color(107, 114, 128);
    private final Color BG      = new Color(250, 250, 250);

    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

    public RoomDetailDialog(Frame owner, Room room, User user) {
        super(owner, "Chi Tiết Phòng " + room.getRoomNumber(), true);
        this.room = room;
        this.currentUser = user;
        
        setSize(500, 650);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG);

        initUI();
        loadData();
    }

    private void initUI() {
        // --- Header ---
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        header.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, new Color(230, 230, 230)),
            new EmptyBorder(20, 25, 20, 25)
        ));

        JLabel lblTitle = new JLabel("Phòng " + room.getRoomNumber());
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitle.setForeground(new Color(31, 41, 55));
        header.add(lblTitle, BorderLayout.WEST);

        lblStatus = new JLabel(toVietnamese(room.getStatus()).toUpperCase());
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblStatus.setOpaque(true);
        lblStatus.setBackground(getStatusColor(room.getStatus()));
        lblStatus.setForeground(Color.WHITE);
        lblStatus.setBorder(new EmptyBorder(4, 12, 4, 12));
        header.add(lblStatus, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        // --- Content ---
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(BG);
        content.setBorder(new EmptyBorder(20, 25, 20, 25));

        // Info Section
        lblTypeName = new JLabel("Đang tải loại phòng...");
        lblTypeName.setFont(new Font("Segoe UI", Font.BOLD, 16));
        content.add(lblTypeName);
        content.add(Box.createVerticalStrut(5));

        lblCapacity = new JLabel("Sức chứa: -- người");
        lblCapacity.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblCapacity.setForeground(MUTED);
        content.add(lblCapacity);
        content.add(Box.createVerticalStrut(10));

        lblDescription = new JLabel("<html><i>Đang tải mô tả chi tiết...</i></html>");
        lblDescription.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        lblDescription.setForeground(new Color(75, 85, 99));
        content.add(lblDescription);

        content.add(Box.createVerticalStrut(20));
        content.add(new JSeparator());
        content.add(Box.createVerticalStrut(20));

        // Booking Controls
        content.add(makeLabel("📅 Ngày Nhận Phòng (yyyy-MM-dd)"));
        txtCheckIn = new JTextField(sdf.format(new Date()));
        styleTextField(txtCheckIn);
        content.add(txtCheckIn);
        content.add(Box.createVerticalStrut(12));

        content.add(makeLabel("📅 Ngày Trả Phòng (yyyy-MM-dd)"));
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        txtCheckOut = new JTextField(sdf.format(cal.getTime()));
        styleTextField(txtCheckOut);
        content.add(txtCheckOut);
        content.add(Box.createVerticalStrut(20));

        // Pricing Section
        JPanel pricePane = new JPanel(new GridLayout(3, 2, 0, 10));
        pricePane.setOpaque(false);

        pricePane.add(new JLabel("Đơn giá / đêm:"));
        lblPrice = new JLabel(formatPrice(room.getPrice()));
        lblPrice.setHorizontalAlignment(SwingConstants.RIGHT);
        pricePane.add(lblPrice);

        pricePane.add(new JLabel("Khuyến mãi áp dụng:"));
        lblDiscountValue = new JLabel("- " + formatPrice(0));
        lblDiscountValue.setForeground(DANGER);
        lblDiscountValue.setHorizontalAlignment(SwingConstants.RIGHT);
        pricePane.add(lblDiscountValue);

        JLabel lblTotalText = new JLabel("💰 TỔNG THANH TOÁN:");
        lblTotalText.setFont(new Font("Segoe UI", Font.BOLD, 15));
        pricePane.add(lblTotalText);

        lblTotalFinal = new JLabel(formatPrice(room.getPrice()));
        lblTotalFinal.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTotalFinal.setForeground(PRIMARY);
        lblTotalFinal.setHorizontalAlignment(SwingConstants.RIGHT);
        pricePane.add(lblTotalFinal);

        content.add(pricePane);
        content.add(Box.createVerticalStrut(20));

        // --- Reviews Section ---
        content.add(new JSeparator());
        content.add(Box.createVerticalStrut(15));
        JLabel lblRevTitle = new JLabel("Đánh giá khách hàng");
        lblRevTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        content.add(lblRevTitle);
        content.add(Box.createVerticalStrut(10));

        reviewListPanel = new JPanel();
        reviewListPanel.setLayout(new BoxLayout(reviewListPanel, BoxLayout.Y_AXIS));
        reviewListPanel.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        
        JScrollPane scrollReviews = new JScrollPane(reviewListPanel);
        scrollReviews.setPreferredSize(new Dimension(0, 150));
        scrollReviews.setBorder(new LineBorder(new Color(229, 231, 235), 1, true));
        content.add(scrollReviews);
        content.add(Box.createVerticalStrut(15));

        // Post Review form
        JPanel postPanel = new JPanel(new BorderLayout(5, 5));
        postPanel.setOpaque(false);
        
        cbRating = new JComboBox<>(new Integer[]{5, 4, 3, 2, 1});
        postPanel.add(cbRating, BorderLayout.WEST);
        
        txtComment = new JTextArea(2, 20);
        txtComment.setLineWrap(true);
        txtComment.setBorder(new LineBorder(new Color(209, 213, 219)));
        postPanel.add(new JScrollPane(txtComment), BorderLayout.CENTER);
        
        btnSubmitReview = new JButton("Gửi");
        btnSubmitReview.addActionListener(e -> postReview());
        postPanel.add(btnSubmitReview, BorderLayout.EAST);
        
        content.add(postPanel);
        
        // Wrap everything in a main scroll pane if it gets too long
        JScrollPane mainScroll = new JScrollPane(content);
        mainScroll.setBorder(null);
        add(mainScroll, BorderLayout.CENTER);

        // --- Actions ---
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 15));
        actions.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        actions.setBorder(new MatteBorder(1, 0, 0, 0, new Color(230, 230, 230)));

        JButton btnCancel = new JButton("Đóng");
        btnCancel.addActionListener(e -> dispose());
        btnCancel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btnPay = new JButton("💳 Thanh toán & Đặt ngay") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? PRIMARY : MUTED);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                super.paintComponent(g);
            }
        };
        btnPay.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnPay.setForeground(Color.WHITE);
        btnPay.setContentAreaFilled(false);
        btnPay.setBorderPainted(false);
        btnPay.setFocusPainted(false);
        btnPay.setPreferredSize(new Dimension(200, 40));
        btnPay.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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
                    lblCapacity.setText("Sức chứa: " + roomType.getCapacity() + " người");
                    lblDescription.setText("<html>" + roomType.getDescription() + "</html>");
                }
                updatePricing();
                loadReviews();
            }
        };
        worker.execute();
    }

    private void loadReviews() {
        reviewListPanel.removeAll();
        SwingWorker<List<Review>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Review> doInBackground() {
                return ReviewAPI.getReviewsByRoom(room.getId());
            }
            @Override
            protected void done() {
                try {
                    List<Review> list = get();
                    if (list.isEmpty()) {
                        JLabel empty = new JLabel("Chưa có đánh giá nào.");
                        empty.setBorder(new EmptyBorder(10, 10, 10, 10));
                        reviewListPanel.add(empty);
                    } else {
                        for (Review r : list) {
                            reviewListPanel.add(createReviewCard(r));
                        }
                    }
                    reviewListPanel.revalidate();
                    reviewListPanel.repaint();
                } catch (Exception e) {}
            }
        };
        worker.execute();
    }

    private JPanel createReviewCard(Review r) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBackground(quanlykhachsan.frontend.utils.ThemeManager.isDarkMode() ? new Color(30, 41, 59) : new Color(241, 245, 249));
        card.setBorder(new CompoundBorder(
            new EmptyBorder(5, 5, 5, 5),
            new EmptyBorder(8, 12, 8, 12)
        ));
        
        // Custom painting for bubble
        JPanel bubble = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(quanlykhachsan.frontend.utils.ThemeManager.getBorderColor());
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 20, 20);
            }
        };
        bubble.setLayout(new BorderLayout(5, 5));
        bubble.setOpaque(false);
        bubble.setBorder(new EmptyBorder(10, 15, 10, 15));

        JLabel top = new JLabel("👤 " + r.getCustomerName() + "  " + "⭐".repeat(r.getRating()));
        top.setFont(new Font("Segoe UI", Font.BOLD, 13));
        bubble.add(top, BorderLayout.NORTH);
        
        JLabel mid = new JLabel("<html><p style='width: 280px;'>" + r.getComment() + "</p></html>");
        mid.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        mid.setForeground(new Color(51, 65, 85));
        bubble.add(mid, BorderLayout.CENTER);
        
        card.add(bubble, BorderLayout.CENTER);
        return card;
    }

    private void postReview() {
        if (currentUser.getCustomerId() == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng cập nhật thông tin khách hàng để đánh giá!");
            return;
        }
        if (txtComment.getText().trim().isEmpty()) return;

        Review r = new Review();
        r.setCustomerId(currentUser.getCustomerId());
        r.setRoomId(room.getId());
        r.setRating((Integer)cbRating.getSelectedItem());
        r.setComment(txtComment.getText().trim());

        btnSubmitReview.setEnabled(false);
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() { return ReviewAPI.addReview(r); }
            @Override protected void done() {
                btnSubmitReview.setEnabled(true);
                try {
                    if ("Success".equals(get())) {
                        txtComment.setText("");
                        loadReviews();
                    } else {
                        JOptionPane.showMessageDialog(RoomDetailDialog.this, get());
                    }
                } catch (Exception e) {}
            }
        }.execute();
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
        f.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(209, 213, 219), 1, true),
            new EmptyBorder(8, 12, 8, 12)
        ));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
    }

    private JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 13));
        l.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        l.setBorder(new EmptyBorder(0, 0, 5, 0));
        return l;
    }

    private String formatPrice(double price) {
        return NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(price);
    }

    private Color getStatusColor(String status) {
        if ("available".equals(status)) return SUCCESS;
        if ("booked".equals(status)) return new Color(234, 179, 8);
        return MUTED;
    }

    private String toVietnamese(String status) {
        if (status == null) return "N/A";
        switch (status.toLowerCase()) {
            case "available":      return "Trống - Sẵn sàng";
            case "booked":         return "Đã được đặt";
            case "occupied":       return "Đang có khách";
            case "maintenance":    return "Đang bảo trì";
            default:               return status;
        }
    }
}
