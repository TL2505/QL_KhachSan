package quanlykhachsan.frontend;

import javax.swing.*;
import java.awt.*;
import quanlykhachsan.backend.model.User;
import quanlykhachsan.frontend.view.BookingForm;
import quanlykhachsan.frontend.view.CustomerForm;
import quanlykhachsan.frontend.view.PaymentForm;
import quanlykhachsan.frontend.view.RoomForm;
import quanlykhachsan.frontend.view.RoomManagementForm;
import quanlykhachsan.frontend.view.LoginForm;
import quanlykhachsan.frontend.view.DashboardForm;

public class MainUI extends JFrame {

    private User currentUser;
    private JTabbedPane tabbedPane;
    
    // Form instances for cross-form communication
    private RoomForm roomForm;
    private BookingForm bookingForm;
    private CustomerForm customerForm;
    private PaymentForm paymentForm;
    private RoomManagementForm roomManagementForm;
    private DashboardForm dashboardForm;

    public MainUI(User user) {
        this.currentUser = user;
        setTitle("Hệ thống Quản lý Khách sạn - User: " + user.getUsername());
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JLabel lblTitle = new JLabel("HOTEL MANAGEMENT DASHBOARD");
        lblTitle.setFont(new Font("Arial", Font.BOLD, 20));
        headerPanel.add(lblTitle, BorderLayout.WEST);

        JButton btnLogout = new JButton("Đăng xuất");
        btnLogout.addActionListener(e -> logout());
        headerPanel.add(btnLogout, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // Tabs
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Arial", Font.PLAIN, 16));

        // Initialize forms
        roomForm = new RoomForm();
        bookingForm = new BookingForm();
        customerForm = new CustomerForm();
        paymentForm = new PaymentForm();

        // RBAC: Admin only Dashboard & Management
        if (currentUser.getRoleId() == 1) {
            dashboardForm = new DashboardForm();
            tabbedPane.addTab("📊 Dashboard", new ImageIcon(), dashboardForm, "Tổng quan hệ thống");
            
            roomManagementForm = new RoomManagementForm();
            tabbedPane.addTab("🔧 Quản lý Phòng", new ImageIcon(), roomManagementForm, "Quyền truy cập Admin");
        }

        tabbedPane.addTab("🏢 Sơ đồ Phòng", new ImageIcon(), roomForm, "Xem và quản lý phòng");

        tabbedPane.addTab("📅 Đặt / Nhận phòng", new ImageIcon(), bookingForm, "Thao tác đặt phòng");
        tabbedPane.addTab("👥 Quản lý Khách hàng", new ImageIcon(), customerForm, "Quản lý thông tin khách");
        tabbedPane.addTab("💳 Thanh toán", new ImageIcon(), paymentForm, "Xử lý thanh toán");

        add(tabbedPane, BorderLayout.CENTER);
    }

    // --- Orchestration Methods for Cross-Form Workflow ---
    
    public void switchTab(String title) {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getTitleAt(i).contains(title)) {
                tabbedPane.setSelectedIndex(i);
                break;
            }
        }
    }

    public RoomForm getRoomForm() { return roomForm; }
    public BookingForm getBookingForm() { return bookingForm; }
    public PaymentForm getPaymentForm() { return paymentForm; }
    public CustomerForm getCustomerForm() { return customerForm; }

    private void logout() {
        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có thực sự muốn đăng xuất?", "Đăng xuất", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            this.dispose();
            SwingUtilities.invokeLater(() -> {
                new LoginForm().setVisible(true);
            });
        }
    }
}
