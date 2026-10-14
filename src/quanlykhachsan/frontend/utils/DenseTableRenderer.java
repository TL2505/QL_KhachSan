package quanlykhachsan.frontend.utils;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

/** Dense 24px table renderer with alternating rows and an optional status dot. */
public final class DenseTableRenderer extends JLabel implements TableCellRenderer {
    private static final Color EVEN = Color.WHITE;
    private static final Color ODD = new Color(0xF5F5F5);
    private Color indicator;

    public DenseTableRenderer() {
        setOpaque(true);
        setHorizontalAlignment(LEFT);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 16, 0, 4));
    }

    @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
            boolean focused, int row, int column) {
        setText(value == null ? "" : value.toString());
        setBackground(selected ? table.getSelectionBackground() : (row % 2 == 0 ? EVEN : ODD));
        setForeground(selected ? table.getSelectionForeground() : table.getForeground());
        setFont(table.getFont());
        indicator = statusColor(value == null ? "" : value.toString());
        setBorder(javax.swing.BorderFactory.createEmptyBorder(0, indicator == null ? 4 : 16, 0, 4));
        return this;
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (indicator != null) {
            graphics.setColor(indicator);
            graphics.fillOval(4, (getHeight() - 8) / 2, 8, 8);
        }
    }

    private static Color statusColor(String value) {
        switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "vacant_clean": case "clean": case "confirmed": return UIThemeTokens.SUCCESS;
            case "vacant_dirty": case "pending": case "chờ nhận phòng": return UIThemeTokens.WARNING;
            case "occupied_clean": case "checked_in": case "đang ở": return UIThemeTokens.INFO;
            case "occupied_dirty": return new Color(0xFF5722);
            case "out_of_order": case "cancelled": case "đã hủy": return UIThemeTokens.ERROR;
            case "reserved": return new Color(0x9C27B0);
            case "đã xác nhận": case "đã trả phòng": return UIThemeTokens.SUCCESS;
            default: return null;
        }
    }
}
