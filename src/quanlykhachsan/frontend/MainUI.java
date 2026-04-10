package quanlykhachsan.frontend;

import javax.swing.*;
import java.awt.*;
import quanlykhachsan.backend.model.User;
import quanlykhachsan.frontend.view.BookingForm;
import quanlykhachsan.frontend.view.CustomerForm;
import quanlykhachsan.frontend.view.PaymentForm;
import quanlykhachsan.frontend.view.RoomForm;
import quanlykhachsan.frontend.view.LoginForm;
import quanlykhachsan.frontend.view.ProfileForm;
import quanlykhachsan.frontend.view.PersonnelForm;
import quanlykhachsan.frontend.view.ReportForm;

public class MainUI extends JFrame {

    private User currentUser;
    private JTabbedPane tabbedPane;

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
        JLabel lblTitle = new JLabel("BẢNG ĐIỀU KHIỂN QUẢN LÝ KHÁCH SẠN");
        lblTitle.setFont(new Font("Arial", Font.BOLD, 20));
        headerPanel.add(lblTitle, BorderLayout.WEST);

        JButton btnLogout = new JButton("Đăng xuất");
        btnLogout.addActionListener(e -> logout());
        headerPanel.add(btnLogout, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // Tabs
        tabbedPane = new JTabbedPane();
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabbedPane.setFont(new Font("Arial", Font.PLAIN, 16));

        tabbedPane.addTab("🏢 Sơ đồ Phòng", new ImageIcon(), new RoomForm(), "Xem và quản lý phòng");
        tabbedPane.addTab("👥 Khách hàng", new ImageIcon(), new CustomerForm(), "Quản lý thông tin khách hàng");
        tabbedPane.addTab("📅 Đặt / Nhận phòng", new ImageIcon(), new BookingForm(),
                "Thao tác đặt phòng và Check-in/Check-out");
        tabbedPane.addTab("💳 Thanh toán", new ImageIcon(), new PaymentForm(), "Xử lý thanh toán");

        // RBAC: Chức năng dành riêng cho Admin
        if (currentUser.getRoleId() == 1) {
            tabbedPane.addTab("👤 Quản lý Nhân sự", new ImageIcon(), new PersonnelForm(), "Quản lý nhân viên");
            tabbedPane.addTab("🔧 Quản trị & Báo cáo", new ImageIcon(), new ReportForm(), "Báo cáo doanh thu");
        }

        // Đặt tab Hồ sơ cá nhân ở dưới cùng danh sách
        tabbedPane.addTab("👤 Hồ sơ cá nhân", new ImageIcon(), new ProfileForm(currentUser),
                "Cài đặt hồ sơ & Mật khẩu");

        add(tabbedPane, BorderLayout.CENTER);
    }

    private void logout() {
        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có thực sự muốn đăng xuất?", "Đăng xuất",
                JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            this.dispose();
            SwingUtilities.invokeLater(() -> {
                new LoginForm().setVisible(true);
            });
        }
    }
}
