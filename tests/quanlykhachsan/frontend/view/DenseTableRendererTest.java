package quanlykhachsan.frontend.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.awt.Color;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;
import quanlykhachsan.frontend.utils.DenseTableRenderer;

class DenseTableRendererTest {
    @Test
    void usesAlternatingDenseRowColorsAndStatusDotPalette() {
        JTable table = new JTable(2, 1);
        table.setRowHeight(24);
        DenseTableRenderer renderer = new DenseTableRenderer();
        renderer.getTableCellRendererComponent(table, "vacant_clean", false, false, 0, 0);
        assertEquals(24, table.getRowHeight());
        assertEquals(Color.WHITE, renderer.getBackground());
        renderer.getTableCellRendererComponent(table, "out_of_order", false, false, 1, 0);
        assertEquals(new Color(0xF5F5F5), renderer.getBackground());
    }
}
