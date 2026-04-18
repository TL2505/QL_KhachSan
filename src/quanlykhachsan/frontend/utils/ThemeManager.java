package quanlykhachsan.frontend.utils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;

public class ThemeManager {
    private static boolean isDarkMode = false;
    private static final List<ThemeListener> listeners = new ArrayList<>();
    private static final String PREF_DARK_MODE = "isDarkMode";
    private static final Preferences prefs = Preferences.userNodeForPackage(ThemeManager.class);

    public interface ThemeListener {
        void onThemeChanged();
    }

    static {
        isDarkMode = prefs.getBoolean(PREF_DARK_MODE, false);
        applyGlobalUI();
    }

    public static void addThemeListener(ThemeListener listener) {
        listeners.add(listener);
    }

    public static void removeThemeListener(ThemeListener listener) {
        listeners.remove(listener);
    }

    public static void setDarkMode(boolean dark) {
        if (isDarkMode != dark) {
            isDarkMode = dark;
            prefs.putBoolean(PREF_DARK_MODE, isDarkMode);
            applyGlobalUI();
            notifyListeners();
        }
    }

    public static void applyGlobalUI() {
        try {
            if (isDarkMode) {
                FlatDarkLaf.setup();
            } else {
                FlatIntelliJLaf.setup();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        javax.swing.UIManager.put("Panel.background", getBgPanel());
        javax.swing.UIManager.put("Label.foreground", getTextMain());
        javax.swing.UIManager.put("TabbedPane.background", getBgPanel());
        javax.swing.UIManager.put("TabbedPane.foreground", getTextMain());
        javax.swing.UIManager.put("TabbedPane.selected", getCardBg());
        javax.swing.UIManager.put("TabbedPane.contentAreaColor", getBgPanel());
        javax.swing.UIManager.put("ScrollPane.background", getBgPanel());
        javax.swing.UIManager.put("Viewport.background", getBgPanel());
        javax.swing.UIManager.put("Table.background", getCardBg());
        javax.swing.UIManager.put("Table.foreground", getTextMain());
        javax.swing.UIManager.put("Button.background", getCardBg());
        javax.swing.UIManager.put("Button.foreground", getTextMain());
        javax.swing.UIManager.put("TextField.background", getCardBg());
        javax.swing.UIManager.put("TextField.foreground", getTextMain());
        javax.swing.UIManager.put("PasswordField.background", getCardBg());
        javax.swing.UIManager.put("PasswordField.foreground", getTextMain());
        javax.swing.UIManager.put("ComboBox.background", getCardBg());
        javax.swing.UIManager.put("ComboBox.foreground", getTextMain());
        javax.swing.UIManager.put("CheckBox.background", getBgPanel());
        javax.swing.UIManager.put("CheckBox.foreground", getTextMain());
        javax.swing.UIManager.put("TableHeader.background", getCardBg());
        javax.swing.UIManager.put("TableHeader.foreground", getTextMain());

        javax.swing.border.Border flatBorder = javax.swing.BorderFactory.createCompoundBorder(
            javax.swing.BorderFactory.createLineBorder(getBorderColor(), 1, true),
            javax.swing.BorderFactory.createEmptyBorder(5, 10, 5, 10)
        );
        javax.swing.UIManager.put("TextField.border", flatBorder);
        javax.swing.UIManager.put("PasswordField.border", flatBorder);
        javax.swing.UIManager.put("ComboBox.border", flatBorder);
        javax.swing.UIManager.put("ScrollPane.border", javax.swing.BorderFactory.createEmptyBorder());
        
        javax.swing.UIManager.put("OptionPane.background", getBgPanel());
        javax.swing.UIManager.put("OptionPane.messageForeground", getTextMain());
        javax.swing.UIManager.put("OptionPane.messageArea", getBgPanel());
        javax.swing.UIManager.put("OptionPane.buttonArea", getBgPanel());
        javax.swing.UIManager.put("OptionPane.foreground", getTextMain());
        javax.swing.UIManager.put("OptionPane.textForeground", getTextMain());
        javax.swing.UIManager.put("Panel.background", getBgPanel());

        // Custom ScrollBar UI globally
        javax.swing.UIManager.put("ScrollBarUI", "quanlykhachsan.frontend.utils.ModernScrollBarUI");
        javax.swing.UIManager.put("ScrollBar.background", getBgPanel());
        javax.swing.UIManager.put("ScrollBar.thumb", getTextMuted());
        
        // FlatLaf TitlePane coloring
        javax.swing.UIManager.put("TitlePane.background", getBgPanel());
        javax.swing.UIManager.put("TitlePane.foreground", getTextMain());
        javax.swing.UIManager.put("TitlePane.inactiveBackground", getBgPanel());
        javax.swing.UIManager.put("TitlePane.inactiveForeground", getTextMuted());
        javax.swing.UIManager.put("TitlePane.buttonHoverBackground", getCardBg());
        javax.swing.UIManager.put("TitlePane.buttonPressedBackground", getCardBg());
        javax.swing.UIManager.put("TitlePane.buttonForeground", getTextMain());
        javax.swing.UIManager.put("TitlePane.unifiedBackground", false); 
        
        // Ensure buttons and icons in TitlePane are visible
        javax.swing.UIManager.put("TitlePane.closeHoverBackground", getDanger());
        javax.swing.UIManager.put("TitlePane.closeHoverForeground", java.awt.Color.WHITE);
        javax.swing.UIManager.put("TitlePane.closePressedBackground", getDanger().darker());
        
        // Hide title text and icon
        javax.swing.UIManager.put("TitlePane.showTitle", false);
        javax.swing.UIManager.put("TitlePane.showIcon", false);

        // Sidebar specific UI keys in UIManager if needed
        javax.swing.UIManager.put("TabbedPane.sidebarBackground", getSidebarBg());

        // Refresh all components
        FlatLaf.updateUI();
    }

    public static boolean isDarkMode() {
        return isDarkMode;
    }

    private static void notifyListeners() {
        for (ThemeListener listener : listeners) {
            listener.onThemeChanged();
        }
    }

    // Dynamic Colors
    public static Color getBgPanel() {
        // Slightly darker off-white for light mode to make cards pop
        return isDarkMode ? new Color(15, 23, 42) : new Color(241, 245, 249);
    }

    public static Color getCardBg() {
        return isDarkMode ? new Color(30, 41, 59) : Color.WHITE;
    }

    public static Color getSidebarBg() {
        // Subtle blue-grey tint for sidebar in light mode
        return isDarkMode ? new Color(15, 23, 42) : new Color(248, 250, 252);
    }

    public static Color getTextMain() {
        return isDarkMode ? new Color(241, 245, 249) : new Color(15, 23, 42);
    }

    public static Color getTextMuted() {
        return isDarkMode ? new Color(148, 163, 184) : new Color(107, 114, 128);
    }

    public static Color getBorderColor() {
        // Slightly more defined border in light mode
        return isDarkMode ? new Color(51, 65, 85) : new Color(211, 218, 227);
    }

    // Fixed branding colors that stay the same in Light/Dark
    public static Color getPrimary() {
        return new Color(37, 99, 235); // Blue
    }

    public static Color getSuccess() {
        return new Color(34, 197, 94); // Green
    }

    public static Color getWarning() {
        return new Color(245, 158, 11); // Amber/Yellow
    }

    public static Color getDanger() {
        return new Color(239, 68, 68); // Red
    }
}
