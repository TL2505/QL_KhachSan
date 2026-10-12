package quanlykhachsan.frontend.view.customer;

import java.awt.*;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import quanlykhachsan.backend.booking.Booking;
import quanlykhachsan.frontend.api.BookingAPI;
import quanlykhachsan.frontend.api.PaymentAPI;

public class CustomerPaymentDialog extends JDialog {

    private Booking booking;
    private Runnable onSuccess;
    private JLabel lblWait;
    private JLabel subtitle;
    private double originalAmount;
    private int pointsUsed = 0;
    private double discountAmount = 0;
    private quanlykhachsan.backend.customer.Customer loyaltyCustomer;
    private JButton btnConfirmPayment;

    private final Color PRIMARY = new Color(13, 148, 136); // Teal 600

    public CustomerPaymentDialog(Window owner, Booking booking, Runnable onSuccess) {
        super(owner, "Thanh Toán & Trả Phòng", Dialog.ModalityType.APPLICATION_MODAL);
        this.booking = booking;
        this.onSuccess = onSuccess;
        
        setSize(450, 600);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());
        getContentPane().setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());

        initUI();
        loadQRCodeBackground();
    }

    private void initUI() {
        // Top Header
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        header.setBorder(new EmptyBorder(20, 20, 10, 20));

        JLabel title = new JLabel("Thanh toán Chuyển khoản QR");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        originalAmount = booking.getTotalPrice() > 0 ? booking.getTotalPrice() : 500000;

        NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        subtitle = new JLabel("Tổng số tiền: " + nf.format(originalAmount));
        subtitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        subtitle.setForeground(new Color(220, 38, 38));
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel desc = new JLabel("Mở ứng dụng Ngân hàng để quét mã QR bên dưới.");
        desc.setFont(new Font("Segoe UI", Font.ITALIC, 14));
        desc.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMuted());
        desc.setAlignmentX(Component.CENTER_ALIGNMENT);

        header.add(title);
        header.add(Box.createVerticalStrut(10));
        header.add(subtitle);
        header.add(Box.createVerticalStrut(10));
        header.add(desc);
        
        loadLoyaltyOptions(header, nf);

        add(header, BorderLayout.NORTH);

        // QR Area inside Center
        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        center.setBorder(new EmptyBorder(10, 20, 10, 20));
        
        lblWait = new JLabel("Đang tạo mã QR, vui lòng chờ...", SwingConstants.CENTER);
        lblWait.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        center.add(lblWait, BorderLayout.CENTER);
        
        add(center, BorderLayout.CENTER);

        // Bottom Actions
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 15));
        bottom.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        bottom.setBorder(new EmptyBorder(0, 0, 20, 0));

        JButton btnCancel = new JButton("Hủy bỏ");
        btnCancel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        btnCancel.addActionListener(e -> dispose());

        btnConfirmPayment = new JButton("Tôi đã chuyển khoản");
        btnConfirmPayment.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnConfirmPayment.setBackground(PRIMARY);
        btnConfirmPayment.setForeground(Color.WHITE);
        btnConfirmPayment.setFocusPainted(false);
        btnConfirmPayment.addActionListener(e -> processConfirm());

        bottom.add(btnCancel);
        bottom.add(btnConfirmPayment);
        
        add(bottom, BorderLayout.SOUTH);
    }

    private void loadLoyaltyOptions(JPanel header, NumberFormat numberFormat) {
        new SwingWorker<quanlykhachsan.backend.customer.Customer, Void>() {
            @Override protected quanlykhachsan.backend.customer.Customer doInBackground() {
                return quanlykhachsan.frontend.api.LoyaltyAPI.getCustomerLoyalty(booking.getCustomerId());
            }
            @Override protected void done() {
                try {
                    loyaltyCustomer = get();
                    if (loyaltyCustomer == null || loyaltyCustomer.getLoyaltyPoints() <= 0 || !isDisplayable()) return;
                    JButton usePoints = new JButton("Dùng " + loyaltyCustomer.getLoyaltyPoints()
                        + " điểm (Giảm " + numberFormat.format(loyaltyCustomer.getLoyaltyPoints() * 1000) + ")");
                    usePoints.setFont(new Font("Segoe UI", Font.BOLD, 12));
                    usePoints.setAlignmentX(Component.CENTER_ALIGNMENT);
                    usePoints.setFocusPainted(false);
                    usePoints.setBackground(quanlykhachsan.frontend.utils.UIThemeTokens.WARNING);
                    usePoints.setForeground(Color.WHITE);
                    usePoints.addActionListener(event -> {
                        int maxPoints = (int) (originalAmount / 1000);
                        pointsUsed = Math.min(loyaltyCustomer.getLoyaltyPoints(), maxPoints);
                        discountAmount = pointsUsed * 1000;
                        subtitle.setText("Tổng số tiền: " + numberFormat.format(originalAmount - discountAmount)
                            + " (Đã trừ " + numberFormat.format(discountAmount) + ")");
                        usePoints.setEnabled(false);
                        usePoints.setText("Đã áp dụng " + pointsUsed + " điểm");
                        lblWait.setIcon(null);
                        lblWait.setText("Đang tải lại mã QR...");
                        loadQRCodeBackground();
                    });
                    header.add(Box.createVerticalStrut(10));
                    header.add(usePoints);
                    header.revalidate();
                    header.repaint();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (java.util.concurrent.ExecutionException ex) {
                    System.err.println("Unable to load loyalty balance: " + ex.getCause());
                }
            }
        }.execute();
    }

    private void loadQRCodeBackground() {
        double currentTotal = originalAmount - discountAmount;
        if (currentTotal <= 0) currentTotal = 0;
        double amount = currentTotal;
        String bankBin = "970422"; // MBBank defaults
        String accountNo = "0987654321"; // Demo
        String accountName = "KHACH SAN DEMO"; 
        String addInfo = "Thanh toan don " + booking.getId();
        
        String url;
        try {
            url = "https://img.vietqr.io/image/" + bankBin + "-" + accountNo + "-compact.png"
                    + "?amount=" + (long)amount
                    + "&addInfo=" + java.net.URLEncoder.encode(addInfo, "UTF-8")
                    + "&accountName=" + java.net.URLEncoder.encode(accountName, "UTF-8");
        } catch (Exception e) {
            lblWait.setText("Lỗi khởi tạo URL QR!");
            return;
        }

        SwingWorker<Image, Void> worker = new SwingWorker<>() {
            @Override
            protected Image doInBackground() throws Exception {
                // Fetch image stream
                return ImageIO.read(new URL(url));
            }
            @Override
            protected void done() {
                try {
                    Image img = get();
                    lblWait.setIcon(new ImageIcon(img.getScaledInstance(350, -1, Image.SCALE_SMOOTH)));
                    lblWait.setText("");
                } catch (Exception ex) {
                    lblWait.setText("Lỗi kết nối tạo QR. Vui lòng thanh toán trực tiếp tại quầy!");
                }
            }
        };
        worker.execute();
    }

    private void processConfirm() {
        int opt = JOptionPane.showConfirmDialog(this, "Bạn xác nhận khoản tiền đã được chuyển thành công?", "Xác nhận Thanh toán", JOptionPane.YES_NO_OPTION);
        if (opt == JOptionPane.YES_OPTION) {
            double amount = originalAmount - discountAmount;
            btnConfirmPayment.setEnabled(false);
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    String paymentResult = PaymentAPI.pay(booking.getId(), amount, "bank_transfer", booking.getCustomerId());
                    if (paymentResult == null || !paymentResult.startsWith("Success")) BookingAPI.checkOut(booking.getId());
                    if (pointsUsed > 0) quanlykhachsan.frontend.api.LoyaltyAPI.redeemPoints(booking.getCustomerId(),
                        pointsUsed, discountAmount, "Đổi điểm thanh toán phòng #" + booking.getId());
                    return null;
                }
                @Override protected void done() {
                    btnConfirmPayment.setEnabled(true);
                    try {
                        get();
                        JOptionPane.showMessageDialog(CustomerPaymentDialog.this,
                            "Thanh toán thành công! Chúc quý khách một ngày tốt lành.");
                        dispose();
                        if (onSuccess != null) onSuccess.run();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        JOptionPane.showMessageDialog(CustomerPaymentDialog.this, ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                    } catch (java.util.concurrent.ExecutionException ex) {
                        JOptionPane.showMessageDialog(CustomerPaymentDialog.this, ex.getCause().getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }
    }
}
