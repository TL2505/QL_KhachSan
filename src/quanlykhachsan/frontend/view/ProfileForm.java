package quanlykhachsan.frontend.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import quanlykhachsan.backend.model.User;
import quanlykhachsan.frontend.api.UserAPI;

public class ProfileForm extends JPanel {

    private User currentUser;
    
    private final Color PRIMARY   = new Color(37, 99, 235);
    private final Color SUCCESS   = new Color(34, 197, 94);
    private final Color DANGER    = new Color(239, 68, 68);
    private final Color BG_PANEL  = quanlykhachsan.frontend.utils.ThemeManager.getBgPanel();
    private final Color BORDER_CLR = quanlykhachsan.frontend.utils.ThemeManager.getBorderColor();
    private final Color CARD_BG   = quanlykhachsan.frontend.utils.ThemeManager.getCardBg();

    // UI components cho Profile update
    private JTextField txtUsername;
    private JTextField txtFullName;
    private JTextField txtEmail;
    private JTextField txtPhone;
    private JButton btnEditProfile;
    private JButton btnSaveProfile;
    
    // UI components cho Change password
    private JPasswordField txtOldPassword;
    private JPasswordField txtNewPassword;
    private JPasswordField txtConfirmPassword;
    private JButton btnEditPassword;
    private JButton btnSavePassword;

    public ProfileForm(User user) {
        this.currentUser = user;
        initComponents();
        setProfileEditMode(false);
        setPasswordEditMode(false);
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setBackground(BG_PANEL);
        setBorder(BorderFactory.createEmptyBorder(20, 50, 20, 50));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createCompoundBorder(
            new javax.swing.border.MatteBorder(0, 0, 1, 0, BORDER_CLR),
            new javax.swing.border.EmptyBorder(10, 0, 20, 0)
        ));
        JLabel titleLabel = new JLabel("Hồ Sơ & Bảo Mật");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        header.add(titleLabel, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);

        // 1. PANEL THÔNG TIN CÁ NHÂN
        JPanel profileContainer = new JPanel(new BorderLayout(0, 15));
        profileContainer.setOpaque(false);
        JLabel profTitle = new JLabel("Thông tin cá nhân");
        profTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        profileContainer.add(profTitle, BorderLayout.NORTH);

        JPanel profilePanel = new JPanel(new GridBagLayout());
        profilePanel.setBackground(CARD_BG);
        profilePanel.setBorder(BorderFactory.createCompoundBorder(
            new RoundBorder(BORDER_CLR, 15),
            BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));
        GridBagConstraints gbcProf = new GridBagConstraints();
        gbcProf.insets = new Insets(10, 10, 10, 10);
        gbcProf.fill = GridBagConstraints.HORIZONTAL;
        gbcProf.weightx = 1.0;

        txtUsername = createStyledTextField();
        txtUsername.setText(currentUser.getUsername());
        txtUsername.setEditable(false);
        
        txtFullName = createStyledTextField();
        if (currentUser.getFullName() != null) txtFullName.setText(currentUser.getFullName());
        
        txtEmail = createStyledTextField();
        if (currentUser.getEmail() != null) txtEmail.setText(currentUser.getEmail());
        
        txtPhone = createStyledTextField();
        if (currentUser.getPhone() != null) txtPhone.setText(currentUser.getPhone());

        addFormField(profilePanel, gbcProf, "Tài khoản đăng nhập:", txtUsername, 0);
        addFormField(profilePanel, gbcProf, "Họ và chữ lót:", txtFullName, 1);
        addFormField(profilePanel, gbcProf, "Email liên hệ:", txtEmail, 2);
        addFormField(profilePanel, gbcProf, "Số điện thoại:", txtPhone, 3);

        JPanel profileBtnBox = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        profileBtnBox.setOpaque(false);
        btnEditProfile = createStyledButton("Chỉnh sửa thông tin", PRIMARY, false);
        btnSaveProfile = createStyledButton("Lưu thay đổi hồ sơ", SUCCESS, true);
        
        btnEditProfile.addActionListener(e -> setProfileEditMode(true));
        btnSaveProfile.addActionListener(this::handleUpdateProfile);
        
        profileBtnBox.add(btnEditProfile);
        profileBtnBox.add(btnSaveProfile);
        
        gbcProf.gridx = 0; gbcProf.gridy = 4; gbcProf.gridwidth = 2;
        profilePanel.add(profileBtnBox, gbcProf);
        profileContainer.add(profilePanel, BorderLayout.CENTER);

        // 2. PANEL ĐỔI MẬT KHẨU
        JPanel passwordContainer = new JPanel(new BorderLayout(0, 15));
        passwordContainer.setOpaque(false);
        JLabel passTitle = new JLabel("Bảo mật & Mật khẩu");
        passTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        passwordContainer.add(passTitle, BorderLayout.NORTH);

        JPanel passwordPanel = new JPanel(new GridBagLayout());
        passwordPanel.setBackground(CARD_BG);
        passwordPanel.setBorder(BorderFactory.createCompoundBorder(
            new RoundBorder(BORDER_CLR, 15),
            BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));
        GridBagConstraints gbcPass = new GridBagConstraints();
        gbcPass.insets = new Insets(10, 10, 10, 10);
        gbcPass.fill = GridBagConstraints.HORIZONTAL;
        gbcPass.weightx = 1.0;

        txtOldPassword = createStyledPasswordField();
        txtNewPassword = createStyledPasswordField();
        txtConfirmPassword = createStyledPasswordField();

        addFormField(passwordPanel, gbcPass, "Mật khẩu hiện tại:", txtOldPassword, 0);
        addFormField(passwordPanel, gbcPass, "Mật khẩu mới:", txtNewPassword, 1);
        addFormField(passwordPanel, gbcPass, "Xác nhận mật khẩu:", txtConfirmPassword, 2);

        JPanel passBtnBox = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        passBtnBox.setOpaque(false);
        btnEditPassword = createStyledButton("Đổi mật khẩu", PRIMARY, false);
        btnSavePassword = createStyledButton("Lưu mật khẩu", DANGER, true);

        btnEditPassword.addActionListener(e -> setPasswordEditMode(true));
        btnSavePassword.addActionListener(this::handleChangePassword);

        passBtnBox.add(btnEditPassword);
        passBtnBox.add(btnSavePassword);
        
        gbcPass.gridx = 0; gbcPass.gridy = 3; gbcPass.gridwidth = 2;
        passwordPanel.add(passBtnBox, gbcPass);
        passwordContainer.add(passwordPanel, BorderLayout.CENTER);

        // Canh giữa form
        JPanel centerWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        centerWrapper.setOpaque(false);
        JPanel fixedWidthContainer = new JPanel();
        fixedWidthContainer.setOpaque(false);
        fixedWidthContainer.setLayout(new BoxLayout(fixedWidthContainer, BoxLayout.Y_AXIS));
        fixedWidthContainer.setPreferredSize(new Dimension(700, 700));

        fixedWidthContainer.add(profileContainer);
        fixedWidthContainer.add(Box.createRigidArea(new Dimension(0, 30)));
        fixedWidthContainer.add(passwordContainer);
        
        centerWrapper.add(fixedWidthContainer);
        
        JScrollPane scrollPane = new JScrollPane(centerWrapper);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(BG_PANEL);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        add(scrollPane, BorderLayout.CENTER);
    }

    private void addFormField(JPanel panel, GridBagConstraints gbc, String labelText, JComponent field, int row) {
        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = row;
        gbc.weightx = 0.3;
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lbl.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMuted());
        panel.add(lbl, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 0.7;
        panel.add(field, gbc);
    }

    private void setProfileEditMode(boolean enable) {
        txtFullName.setEditable(enable);
        txtEmail.setEditable(enable);
        txtPhone.setEditable(enable);
        
        btnEditProfile.setVisible(!enable);
        btnSaveProfile.setVisible(enable);
        
        if (!enable) {
            // Revert changes if cancelled
            txtFullName.setText(currentUser.getFullName() != null ? currentUser.getFullName() : "");
            txtEmail.setText(currentUser.getEmail() != null ? currentUser.getEmail() : "");
            txtPhone.setText(currentUser.getPhone() != null ? currentUser.getPhone() : "");
        }
    }

    private void setPasswordEditMode(boolean enable) {
        txtOldPassword.setEditable(enable);
        txtNewPassword.setEditable(enable);
        txtConfirmPassword.setEditable(enable);
        
        btnEditPassword.setVisible(!enable);
        btnSavePassword.setVisible(enable);
        
        if (!enable) {
            txtOldPassword.setText("");
            txtNewPassword.setText("");
            txtConfirmPassword.setText("");
        }
    }

    private void handleUpdateProfile(ActionEvent e) {
        String fName = txtFullName.getText().trim();
        String mail = txtEmail.getText().trim();
        String sdt = txtPhone.getText().trim();

        try {
            String result = UserAPI.updateProfile(currentUser.getUsername(), fName, mail, sdt);
            JOptionPane.showMessageDialog(this, result, "Thành công", JOptionPane.INFORMATION_MESSAGE);
            // Cập nhật lại cache hiện tại
            currentUser.setFullName(fName);
            currentUser.setEmail(mail);
            currentUser.setPhone(sdt);
            
            // Xong trở về Readonly
            setProfileEditMode(false);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleChangePassword(ActionEvent e) {
        String oldPass = new String(txtOldPassword.getPassword());
        String newPass = new String(txtNewPassword.getPassword());
        String confirmPass = new String(txtConfirmPassword.getPassword());

        if (oldPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đủ các trường mật khẩu!", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!newPass.equals(confirmPass)) {
            JOptionPane.showMessageDialog(this, "Mật khẩu xác nhận không khớp!", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            String result = UserAPI.changePassword(currentUser.getUsername(), oldPass, newPass);
            JOptionPane.showMessageDialog(this, result + "\nVui lòng dùng mật khẩu mới cho lần đăng nhập kế.", "Thành công", JOptionPane.INFORMATION_MESSAGE);
            setPasswordEditMode(false);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JTextField createStyledTextField() {
        JTextField tf = new JTextField(30);
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tf.setBackground(CARD_BG);
        tf.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        tf.setBorder(BorderFactory.createCompoundBorder(
            new RoundBorder(BORDER_CLR, 8),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        return tf;
    }

    private JPasswordField createStyledPasswordField() {
        JPasswordField pf = new JPasswordField(30);
        pf.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        pf.setBackground(CARD_BG);
        pf.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        pf.setBorder(BorderFactory.createCompoundBorder(
            new RoundBorder(BORDER_CLR, 8),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        return pf;
    }

    private JButton createStyledButton(String text, Color color, boolean solid) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFocusPainted(false);
        if (solid) {
            btn.setBackground(color);
            btn.setForeground(Color.WHITE);
            btn.setBorder(BorderFactory.createCompoundBorder(
                new RoundBorder(color, 8),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)
            ));
        } else {
            btn.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
            btn.setForeground(color);
            btn.setBorder(BorderFactory.createCompoundBorder(
                new RoundBorder(color, 8),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)
            ));
        }
        return btn;
    }

    // Helper class for rounded borders
    private static class RoundBorder extends javax.swing.border.AbstractBorder {
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
