package quanlykhachsan.frontend;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import quanlykhachsan.backend.user.User;
import quanlykhachsan.frontend.view.WalkInWizardDialog;

/**
 * MainFrame - Primary frame representation for Hotel Management System.
 * Registers Ctrl+W shortcut and performance timing for Walk-in wizard & room grid updates.
 */
public class MainFrame extends MainUI {

    public MainFrame(User user) {
        super(user);
        registerWalkInShortcut();
    }

    public void registerWalkInShortcut() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK), "walk-in-main-frame");
        getRootPane().getActionMap().put("walk-in-main-frame", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final long keyPressNano = System.nanoTime();
                new WalkInWizardDialog(MainFrame.this, () -> {
                    long elapsedMs = (System.nanoTime() - keyPressNano) / 1_000_000;
                    System.out.println("[TASK C5.3] Walk-in completed and room data fully loaded into room grid in: " + elapsedMs + " ms");
                }).setVisible(true);
            }
        });
    }
}
