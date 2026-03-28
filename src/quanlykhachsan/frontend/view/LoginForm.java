package quanlykhachsan.frontend.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import quanlykhachsan.frontend.api.AuthAPI;
import quanlykhachsan.backend.model.User;
import quanlykhachsan.frontend.MainUI;

public class LoginForm extends JFrame {

    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JButton btnLogin;

    public LoginForm() {
        setTitle("Đăng nhập Hệ thống");
        setSize(350, 250);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
        
        initUI();
    }

    private void initUI() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Username
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Username:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 0;
        txtUsername = new JTextField(15);
        panel.add(txtUsername, gbc);

        // Password
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Password:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 1;
        txtPassword = new JPasswordField(15);
        panel.add(txtPassword, gbc);

        // Login Button
        gbc.gridx = 1; gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST;
        btnLogin = new JButton("Đăng nhập");
        panel.add(btnLogin, gbc);

        // --- BẮT ĐẦU VÙNG DEBUG (XÓA KHI HOÀN THÀNH DỰ ÁN) ---
        JPanel debugPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton btnAdmin = new JButton("Login Admin (Debug)");
        JButton btnStaff = new JButton("Login Staff (Debug)");
        
        btnAdmin.setFont(new Font("Arial", Font.ITALIC, 10));
        btnStaff.setFont(new Font("Arial", Font.ITALIC, 10));
        
        btnAdmin.addActionListener(e -> {
            txtUsername.setText("admin_main");
            txtPassword.setText("hashed_pass_123");
            handleLogin();
        });
        
        btnStaff.addActionListener(e -> {
            txtUsername.setText("staff_01");
            txtPassword.setText("hashed_pass_456");
            handleLogin();
        });

        debugPanel.add(btnAdmin);
        debugPanel.add(btnStaff);

        add(panel, BorderLayout.CENTER);
        add(debugPanel, BorderLayout.SOUTH);
        // --- KẾT THÚC VÙNG DEBUG ---
        btnLogin.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleLogin();
            }
        });
    }

    private void handleLogin() {
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ thông tin!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnLogin.setEnabled(false);
        try {
            // Call API
            User user = AuthAPI.login(username, password);
            
            if (user != null) {
                JOptionPane.showMessageDialog(this, "Đăng nhập thành công!\nXin chào " + user.getUsername(), "Thành công", JOptionPane.INFORMATION_MESSAGE);
                this.dispose();
                
                // Mở MainUI
                SwingUtilities.invokeLater(() -> {
                    MainUI mainUI = new MainUI(user);
                    mainUI.setVisible(true);
                });
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Lỗi đăng nhập", JOptionPane.ERROR_MESSAGE);
            btnLogin.setEnabled(true);
        }
    }
}
