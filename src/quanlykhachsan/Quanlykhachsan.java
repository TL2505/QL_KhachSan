package quanlykhachsan;

import com.formdev.flatlaf.FlatIntelliJLaf;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Quanlykhachsan {

    public static void main(String[] args) {
        // Kích hoạt FlatLaf - giao diện hiện đại cho Java Swing
        try {
            FlatIntelliJLaf.setup();
            // Tùy chỉnh màu sắc chủ đạo
            javax.swing.UIManager.put("Component.arc", 8);
            javax.swing.UIManager.put("Button.arc", 8);
            javax.swing.UIManager.put("TextComponent.arc", 8);
            javax.swing.UIManager.put("Button.margin", new java.awt.Insets(6, 16, 6, 16));
            javax.swing.UIManager.put("defaultFont", new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 14));
        } catch (Exception ex) {
            Logger.getLogger(Quanlykhachsan.class.getName()).log(Level.SEVERE, "FlatLaf init failed", ex);
        }

        java.awt.EventQueue.invokeLater(() -> {
            new quanlykhachsan.frontend.view.LoginForm().setVisible(true);
        });
    }
}
