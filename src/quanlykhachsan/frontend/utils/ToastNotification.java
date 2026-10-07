package quanlykhachsan.frontend.utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;

public class ToastNotification extends JWindow {

    private String message;
    private final int TOAST_WIDTH = 350;
    private final int TOAST_HEIGHT = 80;
    private float opacity = 0.0f;
    private Timer fadeInTimer, fadeOutTimer;

    public ToastNotification(String title, String message, Color bgColor) {
        this.message = message;
        
        setSize(TOAST_WIDTH, TOAST_HEIGHT);
        setAlwaysOnTop(true);
        setBackground(new Color(0, 0, 0, 0)); // Transparent background for rounded corners
        
        // Căn góc phải dưới màn hình
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int x = screenSize.width - TOAST_WIDTH - 20;
        int y = screenSize.height - TOAST_HEIGHT - 60; // Tránh taskbar
        setLocation(x, y);
        setShape(new RoundRectangle2D.Double(0, 0, TOAST_WIDTH, TOAST_HEIGHT, 20, 20));

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(bgColor);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTitle.setForeground(Color.WHITE);
        
        JLabel lblMsg = new JLabel("<html>" + message + "</html>");
        lblMsg.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblMsg.setForeground(new Color(241, 245, 249));

        panel.add(lblTitle, BorderLayout.NORTH);
        panel.add(lblMsg, BorderLayout.CENTER);

        add(panel);
    }

    public static void showToast(String title, String message) {
        // Màu xanh lục nổi bật
        Color bgColor = new Color(16, 185, 129, 230); // Lục bảo, có độ trong suốt 90%
        ToastNotification toast = new ToastNotification(title, message, bgColor);
        toast.animate();
    }

    private void animate() {
        setOpacity(opacity);
        setVisible(true);

        // Hiệu ứng mờ dần (Fade In)
        fadeInTimer = new Timer(20, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                opacity += 0.05f;
                if (opacity >= 1.0f) {
                    opacity = 1.0f;
                    setOpacity(opacity);
                    fadeInTimer.stop();
                    startHoldTimer();
                } else {
                    setOpacity(opacity);
                }
            }
        });
        fadeInTimer.start();
    }

    private void startHoldTimer() {
        // Đợi 4 giây rồi tắt
        Timer holdTimer = new Timer(4000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startFadeOut();
            }
        });
        holdTimer.setRepeats(false);
        holdTimer.start();
    }

    private void startFadeOut() {
        // Hiệu ứng mờ đi (Fade Out)
        fadeOutTimer = new Timer(20, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                opacity -= 0.05f;
                if (opacity <= 0.0f) {
                    opacity = 0.0f;
                    setOpacity(opacity);
                    fadeOutTimer.stop();
                    dispose(); // Xóa cửa sổ khỏi bộ nhớ
                } else {
                    setOpacity(opacity);
                }
            }
        });
        fadeOutTimer.start();
    }
}
