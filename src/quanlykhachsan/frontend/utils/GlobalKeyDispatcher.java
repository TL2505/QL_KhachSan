package quanlykhachsan.frontend.utils;

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;

/** Per-window global staff shortcuts with deterministic install/uninstall lifecycle. */
public final class GlobalKeyDispatcher implements KeyEventDispatcher, AutoCloseable {
    private final Window owner;
    private final Map<Integer, Runnable> actions = new ConcurrentHashMap<>();
    private boolean installed;

    public GlobalKeyDispatcher(Window owner) { this.owner = Objects.requireNonNull(owner, "owner"); }
    public void bind(int keyCode, Runnable action) { actions.put(keyCode, Objects.requireNonNull(action, "action")); }
    public void install() {
        if (!installed) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
            installed = true;
        }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getID() != KeyEvent.KEY_PRESSED || !owner.isShowing()) return false;
        Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (!belongsToOwner(active)) return false;
        if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
            if (active instanceof JDialog dialog && dialog.isModal()) {
                dialog.dispose();
                return true;
            }
            return false;
        }
        Runnable action = actions.get(event.getKeyCode());
        if (action == null) return false;
        SwingUtilities.invokeLater(action);
        return true;
    }
    private boolean belongsToOwner(Window active) {
        for (Window window = active; window != null; window = window.getOwner()) {
            if (window == owner) return true;
        }
        return false;
    }
    @Override public void close() {
        if (installed) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
            installed = false;
        }
        actions.clear();
    }
}
