package quanlykhachsan.frontend.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import quanlykhachsan.backend.model.User;
import quanlykhachsan.frontend.api.UserAPI;

public class ProfileForm extends JPanel {

    private User currentUser;
    
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
        setBorder(BorderFactory.createEmptyBorder(20, 50, 20, 50));

        JLabel titleLabel = new JLabel("Hồ Sơ & Bảo Mật", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        add(titleLabel, BorderLayout.NORTH);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        // 1. PANEL THÔNG TIN CÁ NHÂN
        JPanel profilePanel = new JPanel(new GridBagLayout());
        profilePanel.setBorder(BorderFactory.createTitledBorder("Cập nhật thông tin cá nhân"));
        GridBagConstraints gbcProf = new GridBagConstraints();
        gbcProf.insets = new Insets(10, 10, 10, 10);
        gbcProf.fill = GridBagConstraints.HORIZONTAL;
        gbcProf.weightx = 1.0;

        txtUsername = new JTextField(30);
        txtUsername.setText(currentUser.getUsername());
        txtUsername.setEditable(false); // Username ko thể đổi
        
        txtFullName = new JTextField(30);
        if (currentUser.getFullName() != null) txtFullName.setText(currentUser.getFullName());
        
        txtEmail = new JTextField(30);
        if (currentUser.getEmail() != null) txtEmail.setText(currentUser.getEmail());
        
        txtPhone = new JTextField(30);
        if (currentUser.getPhone() != null) txtPhone.setText(currentUser.getPhone());

        addFormField(profilePanel, gbcProf, "Tài khoản đăng nhập:", txtUsername, 0);
        addFormField(profilePanel, gbcProf, "Họ và chữ lót:", txtFullName, 1);
        addFormField(profilePanel, gbcProf, "Email liên hệ:", txtEmail, 2);
        addFormField(profilePanel, gbcProf, "Số điện thoại:", txtPhone, 3);

        JPanel profileBtnBox = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnEditProfile = new JButton("Chỉnh sửa thông tin");
        btnSaveProfile = new JButton("Lưu thay đổi hồ sơ");
        btnSaveProfile.setBackground(new Color(46, 204, 113));
        btnSaveProfile.setForeground(Color.WHITE);
        
        btnEditProfile.addActionListener(e -> setProfileEditMode(true));
        btnSaveProfile.addActionListener(this::handleUpdateProfile);
        
        profileBtnBox.add(btnEditProfile);
        profileBtnBox.add(btnSaveProfile);
        
        gbcProf.gridx = 0; gbcProf.gridy = 4; gbcProf.gridwidth = 2;
        profilePanel.add(profileBtnBox, gbcProf);

        // 2. PANEL ĐỔI MẬT KHẨU
        JPanel passwordPanel = new JPanel(new GridBagLayout());
        passwordPanel.setBorder(BorderFactory.createTitledBorder("Đổi mật khẩu bảo mật"));
        GridBagConstraints gbcPass = new GridBagConstraints();
        gbcPass.insets = new Insets(10, 10, 10, 10);
        gbcPass.fill = GridBagConstraints.HORIZONTAL;
        gbcPass.weightx = 1.0;

        txtOldPassword = new JPasswordField(30);
        txtNewPassword = new JPasswordField(30);
        txtConfirmPassword = new JPasswordField(30);

        addFormField(passwordPanel, gbcPass, "Mật khẩu hiện tại:", txtOldPassword, 0);
        addFormField(passwordPanel, gbcPass, "Mật khẩu mới:", txtNewPassword, 1);
        addFormField(passwordPanel, gbcPass, "Xác nhận mật khẩu:", txtConfirmPassword, 2);

        JPanel passBtnBox = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnEditPassword = new JButton("Đổi mật khẩu");
        btnSavePassword = new JButton("Lưu mật khẩu");
        btnSavePassword.setBackground(new Color(231, 76, 60));
        btnSavePassword.setForeground(Color.WHITE);

        btnEditPassword.addActionListener(e -> setPasswordEditMode(true));
        btnSavePassword.addActionListener(this::handleChangePassword);

        passBtnBox.add(btnEditPassword);
        passBtnBox.add(btnSavePassword);
        
        gbcPass.gridx = 0; gbcPass.gridy = 3; gbcPass.gridwidth = 2;
        passwordPanel.add(passBtnBox, gbcPass);

        // Canh giữa form
        JPanel centerWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JPanel fixedWidthContainer = new JPanel();
        fixedWidthContainer.setLayout(new BoxLayout(fixedWidthContainer, BoxLayout.Y_AXIS));
        fixedWidthContainer.setPreferredSize(new Dimension(600, 600)); // Cố định chiều rộng 600px cho thon gọn trang

        profilePanel.setMaximumSize(new Dimension(600, 300));
        passwordPanel.setMaximumSize(new Dimension(600, 300));
        
        fixedWidthContainer.add(profilePanel);
        fixedWidthContainer.add(Box.createRigidArea(new Dimension(0, 20))); // Khoảng trắng giữa 2 bảng
        fixedWidthContainer.add(passwordPanel);
        
        centerWrapper.add(fixedWidthContainer);
        
        // Wrap content in a JScrollPane to handle window resizing
        JScrollPane scrollPane = new JScrollPane(centerWrapper);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16); // Smoother scrolling
        
        add(scrollPane, BorderLayout.CENTER);
    }

    private void addFormField(JPanel panel, GridBagConstraints gbc, String labelText, JComponent field, int row) {
        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = row;
        gbc.weightx = 0.3;
        panel.add(new JLabel(labelText), gbc);
        
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
}
