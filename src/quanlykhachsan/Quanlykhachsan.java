package quanlykhachsan;

import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.UIManager;
import java.awt.Font;
import java.awt.Insets;
import quanlykhachsan.frontend.utils.UIThemeTokens;
import quanlykhachsan.frontend.utils.ThemeManager;

public class Quanlykhachsan {

    public static void main(String[] args) {
        // Initialize Aurelia 5★ Dark Luxury FlatLaf theme directly in main()
        initializeTheme();

        java.awt.EventQueue.invokeLater(() -> {
            var splash = new quanlykhachsan.frontend.view.SplashScreen();
            splash.setVisible(true);
            new javax.swing.SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    quanlykhachsan.frontend.api.ApiClient.getInstance();
                    return null;
                }
                @Override
                protected void done() {
                    try {
                        get();
                        new quanlykhachsan.frontend.view.LoginForm().setVisible(true);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } catch (java.util.concurrent.ExecutionException ex) {
                        javax.swing.JOptionPane.showMessageDialog(null, "Không thể khởi động ứng dụng. Kiểm tra cấu hình và thử lại.");
                    } finally {
                        splash.dispose();
                    }
                }
            }.execute();
        });
    }

    public static void initializeTheme() {
        System.setProperty("flatlaf.useWindowDecorations", "true");

        // 1. Initialize FlatMacDarkLaf with fallback to FlatDarkLaf
        try {
            FlatMacDarkLaf.setup();
        } catch (Throwable t) {
            FlatDarkLaf.setup();
        }

        // 2. Set dark mode and apply global UI tokens
        ThemeManager.setDarkMode(true);
        ThemeManager.applyGlobalUI();

        // 3. Precise Aurelia Dark Luxury theme parameters:
        // Arcs: Button.arc = 12, Component.arc = 10, TextComponent.arc = 10, ProgressBar.arc = 10
        UIManager.put("Button.arc", UIThemeTokens.BUTTON_ARC);               // 12
        UIManager.put("Component.arc", UIThemeTokens.COMPONENT_ARC);         // 10
        UIManager.put("TextComponent.arc", UIThemeTokens.TEXT_ARC);          // 10
        UIManager.put("ProgressBar.arc", UIThemeTokens.PROGRESS_ARC);        // 10

        // Dimensions: JTable rowHeight = 36
        UIManager.put("Table.rowHeight", UIThemeTokens.TABLE_ROW_HEIGHT);    // 36

        // Colors: Slate Dark (#0F172A), Panel (#1E293B), Metallic Gold (#F59E0B)
        UIManager.put("Panel.background", UIThemeTokens.PANEL_DARK);
        UIManager.put("Panel.foreground", UIThemeTokens.LIGHT_TEXT);
        UIManager.put("Application.background", UIThemeTokens.SLATE_DARK_BG);
        UIManager.put("Label.foreground", UIThemeTokens.LIGHT_TEXT);
        UIManager.put("Accent.color", UIThemeTokens.GOLD_ACCENT);
        UIManager.put("Component.accentColor", UIThemeTokens.GOLD_ACCENT);
        UIManager.put("Button.focusedBorderColor", UIThemeTokens.GOLD_ACCENT);
        UIManager.put("Button.default.borderColor", UIThemeTokens.GOLD_ACCENT);

        UIManager.put("Button.margin", new Insets(6, 16, 6, 16));
        UIManager.put("defaultFont", new Font("Segoe UI", Font.PLAIN, 14));
    }
}
