package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import quanlykhachsan.backend.model.Booking;
import quanlykhachsan.backend.model.Customer;
import quanlykhachsan.backend.model.Room;
import quanlykhachsan.frontend.api.BookingAPI;
import quanlykhachsan.frontend.api.CustomerAPI;
import quanlykhachsan.frontend.api.RoomAPI;
import quanlykhachsan.frontend.api.PaymentAPI;

public class PaymentForm extends JPanel {

    // ─── Constants ────────────────────────────────────────────────────────
    private static final Color PRIMARY    = new Color(37, 99, 235);
    private static final Color SUCCESS    = new Color(5, 150, 105);
    private static final Color DANGER     = new Color(220, 38, 38);
    private static final Color MUTED      = new Color(107, 114, 128);
    private static final Color BG_PANEL   = new Color(248, 250, 252);
    private static final Color BORDER_CLR = new Color(226, 232, 240);
    private static final Color ROW_EVEN   = new Color(249, 250, 251);
    private static final Color ROW_SELECT = new Color(219, 234, 254);

    private final SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm");
    private final DecimalFormat nf = new DecimalFormat("#,###");

    // ─── UI Components ────────────────────────────────────────────────────
    private JTable bookingTable;
    private DefaultTableModel tableModel;
    private JLabel lblStatus;
    
    // Invoice Panel Components
    private JLabel lblCustName, lblRoomNum, lblDuration, lblSubtotal, lblTax, lblTotal;
    private JComboBox<String> cbMethod;
    private JButton btnPay;
    
    // ─── State ────────────────────────────────────────────────────────────
    private List<Booking> bookingsList = new ArrayList<>();
    private List<Customer> customersList = new ArrayList<>();
    private List<Room> roomsList = new ArrayList<>();
    private Booking selectedBooking = null;

    public PaymentForm() {
        setLayout(new BorderLayout());
        setBackground(BG_PANEL);
        initUI();
        loadInitialData();
    }

    private void initUI() {
        // ── Header ──────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER_CLR),
            new EmptyBorder(12, 20, 12, 20)
        ));
        
        JLabel title = new JLabel("\uD83D\uDCC4 Quản lý Thanh toán");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(new Color(17, 24, 39));
        
        lblStatus = new JLabel("Đang tải...");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblStatus.setForeground(MUTED);
        
        header.add(title, BorderLayout.WEST);
        header.add(lblStatus, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ── Main Content ────────────────────────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setDividerLocation(550);
        split.setDividerSize(1);
        split.setBorder(null);

        split.setLeftComponent(buildListPanel());
        split.setRightComponent(buildInvoicePanel());
        add(split, BorderLayout.CENTER);
    }

    private JPanel buildListPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(new MatteBorder(0, 0, 0, 1, BORDER_CLR));

        JPanel toolBar = new JPanel(new BorderLayout());
        toolBar.setBackground(Color.WHITE);
        toolBar.setBorder(new EmptyBorder(10, 15, 10, 15));
        JLabel sub = new JLabel("Danh sách phòng chờ thanh toán");
        sub.setFont(new Font("Segoe UI", Font.BOLD, 14));
        sub.setForeground(new Color(55, 65, 81));
        
        JButton btnRefresh = new JButton("\u21BA Tải lại");
        btnRefresh.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRefresh.addActionListener(e -> loadInitialData());
        
        toolBar.add(sub, BorderLayout.WEST);
        toolBar.add(btnRefresh, BorderLayout.EAST);
        panel.add(toolBar, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(new String[]{"ID", "Khách Hàng", "Phòng", "Check-in", "Trạng Thái"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        bookingTable = new JTable(tableModel);
        bookingTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        bookingTable.setRowHeight(40);
        bookingTable.setShowGrid(false);
        bookingTable.setSelectionBackground(ROW_SELECT);
        bookingTable.setSelectionForeground(new Color(17, 24, 39));
        
        // Header
        bookingTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        bookingTable.getTableHeader().setReorderingAllowed(false);
        bookingTable.getTableHeader().setPreferredSize(new Dimension(0, 35));

        // Event
        bookingTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onBookingSelected();
        });

        panel.add(new JScrollPane(bookingTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildInvoicePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_PANEL);
        panel.setBorder(new EmptyBorder(25, 30, 25, 30));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        JLabel title = new JLabel("CHI TIẾT HÓA ĐƠN");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(PRIMARY);
        title.setAlignmentX(LEFT_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(20));

        // Invoice Rows
        lblCustName = createInvoiceRow(content, "Khách hàng:", "---");
        lblRoomNum  = createInvoiceRow(content, "Phòng:", "---");
        lblDuration = createInvoiceRow(content, "Số ngày ở:", "---");
        
        JSeparator s1 = new JSeparator();
        s1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        content.add(Box.createVerticalStrut(15));
        content.add(s1);
        content.add(Box.createVerticalStrut(15));

        lblSubtotal = createInvoiceRow(content, "Tiền phòng:", "0 VNĐ");
        lblTax      = createInvoiceRow(content, "Thuế (10%):", "0 VNĐ");
        
        content.add(Box.createVerticalStrut(10));
        lblTotal = new JLabel("TỔNG CỘNG: 0 VNĐ");
        lblTotal.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTotal.setForeground(DANGER);
        lblTotal.setAlignmentX(LEFT_ALIGNMENT);
        content.add(lblTotal);

        content.add(Box.createVerticalStrut(30));
        
        JLabel lblM = new JLabel("Phương thức thanh toán:");
        lblM.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblM.setAlignmentX(LEFT_ALIGNMENT);
        content.add(lblM);
        content.add(Box.createVerticalStrut(8));
        
        cbMethod = new JComboBox<>(new String[]{"Tiền mặt (Cash)", "Chuyển khoản", "Thẻ tín dụng (Credit Card)"});
        cbMethod.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        cbMethod.setAlignmentX(LEFT_ALIGNMENT);
        content.add(cbMethod);
        
        content.add(Box.createVerticalGlue());

        btnPay = new JButton("XÁC NHẬN THANH TOÁN");
        btnPay.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnPay.setForeground(Color.WHITE);
        btnPay.setBackground(SUCCESS);
        btnPay.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnPay.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        btnPay.setAlignmentX(LEFT_ALIGNMENT);
        btnPay.setEnabled(false);
        btnPay.addActionListener(e -> actionProcessPayment());
        
        content.add(btnPay);

        panel.add(content, BorderLayout.NORTH);
        return panel;
    }

    private JLabel createInvoiceRow(JPanel parent, String label, String value) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        
        JLabel l = new JLabel(label);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        l.setForeground(MUTED);
        
        JLabel v = new JLabel(value);
        v.setFont(new Font("Segoe UI", Font.BOLD, 14));
        v.setForeground(new Color(17, 24, 39));
        
        row.add(l, BorderLayout.WEST);
        row.add(v, BorderLayout.EAST);
        parent.add(row);
        parent.add(Box.createVerticalStrut(8));
        return v;
    }

    // ─── Logic ────────────────────────────────────────────────────────────

    private void loadInitialData() {
        lblStatus.setText("Đang tải dữ liệu...");
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                customersList = CustomerAPI.getAllCustomers();
                roomsList = RoomAPI.getAllRooms();
                bookingsList = BookingAPI.getAllBookings();
                return null;
            }
            @Override
            protected void done() {
                try {
                    get();
                    refreshTable();
                    lblStatus.setText("Sẵn sàng. " + tableModel.getRowCount() + " đơn cần thanh toán.");
                    lblStatus.setForeground(SUCCESS);
                } catch (Exception e) {
                    lblStatus.setText("Lỗi tải dữ liệu!");
                    lblStatus.setForeground(DANGER);
                }
            }
        };
        worker.execute();
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        for (Booking b : bookingsList) {
            if ("occupied".equals(b.getStatus()) || "checked_out".equals(b.getStatus())) {
                String custName = getCustomerName(b.getCustomerId());
                String roomNum = getRoomNumber(b.getRoomId());
                
                String statusVn = b.getStatus().equals("occupied") ? "Đang ở" : "Đã trả phòng";
                
                tableModel.addRow(new Object[]{
                    b.getId(), custName, roomNum, df.format(b.getCheckInDate()), statusVn
                });
            }
        }
        resetInvoice();
    }

    private void onBookingSelected() {
        int row = bookingTable.getSelectedRow();
        if (row < 0) {
            resetInvoice();
            return;
        }
        
        int bookingId = (int) tableModel.getValueAt(row, 0);
        selectedBooking = null;
        for (Booking b : bookingsList) {
            if (b.getId() == bookingId) {
                selectedBooking = b;
                break;
            }
        }
        
        if (selectedBooking != null) {
            updateInvoiceDisplay();
        }
    }

    private void updateInvoiceDisplay() {
        lblCustName.setText(getCustomerName(selectedBooking.getCustomerId()));
        String roomNum = getRoomNumber(selectedBooking.getRoomId());
        double roomPrice = getRoomPrice(selectedBooking.getRoomId());
        lblRoomNum.setText(roomNum + " (" + nf.format(roomPrice) + " VNĐ/đêm)");
        
        // Tính số ngày
        long diff = selectedBooking.getCheckOutDate().getTime() - selectedBooking.getCheckInDate().getTime();
        long days = (diff / (1000 * 60 * 60 * 24));
        if (days == 0) days = 1; 
        
        lblDuration.setText(days + " đêm");
        
        double subtotal = days * roomPrice;
        double tax = subtotal * 0.1;
        double total = subtotal + tax;
        
        lblSubtotal.setText(nf.format(subtotal) + " VNĐ");
        lblTax.setText(nf.format(tax) + " VNĐ");
        lblTotal.setText("TỔNG CỘNG: " + nf.format(total) + " VNĐ");
        
        btnPay.setEnabled(true);
    }

    private void resetInvoice() {
        selectedBooking = null;
        lblCustName.setText("---");
        lblRoomNum.setText("---");
        lblDuration.setText("---");
        lblSubtotal.setText("0 VNĐ");
        lblTax.setText("0 VNĐ");
        lblTotal.setText("TỔNG CỘNG: 0 VNĐ");
        btnPay.setEnabled(false);
    }

    private void actionProcessPayment() {
        if (selectedBooking == null) return;
        
        int confirm = JOptionPane.showConfirmDialog(this, 
            "Bạn có chắc chắn muốn xác nhận thanh toán cho Đơn đặt #" + selectedBooking.getId() + "?",
            "Xác nhận", JOptionPane.YES_NO_OPTION);
            
        if (confirm == JOptionPane.YES_OPTION) {
            btnPay.setEnabled(false);
            lblStatus.setText("Đang xử lý thanh toán...");
            
            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override
                protected String doInBackground() {
                    String method = (String) cbMethod.getSelectedItem();
                    long diff = selectedBooking.getCheckOutDate().getTime() - selectedBooking.getCheckInDate().getTime();
                    long days = Math.max(1, diff / (1000 * 60 * 60 * 24));
                    double amount = days * getRoomPrice(selectedBooking.getRoomId()) * 1.1;
                    
                    return PaymentAPI.pay(selectedBooking.getId(), amount, method);
                }
                @Override
                protected void done() {
                    try {
                        String res = get();
                        if (res.startsWith("Success")) {
                            JOptionPane.showMessageDialog(PaymentForm.this, 
                                "Thanh toán thành công!\nĐơn hàng đã hoàn tất và Phòng đang được dọn dẹp.", 
                                "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                            loadInitialData(); 
                        } else {
                            JOptionPane.showMessageDialog(PaymentForm.this, "Lỗi: " + res, "Thất Bại", JOptionPane.ERROR_MESSAGE);
                            btnPay.setEnabled(true);
                        }
                    } catch (Exception e) {}
                }
            };
            worker.execute();
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private String getCustomerName(int id) {
        for (Customer c : customersList) if (c.getId() == id) return c.getFullName();
        return "Không rõ";
    }

    private String getRoomNumber(int id) {
        for (Room r : roomsList) if (r.getId() == id) return r.getRoomNumber();
        return "Không rõ";
    }

    private double getRoomPrice(int id) {
        for (Room r : roomsList) if (r.getId() == id) return r.getPrice();
        return 0;
    }
}
