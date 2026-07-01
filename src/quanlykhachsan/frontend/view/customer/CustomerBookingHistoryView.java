package quanlykhachsan.frontend.view.customer;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import quanlykhachsan.backend.booking.Booking;
import quanlykhachsan.backend.user.User;
import quanlykhachsan.frontend.api.BookingAPI;
import quanlykhachsan.frontend.view.customer.CustomerPaymentDialog;
import quanlykhachsan.frontend.view.customer.ReviewSubmissionDialog;
import quanlykhachsan.frontend.utils.ThemeManager;

public class CustomerBookingHistoryView extends JPanel {

    private User currentUser;
    private JTable tblBookings;
    private DefaultTableModel tblModel;

    private final Color PRIMARY  = new Color(37, 99, 235);
    private final Color BG       = ThemeManager.getBgPanel();
    private final Color CARD_BG  = ThemeManager.getCardBg();
    private final Color BORDER_C = ThemeManager.getBorderColor();
    private final Color SUCCESS  = new Color(34, 197, 94);

    private SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
    private SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm");
    private NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

    public CustomerBookingHistoryView(User currentUser) {
        this.currentUser = currentUser;
        setLayout(new BorderLayout(20, 20));
        setBackground(BG);
        setBorder(new EmptyBorder(25, 25, 25, 25));

        initUI();
        loadData();
    }

    private void initUI() {
        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.setOpaque(false);

        JLabel title = new JLabel("Lịch sử đặt phòng của bạn");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(ThemeManager.getTextMain());
        header.add(title, BorderLayout.WEST);

        JButton btnReload = new JButton("🔄 Tải lại");
        btnReload.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnReload.setBackground(ThemeManager.getBorderColor());
        btnReload.setForeground(ThemeManager.getTextMain());
        btnReload.setFocusPainted(false);
        btnReload.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnReload.addActionListener(e -> loadData());
        header.add(btnReload, BorderLayout.EAST);

        JLabel subtitle = new JLabel("Nhấn đúp vào một đơn để xem chi tiết và đánh giá dịch vụ.");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(ThemeManager.getTextMuted());
        header.add(subtitle, BorderLayout.SOUTH);

        add(header, BorderLayout.NORTH);
        add(buildBookingHistoryPanel(), BorderLayout.CENTER);
    }

    private JPanel buildBookingHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 15)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                int arc = 14;
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                g2.setColor(BORDER_C);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            }
        };
        panel.setOpaque(false);

        tblModel = new DefaultTableModel(new String[]{"Ngày đặt", "Phòng", "Thời gian", "Tổng tiền", "Trạng thái", "Hành động"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return column == 5; }
        };

        tblBookings = new JTable(tblModel);
        styleTable(tblBookings);
        tblBookings.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblBookings.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && tblBookings.getSelectedRow() != -1) {
                    int row = tblBookings.convertRowIndexToModel(tblBookings.getSelectedRow());
                    Object value = tblModel.getValueAt(row, 5);
                    if (value instanceof Booking) {
                        Booking booking = (Booking) value;
                        Window owner = SwingUtilities.getWindowAncestor(CustomerBookingHistoryView.this);
                        BookingDetailDialog dialog = new BookingDetailDialog(owner, booking);
                        dialog.setVisible(true);
                    }
                }
            }
        });

        JScrollPane scroll = new JScrollPane(tblBookings);
        scroll.setBorder(new EmptyBorder(10, 10, 10, 10));
        scroll.getViewport().setBackground(CARD_BG);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private void styleTable(JTable table) {
        table.setRowHeight(48);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        table.setBackground(CARD_BG);
        table.setForeground(ThemeManager.getTextMain());
        table.setSelectionBackground(new Color(224, 242, 254));
        table.setSelectionForeground(ThemeManager.getTextMain());

        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.getTableHeader().setPreferredSize(new Dimension(0, 40));
        table.getTableHeader().setBackground(ThemeManager.getCardBg());
        table.getTableHeader().setForeground(ThemeManager.getTextMain());
        table.getTableHeader().setOpaque(true);

        table.getColumnModel().getColumn(4).setCellRenderer(new StatusRenderer());
        table.getColumnModel().getColumn(5).setCellRenderer(new ActionBtnRenderer());
        table.getColumnModel().getColumn(5).setCellEditor(new ActionBtnEditor());
    }

    private void loadData() {
        SwingWorker<List<Booking>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Booking> doInBackground() {
                if (currentUser.getCustomerId() == null) {
                    return java.util.Collections.emptyList();
                }
                return BookingAPI.getBookingsByCustomer(currentUser.getCustomerId());
            }

            @Override
            protected void done() {
                try {
                    List<Booking> bookings = get();
                    tblModel.setRowCount(0);
                    if (bookings == null || bookings.isEmpty()) {
                        tblModel.addRow(new Object[]{"---", "---", "---", "---", "---", "---"});
                    } else {
                        for (Booking b : bookings) {
                            tblModel.addRow(new Object[]{
                                    b.getCheckInDate() != null ? sdf.format(b.getCheckInDate()) :
                                            (b.getCreatedAt() != null ? sdf.format(b.getCreatedAt()) : "---"),
                                    "Phòng #" + b.getRoomId(),
                                    formatBookingTimes(b.getCheckInDate(), b.getCheckOutDate()),
                                    nf.format(b.getTotalPrice()),
                                    b.getStatus(),
                                    b
                            });
                        }
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(CustomerBookingHistoryView.this, "Không tải được lịch sử đặt phòng: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private String formatBookingTimes(Date checkIn, Date checkOut) {
        String from = checkIn != null ? timeFmt.format(checkIn) : "--:--";
        String to = checkOut != null ? timeFmt.format(checkOut) : "--:--";
        return from + " - " + to;
    }

    class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            label.setHorizontalAlignment(CENTER);
            label.setFont(new Font("Segoe UI", Font.BOLD, 12));

            String status = value != null ? String.valueOf(value).toLowerCase() : "";
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
                label.setForeground(ThemeManager.getTextMuted());
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
            if (value instanceof Booking) {
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
                    setBackground(CARD_BG);
                    setForeground(new Color(203, 213, 225));
                    setEnabled(false);
                }
            } else {
                setText("---");
                setBackground(CARD_BG);
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
                        CustomerPaymentDialog dialog = new CustomerPaymentDialog(owner, currentBooking, () -> loadData());
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
            currentBooking = value instanceof Booking ? (Booking) value : null;
            if (currentBooking != null) {
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
                    btn.setBackground(CARD_BG);
                    btn.setForeground(new Color(203, 213, 225));
                }
            }
            return btn;
        }

        @Override
        public Object getCellEditorValue() {
            return currentBooking;
        }
    }
}
