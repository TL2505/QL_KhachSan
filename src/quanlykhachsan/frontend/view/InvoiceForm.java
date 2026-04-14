package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import quanlykhachsan.backend.model.Invoice;
import quanlykhachsan.backend.model.Booking;
import quanlykhachsan.backend.model.Customer;
import quanlykhachsan.backend.model.Room;
import quanlykhachsan.frontend.api.InvoiceAPI;
import quanlykhachsan.frontend.api.BookingAPI;
import quanlykhachsan.frontend.api.CustomerAPI;
import quanlykhachsan.frontend.api.RoomAPI;
import quanlykhachsan.frontend.utils.InvoicePDFExporter;

public class InvoiceForm extends JPanel {

    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField txtSearch;
    private JLabel lblStatus;
    private JButton btnPrintPDF;

    private List<Invoice> currentInvoices;
    private final SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm");
    private final DecimalFormat nf = new DecimalFormat("#,### VNĐ");

    public InvoiceForm() {
        setLayout(new BorderLayout());
        setBackground(new Color(248, 250, 252));
        initUI();
        loadData("");
    }

    private void initUI() {
        // ── Header ──────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 1, 0, new Color(226, 232, 240)),
            new EmptyBorder(12, 20, 12, 20)
        ));
        
        JLabel title = new JLabel("🧾 Quản lý Hóa Đơn (Doanh thu)");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(new Color(17, 24, 39));
        
        lblStatus = new JLabel("Đang tải...");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblStatus.setForeground(new Color(107, 114, 128));
        
        header.add(title, BorderLayout.WEST);
        header.add(lblStatus, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ── Top Bar ─────────────────────────────────────────────────────
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        topBar.setBackground(Color.WHITE);
        
        txtSearch = new JTextField(20);
        txtSearch.setPreferredSize(new Dimension(200, 32));
        txtSearch.putClientProperty("JTextField.placeholderText", "Tìm theo tên khách, phòng...");
        
        JButton btnSearch = new JButton("\uD83D\uDD0D Tìm");
        btnSearch.setBackground(new Color(37, 99, 235));
        btnSearch.setForeground(Color.WHITE);
        btnSearch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnSearch.addActionListener(e -> loadData(txtSearch.getText()));

        JButton btnRefresh = new JButton("\u21BA Tải lại");
        btnRefresh.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRefresh.addActionListener(e -> {
            txtSearch.setText("");
            loadData("");
        });
        
        topBar.add(txtSearch);
        topBar.add(btnSearch);
        topBar.add(btnRefresh);
        
        // ── Bottom Bar ──────────────────────────────────────────────────
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        bottomBar.setBackground(Color.WHITE);
        bottomBar.setBorder(new MatteBorder(1, 0, 0, 0, new Color(226, 232, 240)));

        btnPrintPDF = new JButton("\uD83D\uDDA8 In Hóa Đơn PDF");
        btnPrintPDF.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnPrintPDF.setBackground(new Color(5, 150, 105));
        btnPrintPDF.setForeground(Color.WHITE);
        btnPrintPDF.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnPrintPDF.setEnabled(false);
        btnPrintPDF.addActionListener(e -> actionPrintPDF()); 

        bottomBar.add(btnPrintPDF);

        // ── Table ───────────────────────────────────────────────────────
        tableModel = new DefaultTableModel(new String[]{"ID Hóa đơn", "Khách Hàng", "Phòng", "Tiền Dịch vụ", "Tổng Cộng", "Ngày Thu", "Trạng thái"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(35);
        table.setShowGrid(false);
        table.setSelectionBackground(new Color(219, 234, 254));
        table.setSelectionForeground(new Color(17, 24, 39));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.getTableHeader().setReorderingAllowed(false);
        
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) btnPrintPDF.setEnabled(table.getSelectedRow() >= 0);
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new EmptyBorder(0, 0, 0, 0));

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(topBar, BorderLayout.NORTH);
        centerPanel.add(scroll, BorderLayout.CENTER);
        centerPanel.add(bottomBar, BorderLayout.SOUTH);

        add(centerPanel, BorderLayout.CENTER);
    }

    private void loadData(String keyword) {
        // ... (existing loadData logic is correct)
        lblStatus.setText("Đang tải dữ liệu...");
        SwingWorker<List<Invoice>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Invoice> doInBackground() {
                return InvoiceAPI.searchInvoices(keyword);
            }

            @Override
            protected void done() {
                try {
                    currentInvoices = get();
                    tableModel.setRowCount(0);
                    double totalRevenue = 0;
                    
                    for (Invoice inv : currentInvoices) {
                        String custName = inv.getCustomerName() != null ? inv.getCustomerName() : "N/A";
                        String roomNum = inv.getRoomNumber() != null ? inv.getRoomNumber() : "N/A";
                        String statusVn = "paid".equals(inv.getStatus()) ? "Đã Thu" : inv.getStatus();
                        
                        totalRevenue += inv.getFinalTotal();
                        
                        tableModel.addRow(new Object[]{
                            "HD" + inv.getId(),
                            custName,
                            roomNum,
                            nf.format(inv.getTotalServiceFee()),
                            nf.format(inv.getFinalTotal()),
                            inv.getIssueDate() != null ? df.format(inv.getIssueDate()) : "N/A",
                            statusVn
                        });
                    }
                    lblStatus.setText("Sẵn sàng. Tính tổng doanh thu: " + nf.format(totalRevenue));
                } catch (Exception e) {
                    lblStatus.setText("Lỗi kết nối mạng!");
                }
            }
        };
        worker.execute();
    }

    private void actionPrintPDF() {
        int index = table.getSelectedRow();
        if (index < 0 || currentInvoices == null || index >= currentInvoices.size()) return;

        Invoice inv = currentInvoices.get(index);
        
        lblStatus.setText("Đang khởi tạo lệnh in...");
        
        SwingWorker<Void, Void> printWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                // Ta cần lấy lại đầy đủ đối tượng Booking, Customer, Room để in
                // Trong thực tế có thể dùng cache, ở đây ta tải lại để đảm bảo mới nhất
                List<Booking> allB = BookingAPI.getAllBookings();
                Booking booking = null;
                for (Booking b : allB) if (b.getId() == inv.getBookingId()) { booking = b; break; }

                if (booking != null) {
                    List<Customer> allC = CustomerAPI.getAllCustomers();
                    Customer customer = null;
                    for (Customer c : allC) if (c.getId() == booking.getCustomerId()) { customer = c; break; }

                    List<Room> allR = RoomAPI.getAllRooms();
                    Room room = null;
                    for (Room r : allR) if (r.getId() == booking.getRoomId()) { room = r; break; }

                    long diff = booking.getCheckOutDate().getTime() - booking.getCheckInDate().getTime();
                    int days = (int) Math.max(1, diff / (1000 * 60 * 60 * 24));
                    
                    java.util.List<quanlykhachsan.backend.model.ServiceUsage> usagesList = 
                        quanlykhachsan.frontend.api.ServiceUsageAPI.getUsageByBooking(booking.getId());
                    
                    quanlykhachsan.frontend.utils.InvoicePDFExporter.exportPDF(booking, customer, room, usagesList, days, inv.getFinalTotal());
                }
                return null;
            }
            @Override
            protected void done() {
                lblStatus.setText("Sẵn sàng.");
            }
        };
        printWorker.execute();
    }
}
