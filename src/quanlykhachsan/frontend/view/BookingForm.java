package quanlykhachsan.frontend.view;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;
import quanlykhachsan.backend.model.Customer;
import quanlykhachsan.backend.model.Room;
import quanlykhachsan.frontend.api.BookingAPI;
import quanlykhachsan.frontend.api.CustomerAPI;
import quanlykhachsan.frontend.api.RoomAPI;
import com.toedter.calendar.JDateChooser;

public class BookingForm extends JPanel {

    private JComboBox<ComboItem> cbCustomer;
    private JComboBox<ComboItem> cbRoom;
    private JDateChooser dateCheckIn;
    private JDateChooser dateCheckOut;
    private JTextField txtBookingID;
    private JButton btnBook, btnCheckIn, btnCheckOut, btnRefresh;

    public BookingForm() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        initUI();
        loadComboBoxData();
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

        JPanel btnTopPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnRefresh = new JButton("Tải lại danh sách");
        btnRefresh.addActionListener(e -> loadComboBoxData());
        btnBook = new JButton("Gửi yêu cầu Đặt Phòng");
        btnBook.addActionListener(e -> actionBook());
        
        btnTopPanel.add(btnRefresh);
        btnTopPanel.add(btnBook);

        gbc.gridx = 1; gbc.gridy = 4; gbc.anchor = GridBagConstraints.EAST;
        topPanel.add(btnTopPanel, gbc);
        
        add(topPanel, BorderLayout.NORTH);

        // PANEL LOWER - CHECK IN / CHECK OUT
        JPanel bottomPanel = new JPanel(new GridBagLayout());
        bottomPanel.setBorder(BorderFactory.createTitledBorder("Nhận / Trả phòng theo Booking ID"));
        GridBagConstraints bgc = new GridBagConstraints();
        bgc.fill = GridBagConstraints.HORIZONTAL;
        bgc.insets = new Insets(5, 5, 5, 5);

        bgc.gridx = 0; bgc.gridy = 0; bottomPanel.add(new JLabel("Booking ID:"), bgc);
        bgc.gridx = 1; bgc.gridy = 0; txtBookingID = new JTextField(10); bottomPanel.add(txtBookingID, bgc);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnCheckIn = new JButton("Check In");
        btnCheckIn.addActionListener(e -> actionCheckIn());
        btnCheckOut = new JButton("Check Out");
        btnCheckOut.addActionListener(e -> actionCheckOut());
        btnPanel.add(btnCheckIn);
        btnPanel.add(btnCheckOut);

        bgc.gridx = 1; bgc.gridy = 1; bottomPanel.add(btnPanel, bgc);

        add(bottomPanel, BorderLayout.CENTER);
    }

    private void loadComboBoxData() {
        cbCustomer.removeAllItems();
        cbRoom.removeAllItems();
        cbCustomer.addItem(new ComboItem(-1, "Đang tải..."));
        cbRoom.addItem(new ComboItem(-1, "Đang tải..."));

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            List<Customer> customers;
            List<Room> rooms;

            @Override
            protected Void doInBackground() throws Exception {
                customers = CustomerAPI.getAllCustomers();
                rooms = RoomAPI.getAllRooms();
                return null;
            }

            @Override
            protected void done() {
                cbCustomer.removeAllItems();
                cbRoom.removeAllItems();
                
                try {
                    if (customers != null && !customers.isEmpty()) {
                        for (Customer c : customers) {
                            cbCustomer.addItem(new ComboItem(c.getId(), c.getFullName() + " - " + c.getPhone()));
                        }
                    } else {
                        cbCustomer.addItem(new ComboItem(-1, "Không có khách hàng nào"));
                    }

                    if (rooms != null && !rooms.isEmpty()) {
                        for (Room r : rooms) {
                            // Cải thiện UI: Chỉ cho đặt phòng available
                            String label = "Phòng " + r.getRoomNumber() + " (" + r.getStatus() + ")";
                            cbRoom.addItem(new ComboItem(r.getId(), label));
                        }
                    } else {
                        cbRoom.addItem(new ComboItem(-1, "Không có phòng nào"));
                    }
                } catch (Exception e) {
                    cbCustomer.addItem(new ComboItem(-1, "Lỗi kết nối"));
                    cbRoom.addItem(new ComboItem(-1, "Lỗi kết nối"));
                }
            }
        };
        worker.execute();
    }

    private void actionBook() {
        try {
            ComboItem selectedCustomer = (ComboItem) cbCustomer.getSelectedItem();
            ComboItem selectedRoom = (ComboItem) cbRoom.getSelectedItem();
            
            if (selectedCustomer == null || selectedRoom == null || selectedCustomer.getId() == -1 || selectedRoom.getId() == -1) {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn khách hàng và phòng hợp lệ!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int customerId = selectedCustomer.getId();
            int roomId = selectedRoom.getId();

            if (dateCheckIn.getDate() == null || dateCheckOut.getDate() == null) {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn ngày hợp lệ trên Lịch!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                return;
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            String checkIn = sdf.format(dateCheckIn.getDate());
            String checkOut = sdf.format(dateCheckOut.getDate());

            btnBook.setEnabled(false);
            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override
                protected String doInBackground() {
                    return BookingAPI.bookRoom(customerId, roomId, checkIn, checkOut);
                }

                @Override
                protected void done() {
                    btnBook.setEnabled(true);
                    try {
                        String msg = get();
                        JOptionPane.showMessageDialog(BookingForm.this, msg, "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                        if (msg.startsWith("Success")) {
                            dateCheckIn.setDate(null);
                            dateCheckOut.setDate(null);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            };
            worker.execute();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Có lỗi xảy ra khi tạo giao dịch!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void actionCheckIn() {
        try {
            int bookingId = Integer.parseInt(txtBookingID.getText().trim());
            btnCheckIn.setEnabled(false);
            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override
                protected String doInBackground() {
                    return BookingAPI.checkIn(bookingId);
                }

                @Override
                protected void done() {
                    btnCheckIn.setEnabled(true);
                    try {
                        JOptionPane.showMessageDialog(BookingForm.this, get(), "Check-in Status", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {}
                }
            };
            worker.execute();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Booking ID phải là số hợp lệ", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void actionCheckOut() {
        try {
            int bookingId = Integer.parseInt(txtBookingID.getText().trim());
            btnCheckOut.setEnabled(false);
            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override
                protected String doInBackground() {
                    return BookingAPI.checkOut(bookingId);
                }

                @Override
                protected void done() {
                    btnCheckOut.setEnabled(true);
                    try {
                        JOptionPane.showMessageDialog(BookingForm.this, get(), "Check-out Status", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {}
                }
            };
            worker.execute();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Booking ID phải là số hợp lệ", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Class phụ trợ giúp JComboBox hiểu Name nhưng lưu ID
    class ComboItem {
        private int id;
        private String label;

        public ComboItem(int id, String label) {
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
