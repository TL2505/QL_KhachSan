package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import quanlykhachsan.backend.model.User;
import quanlykhachsan.frontend.api.AuthAPI;

public class RegisterForm extends JFrame {

    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JPasswordField txtConfirmPassword;
    private JTextField txtFullName;
    private JTextField txtEmail;
    private JTextField txtPhone;
    private JButton btnRegister;
    private JButton btnLogin;
    private JLabel lblError;
    private JLabel lblLoading;

    private static final Color PRIMARY_COLOR = new Color(16, 185, 129); // Emerald color for register
    private static final Color PRIMARY_HOVER = new Color(5, 150, 105);
    private static final Color BG_COLOR = new Color(245, 247, 250);
    private static final Color CARD_COLOR = Color.WHITE;
    private static final Color TEXT_MAIN = new Color(17, 24, 39);
    private static final Color TEXT_MUTED = new Color(107, 114, 128);
    private static final Color ERROR_COLOR = new Color(220, 38, 38);
    private static final Color SUCCESS_COLOR = new Color(5, 150, 105);
    private static final Color BORDER_COLOR = new Color(209, 213, 219);
    private static final Color BORDER_FOCUS = new Color(16, 185, 129);

    private LoginForm loginFrame;

    public RegisterForm(LoginForm loginFrame) {
        this.loginFrame = loginFrame;
        setTitle("Đăng ký tài khoản");
        setSize(540, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
        setUndecorated(true);
        setShape(new RoundRectangle2D.Double(0, 0, 540, 720, 20, 20));
        initUI();
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, new Color(209, 250, 229), 0, getHeight(), BG_COLOR);
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
                        new Color(52, 211, 153));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        headerPanel.setPreferredSize(new Dimension(480, 140));
        headerPanel.setBorder(new EmptyBorder(15, 20, 15, 20));

        JPanel headerContent = new JPanel();
        headerContent.setLayout(new BoxLayout(headerContent, BoxLayout.Y_AXIS));
        headerContent.setOpaque(false);

        JLabel iconLabel = new JLabel("\uD83D\uDC64", SwingConstants.CENTER); // User icon
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 40));
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel titleLabel = new JLabel("ĐĂNG KÝ TÀI KHOẢN", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerContent.add(iconLabel);
        headerContent.add(Box.createVerticalStrut(6));
        headerContent.add(titleLabel);

        JButton btnClose = new JButton("x");
        btnClose.setFont(new Font("Arial", Font.BOLD, 18));
        btnClose.setForeground(new Color(236, 253, 245));
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

        // ── Card form ───────────────────────────────────────────────
        JPanel cardOuter = new JPanel(new GridBagLayout());
        cardOuter.setOpaque(false);
        cardOuter.setBorder(new EmptyBorder(10, 30, 10, 30));

        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                for (int i = 4; i >= 0; i--) {
                    g2.setColor(new Color(0, 0, 0, (4 - i) * 3));
                    g2.fillRoundRect(i, i + 2, getWidth() - i * 2, getHeight() - i * 2, 16, 16);
                }
                g2.setColor(CARD_COLOR);
                g2.fillRoundRect(0, 0, getWidth() - 4, getHeight() - 4, 16, 16);
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(20, 25, 20, 25));

        lblError = new JLabel(" ");
        lblError.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblError.setForeground(ERROR_COLOR);
        lblError.setAlignmentX(LEFT_ALIGNMENT);

        // Fields
        txtUsername = createStyledTextField("Nhập tên đăng nhập (*)");
        txtFullName = createStyledTextField("Họ và tên (*)");
        txtEmail = createStyledTextField("Email");
        txtPhone = createStyledTextField("Số điện thoại");
        txtPassword = createStyledPasswordField("Mật khẩu (*)");
        txtConfirmPassword = createStyledPasswordField("Xác nhận mật khẩu (*)");

        addFormRow(card, "Tên đăng nhập", txtUsername);
        addFormRow(card, "Họ và tên", txtFullName);
        addFormRow(card, "Email", txtEmail);
        addFormRow(card, "Số điện thoại", txtPhone);

        // Password row
        JLabel lblPassword = new JLabel("Mật khẩu");
        lblPassword.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblPassword.setForeground(TEXT_MAIN);
        lblPassword.setAlignmentX(LEFT_ALIGNMENT);
        card.add(lblPassword);
        card.add(Box.createVerticalStrut(4));
        txtPassword.setAlignmentX(LEFT_ALIGNMENT);
        txtPassword.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        card.add(txtPassword);
        card.add(Box.createVerticalStrut(10));

        // Confirm password row
        JLabel lblConfirmPassword = new JLabel("Xác nhận mật khẩu");
        lblConfirmPassword.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblConfirmPassword.setForeground(TEXT_MAIN);
        lblConfirmPassword.setAlignmentX(LEFT_ALIGNMENT);
        card.add(lblConfirmPassword);
        card.add(Box.createVerticalStrut(4));
        txtConfirmPassword.setAlignmentX(LEFT_ALIGNMENT);
        txtConfirmPassword.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        card.add(txtConfirmPassword);
        card.add(Box.createVerticalStrut(5));

        JCheckBox chkShowPassword = new JCheckBox("Hiển thị mật khẩu");
        chkShowPassword.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chkShowPassword.setForeground(TEXT_MUTED);
        chkShowPassword.setOpaque(false);
        chkShowPassword.setFocusPainted(false);
        chkShowPassword.setAlignmentX(LEFT_ALIGNMENT);
        chkShowPassword.addActionListener(e -> {
            char echo = chkShowPassword.isSelected() ? (char) 0 : '\u2022';
            txtPassword.setEchoChar(echo);
            txtConfirmPassword.setEchoChar(echo);
        });
        card.add(chkShowPassword);
        card.add(Box.createVerticalStrut(10));

        lblLoading = new JLabel(" ");
        lblLoading.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblLoading.setForeground(TEXT_MUTED);
        lblLoading.setAlignmentX(LEFT_ALIGNMENT);
        card.add(lblLoading);
        card.add(Box.createVerticalStrut(10));

        // Buttons
        btnRegister = new JButton("Tạo tài khoản") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? (getModel().isRollover() ? PRIMARY_HOVER : PRIMARY_COLOR) : BORDER_COLOR);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                super.paintComponent(g);
            }
        };
        btnRegister.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnRegister.setForeground(Color.WHITE);
        btnRegister.setContentAreaFilled(false);
        btnRegister.setBorderPainted(false);
        btnRegister.setFocusPainted(false);
        btnRegister.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRegister.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnRegister.setAlignmentX(LEFT_ALIGNMENT);
        btnRegister.addActionListener(e -> handleRegister());

        btnLogin = new JButton("← Trở lại Đăng nhập");
        btnLogin.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btnLogin.setForeground(new Color(37, 99, 235));
        btnLogin.setContentAreaFilled(false);
        btnLogin.setBorderPainted(false);
        btnLogin.setFocusPainted(false);
        btnLogin.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnLogin.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        btnLogin.setAlignmentX(LEFT_ALIGNMENT);
        btnLogin.addActionListener(e -> {
            this.dispose();
            loginFrame.setVisible(true);
        });

        card.add(lblError);
        card.add(Box.createVerticalStrut(5));
        card.add(btnRegister);
        card.add(Box.createVerticalStrut(5));
        card.add(btnLogin);

        cardOuter.add(card, new GridBagConstraints());
        root.add(cardOuter, BorderLayout.CENTER);

        setContentPane(root);
    }

    private void addFormRow(JPanel parent, String labelText, JTextField field) {
        JLabel label = new JLabel(labelText);
        label.setFont(new Font("Segoe UI", Font.BOLD, 13));
        label.setForeground(TEXT_MAIN);
        label.setAlignmentX(LEFT_ALIGNMENT);

        field.setAlignmentX(LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        parent.add(label);
        parent.add(Box.createVerticalStrut(4));
        parent.add(field);
        parent.add(Box.createVerticalStrut(10));
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
                field.setBorder(BorderFactory.createCompoundBorder(new LineBorder(BORDER_FOCUS, 2, true),
                        new EmptyBorder(5, 11, 5, 11)));
                field.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(new LineBorder(BORDER_COLOR, 1, true),
                        new EmptyBorder(6, 12, 6, 12)));
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
                field.setBorder(BorderFactory.createCompoundBorder(new LineBorder(BORDER_FOCUS, 2, true),
                        new EmptyBorder(5, 11, 5, 11)));
                field.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(new LineBorder(BORDER_COLOR, 1, true),
                        new EmptyBorder(6, 12, 6, 12)));
                field.repaint();
            }
        });
        return field;
    }

    private void handleRegister() {
        lblError.setText(" ");
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword()).trim();
        String confirm = new String(txtConfirmPassword.getPassword()).trim();
        String fullName = txtFullName.getText().trim();
        String email = txtEmail.getText().trim();
        String phone = txtPhone.getText().trim();

        if (username.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
            lblError.setText("\u26A0 Vui lòng nhập các trường bắt buộc (*)");
            return;
        }

        if (!password.equals(confirm)) {
            lblError.setText("\u26A0 Mật khẩu xác nhận không khớp!");
            return;
        }

        btnRegister.setEnabled(false);
        lblLoading.setText("\u23F3 Đang xử lý đăng ký...");

        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPassword(password);
        newUser.setFullName(fullName);
        newUser.setEmail(email);
        newUser.setPhone(phone);

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                return AuthAPI.register(newUser);
            }

            @Override
            protected void done() {
                btnRegister.setEnabled(true);
                lblLoading.setText(" ");
                try {
                    String msg = get();
                    JOptionPane.showMessageDialog(RegisterForm.this,
                            msg, "Thành công", JOptionPane.INFORMATION_MESSAGE);
                    // Quay lại màn hình đăng nhập
                    dispose();
                    loginFrame.setVisible(true);
                } catch (Exception ex) {
                    lblError.setText("\u26A0 " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }
}
