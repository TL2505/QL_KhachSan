package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import quanlykhachsan.frontend.api.AuthAPI;
import quanlykhachsan.frontend.utils.SessionManagerUtil;
import quanlykhachsan.backend.model.User;
import quanlykhachsan.frontend.MainUI;

public class LoginForm extends JFrame {

    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JCheckBox chkShowPassword;
    private JButton btnLogin;
    private JLabel lblError;
    private JLabel lblLoading;

    private static final Color PRIMARY_COLOR = new Color(37, 99, 235);
    private static final Color PRIMARY_HOVER = new Color(29, 78, 216);
    private static final Color BG_COLOR = new Color(245, 247, 250);
    private static final Color CARD_COLOR = Color.WHITE;
    private static final Color TEXT_MAIN = new Color(17, 24, 39);
    private static final Color TEXT_MUTED = new Color(107, 114, 128);
    private static final Color ERROR_COLOR = new Color(220, 38, 38);
    private static final Color BORDER_COLOR = new Color(209, 213, 219);
    private static final Color BORDER_FOCUS = new Color(37, 99, 235);

    public LoginForm() {
        setTitle("Đăng nhập - Hệ thống Quản lý Khách sạn");
        setSize(480, 580);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
        setUndecorated(true);
        setShape(new RoundRectangle2D.Double(0, 0, 480, 580, 20, 20));
        initUI();
        setupKeyBindings();
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, new Color(219, 234, 254), 0, getHeight(), BG_COLOR);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        root.setBorder(new LineBorder(BORDER_COLOR, 1));

        // ── Header gradient ──────────────────────────────────────────────
        JPanel headerPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, PRIMARY_COLOR, getWidth(), getHeight(),
                        new Color(99, 102, 241));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        headerPanel.setPreferredSize(new Dimension(480, 170));
        headerPanel.setBorder(new EmptyBorder(20, 30, 20, 30));

        JPanel headerContent = new JPanel();
        headerContent.setLayout(new BoxLayout(headerContent, BoxLayout.Y_AXIS));
        headerContent.setOpaque(false);

        JLabel iconLabel = new JLabel("\uD83C\uDFE8", SwingConstants.CENTER);
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 45));
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel titleLabel = new JLabel("QUẢN LÝ KHÁCH SẠN", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subLabel = new JLabel("Hệ thống Quản lý Khách sạn Chuyên nghiệp", SwingConstants.CENTER);
        subLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subLabel.setForeground(new Color(191, 219, 254));
        subLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerContent.add(iconLabel);
        headerContent.add(Box.createVerticalStrut(6));
        headerContent.add(titleLabel);
        headerContent.add(Box.createVerticalStrut(3));
        headerContent.add(subLabel);

        JButton btnClose = new JButton("x");
        btnClose.setFont(new Font("Arial", Font.BOLD, 18));
        btnClose.setForeground(new Color(191, 219, 254));
        btnClose.setContentAreaFilled(false);
        btnClose.setBorderPainted(false);
        btnClose.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnClose.addActionListener(e -> System.exit(0));
        JPanel closeWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closeWrapper.setOpaque(false);
        closeWrapper.add(btnClose);

        headerPanel.add(closeWrapper, BorderLayout.NORTH);
        headerPanel.add(headerContent, BorderLayout.CENTER);

        MouseAdapter mover = new MouseAdapter() {
            Point start;

            @Override
            public void mousePressed(MouseEvent e) {
                start = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                Point loc = getLocation();
                setLocation(loc.x + e.getX() - start.x, loc.y + e.getY() - start.y);
            }
        };
        headerPanel.addMouseListener(mover);
        headerPanel.addMouseMotionListener(mover);
        root.add(headerPanel, BorderLayout.NORTH);

        // ── Card đăng nhập ───────────────────────────────────────────────
        JPanel cardOuter = new JPanel(new GridBagLayout());
        cardOuter.setOpaque(false);
        cardOuter.setBorder(new EmptyBorder(20, 40, 10, 40));

        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Shadow
                for (int i = 5; i >= 0; i--) {
                    g2.setColor(new Color(0, 0, 0, (5 - i) * 5));
                    g2.fillRoundRect(i, i + 2, getWidth() - i * 2, getHeight() - i * 2, 16, 16);
                }
                g2.setColor(CARD_COLOR);
                g2.fillRoundRect(0, 0, getWidth() - 4, getHeight() - 4, 16, 16);
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(28, 28, 28, 28));

        JLabel lblWelcome = new JLabel("Chào mừng trở lại!");
        lblWelcome.setFont(new Font("Segoe UI", Font.BOLD, 19));
        lblWelcome.setForeground(TEXT_MAIN);
        lblWelcome.setAlignmentX(LEFT_ALIGNMENT);

        JLabel lblSub = new JLabel("Vui lòng đăng nhập để tiếp tục sử dụng hệ thống");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(TEXT_MUTED);
        lblSub.setAlignmentX(LEFT_ALIGNMENT);

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

        btnLogin = new JButton("Đăng nhập \u2192") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? (getModel().isRollover() ? PRIMARY_HOVER : PRIMARY_COLOR) : BORDER_COLOR);
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

        card.add(lblWelcome);
        card.add(Box.createVerticalStrut(4));
        card.add(lblSub);
        card.add(Box.createVerticalStrut(4));
        card.add(lblError);
        card.add(Box.createVerticalStrut(6));
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
        card.add(btnLogin);

        JButton btnRegister = new JButton("Bạn chưa có tài khoản? Đăng ký ngay!");
        btnRegister.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnRegister.setForeground(new Color(37, 99, 235));
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

        card.add(Box.createVerticalStrut(10));
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
        field.setBackground(new Color(249, 250, 251));
        field.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(6, 12, 6, 12)));
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(BORDER_FOCUS, 2, true), new EmptyBorder(5, 11, 5, 11)));
                field.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(BORDER_COLOR, 1, true), new EmptyBorder(6, 12, 6, 12)));
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
        field.setBackground(new Color(249, 250, 251));
        field.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(6, 12, 6, 12)));
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(BORDER_FOCUS, 2, true), new EmptyBorder(5, 11, 5, 11)));
                field.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(BORDER_COLOR, 1, true), new EmptyBorder(6, 12, 6, 12)));
                field.repaint();
            }
        });
        return field;
    }

    private void setupKeyBindings() {
        ActionListener loginAction = e -> handleLogin();
        txtUsername.addActionListener(loginAction);
        txtPassword.addActionListener(loginAction);
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
}
