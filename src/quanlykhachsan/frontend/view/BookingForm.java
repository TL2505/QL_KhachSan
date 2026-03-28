package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.List;
import quanlykhachsan.backend.model.Customer;
import quanlykhachsan.backend.model.Room;
import quanlykhachsan.backend.model.Booking;
import quanlykhachsan.frontend.api.BookingAPI;
import quanlykhachsan.frontend.api.CustomerAPI;
import quanlykhachsan.frontend.api.RoomAPI;
import quanlykhachsan.frontend.MainUI;
import com.toedter.calendar.JDateChooser;
import com.google.gson.JsonObject;

public class BookingForm extends JPanel {

    private JComboBox<ComboItem> cbCustomer;
    private JComboBox<ComboItem> cbRoom;
    private JDateChooser dateCheckIn;
    private JDateChooser dateCheckOut;
    private JTextField txtBookingID;
    private JCheckBox chkCheckInNow;
    private JButton btnBook, btnCheckIn, btnCheckOut, btnRefresh;
    
    // Bảng hỗ trợ tra cứu Booking
    private JTable activeBookingTable;
    private DefaultTableModel activeBookingModel;

    public BookingForm() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        initUI();
        loadData();
    }

    private void initUI() {
        // PANEL UPPER - ĐẶT PHÒNG
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(BorderFactory.createTitledBorder("Đặt phòng mới"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0; gbc.gridy = 0; topPanel.add(new JLabel("Khách hàng:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; 
        cbCustomer = new JComboBox<>();
        cbCustomer.setEditable(true); // Cho phép nhập tìm kiếm
        topPanel.add(cbCustomer, gbc);

        gbc.gridx = 0; gbc.gridy = 1; topPanel.add(new JLabel("Phòng trống:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; 
        cbRoom = new JComboBox<>();
        topPanel.add(cbRoom, gbc);

        gbc.gridx = 0; gbc.gridy = 2; topPanel.add(new JLabel("Ngày bắt đầu:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; 
        dateCheckIn = new JDateChooser();
        dateCheckIn.setDateFormatString("yyyy-MM-dd");
        topPanel.add(dateCheckIn, gbc);

        gbc.gridx = 0; gbc.gridy = 3; topPanel.add(new JLabel("Ngày kết thúc:"), gbc);
        gbc.gridx = 1; gbc.gridy = 3; 
        dateCheckOut = new JDateChooser();
        dateCheckOut.setDateFormatString("yyyy-MM-dd");
        topPanel.add(dateCheckOut, gbc);

        gbc.gridx = 1; gbc.gridy = 4;
        chkCheckInNow = new JCheckBox("Nhận phòng ngay (Check-in now)");
        chkCheckInNow.addItemListener(e -> {
            if (chkCheckInNow.isSelected()) {
                dateCheckIn.setDate(new java.util.Date());
            }
        });
        topPanel.add(chkCheckInNow, gbc);

        JPanel btnTopPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnRefresh = new JButton("Làm mới dữ liệu");
        btnRefresh.addActionListener(e -> loadData());
        btnBook = new JButton("Xác nhận Đặt Phòng");
        btnBook.addActionListener(e -> actionBook());
        
        btnTopPanel.add(btnRefresh);
        btnTopPanel.add(btnBook);

        gbc.gridx = 1; gbc.gridy = 5; gbc.anchor = GridBagConstraints.EAST;
        topPanel.add(btnTopPanel, gbc);
        
        add(topPanel, BorderLayout.NORTH);

        // PANEL LOWER - NHẬN / TRẢ PHÒNG & TRA CỨU
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        
        // Bên trái: Nhập ID
        JPanel leftBottom = new JPanel(new GridBagLayout());
        leftBottom.setBorder(BorderFactory.createTitledBorder("Thủ tục Nhận / Trả"));
        GridBagConstraints bgc = new GridBagConstraints();
        bgc.fill = GridBagConstraints.HORIZONTAL; bgc.insets = new Insets(5, 5, 5, 5);
        bgc.gridx = 0; bgc.gridy = 0; leftBottom.add(new JLabel("Booking ID:"), bgc);
        bgc.gridx = 1; bgc.gridy = 0; txtBookingID = new JTextField(10); leftBottom.add(txtBookingID, bgc);
        
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnCheckIn = new JButton("Check In"); btnCheckIn.addActionListener(e -> actionCheckIn(-1));
        btnCheckOut = new JButton("Check Out"); btnCheckOut.addActionListener(e -> actionCheckOut());
        btnPanel.add(btnCheckIn); btnPanel.add(btnCheckOut);
        bgc.gridx = 1; bgc.gridy = 1; leftBottom.add(btnPanel, bgc);
        bottomPanel.add(leftBottom, BorderLayout.NORTH);

        // Bên dưới: Bảng tra cứu Booking Active
        activeBookingModel = new DefaultTableModel(new String[]{"ID", "Phòng", "Khách hàng", "Trạng thái"}, 0);
        activeBookingTable = new JTable(activeBookingModel);
        activeBookingTable.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int row = activeBookingTable.getSelectedRow();
                if (row != -1) {
                    txtBookingID.setText(activeBookingModel.getValueAt(row, 0).toString());
                }
            }
        });
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("Danh sách Booking hiện tại (Click để chọn nhanh)"));
        tablePanel.add(new JScrollPane(activeBookingTable), BorderLayout.CENTER);
        bottomPanel.add(tablePanel, BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.CENTER);
    }

    private void loadData() {
        cbCustomer.removeAllItems();
        cbRoom.removeAllItems();
        activeBookingModel.setRowCount(0);

        new SwingWorker<Void, Void>() {
            List<Customer> customers;
            List<Room> rooms;
            List<Booking> bookings;

            @Override protected Void doInBackground() {
                customers = CustomerAPI.getAllCustomers();
                rooms = RoomAPI.getAllRooms();
                bookings = BookingAPI.getAllBookings();
                return null;
            }

            @Override protected void done() {
                if (customers != null) for (Customer c : customers) cbCustomer.addItem(new ComboItem(c.getId(), c.getFullName()));
                if (rooms != null) {
                    for (Room r : rooms) {
                        if ("available".equals(r.getStatus())) {
                            cbRoom.addItem(new ComboItem(r.getId(), "Phòng " + r.getRoomNumber() + " - " + String.format("%,.0f VNĐ", r.getPrice())));
                        }
                    }
                }
                if (bookings != null) {
                    for (Booking b : bookings) {
                        if (!"checked_out".equals(b.getStatus())) {
                            activeBookingModel.addRow(new Object[]{b.getId(), b.getRoomId(), "ID Khách: " + b.getCustomerId(), b.getStatus()});
                        }
                    }
                }
            }
        }.execute();
    }

    public void preFillRoom(String roomNumber) {
        // Đợi load xong rồi mới chọn (Đơn giản hóa: load lại luôn)
        loadData();
        // Việc chọn phòng cụ thể sẽ khó hơn nếu load async, tạm thời user chọn tay
    }

    private void actionBook() {
        Object selected = cbCustomer.getSelectedItem();
        if (selected == null || selected.toString().isEmpty()) return;

        int customerId = -1;

        if (selected instanceof ComboItem) {
            customerId = ((ComboItem) selected).getId();
        } else {
            // User tự nhập text -> Tìm kiếm hoặc hỏi thêm mới
            String inputName = selected.toString();
            for (int i = 0; i < cbCustomer.getItemCount(); i++) {
                if (cbCustomer.getItemAt(i).getLabel().equalsIgnoreCase(inputName)) {
                    customerId = cbCustomer.getItemAt(i).getId();
                    break;
                }
            }
            if (customerId == -1) {
                int choice = JOptionPane.showConfirmDialog(this, "Khách hàng '" + inputName + "' không tồn tại. Thêm mới?", "Thêm khách hàng", JOptionPane.YES_NO_OPTION);
                if (choice == JOptionPane.YES_OPTION) {
                    MainUI mainUI = (MainUI) SwingUtilities.getWindowAncestor(this);
                    if (mainUI != null) {
                        mainUI.getCustomerForm().setCustomerName(inputName);
                        mainUI.switchTab("Quản lý Khách hàng");
                    }
                }
                return;
            }
        }

        ComboItem roomItem = (ComboItem) cbRoom.getSelectedItem();
        if (roomItem == null || dateCheckIn.getDate() == null || dateCheckOut.getDate() == null) {
            JOptionPane.showMessageDialog(this, "Thiếu thông tin đặt phòng!");
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String checkInStr = sdf.format(dateCheckIn.getDate());
        String checkOutStr = sdf.format(dateCheckOut.getDate());

        final int finalCustomerId = customerId;
        new SwingWorker<JsonObject, Void>() {
            @Override protected JsonObject doInBackground() {
                return BookingAPI.bookRoom(finalCustomerId, roomItem.getId(), checkInStr, checkOutStr);
            }
            @Override protected void done() {
                try {
                    JsonObject res = get();
                    if ("success".equals(res.get("status").getAsString())) {
                        int bId = res.get("bookingId").getAsInt();
                        if (chkCheckInNow.isSelected()) {
                            actionCheckIn(bId);
                        } else {
                            JOptionPane.showMessageDialog(BookingForm.this, "Đặt phòng thành công! ID: " + bId);
                            loadData();
                        }
                    } else {
                        JOptionPane.showMessageDialog(BookingForm.this, res.get("message").getAsString(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        }.execute();
    }

    private void actionCheckIn(int bookingId) {
        int finalId = (bookingId != -1) ? bookingId : 0;
        if (finalId == 0) {
            try { finalId = Integer.parseInt(txtBookingID.getText().trim()); } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Gõ mã Booking ID hoặc chọn từ danh sách!");
                return;
            }
        }
        
        final int id = finalId;
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() { return BookingAPI.checkIn(id); }
            @Override protected void done() {
                try {
                    String msg = get();
                    if (msg.startsWith("Success")) {
                        if (chkCheckInNow.isSelected()) {
                            MainUI mainUI = (MainUI) SwingUtilities.getWindowAncestor(BookingForm.this);
                            if (mainUI != null) mainUI.switchTab("Sơ đồ Phòng");
                        } else {
                            JOptionPane.showMessageDialog(BookingForm.this, msg);
                        }
                        loadData();
                    } else {
                        JOptionPane.showMessageDialog(BookingForm.this, msg, "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        }.execute();
    }

    private void actionCheckOut() {
        try {
            int id = Integer.parseInt(txtBookingID.getText().trim());
            new SwingWorker<String, Void>() {
                @Override protected String doInBackground() { return BookingAPI.checkOut(id); }
                @Override protected void done() {
                    try {
                        String msg = get();
                        JOptionPane.showMessageDialog(BookingForm.this, msg);
                        loadData();
                    } catch (Exception ex) {}
                }
            }.execute();
        } catch (Exception e) { JOptionPane.showMessageDialog(this, "Nhập ID Booking!"); }
    }

    class ComboItem {
        private int id; private String label;
        public ComboItem(int id, String label) { this.id = id; this.label = label; }
        public int getId() { return id; }
        public String getLabel() { return label; }
        @Override public String toString() { return label; }
    }
}
