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
import quanlykhachsan.frontend.utils.WrapLayout;
import com.google.gson.JsonObject;

public class BookingForm extends JPanel {

    // ─── Widgets ──────────────────────────────────────────────────────────
    private JComboBox<ComboItem> cbRoom;
    private JButton btnSelectCustomer;
    private Customer selectedCustomer = null;
    private JDateChooser dateCheckIn, dateCheckOut;
    private JButton btnBook, btnRefresh;

    private JLabel lblEstimate; // hiển thị tạm tính tiền
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
    private final Color PRIMARY = new Color(37, 99, 235);
    private final Color SUCCESS = new Color(5, 150, 105);
    private final Color WARNING = new Color(217, 119, 6);
    private final Color DANGER = new Color(220, 38, 38);
    private final Color MUTED = new Color(107, 114, 128);
    private final Color BG = quanlykhachsan.frontend.utils.ThemeManager.getBgPanel();
    private final Color BORDER_CLR = quanlykhachsan.frontend.utils.ThemeManager.getBorderColor();
    private final Color ROW_EVEN = quanlykhachsan.frontend.utils.ThemeManager.isDarkMode() ? new Color(15, 23, 42) : new Color(249, 250, 251);
    private final Color ROW_SELECT = new Color(219, 234, 254);

    public BookingForm() {
        setLayout(new BorderLayout(0, 0));
        setBackground(BG);
        initUI();
        loadInitialData();
    }

    private void initUI() {
        // ── Page header ──────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, BORDER_CLR),
                new EmptyBorder(12, 16, 12, 16)));
        JLabel pageTitle = new JLabel("Phòng & Đặt phòng");
        pageTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        pageTitle.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        lblStatus = new JLabel(" ");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        header.add(pageTitle, BorderLayout.WEST);
        header.add(lblStatus, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ── Body: chia dọc trái/phải ──────────────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setDividerLocation(460);
        split.setDividerSize(1);
        split.setBorder(null);
        split.setLeftComponent(buildLeftPanel());
        split.setRightComponent(buildRightPanel());
        add(split, BorderLayout.CENTER);
    }

    private JPanel buildLeftPanel() {
        JPanel left = new JPanel(new BorderLayout());
        left.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        left.setBorder(new MatteBorder(0, 0, 0, 1, BORDER_CLR));

        JPanel scrollContent = new JPanel();
        scrollContent.setLayout(new BoxLayout(scrollContent, BoxLayout.Y_AXIS));
        scrollContent.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        scrollContent.setBorder(new EmptyBorder(16, 12, 16, 12));

        // Card 1: Đặt phòng mới
        JPanel cardNew = createSectionPanel();
        
        JLabel sec1Title = new JLabel("Đặt Phòng Mới");
        sec1Title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        sec1Title.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        sec1Title.setAlignmentX(LEFT_ALIGNMENT);
        cardNew.add(sec1Title);
        cardNew.add(Box.createVerticalStrut(4));
        cardNew.add(createSubLabel("Chọn khách hàng, phòng và thời gian"));
        cardNew.add(Box.createVerticalStrut(14));

        // Khách hàng
        cardNew.add(createFieldLabel("Khách Hàng"));
        btnSelectCustomer = new JButton(" Chọn khách hàng...");
        btnSelectCustomer.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnSelectCustomer.setHorizontalAlignment(SwingConstants.LEFT);
        btnSelectCustomer.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        btnSelectCustomer.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        btnSelectCustomer.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_CLR, 1, true), new EmptyBorder(8, 12, 8, 12)));
        btnSelectCustomer.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnSelectCustomer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnSelectCustomer.setAlignmentX(LEFT_ALIGNMENT);
        btnSelectCustomer.setFocusPainted(false);
        btnSelectCustomer.addActionListener(e -> {
            Window parentWindow = SwingUtilities.getWindowAncestor(BookingForm.this);
            Frame frame = null;
            if (parentWindow instanceof Frame) {
                frame = (Frame) parentWindow;
            }
            CustomerSelectDialog dialog = new CustomerSelectDialog(frame);
            dialog.setVisible(true);
            Customer c = dialog.getSelectedCustomer();
            if (c != null) {
                selectedCustomer = c;
                btnSelectCustomer.setText("  " + c.getFullName() + " - " + c.getPhone());
                btnSelectCustomer.setForeground(PRIMARY);
                btnSelectCustomer.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(PRIMARY, 2, true), new EmptyBorder(7, 11, 7, 11)));
            }
        });
        cardNew.add(btnSelectCustomer);
        cardNew.add(Box.createVerticalStrut(12));

        // Phòng
        cardNew.add(createFieldLabel("Phòng Trống"));
        cbRoom = new JComboBox<>();
        cbRoom.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        cbRoom.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        cbRoom.setAlignmentX(LEFT_ALIGNMENT);
        cardNew.add(cbRoom);
        cardNew.add(Box.createVerticalStrut(12));

        // Check-in & Out row
        JPanel dateRow = new JPanel(new GridLayout(1, 2, 10, 0));
        dateRow.setOpaque(false);
        dateRow.setAlignmentX(LEFT_ALIGNMENT);
        dateRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));
        
        JPanel pIn = new JPanel(new BorderLayout()); pIn.setOpaque(false);
        pIn.add(createFieldLabel("Check-in Ngày Đến"), BorderLayout.NORTH);
        dateCheckIn = new JDateChooser();
        dateCheckIn.setDateFormatString("dd/MM/yyyy");
        dateCheckIn.setPreferredSize(new Dimension(0, 40));
        pIn.add(dateCheckIn, BorderLayout.CENTER);
        
        JPanel pOut = new JPanel(new BorderLayout()); pOut.setOpaque(false);
        pOut.add(createFieldLabel("Check-out Ngày Đi"), BorderLayout.NORTH);
        dateCheckOut = new JDateChooser();
        dateCheckOut.setDateFormatString("dd/MM/yyyy");
        dateCheckOut.setPreferredSize(new Dimension(0, 40));
        pOut.add(dateCheckOut, BorderLayout.CENTER);
        
        dateRow.add(pIn);
        dateRow.add(pOut);
        cardNew.add(dateRow);
        cardNew.add(Box.createVerticalStrut(12));

        themeDateChooser(dateCheckIn);
        themeDateChooser(dateCheckOut);

        // Ước tính tiền
        lblEstimate = new JLabel(" ");
        lblEstimate.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblEstimate.setForeground(PRIMARY);
        lblEstimate.setAlignmentX(LEFT_ALIGNMENT);
        cardNew.add(lblEstimate);
        cardNew.add(Box.createVerticalStrut(16));

        // Buttons
        JPanel btnRow = new JPanel(new GridLayout(1, 2, 10, 0));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(LEFT_ALIGNMENT);
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        btnRefresh = createGhostBtn("Tải Lại Bảng");
        btnRefresh.setPreferredSize(new Dimension(0, 40));
        btnRefresh.addActionListener(e -> loadInitialData());
        btnBook = createSolidBtn("Đặt Phòng Mới", PRIMARY);
        btnBook.setPreferredSize(new Dimension(0, 40));
        btnBook.addActionListener(e -> actionBook());
        
        btnRow.add(btnRefresh);
        btnRow.add(btnBook);
        cardNew.add(btnRow);
        
        scrollContent.add(cardNew);
        scrollContent.add(Box.createVerticalStrut(20));

        // Card 2: Nhận & Trả phòng
        JPanel cardAction = createSectionPanel();
        
        JLabel sec2Title = new JLabel("Nhận & Trả Phòng");
        sec2Title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        sec2Title.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        sec2Title.setAlignmentX(LEFT_ALIGNMENT);
        cardAction.add(sec2Title);
        cardAction.add(Box.createVerticalStrut(4));
        cardAction.add(createSubLabel("Chọn đơn đặt từ bảng hoặc nhập mã"));
        cardAction.add(Box.createVerticalStrut(14));

        cardAction.add(createFieldLabel("Mã Đặt Phòng (ID)"));
        txtBookingID = new JTextField();
        txtBookingID.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtBookingID.putClientProperty("JTextField.placeholderText", "Ví dụ: 102...");
        txtBookingID.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_CLR, 1, true),
                new EmptyBorder(8, 12, 8, 12)));
        txtBookingID.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        txtBookingID.setAlignmentX(LEFT_ALIGNMENT);
        cardAction.add(txtBookingID);
        cardAction.add(Box.createVerticalStrut(16));

        JPanel cicoRow = new JPanel(new GridLayout(1, 2, 10, 0));
        cicoRow.setOpaque(false);
        cicoRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        cicoRow.setAlignmentX(LEFT_ALIGNMENT);
        btnCheckIn = createSolidBtn("Nhận phòng", SUCCESS);
        btnCheckOut = createSolidBtn("Trả phòng", WARNING);
        btnCheckIn.addActionListener(e -> actionCheckIn());
        btnCheckOut.addActionListener(e -> actionCheckOut());
        cicoRow.add(btnCheckIn);
        cicoRow.add(btnCheckOut);
        cardAction.add(cicoRow);
        
        scrollContent.add(cardAction);

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
        right.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());

        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        topBar.setBorder(new EmptyBorder(12, 16, 10, 16));
        JLabel lbl = new JLabel("Danh Sách Đặt Phòng");
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lbl.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        topBar.add(lbl, BorderLayout.WEST);
        right.add(topBar, BorderLayout.NORTH);

        bookingModel = new DefaultTableModel(
                new String[] { "ID", "Khách Hàng", "Phòng", "Check-in", "Check-out", "Trạng Thái" }, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        bookingTable = new JTable(bookingModel);
        bookingTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        bookingTable.setRowHeight(38);
        bookingTable.setShowGrid(false);
        bookingTable.setIntercellSpacing(new Dimension(0, 0));
        bookingTable.setSelectionBackground(ROW_SELECT);
        bookingTable.setFocusable(false);

        bookingTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        bookingTable.getTableHeader().setBackground(quanlykhachsan.frontend.utils.ThemeManager.isDarkMode() ? new Color(30, 41, 59) : new Color(241, 245, 249));
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
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int row,
                    int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                String status = val != null ? val.toString() : "";

                String statusVn = toVietnameseStatus(status);
                lbl.setText(statusVn);

                if (!sel) {
                    switch (status.toLowerCase()) {
                        case "pending":
                            lbl.setForeground(WARNING);
                            break;
                        case "checked_in":
                            lbl.setForeground(SUCCESS);
                            break;
                        case "checked_out":
                            lbl.setForeground(MUTED);
                            break;
                        case "paid":
                            lbl.setForeground(PRIMARY);
                            break;
                        default:
                            lbl.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
                    }
                }
                return lbl;
            }
        });

        // Zebra stripes
        bookingTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int row,
                    int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                if (!sel)
                    setBackground(row % 2 == 0 ? ROW_EVEN : quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
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
        scroll.getViewport().setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        right.add(scroll, BorderLayout.CENTER);

        return right;
    }

    // ── LOAD DỮ LIỆU ──────────────────────────────────────────────────────

    private void loadInitialData() {
        cbRoom.removeAllItems();
        cbRoom.addItem(new ComboItem(-1, "Đang tải..."));
        setStatus("Đang tải thông tin...", MUTED);
        
        selectedCustomer = null; 
        btnSelectCustomer.setText(" Chọn khách hàng...");
        btnSelectCustomer.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        btnSelectCustomer.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_CLR, 1, true), new EmptyBorder(8, 12, 8, 12)));

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                customersList = CustomerAPI.getAllCustomers();
                roomsList = RoomAPI.getAllRooms();
                return null;
            }

            @Override
            protected void done() {
                cbRoom.removeAllItems();
                try {
                    get();
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

                            bookingModel.addRow(new Object[] {
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
        ComboItem selRoom = (ComboItem) cbRoom.getSelectedItem();

        if (selectedCustomer == null) {
            showWarn("Vui lòng chọn khách hàng hợp lệ!");
            return;
        }
        if (selRoom == null || selRoom.getId() == -1) {
            showWarn("Vui lòng chọn phòng trống!");
            return;
        }
        if (dateCheckIn.getDate() == null || dateCheckOut.getDate() == null) {
            showWarn("Vui lòng chọn ngày check-in và check-out!");
            return;
        }
        long diff = dateCheckOut.getDate().getTime() - dateCheckIn.getDate().getTime();
        if (TimeUnit.MILLISECONDS.toDays(diff) <= 0) {
            showWarn("Ngày check-out phải sau ngày check-in!");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Xác nhận đặt phòng cho:\n  Khách: " + selectedCustomer.getFullName() + "\n  Phòng: " + selRoom,
                "Xác nhận Đặt Phòng", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION)
            return;

        int customerId = selectedCustomer.getId();
        int roomId = selRoom.getId();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String ci = sdf.format(dateCheckIn.getDate());
        String co = sdf.format(dateCheckOut.getDate());

        btnBook.setEnabled(false);
        setStatus("Đang đặt phòng...", MUTED);

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                JsonObject res = BookingAPI.bookRoom(customerId, roomId, ci, co);
                if (res != null) {
                    return ("success".equals(res.get("status").getAsString()) ? "Success: " : "Error: ") 
                            + res.get("message").getAsString();
                }
                return "Error: Lỗi kết nối máy chủ";
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
        if (idStr.isEmpty()) {
            showWarn("Vui lòng nhập hoặc chọn Mã Đặt Phòng!");
            return;
        }
        try {
            int bookingId = Integer.parseInt(idStr);
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Xác nhận NHẬN PHÒNG (Check-in) cho đơn ID: " + bookingId + "?",
                    "Xác nhận Check-in", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION)
                return;

            btnCheckIn.setEnabled(false);
            setStatus("Đang check-in...", MUTED);
            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override
                protected String doInBackground() {
                    return BookingAPI.checkIn(bookingId);
                }

                @Override
                protected void done() {
                    btnCheckIn.setEnabled(true);
                    try {
                        String msg = get();
                        boolean ok = msg != null && msg.toLowerCase().contains("success");
                        setStatus(ok ? "Nhận phòng thành công!" : msg, ok ? SUCCESS : DANGER);
                        if (ok) {
                            loadInitialData();
                        }
                    } catch (Exception ex) {
                        setStatus("Lỗi check-in!", DANGER);
                    }
                }
            };
            worker.execute();
        } catch (NumberFormatException e) {
            showWarn("Mã Đặt Phòng phải là số!");
        }
    }

    private void actionCheckOut() {
        String idStr = txtBookingID.getText().trim();
        if (idStr.isEmpty()) {
            showWarn("Vui lòng nhập hoặc chọn Mã Đặt Phòng!");
            return;
        }
        try {
            int bookingId = Integer.parseInt(idStr);
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Xác nhận TRẢ PHÒNG (Check-out) cho đơn ID: " + bookingId + "?\nHệ thống sẽ tự động tính tiền.",
                    "Xác nhận Check-out", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION)
                return;

            btnCheckOut.setEnabled(false);
            setStatus("Đang check-out...", MUTED);
            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override
                protected String doInBackground() {
                    return BookingAPI.checkOut(bookingId);
                }

                @Override
                protected void done() {
                    btnCheckOut.setEnabled(true);
                    try {
                        String msg = get();
                        boolean ok = msg != null && msg.toLowerCase().contains("success");
                        setStatus(ok ? "Trả phòng thành công!" : msg, ok ? SUCCESS : DANGER);
                        if (ok) {
                            loadInitialData();
                        }
                    } catch (Exception ex) {
                        setStatus("Lỗi check-out!", DANGER);
                    }
                }
            };
            worker.execute();
        } catch (NumberFormatException e) {
            showWarn("Mã Đặt Phòng phải là số!");
        }
    }

    // ── HELPERS ───────────────────────────────────────────────────────────

    private String toVietnameseStatus(String status) {
        if (status == null)
            return "--";
        switch (status.toLowerCase()) {
            case "pending": return "Chờ nhận phòng";
            case "confirmed": return "Đã xác nhận";
            case "checked_in": return "Đang ở";
            case "checked_out": return "Đã trả phòng";
            case "paid": return "Đã thanh toán";
            case "cancelled": return "Đã hủy";
            default: return status;
        }
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
        l.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
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
                g2.setColor(isEnabled() ? (getModel().isRollover() ? bg.darker() : bg) : new Color(209, 213, 219));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
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
        btn.setFocusPainted(false);
        btn.setBackground(quanlykhachsan.frontend.utils.ThemeManager.isDarkMode() ? new Color(30, 41, 59) : new Color(241, 245, 249));
        btn.setBorder(new LineBorder(BORDER_CLR, 1, true));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JPanel createSectionPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getBgPanel());
        p.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_CLR, 1, true),
            new EmptyBorder(12, 12, 12, 12)
        ));
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private void themeDateChooser(JDateChooser dc) {
        boolean isDark = quanlykhachsan.frontend.utils.ThemeManager.isDarkMode();
        Color cardBg = quanlykhachsan.frontend.utils.ThemeManager.getCardBg();
        Color textMain = quanlykhachsan.frontend.utils.ThemeManager.getTextMain();
        Color borderC = quanlykhachsan.frontend.utils.ThemeManager.getBorderColor();
        Color popupBg = isDark ? new Color(30, 41, 59) : Color.WHITE;

        // 1. Text Editor
        JTextField editor = (JTextField) dc.getDateEditor().getUiComponent();
        editor.setBackground(cardBg);
        editor.setForeground(textMain);
        editor.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(borderC, 1, true),
            new EmptyBorder(0, 10, 0, 10)
        ));
        // Add placeholder
        editor.putClientProperty("JTextField.placeholderText", "dd/MM/yyyy");

        // 2. Button
        dc.getCalendarButton().setBackground(cardBg);
        dc.getCalendarButton().setBorder(null);
        dc.getCalendarButton().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // 3. The Popup Calendar - This is harder, we try to set on the JCalendar component
        dc.getJCalendar().setBackground(popupBg);
        dc.getJCalendar().setForeground(textMain);

        // Decorate internal parts
        if (dc.getJCalendar().getDayChooser() != null) {
            dc.getJCalendar().getDayChooser().setBackground(popupBg);
            dc.getJCalendar().getDayChooser().setForeground(textMain);
            // Sunday/Saturday header
            dc.getJCalendar().getDayChooser().setDecorationBackgroundVisible(false);
            dc.getJCalendar().getDayChooser().setWeekdayForeground(isDark ? new Color(148, 163, 184) : Color.DARK_GRAY);
            dc.getJCalendar().getDayChooser().setSundayForeground(DANGER);
        }
        
        if (dc.getJCalendar().getMonthChooser() != null) {
            dc.getJCalendar().getMonthChooser().setBackground(popupBg);
            Component cb = dc.getJCalendar().getMonthChooser().getComboBox();
            if (cb instanceof JComboBox) {
                cb.setBackground(popupBg);
                cb.setForeground(textMain);
            }
        }
        
        if (dc.getJCalendar().getYearChooser() != null) {
            dc.getJCalendar().getYearChooser().setBackground(popupBg);
            Component sp = dc.getJCalendar().getYearChooser().getSpinner();
            if (sp instanceof JSpinner) {
                JSpinner spinner = (JSpinner) sp;
                spinner.setBackground(popupBg);
                if (spinner.getEditor() instanceof JSpinner.DefaultEditor) {
                    JTextField txt = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
                    txt.setBackground(popupBg);
                    txt.setForeground(textMain);
                }
            }
        }
    }

    static class ComboItem {
        private final int id;
        private final String label;

        ComboItem(int id, String label) {
            this.id = id;
            this.label = label;
        }

        public int getId() {
            return id;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
