package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import quanlykhachsan.backend.model.Customer;
import quanlykhachsan.backend.model.Room;
import quanlykhachsan.backend.model.Booking;
import quanlykhachsan.frontend.api.BookingAPI;
import quanlykhachsan.frontend.api.CustomerAPI;
import quanlykhachsan.frontend.api.RoomAPI;
import com.toedter.calendar.JDateChooser;

public class BookingForm extends JPanel {

    // ─── Widgets ──────────────────────────────────────────────────────────
    private JComboBox<ComboItem> cbCustomer, cbRoom;
    private JDateChooser dateCheckIn, dateCheckOut;
    private JButton btnBook, btnRefresh;

    private JLabel lblEstimate;         // hiển thị tạm tính tiền
    private JLabel lblStatus;

    // Bảng danh sách booking
    private JTable bookingTable;
    private DefaultTableModel bookingModel;

    // Check-in / Check-out
    private JTextField txtBookingID;
    private JButton btnCheckIn, btnCheckOut;

    // Dữ liệu hỗ trợ hiển thị tên thay vì ID
    private List<Customer> customersList;
    private List<Room> roomsList;

    // ─── Màu sắc ──────────────────────────────────────────────────────────
    private static final Color PRIMARY    = new Color(37, 99, 235);
    private static final Color SUCCESS    = new Color(5, 150, 105);
    private static final Color WARNING    = new Color(217, 119, 6);
    private static final Color DANGER     = new Color(220, 38, 38);
    private static final Color MUTED      = new Color(107, 114, 128);
    private static final Color BG         = new Color(248, 250, 252);
    private static final Color BORDER_CLR = new Color(226, 232, 240);
    private static final Color ROW_EVEN   = new Color(249, 250, 251);
    private static final Color ROW_SELECT = new Color(219, 234, 254);

    public BookingForm() {
        setLayout(new BorderLayout(0, 0));
        setBackground(BG);
        initUI();
        loadInitialData();
    }

    private void initUI() {
        // ── Page header ──────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER_CLR),
            new EmptyBorder(12, 16, 12, 16)
        ));
        JLabel pageTitle = new JLabel("Đặt Phòng & Check-in / Check-out");
        pageTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        pageTitle.setForeground(new Color(17, 24, 39));
        lblStatus = new JLabel(" ");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        header.add(pageTitle, BorderLayout.WEST);
        header.add(lblStatus, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ── Body: chia dọc trái/phải ──────────────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setDividerLocation(340);
        split.setDividerSize(1);
        split.setBorder(null);
        split.setLeftComponent(buildLeftPanel());
        split.setRightComponent(buildRightPanel());
        add(split, BorderLayout.CENTER);
    }

    private JPanel buildLeftPanel() {
        JPanel left = new JPanel(new BorderLayout());
        left.setBackground(Color.WHITE);
        left.setBorder(new MatteBorder(0, 0, 0, 1, BORDER_CLR));

        JPanel scrollContent = new JPanel();
        scrollContent.setLayout(new BoxLayout(scrollContent, BoxLayout.Y_AXIS));
        scrollContent.setBackground(Color.WHITE);
        scrollContent.setBorder(new EmptyBorder(20, 20, 20, 20));

        // ── Section 1: Đặt phòng mới ──
        JLabel sec1Title = new JLabel("Đặt Phòng Mới");
        sec1Title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        sec1Title.setForeground(new Color(17, 24, 39));
        sec1Title.setAlignmentX(LEFT_ALIGNMENT);
        scrollContent.add(sec1Title);
        scrollContent.add(Box.createVerticalStrut(4));
        scrollContent.add(createSubLabel("Chọn khách hàng, phòng và thời gian"));
        scrollContent.add(Box.createVerticalStrut(14));

        // Khách hàng
        scrollContent.add(createFieldLabel("Khách Hàng"));
        cbCustomer = new JComboBox<>();
        cbCustomer.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        cbCustomer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        cbCustomer.setAlignmentX(LEFT_ALIGNMENT);
        scrollContent.add(cbCustomer);
        scrollContent.add(Box.createVerticalStrut(10));

        // Phòng
        scrollContent.add(createFieldLabel("Phòng Trống"));
        cbRoom = new JComboBox<>();
        cbRoom.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        cbRoom.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        cbRoom.setAlignmentX(LEFT_ALIGNMENT);
        scrollContent.add(cbRoom);
        scrollContent.add(Box.createVerticalStrut(10));

        // Ngày đến
        scrollContent.add(createFieldLabel("Ngày Check-in"));
        dateCheckIn = new JDateChooser();
        dateCheckIn.setDateFormatString("dd/MM/yyyy");
        dateCheckIn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        dateCheckIn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        dateCheckIn.setAlignmentX(LEFT_ALIGNMENT);
        scrollContent.add(dateCheckIn);
        scrollContent.add(Box.createVerticalStrut(10));

        // Ngày đi
        scrollContent.add(createFieldLabel("Ngày Check-out"));
        dateCheckOut = new JDateChooser();
        dateCheckOut.setDateFormatString("dd/MM/yyyy");
        dateCheckOut.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        dateCheckOut.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        dateCheckOut.setAlignmentX(LEFT_ALIGNMENT);
        scrollContent.add(dateCheckOut);
        scrollContent.add(Box.createVerticalStrut(10));

        // Ước tính tiền
        lblEstimate = new JLabel(" ");
        lblEstimate.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblEstimate.setForeground(PRIMARY);
        lblEstimate.setAlignmentX(LEFT_ALIGNMENT);
        scrollContent.add(lblEstimate);
        scrollContent.add(Box.createVerticalStrut(14));

        // Buttons
        JPanel btnRow = new JPanel(new GridLayout(1, 2, 8, 0));
        btnRow.setOpaque(false);
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnRow.setAlignmentX(LEFT_ALIGNMENT);

        btnRefresh = createGhostBtn("\u21BA Tải Lại");
        btnRefresh.addActionListener(e -> loadInitialData());
        btnBook = createSolidBtn("Đặt Phòng", PRIMARY);
        btnBook.addActionListener(e -> actionBook());
        btnRow.add(btnRefresh);
        btnBook.setFocusPainted(false);
        btnRow.add(btnBook);
        scrollContent.add(btnRow);

        // ── Divider ──
        scrollContent.add(Box.createVerticalStrut(24));
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setForeground(BORDER_CLR);
        scrollContent.add(sep);
        scrollContent.add(Box.createVerticalStrut(20));

        // ── Section 2: Check-in / Check-out ──
        JLabel sec2Title = new JLabel("Nhận & Trả Phòng");
        sec2Title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        sec2Title.setForeground(new Color(17, 24, 39));
        sec2Title.setAlignmentX(LEFT_ALIGNMENT);
        scrollContent.add(sec2Title);
        scrollContent.add(Box.createVerticalStrut(4));
        scrollContent.add(createSubLabel("Chọn đặt phòng trên bảng hoặc nhập ID"));
        scrollContent.add(Box.createVerticalStrut(14));

        scrollContent.add(createFieldLabel("Mã Đặt Phòng (ID)"));
        txtBookingID = new JTextField();
        txtBookingID.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtBookingID.putClientProperty("JTextField.placeholderText", "Chọn hàng ở bảng hoặc nhập ID...");
        txtBookingID.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_CLR, 1, true),
            new EmptyBorder(6, 10, 6, 10)
        ));
        txtBookingID.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        txtBookingID.setAlignmentX(LEFT_ALIGNMENT);
        scrollContent.add(txtBookingID);
        scrollContent.add(Box.createVerticalStrut(12));

        JPanel cicoRow = new JPanel(new GridLayout(1, 2, 8, 0));
        cicoRow.setOpaque(false);
        cicoRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        cicoRow.setAlignmentX(LEFT_ALIGNMENT);
        btnCheckIn  = createSolidBtn("Check-in",  SUCCESS);
        btnCheckOut = createSolidBtn("Check-out", WARNING);
        btnCheckIn.addActionListener(e -> actionCheckIn());
        btnCheckOut.addActionListener(e -> actionCheckOut());
        cicoRow.add(btnCheckIn);
        cicoRow.add(btnCheckOut);
        scrollContent.add(cicoRow);

        // Tự tính tiền tạm khi chọn ngày
        dateCheckIn.addPropertyChangeListener("date", e -> updateEstimate());
        dateCheckOut.addPropertyChangeListener("date", e -> updateEstimate());

        left.add(new JScrollPane(scrollContent,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
        return left;
    }

    private JPanel buildRightPanel() {
        JPanel right = new JPanel(new BorderLayout());
        right.setBackground(Color.WHITE);

        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setBackground(Color.WHITE);
        topBar.setBorder(new EmptyBorder(12, 16, 10, 16));
        JLabel lbl = new JLabel("Danh Sách Đặt Phòng");
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lbl.setForeground(new Color(17, 24, 39));
        topBar.add(lbl, BorderLayout.WEST);
        right.add(topBar, BorderLayout.NORTH);

        bookingModel = new DefaultTableModel(
            new String[]{"ID", "Khách Hàng", "Phòng", "Check-in", "Check-out", "Trạng Thái"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        bookingTable = new JTable(bookingModel);
        bookingTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        bookingTable.setRowHeight(38);
        bookingTable.setShowGrid(false);
        bookingTable.setIntercellSpacing(new Dimension(0, 0));
        bookingTable.setSelectionBackground(ROW_SELECT);
        bookingTable.setFocusable(false);

        bookingTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        bookingTable.getTableHeader().setBackground(new Color(241, 245, 249));
        bookingTable.getTableHeader().setForeground(new Color(71, 85, 105));
        bookingTable.getTableHeader().setBorder(new MatteBorder(0, 0, 1, 0, BORDER_CLR));
        bookingTable.getTableHeader().setPreferredSize(new Dimension(0, 40));

        // Cột ID nhỏ
        bookingTable.getColumnModel().getColumn(0).setMaxWidth(60);
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        bookingTable.getColumnModel().getColumn(0).setCellRenderer(center);

        // Cột Trạng thái có màu
        bookingTable.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                String status = val != null ? val.toString() : "";
                
                String statusVn = toVietnameseStatus(status);
                lbl.setText(statusVn);

                if (!sel) {
                    switch (status.toLowerCase()) {
                        case "pending":    lbl.setForeground(WARNING); break;
                        case "checked_in": lbl.setForeground(SUCCESS); break;
                        case "checked_out":lbl.setForeground(MUTED);   break;
                        case "paid":       lbl.setForeground(PRIMARY); break;
                        default:           lbl.setForeground(new Color(17,24,39));
                    }
                }
                return lbl;
            }
        });

        // Zebra stripes
        bookingTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                if (!sel) setBackground(row % 2 == 0 ? ROW_EVEN : Color.WHITE);
                setBorder(new EmptyBorder(0, 12, 0, 12));
                return this;
            }
        });

        // Click hàng → điền Booking ID
        bookingTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = bookingTable.getSelectedRow();
                if (row >= 0) {
                    Object id = bookingModel.getValueAt(row, 0);
                    txtBookingID.setText(id.toString());
                }
            }
        });

        JScrollPane scroll = new JScrollPane(bookingTable);
        scroll.setBorder(new MatteBorder(1, 0, 0, 0, BORDER_CLR));
        scroll.getViewport().setBackground(Color.WHITE);
        right.add(scroll, BorderLayout.CENTER);

        return right;
    }

    // ── LOAD DỮ LIỆU ──────────────────────────────────────────────────────

    private void loadInitialData() {
        cbCustomer.removeAllItems();
        cbRoom.removeAllItems();
        cbCustomer.addItem(new ComboItem(-1, "Đang tải..."));
        cbRoom.addItem(new ComboItem(-1, "Đang tải..."));
        setStatus("Đang tải thông tin...", MUTED);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                customersList = CustomerAPI.getAllCustomers();
                roomsList = RoomAPI.getAllRooms();
                return null;
            }
            @Override
            protected void done() {
                cbCustomer.removeAllItems();
                cbRoom.removeAllItems();
                try {
                    get(); 
                    if (customersList != null && !customersList.isEmpty()) {
                        for (Customer c : customersList) {
                            cbCustomer.addItem(new ComboItem(c.getId(),
                                c.getFullName() + "  \u2014  " + c.getPhone()));
                        }
                    } else {
                        cbCustomer.addItem(new ComboItem(-1, "Không có khách hàng"));
                    }
                    if (roomsList != null && !roomsList.isEmpty()) {
                        for (Room r : roomsList) {
                            if ("available".equalsIgnoreCase(r.getStatus())) {
                                cbRoom.addItem(new ComboItem(r.getId(),
                                    "Phòng " + r.getRoomNumber() + "  (Trống)"));
                            }
                        }
                        if (cbRoom.getItemCount() == 0)
                            cbRoom.addItem(new ComboItem(-1, "Không còn phòng trống"));
                    } else {
                        cbRoom.addItem(new ComboItem(-1, "Không có phòng nào"));
                    }
                    
                    loadBookings();
                } catch (Exception e) {
                    cbCustomer.addItem(new ComboItem(-1, "Lỗi kết nối"));
                    cbRoom.addItem(new ComboItem(-1, "Lỗi kết nối"));
                    setStatus("Lỗi tải dữ liệu: " + e.getMessage(), DANGER);
                }
            }
        };
        worker.execute();
    }

    private void loadBookings() {
        bookingModel.setRowCount(0);
        setStatus("Đang tải danh sách đặt phòng...", MUTED);
        
        SwingWorker<List<Booking>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Booking> doInBackground() throws Exception {
                return BookingAPI.getAllBookings();
            }
            @Override
            protected void done() {
                try {
                    List<Booking> bookings = get();
                    bookingModel.setRowCount(0);
                    if (bookings != null && !bookings.isEmpty()) {
                        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
                        for (Booking b : bookings) {
                            String customerName = "Không rõ (" + b.getCustomerId() + ")";
                            if (customersList != null) {
                                for (Customer c : customersList) {
                                    if (c.getId() == b.getCustomerId()) {
                                        customerName = c.getFullName();
                                        break;
                                    }
                                }
                            }
                            
                            String roomNumber = "Phòng " + b.getRoomId();
                            if (roomsList != null) {
                                for (Room r : roomsList) {
                                    if (r.getId() == b.getRoomId()) {
                                        roomNumber = r.getRoomNumber();
                                        break;
                                    }
                                }
                            }
                            
                            bookingModel.addRow(new Object[]{
                                b.getId(),
                                customerName,
                                roomNumber,
                                b.getCheckInDate() != null ? sdf.format(b.getCheckInDate()) : "--",
                                b.getCheckOutDate() != null ? sdf.format(b.getCheckOutDate()) : "--",
                                b.getStatus()
                            });
                        }
                        setStatus("Đã tải " + bookings.size() + " đơn đặt phòng.", SUCCESS);
                    } else {
                        setStatus("Chưa có danh sách đặt phòng.", MUTED);
                    }
                } catch (Exception e) {
                    setStatus("Lỗi tải danh sách đặt phòng.", DANGER);
                }
            }
        };
        worker.execute();
    }

    // ── HÀNH ĐỘNG ─────────────────────────────────────────────────────────

    private void updateEstimate() {
        Date di = dateCheckIn.getDate();
        Date co = dateCheckOut.getDate();
        if (di == null || co == null) {
            lblEstimate.setText(" ");
            return;
        }
        long diff = co.getTime() - di.getTime();
        long days = TimeUnit.MILLISECONDS.toDays(diff);
        if (days <= 0) {
            lblEstimate.setText("  Ngày đi phải sau ngày đến!");
            lblEstimate.setForeground(DANGER);
        } else {
            lblEstimate.setText("  " + days + " đêm");
            lblEstimate.setForeground(PRIMARY);
        }
    }

    private void actionBook() {
        ComboItem selCustomer = (ComboItem) cbCustomer.getSelectedItem();
        ComboItem selRoom     = (ComboItem) cbRoom.getSelectedItem();

        if (selCustomer == null || selCustomer.getId() == -1) {
            showWarn("Vui lòng chọn khách hàng hợp lệ!"); return;
        }
        if (selRoom == null || selRoom.getId() == -1) {
            showWarn("Vui lòng chọn phòng trống!"); return;
        }
        if (dateCheckIn.getDate() == null || dateCheckOut.getDate() == null) {
            showWarn("Vui lòng chọn ngày check-in và check-out!"); return;
        }
        long diff = dateCheckOut.getDate().getTime() - dateCheckIn.getDate().getTime();
        if (TimeUnit.MILLISECONDS.toDays(diff) <= 0) {
            showWarn("Ngày check-out phải sau ngày check-in!"); return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
            "Xác nhận đặt phòng cho:\n  Khách: " + selCustomer + "\n  Phòng: " + selRoom,
            "Xác nhận Đặt Phòng", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        int customerId = selCustomer.getId();
        int roomId     = selRoom.getId();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String ci = sdf.format(dateCheckIn.getDate());
        String co = sdf.format(dateCheckOut.getDate());

        btnBook.setEnabled(false);
        setStatus("Đang đặt phòng...", MUTED);

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                return BookingAPI.bookRoom(customerId, roomId, ci, co);
            }
            @Override
            protected void done() {
                btnBook.setEnabled(true);
                try {
                    String msg = get();
                    if (msg != null && msg.startsWith("Success")) {
                        setStatus("Đặt phòng thành công!", SUCCESS);
                        dateCheckIn.setDate(null);
                        dateCheckOut.setDate(null);
                        lblEstimate.setText(" ");
                        loadInitialData();
                    } else {
                        setStatus("Thất bại: " + msg, DANGER);
                    }
                } catch (Exception ex) {
                    setStatus("Lỗi kết nối máy chủ!", DANGER);
                }
            }
        };
        worker.execute();
    }

    private void actionCheckIn() {
        String idStr = txtBookingID.getText().trim();
        if (idStr.isEmpty()) { showWarn("Vui lòng nhập hoặc chọn Mã Đặt Phòng!"); return; }
        try {
            int bookingId = Integer.parseInt(idStr);
            int confirm = JOptionPane.showConfirmDialog(this,
                "Xác nhận NHẬN PHÒNG (Check-in) cho đơn ID: " + bookingId + "?",
                "Xác nhận Check-in", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;

            btnCheckIn.setEnabled(false);
            setStatus("Đang check-in...", MUTED);
            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override protected String doInBackground() { return BookingAPI.checkIn(bookingId); }
                @Override
                protected void done() {
                    btnCheckIn.setEnabled(true);
                    try {
                        String msg = get();
                        boolean ok = msg != null && msg.toLowerCase().contains("success");
                        setStatus(ok ? "Nhận phòng thành công!" : msg, ok ? SUCCESS : DANGER);
                        if (ok) { loadInitialData(); }
                    } catch (Exception ex) { setStatus("Lỗi check-in!", DANGER); }
                }
            };
            worker.execute();
        } catch (NumberFormatException e) {
            showWarn("Mã Đặt Phòng phải là số!");
        }
    }

    private void actionCheckOut() {
        String idStr = txtBookingID.getText().trim();
        if (idStr.isEmpty()) { showWarn("Vui lòng nhập hoặc chọn Mã Đặt Phòng!"); return; }
        try {
            int bookingId = Integer.parseInt(idStr);
            int confirm = JOptionPane.showConfirmDialog(this,
                "Xác nhận TRẢ PHÒNG (Check-out) cho đơn ID: " + bookingId + "?\nHệ thống sẽ tự động tính tiền.",
                "Xác nhận Check-out", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;

            btnCheckOut.setEnabled(false);
            setStatus("Đang check-out...", MUTED);
            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override protected String doInBackground() { return BookingAPI.checkOut(bookingId); }
                @Override
                protected void done() {
                    btnCheckOut.setEnabled(true);
                    try {
                        String msg = get();
                        boolean ok = msg != null && msg.toLowerCase().contains("success");
                        setStatus(ok ? "Trả phòng thành công!" : msg, ok ? SUCCESS : DANGER);
                        if (ok) { loadInitialData(); }
                    } catch (Exception ex) { setStatus("Lỗi check-out!", DANGER); }
                }
            };
            worker.execute();
        } catch (NumberFormatException e) {
            showWarn("Mã Đặt Phòng phải là số!");
        }
    }

    // ── HELPERS ───────────────────────────────────────────────────────────

    private String toVietnameseStatus(String status) {
        if (status == null) return "--";
        return switch (status.toLowerCase()) {
            case "pending"     -> "Chờ nhận phòng";
            case "confirmed"   -> "Đã xác nhận";
            case "checked_in"  -> "Đang ở";
            case "checked_out" -> "Đã trả phòng";
            case "paid"        -> "Đã thanh toán";
            case "cancelled"   -> "Đã hủy";
            default            -> status;
        };
    }

    private void setStatus(String msg, Color color) {
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText(msg);
            lblStatus.setForeground(color);
        });
    }

    private void showWarn(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Cảnh báo", JOptionPane.WARNING_MESSAGE);
    }

    private JLabel createFieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l.setForeground(new Color(55, 65, 81));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JLabel createSubLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        l.setForeground(MUTED);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JButton createSolidBtn(String text, Color bg) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? (getModel().isRollover() ? bg.darker() : bg) : new Color(209,213,219));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton createGhostBtn(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btn.setForeground(MUTED);
        btn.setBackground(new Color(241, 245, 249));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    static class ComboItem {
        private final int id;
        private final String label;
        ComboItem(int id, String label) { this.id = id; this.label = label; }
        public int getId() { return id; }
        @Override public String toString() { return label; }
    }
}
