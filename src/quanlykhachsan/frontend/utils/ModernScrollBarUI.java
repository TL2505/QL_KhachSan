package quanlykhachsan.frontend.utils;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;

public class ModernScrollBarUI extends BasicScrollBarUI {
    
    private final int THUMB_SIZE = 8;
    
    public static ComponentUI createUI(JComponent c) {
        return new ModernScrollBarUI();
    }

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        if (c instanceof JScrollBar) {
            ((JScrollBar) c).setUnitIncrement(16);
        }
    }

    @Override
    protected void configureScrollBarColors() {
        // Theme aware colors
        this.thumbColor = ThemeManager.getTextMuted();
        this.trackColor = ThemeManager.getBgPanel();
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return createZeroButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return createZeroButton();
    }

    private JButton createZeroButton() {
        JButton button = new JButton();
        Dimension zeroDim = new Dimension(0, 0);
        button.setPreferredSize(zeroDim);
        button.setMinimumSize(zeroDim);
        button.setMaximumSize(zeroDim);
        button.setFocusable(false);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        return button;
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        g.setColor(trackColor);
        g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
        if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Darker thumb when dragging/hovering (if supported, else simple tone)
        if (isDragging) {
            g2.setColor(thumbColor.darker());
        } else if (isThumbRollover()) {
            g2.setColor(thumbColor);
        } else {
            g2.setColor(new Color(thumbColor.getRed(), thumbColor.getGreen(), thumbColor.getBlue(), 150));
        }

        int arc = THUMB_SIZE;
        int x = thumbBounds.x + 2;
        int y = thumbBounds.y + 2;
        int width = thumbBounds.width - 4;
        int height = thumbBounds.height - 4;

        if (scrollbar.getOrientation() == JScrollBar.VERTICAL) {
            x = thumbBounds.x + (thumbBounds.width - THUMB_SIZE) / 2;
            width = THUMB_SIZE;
        } else {
            y = thumbBounds.y + (thumbBounds.height - THUMB_SIZE) / 2;
            height = THUMB_SIZE;
        }

        g2.fillRoundRect(x, y, width, height, arc, arc);
        g2.dispose();
    }
}
