package quanlykhachsan.frontend.view;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Objects;
import java.util.function.Consumer;
import quanlykhachsan.frontend.utils.ThemeManager;

/** Search field for filtering the staff room grid by room, guest, or booking ID. */
public final class QuickFilterBar extends JPanel {
    private static final int DEBOUNCE_MILLIS = 300;

    private final JTextField searchField = new JTextField(28);
    private final JButton clearButton = new JButton("Clear");
    private final Timer debounceTimer;
    private Consumer<String> filterListener = ignored -> { };

    public QuickFilterBar() {
        super(new BorderLayout(8, 0));
        setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        setBackground(ThemeManager.getCardBg());

        JLabel label = new JLabel("Search rooms");
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        label.setForeground(ThemeManager.getTextMain());
        searchField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        searchField.setBackground(ThemeManager.getCardBg());
        searchField.setForeground(ThemeManager.getTextMain());
        searchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeManager.getBorderColor()),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        searchField.setToolTipText("Room number, guest name, or booking ID (F4)");
        searchField.getAccessibleContext().setAccessibleName("Room, guest, or booking search");
        searchField.setPreferredSize(new Dimension(280, 32));

        JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        inputRow.setOpaque(false);
        inputRow.add(label);
        inputRow.add(searchField);
        inputRow.add(clearButton);
        add(inputRow, BorderLayout.WEST);

        debounceTimer = new Timer(DEBOUNCE_MILLIS, event -> filterListener.accept(searchField.getText().trim()));
        debounceTimer.setRepeats(false);
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() { debounceTimer.restart(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { changed(); }
        });
        clearButton.addActionListener(event -> clear());
        searchField.addActionListener(event -> {
            debounceTimer.stop();
            filterListener.accept(searchField.getText().trim());
        });
    }

    public void setFilterListener(Consumer<String> listener) {
        filterListener = Objects.requireNonNull(listener, "listener");
    }

    public void focusSearch() {
        Runnable focus = () -> {
            searchField.requestFocusInWindow();
            searchField.selectAll();
        };
        if (SwingUtilities.isEventDispatchThread()) focus.run();
        else SwingUtilities.invokeLater(focus);
    }

    public void clear() {
        searchField.setText("");
        debounceTimer.stop();
        filterListener.accept("");
    }
}
