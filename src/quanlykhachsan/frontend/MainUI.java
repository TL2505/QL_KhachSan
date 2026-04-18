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
import quanlykhachsan.frontend.view.InvoiceForm;
import quanlykhachsan.frontend.utils.ThemeManager;
import quanlykhachsan.frontend.view.PromotionForm;
import quanlykhachsan.frontend.view.CustomerPromotionView;
import quanlykhachsan.frontend.view.AdminDashboard;
import quanlykhachsan.frontend.view.ReviewManagementForm;
import quanlykhachsan.frontend.view.CustomerDashboard;
import quanlykhachsan.frontend.view.LoyaltyForm;
import quanlykhachsan.frontend.view.RoomDiscoveryPanel;
import quanlykhachsan.frontend.view.SupportManagementForm;

public class MainUI extends JFrame {

    private User currentUser;
    private JTabbedPane tabbedPane;

    public MainUI(User user) {
        this.currentUser = user;
        setTitle("Hệ thống Quản lý Khách sạn - User: " + user.getUsername());
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                GradientPaint gp;
                int rId = currentUser.getRoleId();
                if (rId == 1) { // ADMIN: Slate/Dark
                    gp = new GradientPaint(0, 0, new Color(30, 41, 59), getWidth(), 0, new Color(15, 23, 42));
                } else if (rId == 2) { // STAFF: Indigo/Blue
                    gp = new GradientPaint(0, 0, new Color(63, 81, 181), getWidth(), 0, new Color(48, 63, 159));
                } else { // CUSTOMER: Teal/Emerald
                    gp = new GradientPaint(0, 0, new Color(13, 148, 136), getWidth(), 0, new Color(15, 118, 110));
                }

                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        headerPanel.setPreferredSize(new Dimension(1000, 60)); // Standard height
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 30));

        JLabel lblTitle = new JLabel("HỆ THỐNG QUẢN LÝ KHÁCH SẠN");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(Color.WHITE);
        headerPanel.add(lblTitle, BorderLayout.WEST);

        JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 15));
        rightHeader.setOpaque(false);

        String roleName = (currentUser.getRoleId() == 1) ? "Admin"
                : (currentUser.getRoleId() == 2 ? "Nhân viên" : "Khách hàng");
        JLabel lblUser = new JLabel("Xin chào, " + currentUser.getFullName() + " | " + roleName.toUpperCase());
        lblUser.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblUser.setForeground(new Color(241, 245, 249));
        rightHeader.add(lblUser);

        JCheckBox chkThemeToggle = new JCheckBox("Dark mode", ThemeManager.isDarkMode());
        chkThemeToggle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        chkThemeToggle.setForeground(Color.WHITE);
        chkThemeToggle.setOpaque(false);
        chkThemeToggle.setFocusPainted(false);
        chkThemeToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        chkThemeToggle.addActionListener(e -> {
            int currentTab = tabbedPane.getSelectedIndex();
            boolean isDark = chkThemeToggle.isSelected();
            
            // Capture state
            Point loc = getLocation();
            Dimension size = getSize();
            int extendedState = getExtendedState();
            
            ThemeManager.setDarkMode(isDark);
            this.dispose();
            
            MainUI newUI = new MainUI(currentUser);
            newUI.setSelectedTab(currentTab);
            
            // Restore state
            newUI.setSize(size);
            newUI.setLocation(loc);
            newUI.setExtendedState(extendedState);
            
            newUI.setVisible(true);
        });
        rightHeader.add(chkThemeToggle);

        JButton btnLogout = new JButton("Đăng xuất");
        btnLogout.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnLogout.setForeground(Color.WHITE);
        btnLogout.setBackground(new Color(239, 68, 68));
        btnLogout.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(239, 68, 68)),
                BorderFactory.createEmptyBorder(8, 20, 8, 20)));
        btnLogout.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnLogout.setFocusPainted(false);
        btnLogout.addActionListener(e -> logout());
        rightHeader.add(btnLogout);

        headerPanel.add(rightHeader, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Tabs
        tabbedPane = new JTabbedPane(JTabbedPane.LEFT);
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        tabbedPane.setBackground(ThemeManager.getSidebarBg());
        tabbedPane.setForeground(ThemeManager.getTextMain());
        tabbedPane.setOpaque(true);
        tabbedPane.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            @Override
            protected void paintTabArea(Graphics g, int tabPlacement, int selectedIndex) {
                g.setColor(ThemeManager.getSidebarBg());
                g.fillRect(0, 0, tabbedPane.getWidth(), tabbedPane.getHeight());
                super.paintTabArea(g, tabPlacement, selectedIndex);
            }
            @Override
            protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
                // Remove content border line
            }
            @Override
            protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex, Rectangle iconRect, Rectangle textRect, boolean isSelected) {
                // Remove focus dotted line
            }
            @Override
            protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
                // Remove individual tab border line
            }
            @Override
            protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
                if (isSelected) {
                    g.setColor(ThemeManager.getCardBg());
                    g.fillRect(x, y, w, h);
                    // Blue indicator line
                    g.setColor(ThemeManager.getPrimary());
                    g.fillRect(x, y, 4, h);
                } else {
                    g.setColor(ThemeManager.getSidebarBg());
                    g.fillRect(x, y, w, h);
                }
            }
        });

        tabbedPane.addChangeListener(e -> updateTabStyles());

        // ── Tabs phân quyền (RBAC) ──
        int rId = currentUser.getRoleId();

        if (rId == 1) { // ADMIN
            addTab("Trang chủ", new AdminDashboard(idx -> tabbedPane.setSelectedIndex(idx)));
            addTab("Sơ đồ Phòng", new RoomForm(currentUser));
            addTab("Khách hàng", new CustomerForm());
            addTab("Hệ thành viên", new LoyaltyForm());
            addTab("Quản lý Đánh giá", new ReviewManagementForm());
            addTab("Quản lý Hóa đơn", new InvoiceForm());
            addTab("Quản lý Người dùng", new PersonnelForm());
            addTab("Quản lý Khuyến mãi", new PromotionForm());
            addTab("Quản trị & Báo cáo", new ReportForm());
        } else if (rId == 2) { // STAFF (Lễ tân)
            addTab("Sơ đồ Phòng", new RoomForm(currentUser));
            addTab("Đơn đặt phòng", new BookingForm());
            addTab("Khách hàng", new CustomerForm());
            addTab("Thanh toán", new PaymentForm());
            addTab("Hệ thành viên", new LoyaltyForm());
            addTab("Hỗ trợ khách hàng", new SupportManagementForm(currentUser));
            addTab("Ưu đãi & KM", new CustomerPromotionView());
        } else if (rId == 3) { // CUSTOMER
            addTab("Tìm & Đặt phòng", new RoomDiscoveryPanel(currentUser));
            addTab("Bảng điều khiển", new CustomerDashboard(currentUser));
            addTab("Ưu đãi & KM", new CustomerPromotionView());
        }

        // 5. Hồ sơ cá nhân (Dùng chung)
        addTab("Hồ sơ cá nhân", new ProfileForm(currentUser));

        add(tabbedPane, BorderLayout.CENTER);
    }

    /**
     * Helper to add tabs with standardized font
     */
    private void addTab(String title, JPanel panel) {
        tabbedPane.addTab(title, panel);
        int index = tabbedPane.getTabCount() - 1;
        JLabel lbl = new JLabel(title);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lbl.setForeground(ThemeManager.getTextMain());
        lbl.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 15));
        tabbedPane.setTabComponentAt(index, lbl);
        updateTabStyles();
    }

    private void updateTabStyles() {
        int selectedIndex = tabbedPane.getSelectedIndex();
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            Component c = tabbedPane.getTabComponentAt(i);
            if (c instanceof JLabel) {
                JLabel lbl = (JLabel) c;
                if (i == selectedIndex) {
                    lbl.setForeground(ThemeManager.getPrimary());
                    // lbl.setFont(new Font("Segoe UI", Font.BOLD, 14)); // Slightly larger if desired
                } else {
                    lbl.setForeground(ThemeManager.getTextMain());
                    // lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
                }
            }
        }
    }

    public void setSelectedTab(int index) {
        if (tabbedPane != null && index >= 0 && index < tabbedPane.getTabCount()) {
            tabbedPane.setSelectedIndex(index);
        }
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
