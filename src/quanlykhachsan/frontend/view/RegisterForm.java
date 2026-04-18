package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import quanlykhachsan.frontend.api.AuthAPI;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import quanlykhachsan.frontend.utils.HttpUtil;

public class RegisterForm extends JFrame {

    private JTextField txtUsername, txtFullName, txtEmail, txtPhone, txtIdentity, txtAddress;
    private JPasswordField txtPassword;
    private JButton btnRegister, btnBack;
    private JFrame loginFrame;

    private static final Color PRIMARY = new Color(13, 148, 136); // Teal
    private static final Color BG = new Color(248, 250, 252);

    public RegisterForm(JFrame loginFrame) {
        this.loginFrame = loginFrame;
        setTitle("Đăng ký thành viên");
        setSize(450, 650);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setUndecorated(true);
        setShape(new RoundRectangle2D.Double(0, 0, 450, 650, 20, 20));

        initUI();
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new LineBorder(new Color(226, 232, 240), 1));
        root.setBackground(BG);

        // Header
        JPanel header = new JPanel(new GridLayout(2, 1));
        header.setBackground(PRIMARY);
        header.setBorder(new EmptyBorder(25, 30, 25, 30));
        
        JLabel title = new JLabel("Tham gia cùng chúng tôi");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Color.WHITE);
        header.add(title);

        JLabel sub = new JLabel("Đăng ký tài khoản khách hàng để nhận nhiều ưu đãi");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(new Color(204, 251, 241));
        header.add(sub);
        root.add(header, BorderLayout.NORTH);

        // Form
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(20, 40, 20, 40));

        addInputField(form, "Tên đăng nhập:", txtUsername = new JTextField());
        addInputField(form, "Mật khẩu:", txtPassword = new JPasswordField());
        addInputField(form, "Họ và tên:", txtFullName = new JTextField());
        addInputField(form, "Email:", txtEmail = new JTextField());
        addInputField(form, "Số điện thoại:", txtPhone = new JTextField());
        addInputField(form, "CCCD/Passport:", txtIdentity = new JTextField());
        addInputField(form, "Địa chỉ:", txtAddress = new JTextField());

        root.add(form, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(0, 0, 30, 0));

        btnBack = new JButton("Quay lại");
        btnBack.addActionListener(e -> {
            dispose();
            loginFrame.setVisible(true);
        });
        footer.add(btnBack);

        btnRegister = new JButton("Đăng ký ngay");
        btnRegister.setBackground(PRIMARY);
        btnRegister.setForeground(Color.WHITE);
        btnRegister.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnRegister.setPreferredSize(new Dimension(150, 40));
        btnRegister.addActionListener(e -> handleRegister());
        footer.add(btnRegister);

        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void addInputField(JPanel p, String label, JTextField field) {
        JLabel l = new JLabel(label);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l.setAlignmentX(LEFT_ALIGNMENT);
        p.add(l);
        p.add(Box.createVerticalStrut(5));
        
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        field.setAlignmentX(LEFT_ALIGNMENT);
        p.add(field);
        p.add(Box.createVerticalStrut(12));
    }

    private void handleRegister() {
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword()).trim();
        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập tên đăng nhập và mật khẩu");
            return;
        }

        JsonObject req = new JsonObject();
        req.addProperty("username", username);
        req.addProperty("password", password);
        req.addProperty("fullName", txtFullName.getText());
        req.addProperty("email", txtEmail.getText());
        req.addProperty("phone", txtPhone.getText());
        req.addProperty("identityCard", txtIdentity.getText());
        req.addProperty("address", txtAddress.getText());

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return HttpUtil.sendPost("/users/register", new Gson().toJson(req));
            }

            @Override
            protected void done() {
                try {
                    String json = get();
                    JsonObject res = new Gson().fromJson(json, JsonObject.class);
                    if (res != null && "success".equals(res.get("status").getAsString())) {
                        JOptionPane.showMessageDialog(RegisterForm.this, "Đăng ký thành công! Hãy đăng nhập.");
                        dispose();
                        loginFrame.setVisible(true);
                    } else {
                        JOptionPane.showMessageDialog(RegisterForm.this, "Lỗi: " + (res != null ? res.get("message").getAsString() : "Unknown"));
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(RegisterForm.this, "Lỗi kết nối: " + e.getMessage());
                }
            }
        }.execute();
    }
}
