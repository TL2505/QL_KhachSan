package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import quanlykhachsan.backend.model.Room;
import quanlykhachsan.backend.model.User;
import quanlykhachsan.frontend.api.BookingAPI;
import com.google.gson.JsonObject;

public class BookingWizardDialog extends JDialog {

    private User currentUser;
    private Room room;
    private String checkIn, checkOut;
    private long days;
    private double totalPrice;

    private final Color PRIMARY = new Color(13, 148, 136);
    private final Color BG = quanlykhachsan.frontend.utils.ThemeManager.getBgPanel();
    private final NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

    public BookingWizardDialog(Window owner, User user, Room room, String cin, String cout) {
        super(owner, "Hoàn tất đặt phòng", Dialog.ModalityType.APPLICATION_MODAL);
        this.currentUser = user;
        this.room = room;
        this.checkIn = cin;
        this.checkOut = cout;

        calculatePrice();
        initUI();
        
        setSize(450, 600);
        setLocationRelativeTo(owner);
    }

    private void calculatePrice() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            long diff = sdf.parse(checkOut).getTime() - sdf.parse(checkIn).getTime();
            days = diff / (1000 * 60 * 60 * 24);
            if (days < 1) days = 1;
            totalPrice = days * room.getPrice();
        } catch (Exception e) {
            days = 1;
            totalPrice = room.getPrice();
        }
    }

    private void initUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG);

        // Header
        JPanel header = new JPanel(new GridLayout(2, 1));
        header.setBackground(PRIMARY);
        header.setBorder(new EmptyBorder(20, 25, 20, 25));
        
        JLabel title = new JLabel("Xác nhận Đặt phòng");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Color.WHITE);
        header.add(title);

        JLabel sub = new JLabel("Bước cuối cùng để sở hữu căn phòng tuyệt vời");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sub.setForeground(new Color(204, 251, 241));
        header.add(sub);
        add(header, BorderLayout.NORTH);

        // Content
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(25, 25, 25, 25));

        content.add(createSectionTitle("Thông tin phòng"));
        content.add(createDetailRow("Số phòng:", "Phòng " + room.getRoomNumber()));
        content.add(createDetailRow("Thời gian:", checkIn + " đến " + checkOut));
        content.add(createDetailRow("Tổng số đêm:", days + " đêm"));
        content.add(Box.createVerticalStrut(20));

        content.add(createSectionTitle("Chi tiết giá"));
        content.add(createDetailRow("Đơn giá:", nf.format(room.getPrice()) + "/đêm"));
        content.add(createDetailRow("Thành tiền:", nf.format(totalPrice)));
        content.add(Box.createVerticalStrut(20));

        content.add(createSectionTitle("Phương thức thanh toán"));
        JComboBox<String> cbMethod = new JComboBox<>(new String[]{"Tiền mặt", "Chuyển khoản QR"});
        cbMethod.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        cbMethod.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        content.add(cbMethod);
        content.add(Box.createVerticalStrut(10));

        JButton btnShowQR = new JButton("HIỂN THỊ MÃ QR");
        btnShowQR.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnShowQR.setForeground(Color.WHITE);
        btnShowQR.setBackground(new Color(14, 165, 233));
        btnShowQR.setFocusPainted(false);
        btnShowQR.setVisible(false);
        content.add(btnShowQR);

        cbMethod.addActionListener(e -> {
            if ("Chuyển khoản QR".equals(cbMethod.getSelectedItem())) {
                btnShowQR.setVisible(true);
            } else {
                btnShowQR.setVisible(false);
            }
        });
        
        btnShowQR.addActionListener(e -> actionShowQR());

        add(content, BorderLayout.CENTER);

        // Action
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        footer.setBorder(new CompoundBorder(
            new MatteBorder(1, 0, 0, 0, quanlykhachsan.frontend.utils.ThemeManager.getBorderColor()),
            new EmptyBorder(15, 25, 15, 25)
        ));

        JButton btnConfirm = new JButton("Xác nhận & Thanh toán " + nf.format(totalPrice));
        btnConfirm.setBackground(PRIMARY);
        btnConfirm.setForeground(Color.WHITE);
        btnConfirm.setFont(new Font("Segoe UI", Font.BOLD, 15));
        btnConfirm.setPreferredSize(new Dimension(0, 50));
        btnConfirm.setFocusPainted(false);
        btnConfirm.addActionListener(e -> processBooking());
        footer.add(btnConfirm);

        add(footer, BorderLayout.SOUTH);
    }

    private JLabel createSectionTitle(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lbl.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        lbl.setBorder(new EmptyBorder(0, 0, 10, 0));
        return lbl;
    }

    private JPanel createDetailRow(String label, String value) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(4, 0, 4, 0));
        
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lbl.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMuted());
        
        JLabel val = new JLabel(value);
        val.setFont(new Font("Segoe UI", Font.BOLD, 13));
        val.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        
        p.add(lbl, BorderLayout.WEST);
        p.add(val, BorderLayout.EAST);
        return p;
    }

    private void actionShowQR() {
        try {
            String bankBin = "970422";
            String accountNo = "0987654321";
            String accountName = "KHACH SAN DEMO";
            String addInfo = "Thanh toan tien coc phong " + room.getRoomNumber();
            String amountStr = String.valueOf((long) totalPrice);

            String qrUrl = "https://img.vietqr.io/image/" + bankBin + "-" + accountNo + "-compact.png"
                    + "?amount=" + amountStr
                    + "&addInfo=" + java.net.URLEncoder.encode(addInfo, "UTF-8")
                    + "&accountName=" + java.net.URLEncoder.encode(accountName, "UTF-8");

            JDialog dialog = new JDialog(this, "Quét mã QR để Thanh toán", true);
            dialog.setSize(400, 500);
            dialog.setLocationRelativeTo(this);
            dialog.setLayout(new BorderLayout());

            JLabel lblWait = new JLabel("Đang tạo mã QR, vui lòng chờ...", SwingConstants.CENTER);
            dialog.add(lblWait, BorderLayout.CENTER);

            SwingWorker<java.awt.Image, Void> loadQR = new SwingWorker<>() {
                @Override
                protected java.awt.Image doInBackground() throws Exception {
                    return javax.imageio.ImageIO.read(new java.net.URL(qrUrl));
                }
                @Override
                protected void done() {
                    try {
                        java.awt.Image img = get();
                        lblWait.setIcon(new ImageIcon(img.getScaledInstance(350, -1, java.awt.Image.SCALE_SMOOTH)));
                        lblWait.setText("");
                    } catch (Exception ex) {
                        lblWait.setText("Lỗi kết nối tạo QR!");
                    }
                }
            };
            loadQR.execute();
            dialog.setVisible(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi tạo QR: " + ex.getMessage());
        }
    }

    private void processBooking() {
        if (currentUser.getCustomerId() == null) {
            JOptionPane.showMessageDialog(this, "Tài khoản của bạn chưa liên kết thông tin khách hàng. Vui lòng cập nhật hồ sơ.");
            return;
        }

        disableUI();
        new SwingWorker<JsonObject, Void>() {
            @Override protected JsonObject doInBackground() {
                return BookingAPI.bookRoom(currentUser.getCustomerId(), room.getId(), checkIn, checkOut);
            }
            @Override protected void done() {
                try {
                    JsonObject res = get();
                    if (res != null && "success".equals(res.get("status").getAsString())) {
                        JOptionPane.showMessageDialog(BookingWizardDialog.this, 
                            "Chúc mừng! Bạn đã đặt phòng thành công.\nHệ thống đang thông báo cho lễ tân để chuẩn bị đón tiếp bạn.", 
                            "Thành công", JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                    } else {
                        String msg = res != null ? res.get("message").getAsString() : "Lỗi không xác định";
                        JOptionPane.showMessageDialog(BookingWizardDialog.this, "Lỗi: " + msg);
                        enableUI();
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(BookingWizardDialog.this, "Lỗi kết nối: " + e.getMessage());
                    enableUI();
                }
            }
        }.execute();
    }

    private void disableUI() {
        getContentPane().setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    }

    private void enableUI() {
        getContentPane().setEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }
}
