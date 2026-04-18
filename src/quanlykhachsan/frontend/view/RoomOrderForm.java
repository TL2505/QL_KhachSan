package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import quanlykhachsan.backend.model.Booking;
import quanlykhachsan.backend.model.Room;
import quanlykhachsan.backend.model.Service;
import quanlykhachsan.backend.model.ServiceUsage;
import quanlykhachsan.frontend.api.BookingAPI;
import quanlykhachsan.frontend.api.RoomAPI;
import quanlykhachsan.frontend.api.ServiceAPI;
import quanlykhachsan.frontend.api.ServiceUsageAPI;

public class RoomOrderForm extends JPanel {
    private JTable bookingTable, usageTable;
    private DefaultTableModel bookingModel, usageModel;
    private JComboBox<ServiceItem> cbService;
    private JSpinner spinQty;
    private JButton btnAddOrder;
    private JLabel lblTotalServicePrice;
    
    // Cache
    private List<Booking> bookingsCache;
    private List<Room> roomsCache;
    private List<Service> servicesCache;
    private Booking selectedBooking = null;
    
    private final DecimalFormat nf = new DecimalFormat("#,###");

    public RoomOrderForm() {
        setLayout(new BorderLayout());
        setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        initUI();
    }

    private void initUI() {
        // --- Left Panel: Danh sách phòng đang ở ---
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(new MatteBorder(0, 0, 0, 1, Color.LIGHT_GRAY));
        leftPanel.setPreferredSize(new Dimension(350, 0));
        
        JLabel lblLeft = new JLabel("📝 Chọn Phòng");
        lblLeft.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblLeft.setBorder(new EmptyBorder(10, 10, 10, 10));
        leftPanel.add(lblLeft, BorderLayout.NORTH);

        bookingModel = new DefaultTableModel(new String[]{"ID", "Số Phòng", "Check-in"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        bookingTable = new JTable(bookingModel);
        bookingTable.setRowHeight(35);
        bookingTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && bookingTable.getSelectedRow() != -1) {
                int bid = (int) bookingModel.getValueAt(bookingTable.getSelectedRow(), 0);
                selectedBooking = bookingsCache.stream().filter(b -> b.getId() == bid).findFirst().orElse(null);
                loadUsageData();
            }
        });
        leftPanel.add(new JScrollPane(bookingTable), BorderLayout.CENTER);
        
        // Nút Reload Bookings
        JButton btnReload = new JButton("↻ Tải lại phòng");
        btnReload.addActionListener(e -> loadInitialData());
        leftPanel.add(btnReload, BorderLayout.SOUTH);

        // --- Right Panel: Trình gọi món & Lịch sử ---
        JPanel rightPanel = new JPanel(new BorderLayout());
        
        // Vùng gọi món (Top của Right)
        JPanel orderPanel = new JPanel(new BorderLayout());
        orderPanel.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getBgPanel());
        orderPanel.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
            new EmptyBorder(10, 10, 10, 10)
        ));
        
        cbService = new JComboBox<>();
        spinQty = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
        
        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        inputPanel.setOpaque(false);
        inputPanel.add(new JLabel("Món/Dịch vụ:"));
        inputPanel.add(cbService);
        inputPanel.add(new JLabel("Số lượng:"));
        inputPanel.add(spinQty);

        btnAddOrder = new JButton("➕ Ghi Hóa Đơn");
        btnAddOrder.setBackground(new Color(5, 150, 105));
        btnAddOrder.setForeground(Color.WHITE);
        btnAddOrder.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnAddOrder.addActionListener(e -> addServiceToRoom());
        
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(btnAddOrder);

        orderPanel.add(inputPanel, BorderLayout.CENTER);
        orderPanel.add(btnPanel, BorderLayout.EAST);
        
        rightPanel.add(orderPanel, BorderLayout.NORTH);
        
        // Vùng bảng hóa đơn (Center của Right)
        JPanel tablePanel = new JPanel(new BorderLayout());
        JLabel lblRight = new JLabel("🍽️ Lịch sử phục vụ tại phòng");
        lblRight.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblRight.setBorder(new EmptyBorder(10, 10, 10, 10));
        tablePanel.add(lblRight, BorderLayout.NORTH);
        
        usageModel = new DefaultTableModel(new String[]{"ID", "Món", "Số Lượng", "Đơn Giá", "Thành Tiền"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        usageTable = new JTable(usageModel);
        usageTable.setRowHeight(30);
        tablePanel.add(new JScrollPane(usageTable), BorderLayout.CENTER);
        
        // Vùng tổng tiền món (South của Right)
        JPanel summaryPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        lblTotalServicePrice = new JLabel("Tổng tiền Hóa đơn phụ: 0 VNĐ");
        lblTotalServicePrice.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTotalServicePrice.setForeground(Color.RED);
        summaryPanel.add(lblTotalServicePrice);
        tablePanel.add(summaryPanel, BorderLayout.SOUTH);
        
        rightPanel.add(tablePanel, BorderLayout.CENTER);
        
        add(leftPanel, BorderLayout.WEST);
        add(rightPanel, BorderLayout.CENTER);

        loadInitialData();
    }

    private void loadInitialData() {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() {
                bookingsCache = BookingAPI.getAllBookings();
                roomsCache = RoomAPI.getAllRooms();
                servicesCache = ServiceAPI.getAllServices();
                return null;
            }
            @Override protected void done() {
                // Đổ book đang ở
                bookingModel.setRowCount(0);
                SimpleDateFormat df = new SimpleDateFormat("dd/MM HH:mm");
                for (Booking b : bookingsCache) {
                    if ("checked_in".equals(b.getStatus())) {
                        String rName = getRoomName(b.getRoomId());
                        bookingModel.addRow(new Object[]{b.getId(), rName, df.format(b.getCheckInDate())});
                    }
                }
                
                // Đổ comboBox Services
                cbService.removeAllItems();
                for (Service s : servicesCache) {
                    if ("active".equals(s.getStatus())) {
                        cbService.addItem(new ServiceItem(s));
                    }
                }
            }
        };
        worker.execute();
    }

    private void loadUsageData() {
        if (selectedBooking == null) return;
        usageModel.setRowCount(0);
        double sum = 0;
        
        List<ServiceUsage> usages = ServiceUsageAPI.getUsageByBooking(selectedBooking.getId());
        for (ServiceUsage u : usages) {
            String sName = getServiceName(u.getServiceId());
            usageModel.addRow(new Object[]{
                u.getId(), sName, u.getQuantity(), nf.format(u.getUnitPrice()), nf.format(u.getTotalPrice())
            });
            sum += u.getTotalPrice();
        }
        lblTotalServicePrice.setText("Tổng tiền Hóa đơn phụ: " + nf.format(sum) + " VNĐ");
    }

    private void addServiceToRoom() {
        if (selectedBooking == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 phòng đang thuê ở cột bên trái!");
            return;
        }
        ServiceItem item = (ServiceItem) cbService.getSelectedItem();
        if (item == null) return;
        
        int qty = (int) spinQty.getValue();
        
        ServiceUsage u = new ServiceUsage();
        u.setBookingId(selectedBooking.getId());
        u.setServiceId(item.service.getId());
        u.setQuantity(qty);
        u.setUnitPrice(item.service.getPrice());
        
        String res = ServiceUsageAPI.addUsage(u);
        if (res.contains("Lỗi")) {
            JOptionPane.showMessageDialog(this, res);
        } else {
            // Success
            spinQty.setValue(1);
            loadUsageData();
        }
    }

    // Helpers
    private String getRoomName(int rId) {
        if (roomsCache == null) return "R"+rId;
        return roomsCache.stream().filter(r -> r.getId() == rId).map(Room::getRoomNumber).findFirst().orElse("R"+rId);
    }
    
    private String getServiceName(int sId) {
        if (servicesCache == null) return "S"+sId;
        return servicesCache.stream().filter(s -> s.getId() == sId).map(Service::getName).findFirst().orElse("S"+sId);
    }

    // Wrapper hien thi ComboBox
    class ServiceItem {
        Service service;
        public ServiceItem(Service s) { this.service = s; }
        @Override public String toString() { return service.getName() + " - " + nf.format(service.getPrice()); }
    }
}
