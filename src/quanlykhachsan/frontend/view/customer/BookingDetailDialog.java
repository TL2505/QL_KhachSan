package quanlykhachsan.frontend.view.customer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import quanlykhachsan.backend.booking.Booking;
import quanlykhachsan.backend.room.Room;
import quanlykhachsan.frontend.api.RoomAPI;
import quanlykhachsan.frontend.utils.ThemeManager;

public class BookingDetailDialog extends JDialog {

    private Booking booking;
    private final Color BG = ThemeManager.getBgPanel();
    private final Color CARD_BG = ThemeManager.getCardBg();
    private final Color BORDER_C = ThemeManager.getBorderColor();
    private final Color PRIMARY = new Color(13, 148, 136);

    private SimpleDateFormat dateFmt = new SimpleDateFormat("dd/MM/yyyy");
    private SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm");
    private NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

    public BookingDetailDialog(Window owner, Booking booking) {
        super(owner, "Chi tiết đặt phòng", Dialog.ModalityType.APPLICATION_MODAL);
        this.booking = booking;

        initUI();
        setSize(520, 420);
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PRIMARY);
        header.setBorder(new EmptyBorder(18, 18, 18, 18));

        JLabel title = new JLabel("Chi tiết đặt phòng");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);

        JLabel bookingIdLabel = new JLabel("Mã đặt: #" + booking.getId());
        bookingIdLabel.setForeground(Color.WHITE);
        bookingIdLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        header.add(bookingIdLabel, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new GridBagLayout());
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(20, 20, 20, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(12, 12, 12, 12);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;

        addLabelPair(content, gbc, 0, "Phòng:", "#" + booking.getRoomId());
        addLabelPair(content, gbc, 1, "Ngày nhận:", booking.getCheckInDate() != null ? dateFmt.format(booking.getCheckInDate()) : "---");
        addLabelPair(content, gbc, 2, "Ngày trả:", booking.getCheckOutDate() != null ? dateFmt.format(booking.getCheckOutDate()) : "---");
        addLabelPair(content, gbc, 3, "Giờ nhận:", booking.getCheckInDate() != null ? timeFmt.format(booking.getCheckInDate()) : "---");
        addLabelPair(content, gbc, 4, "Giờ trả:", booking.getCheckOutDate() != null ? timeFmt.format(booking.getCheckOutDate()) : "---");
        addLabelPair(content, gbc, 5, "Tổng tiền:", nf.format(booking.getTotalPrice()));
        addLabelPair(content, gbc, 6, "Trạng thái:", translateStatus(booking.getStatus()));
        addLabelPair(content, gbc, 7, "Ngày đặt:", booking.getCreatedAt() != null ? dateFmt.format(booking.getCreatedAt()) : "---");

        add(content, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(0, 0, 15, 15));

        JButton btnClose = new JButton("Đóng");
        btnClose.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnClose.setBackground(ThemeManager.getBorderColor());
        btnClose.setForeground(ThemeManager.getTextMain());
        btnClose.setFocusPainted(false);
        btnClose.addActionListener(e -> dispose());
        footer.add(btnClose);

        add(footer, BorderLayout.SOUTH);
    }

    private void addLabelPair(JPanel parent, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridy = row;

        gbc.gridx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lbl.setForeground(ThemeManager.getTextMuted());
        parent.add(lbl, gbc);

        gbc.gridx = 1;
        JLabel val = new JLabel(value);
        val.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        val.setForeground(ThemeManager.getTextMain());
        parent.add(val, gbc);
    }

    private String translateStatus(String status) {
        if (status == null) return "---";
        switch (status.toLowerCase()) {
            case "pending": return "Chờ xác nhận";
            case "checked_in": return "Đang ở";
            case "checked_out": return "Đã trả phòng";
            case "paid": return "Đã thanh toán";
            case "cancelled": return "Đã hủy";
            default: return status;
        }
    }
}
