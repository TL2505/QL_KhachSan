package quanlykhachsan.frontend.view.staff;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;
import quanlykhachsan.backend.booking.Booking;
import quanlykhachsan.backend.room.Room;
import quanlykhachsan.frontend.api.BookingAPI;
import quanlykhachsan.frontend.api.RoomAPI;

public class CustomerHistoryDialog extends JDialog {

    private int customerId;
    private String customerName;
    private JTable historyTable;
    private DefaultTableModel tableModel;
    private JLabel lblStatus;
    private JLabel lblTotalSpent;

    private final Color SUCCESS = new Color(5, 150, 105);
    private final Color DANGER = new Color(220, 38, 38);
    private final Color MUTED = new Color(107, 114, 128);
    private final Color ROW_EVEN = quanlykhachsan.frontend.utils.ThemeManager.isDarkMode() ? new Color(15, 23, 42) : new Color(249, 250, 251);
    private final Color ROW_ODD = quanlykhachsan.frontend.utils.ThemeManager.getCardBg();
    private final Color BORDER_CLR = quanlykhachsan.frontend.utils.ThemeManager.getBorderColor();

    public CustomerHistoryDialog(JFrame parent, int customerId, String customerName) {
        super(parent, "Lịch sử thuê phòng - " + customerName, true);
        this.customerId = customerId;
        this.customerName = customerName;

        setSize(800, 500);
        setLocationRelativeTo(parent);
        setResizable(false);
        setLayout(new BorderLayout());
        getContentPane().setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());

        initUI();
        loadHistory();
    }

    private void initUI() {
        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        headerPanel.setBorder(new EmptyBorder(15, 20, 15, 20));

        JLabel titleLabel = new JLabel("Lịch sử thuê phòng");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());

        JLabel subLabel = new JLabel("Khách hàng: " + customerName);
        subLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subLabel.setForeground(new Color(71, 85, 105));

        JPanel titleWrapper = new JPanel();
        titleWrapper.setLayout(new BoxLayout(titleWrapper, BoxLayout.Y_AXIS));
        titleWrapper.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        subLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleWrapper.add(titleLabel);
        titleWrapper.add(Box.createVerticalStrut(5));
        titleWrapper.add(subLabel);

        headerPanel.add(titleWrapper, BorderLayout.WEST);

        // Nút Đóng
        JButton btnClose = new JButton("Đóng");
        btnClose.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        btnClose.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnClose.addActionListener(e -> dispose());
        headerPanel.add(btnClose, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // Table
        tableModel = new DefaultTableModel(
                new String[] { "STT", "ID", "Số Phòng", "Ngày Check-in", "Ngày Check-out", "Tổng Tiền", "Trạng Thái" },
                0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        historyTable = new JTable(tableModel);
        historyTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        historyTable.setRowHeight(35);
        historyTable.setShowGrid(false);
        historyTable.setFocusable(false);

        historyTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        historyTable.getTableHeader().setBackground(quanlykhachsan.frontend.utils.ThemeManager.isDarkMode() ? new Color(30, 41, 59) : new Color(241, 245, 249));
        historyTable.getTableHeader().setForeground(new Color(71, 85, 105));
        historyTable.getTableHeader().setPreferredSize(new Dimension(0, 40));
        historyTable.getTableHeader().setBorder(new MatteBorder(1, 0, 1, 0, BORDER_CLR));

        historyTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        historyTable.getColumnModel().getColumn(0).setMaxWidth(60);
        historyTable.getColumnModel().getColumn(1).setPreferredWidth(60);
        historyTable.getColumnModel().getColumn(2).setPreferredWidth(80);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        historyTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        historyTable.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        historyTable.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);

        historyTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int row,
                    int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                setBackground(row % 2 == 0 ? ROW_EVEN : ROW_ODD);
                setBorder(new EmptyBorder(0, 10, 0, 10));

                // Color code status if it's the last column
                if (col == 6) {
                    String status = (String) val;
                    if ("paid".equalsIgnoreCase(status) || "checked_out".equalsIgnoreCase(status)) {
                        setForeground(SUCCESS);
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else if ("cancelled".equalsIgnoreCase(status)) {
                        setForeground(DANGER);
                    } else {
                        setForeground(new Color(217, 119, 6)); // Orange for pending/checked_in
                    }
                } else {
                    setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
                    setFont(getFont().deriveFont(Font.PLAIN));
                }

                return this;
            }
        });

        JScrollPane scroll = new JScrollPane(historyTable);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        add(scroll, BorderLayout.CENTER);

        // Footer
        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        footerPanel.setBorder(new EmptyBorder(10, 20, 10, 20));

        lblStatus = new JLabel("Đang tải dữ liệu...");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblStatus.setForeground(MUTED);
        footerPanel.add(lblStatus, BorderLayout.WEST);

        lblTotalSpent = new JLabel("Tổng đã chi: 0 VNĐ");
        lblTotalSpent.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblTotalSpent.setForeground(SUCCESS);
        footerPanel.add(lblTotalSpent, BorderLayout.EAST);

        add(footerPanel, BorderLayout.SOUTH);
    }

    private void loadHistory() {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            List<Booking> bookings;
            List<Room> rooms;

            @Override
            protected Void doInBackground() throws Exception {
                bookings = BookingAPI.getBookingsByCustomer(customerId);
                rooms = RoomAPI.getAllRooms();
                return null;
            }

            @Override
            protected void done() {
                try {
                    tableModel.setRowCount(0);
                    if (bookings != null && !bookings.isEmpty()) {
                        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
                        int stt = 1;
                        double totalSpent = 0;

                        for (Booking b : bookings) {
                            String roomNumber = "N/A";
                            if (rooms != null) {
                                for (Room r : rooms) {
                                    if (r.getId() == b.getRoomId()) {
                                        roomNumber = r.getRoomNumber();
                                        break;
                                    }
                                }
                            }

                            String formattedPrice = String.format("%,.0f VNĐ", b.getTotalPrice());

                            String statusStr = b.getStatus() != null ? b.getStatus().toLowerCase() : "";
                            if (statusStr.equals("paid") || statusStr.equals("checked_out")
                                    || statusStr.equals("checked_in") || statusStr.equals("confirmed")) {
                                totalSpent += b.getTotalPrice();
                            }

                            tableModel.addRow(new Object[] {
                                    stt++,
                                    "BK#" + b.getId(),
                                    roomNumber,
                                    sdf.format(b.getCheckInDate()),
                                    sdf.format(b.getCheckOutDate()),
                                    formattedPrice,
                                    b.getStatus().toUpperCase()
                            });
                        }
                        lblStatus.setText("Đã tìm thấy " + bookings.size() + " lượt đặt phòng.");
                        lblStatus.setForeground(SUCCESS);
                        lblTotalSpent.setText(String.format("Tổng đã chi: %,.0f VNĐ", totalSpent));
                    } else {
                        lblStatus.setText("Khách hàng này chưa có lịch sử đặt phòng nào.");
                        lblStatus.setForeground(MUTED);
                        lblTotalSpent.setText("Tổng đã chi: 0 VNĐ");
                    }
                } catch (Exception e) {
                    lblStatus.setText("Lỗi khi tải dữ liệu.");
                    lblStatus.setForeground(DANGER);
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }
}
