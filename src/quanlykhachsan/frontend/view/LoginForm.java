package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import quanlykhachsan.frontend.api.AuthAPI;
import quanlykhachsan.frontend.utils.SessionManagerUtil;
import quanlykhachsan.frontend.utils.ThemeManager;
import quanlykhachsan.backend.model.User;
import quanlykhachsan.frontend.MainUI;

public class LoginForm extends JFrame {

    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JCheckBox chkShowPassword;
    private JButton btnLogin;
    private JLabel lblError;
    private JLabel lblLoading;

    private final Color PRIMARY_COLOR = ThemeManager.getPrimary();
    private final Color PRIMARY_HOVER = new Color(29, 78, 216);
    private final Color BG_COLOR = ThemeManager.getBgPanel();
    private final Color CARD_COLOR = ThemeManager.getCardBg();
    private final Color TEXT_MAIN = ThemeManager.getTextMain();
    private final Color TEXT_MUTED = ThemeManager.getTextMuted();
    private final Color ERROR_COLOR = ThemeManager.getDanger();
    private final Color BORDER_COLOR = ThemeManager.getBorderColor();
    private final Color BORDER_FOCUS = ThemeManager.getPrimary();
    private final Color SUCCESS_COLOR = ThemeManager.getSuccess();

    public LoginForm() {
        setTitle("Đăng nhập - Hệ thống Quản lý Khách sạn");
        setSize(480, 580);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
        initUI();
        setupKeyBindings();
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_COLOR);
        root.setBorder(new LineBorder(BORDER_COLOR, 1));

        // ── Header gradient ──────────────────────────────────────────────
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setPreferredSize(new Dimension(480, 160));
        headerPanel.setBorder(new EmptyBorder(10, 20, 20, 20));

        JPanel headerContent = new JPanel();
        headerContent.setLayout(new BoxLayout(headerContent, BoxLayout.Y_AXIS));
        headerContent.setOpaque(false);

        JLabel iconLabel = new JLabel("HỆ THỐNG", SwingConstants.CENTER);
        iconLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        iconLabel.setForeground(SUCCESS_COLOR);
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel titleLabel = new JLabel("ĐĂNG NHẬP", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 30));
        titleLabel.setForeground(TEXT_MAIN);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subLabel = new JLabel("Hệ thống Quản lý Khách sạn Chuyên nghiệp", SwingConstants.CENTER);
        subLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subLabel.setForeground(TEXT_MUTED);
        subLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerContent.add(Box.createVerticalStrut(20));
        headerContent.add(iconLabel);
        headerContent.add(Box.createVerticalStrut(4));
        headerContent.add(titleLabel);
        headerContent.add(Box.createVerticalStrut(2));
        headerContent.add(subLabel);

        JCheckBox chkThemeToggle = new JCheckBox("Dark mode", ThemeManager.isDarkMode());
        chkThemeToggle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        chkThemeToggle.setForeground(new Color(156, 163, 175));
        chkThemeToggle.setOpaque(false);
        chkThemeToggle.setFocusPainted(false);
        chkThemeToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        chkThemeToggle.addActionListener(e -> {
            Point loc = getLocation();
            ThemeManager.setDarkMode(chkThemeToggle.isSelected());
            this.dispose();
            LoginForm newLogin = new LoginForm();
            newLogin.setLocation(loc);
            newLogin.setVisible(true);
        });

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        topBar.setOpaque(false);
        topBar.add(chkThemeToggle);

        headerPanel.add(topBar, BorderLayout.NORTH);
        headerPanel.add(headerContent, BorderLayout.CENTER);
        root.add(headerPanel, BorderLayout.NORTH);

        // ── Card đăng nhập ───────────────────────────────────────────────
        JPanel cardOuter = new JPanel(new GridBagLayout());
        cardOuter.setOpaque(false);
        cardOuter.setBorder(new EmptyBorder(0, 40, 10, 40));

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
            new RoundBorder(BORDER_COLOR, 15),
            new EmptyBorder(20, 28, 20, 28)
        ));

        // Labels removed as requested

        lblError = new JLabel(" ");
        lblError.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblError.setForeground(ERROR_COLOR);
        lblError.setAlignmentX(LEFT_ALIGNMENT);
        lblError.setBorder(new EmptyBorder(3, 8, 3, 8));

        JLabel lblUsername = new JLabel("Tên đăng nhập");
        lblUsername.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblUsername.setForeground(TEXT_MAIN);
        lblUsername.setAlignmentX(LEFT_ALIGNMENT);

        txtUsername = createStyledTextField("Nhập tên đăng nhập...");
        txtUsername.setAlignmentX(LEFT_ALIGNMENT);
        txtUsername.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

        JLabel lblPassword = new JLabel("Mật khẩu");
        lblPassword.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblPassword.setForeground(TEXT_MAIN);
        lblPassword.setAlignmentX(LEFT_ALIGNMENT);

        txtPassword = createStyledPasswordField("Nhập mật khẩu...");
        txtPassword.setAlignmentX(LEFT_ALIGNMENT);
        txtPassword.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

        chkShowPassword = new JCheckBox("Hiển thị mật khẩu");
        chkShowPassword.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chkShowPassword.setForeground(TEXT_MUTED);
        chkShowPassword.setOpaque(false);
        chkShowPassword.setFocusPainted(false);
        chkShowPassword.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        chkShowPassword.setAlignmentX(LEFT_ALIGNMENT);
        chkShowPassword.addActionListener(e -> {
            if (chkShowPassword.isSelected()) {
                txtPassword.setEchoChar((char) 0);
            } else {
                txtPassword.setEchoChar('\u2022'); // Standard bullet character
            }
        });

        lblLoading = new JLabel(" ");
        lblLoading.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblLoading.setForeground(TEXT_MUTED);
        lblLoading.setAlignmentX(LEFT_ALIGNMENT);

        btnLogin = new JButton("Đăng nhập ngay") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? PRIMARY_COLOR : BORDER_COLOR);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                super.paintComponent(g);
            }
        };
        btnLogin.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnLogin.setForeground(Color.WHITE);
        btnLogin.setContentAreaFilled(false);
        btnLogin.setBorderPainted(false);
        btnLogin.setFocusPainted(false);
        btnLogin.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnLogin.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        btnLogin.setAlignmentX(LEFT_ALIGNMENT);
        btnLogin.addActionListener(e -> handleLogin());

        // card.add(lblWelcome); // Removed as requested
        // card.add(Box.createVerticalStrut(4));
        // card.add(lblSub);
        // card.add(Box.createVerticalStrut(4));
        card.add(lblError);
        card.add(Box.createVerticalStrut(2));
        card.add(lblUsername);
        card.add(Box.createVerticalStrut(6));
        card.add(txtUsername);
        card.add(Box.createVerticalStrut(12));
        card.add(lblPassword);
        card.add(Box.createVerticalStrut(6));
        card.add(txtPassword);
        card.add(Box.createVerticalStrut(4));
        card.add(chkShowPassword);
        card.add(Box.createVerticalStrut(6));
        card.add(lblLoading);
        card.add(Box.createVerticalStrut(14));
        JButton btnRegister = new JButton("Bạn chưa có tài khoản? Đăng ký ngay!");
        btnRegister.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnRegister.setForeground(SUCCESS_COLOR);
        btnRegister.setContentAreaFilled(false);
        btnRegister.setBorderPainted(false);
        btnRegister.setFocusPainted(false);
        btnRegister.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRegister.setAlignmentX(LEFT_ALIGNMENT);
        btnRegister.addActionListener(e -> {
            RegisterForm registerForm = new RegisterForm(this);
            this.setVisible(false);
            registerForm.setVisible(true);
        });

        card.add(Box.createVerticalStrut(14));
        card.add(btnLogin);
        card.add(Box.createVerticalStrut(14));
        card.add(btnRegister);

        cardOuter.add(card, new GridBagConstraints());
        root.add(cardOuter, BorderLayout.CENTER);

        int currentYear = java.time.Year.now().getValue();
        JLabel lblAppFooter = new JLabel("\u00A9 " + currentYear + " Hotel Manager System  |  v1.0",
                SwingConstants.CENTER);
        lblAppFooter.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblAppFooter.setForeground(TEXT_MUTED);
        lblAppFooter.setBorder(new EmptyBorder(0, 0, 14, 0));
        root.add(lblAppFooter, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private JTextField createStyledTextField(String placeholder) {
        JTextField field = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setColor(new Color(156, 163, 175));
                    g2.setFont(getFont().deriveFont(Font.ITALIC));
                    Insets ins = getInsets();
                    g2.drawString(placeholder, ins.left, getHeight() / 2 + g2.getFontMetrics().getAscent() / 2 - 2);
                }
            }
        };
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setForeground(TEXT_MAIN);
        field.setBorder(BorderFactory.createCompoundBorder(
                new RoundBorder(BORDER_COLOR, 8),
                new EmptyBorder(8, 12, 8, 12)));
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                        new RoundBorder(BORDER_FOCUS, 8), new EmptyBorder(8, 12, 8, 12)));
                field.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                        new RoundBorder(BORDER_COLOR, 8), new EmptyBorder(8, 12, 8, 12)));
                field.repaint();
            }
        });
        return field;
    }

    private JPasswordField createStyledPasswordField(String placeholder) {
        JPasswordField field = new JPasswordField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getPassword().length == 0 && !isFocusOwner()) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setColor(new Color(156, 163, 175));
                    g2.setFont(getFont().deriveFont(Font.ITALIC));
                    Insets ins = getInsets();
                    g2.drawString(placeholder, ins.left, getHeight() / 2 + g2.getFontMetrics().getAscent() / 2 - 2);
                }
            }
        };
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setForeground(TEXT_MAIN);
        field.setBorder(BorderFactory.createCompoundBorder(
                new RoundBorder(BORDER_COLOR, 8),
                new EmptyBorder(8, 12, 8, 12)));
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                        new RoundBorder(BORDER_FOCUS, 8), new EmptyBorder(8, 12, 8, 12)));
                field.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                        new RoundBorder(BORDER_COLOR, 8), new EmptyBorder(8, 12, 8, 12)));
                field.repaint();
            }
        });
        return field;
    }

    private void setupKeyBindings() {
        txtUsername.addActionListener(e -> txtPassword.requestFocus());
        txtPassword.addActionListener(e -> handleLogin());
    }

    private void showError(String msg) {
        lblError.setText("\u26A0 " + msg);
        lblError.setForeground(ERROR_COLOR);
    }

    private void clearError() {
        lblError.setText(" ");
    }

    private void handleLogin() {
        clearError();
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!");
            return;
        }

        btnLogin.setEnabled(false);
        lblLoading.setText("\u23F3 Đang xác thực...");

        SwingWorker<User, Void> worker = new SwingWorker<>() {
            @Override
            protected User doInBackground() throws Exception {
                return AuthAPI.login(username, password);
            }

            @Override
            protected void done() {
                btnLogin.setEnabled(true);
                lblLoading.setText(" ");
                try {
                    User user = get();
                    if (user != null) {
                        SessionManagerUtil.setUser(user); // Save session
                        dispose();
                        SwingUtilities.invokeLater(() -> new MainUI(user).setVisible(true));
                    } else {
                        showError("Tên đăng nhập hoặc mật khẩu không chính xác!");
                        txtPassword.setText("");
                        txtPassword.requestFocus();
                    }
                } catch (Exception ex) {
                    showError(ex.getMessage() != null ? ex.getMessage() : "Lỗi kết nối đến máy chủ. Vui lòng thử lại!");
                }
            }
        };
        worker.execute();
    }

    private static class RoundBorder extends AbstractBorder {
        private final Color color;
        private final int radius;
        public RoundBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
        }
        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(radius / 2, radius / 2, radius / 2, radius / 2);
        }
    }
}
