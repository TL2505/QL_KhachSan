package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import quanlykhachsan.frontend.utils.UIThemeTokens;

/**
 * TASK-C3.2 — SystemStatusBanner
 *
 * <p>Persistent amber warning banner displayed at the top of the main application window
 * when the PMS enters "Frozen" state during Night Audit (00:00–00:05).</p>
 *
 * <h3>Behaviour contract:</h3>
 * <ul>
 *   <li>Renders amber gradient bar: {@code "⚠  Night Audit in progress (00:00–00:05). Mutations temporarily locked."}</li>
 *   <li>Live countdown timer ticks down the {@code Retry-After} seconds received in the 503 response.</li>
 *   <li>Exposes {@link #setFrozen(boolean, int)} so callers can activate/deactivate from any thread safely.</li>
 *   <li>Fires registered {@link FreezeListener#onUnfrozen()} callbacks when the freeze window expires,
 *       allowing the OfflinePosDialog to trigger auto-sync.</li>
 *   <li>Zero EDT freeze: all timers run on {@link javax.swing.Timer} (EDT-safe).</li>
 * </ul>
 *
 * <h3>Integration:</h3>
 * <pre>
 *   systemStatusBanner = new SystemStatusBanner();
 *   add(systemStatusBanner, BorderLayout.NORTH);  // below existing header
 *   // On 503 response with Retry-After: 300 →
 *   systemStatusBanner.setFrozen(true, 300);
 * </pre>
 */
public class SystemStatusBanner extends JPanel {

    // ─── Colour tokens ────────────────────────────────────────────────────────
    /** Amber shared warning token. */
    private static final Color AMBER_DARK   = UIThemeTokens.WARNING;
    /** Slightly lighter amber for gradient end */
    private static final Color AMBER_LIGHT  = UIThemeTokens.WARNING.brighter();
    private static final Color TEXT_WHITE   = Color.WHITE;

    // ─── Freeze state (volatile for cross-thread visibility) ─────────────────
    private volatile boolean frozen = false;
    private int retryAfterSeconds  = 300;
    private int secondsRemaining   = 0;

    // ─── UI components ───────────────────────────────────────────────────────
    private final JLabel  lblIcon;
    private final JLabel  lblMessage;
    private final JLabel  lblCountdown;
    private final JButton btnOpenOfflineQueue;
    private final Timer   countdownTimer;
    private OfflinePosDialog offlinePosDialog;

    // ─── Listener registry ───────────────────────────────────────────────────
    /** Notified when the freeze window expires or is manually cleared. */
    public interface FreezeListener {
        void onUnfrozen();
    }
    private final List<FreezeListener> freezeListeners = new ArrayList<>();

    /** Components to disable during system freeze (registered by caller). */
    private final Map<JComponent, Boolean> mutationComponents = new IdentityHashMap<>();

    // ─── Constructor ──────────────────────────────────────────────────────────

    public SystemStatusBanner() {
        setLayout(new BorderLayout(0, 0));
        setOpaque(false);
        setVisible(false); // hidden by default; shows when frozen
        setBorder(new EmptyBorder(0, 0, 0, 0));

        // ── Left icon ──────────────────────────────────────────────────────
        lblIcon = new JLabel("  ⚠  ");
        lblIcon.setFont(new Font("Segoe UI Emoji", Font.BOLD, 16));
        lblIcon.setForeground(TEXT_WHITE);
        lblIcon.setBorder(new EmptyBorder(0, 12, 0, 0));

        // ── Centre message ─────────────────────────────────────────────────
        lblMessage = new JLabel(
            "Night Audit in progress (00:00 - 00:05). Mutations temporarily locked.");
        lblMessage.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblMessage.setForeground(TEXT_WHITE);

        // ── Countdown label ────────────────────────────────────────────────
        lblCountdown = new JLabel("Retry in: 300s");
        lblCountdown.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblCountdown.setForeground(TEXT_WHITE);
        lblCountdown.setBorder(new EmptyBorder(0, 12, 0, 0));

        // ── Offline queue button ───────────────────────────────────────────
        btnOpenOfflineQueue = createOfflineQueueButton();

        // ── Assemble inner panel (rendered over gradient) ──────────────────
        JPanel inner = new JPanel(new BorderLayout(8, 0));
        inner.setOpaque(false);
        inner.setBorder(new EmptyBorder(7, 4, 7, 12));

        JPanel leftSide = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftSide.setOpaque(false);
        leftSide.add(lblIcon);
        leftSide.add(lblMessage);
        leftSide.add(lblCountdown);

        inner.add(leftSide, BorderLayout.CENTER);
        inner.add(btnOpenOfflineQueue, BorderLayout.EAST);

        add(inner, BorderLayout.CENTER);

        // ── Countdown timer (1 tick / second, runs on EDT) ────────────────
        countdownTimer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!frozen) {
                    countdownTimer.stop();
                    return;
                }
                secondsRemaining = Math.max(0, secondsRemaining - 1);
                updateCountdownLabel();
                if (secondsRemaining <= 0) {
                    countdownTimer.stop();
                    // Auto-clear freeze when countdown reaches zero
                    setFrozen(false, 0);
                }
            }
        });
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Activate or deactivate the system freeze banner.
     * <b>Thread-safe:</b> dispatches UI updates to the EDT.
     *
     * @param freeze         {@code true} = frozen (Night Audit in progress)
     * @param retryAfterSecs Seconds from the {@code Retry-After} header (503 response).
     *                       Ignored when {@code freeze == false}.
     */
    public void setFrozen(boolean freeze, int retryAfterSecs) {
        SwingUtilities.invokeLater(() -> {
            boolean wasFrozen = this.frozen;
            this.frozen = freeze;
            if (freeze) {
                this.retryAfterSeconds = retryAfterSecs > 0 ? retryAfterSecs : 300;
                this.secondsRemaining  = this.retryAfterSeconds;
                updateCountdownLabel();
                setVisible(true);
                setMutationComponentsEnabled(false);
                if (!countdownTimer.isRunning()) {
                    countdownTimer.start();
                }
            } else {
                setVisible(false);
                countdownTimer.stop();
                setMutationComponentsEnabled(true);
                if (wasFrozen) fireFreezeListeners();
            }
            revalidate();
            repaint();
        });
    }

    /**
     * Returns {@code true} if the system is currently in a frozen state.
     */
    public boolean isFrozen() {
        return frozen;
    }

    /**
     * Register a component whose interaction should be suppressed during freeze.
     * Typically: Save, Book, Check-in, Check-out, Payment buttons.
     *
     * @param component the mutable action button/panel to disable during freeze
     */
    public void registerMutationComponent(JComponent component) {
        if (component == null) return;
        Runnable register = () -> {
            mutationComponents.putIfAbsent(component, component.isEnabled());
            if (frozen) component.setEnabled(false);
        };
        if (SwingUtilities.isEventDispatchThread()) register.run();
        else SwingUtilities.invokeLater(register);
    }

    /**
     * Register a listener notified when the freeze window clears,
     * so callers (e.g. {@link OfflinePosDialog}) can trigger auto-sync.
     */
    public void addFreezeListener(FreezeListener listener) {
        if (listener == null) return;
        Runnable register = () -> { if (!freezeListeners.contains(listener)) freezeListeners.add(listener); };
        if (SwingUtilities.isEventDispatchThread()) register.run(); else SwingUtilities.invokeLater(register);
    }

    public void removeFreezeListener(FreezeListener listener) {
        Runnable remove = () -> freezeListeners.remove(listener);
        if (SwingUtilities.isEventDispatchThread()) remove.run(); else SwingUtilities.invokeLater(remove);
    }

    /** Activate the freeze UI after an HTTP 503 response. */
    public void handleServiceUnavailable(int retryAfterSecs) {
        setFrozen(true, retryAfterSecs);
    }

    /**
     * Convenience factory: return the singleton instance used by this
     * banner's "Open Offline Queue" button, pre-bound to this banner.
     */
    public void bindOfflinePosDialog(Window ownerWindow) {
        btnOpenOfflineQueue.addActionListener(e -> {
            if (offlinePosDialog == null || !offlinePosDialog.isDisplayable()) {
                offlinePosDialog = new OfflinePosDialog(ownerWindow, this);
            }
            offlinePosDialog.setLocationRelativeTo(ownerWindow);
            offlinePosDialog.setVisible(true);
        });
    }

    // ─── Painting ─────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        if (!isVisible()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Animated pulsing gradient: shift hue slightly every repaint for a
        // subtle "alive" feel while frozen.  Driven by System time, not a
        // separate timer, so it adds zero thread overhead.
        float pulse = (float) (0.08 * Math.sin(System.currentTimeMillis() / 700.0));
        Color c1 = blend(AMBER_DARK,  Color.BLACK, 0.10f + pulse);
        Color c2 = blend(AMBER_LIGHT, Color.WHITE, 0.05f + pulse);

        GradientPaint gp = new GradientPaint(0, 0, c1, getWidth(), 0, c2);
        g2.setPaint(gp);
        g2.fillRect(0, 0, getWidth(), getHeight());

        // Bottom separator line
        g2.setColor(new Color(0, 0, 0, 40));
        g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
        g2.dispose();

        super.paintComponent(g); // paint children
    }

    @Override
    public Dimension getPreferredSize() {
        return frozen ? new Dimension(super.getPreferredSize().width, 40) : new Dimension(0, 0);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private JButton createOfflineQueueButton() {
        JButton btn = new JButton("📋  Offline Queue") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 40));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(Color.WHITE);
        btn.setBackground(new Color(0, 0, 0, 0));
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(255, 255, 255, 120), 1, true),
            BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFocusPainted(false);
        return btn;
    }

    private void updateCountdownLabel() {
        int mins = secondsRemaining / 60;
        int secs = secondsRemaining % 60;
        lblCountdown.setText(String.format("  Retry in: %02d:%02d", mins, secs));
        // Pulse: turn red in the last 30 seconds
        lblCountdown.setForeground(secondsRemaining <= 30
            ? UIThemeTokens.WARNING.brighter()
            : TEXT_WHITE);
        repaint(); // trigger gradient re-draw for pulse animation
    }

    private void setMutationComponentsEnabled(boolean enabled) {
        for (Map.Entry<JComponent, Boolean> entry : mutationComponents.entrySet()) {
            JComponent component = entry.getKey();
            component.setEnabled(enabled ? entry.getValue() : false);
        }
    }

    private void fireFreezeListeners() {
        for (FreezeListener l : freezeListeners) {
            try { l.onUnfrozen(); } catch (Exception ex) {
                System.err.println("[SystemStatusBanner] FreezeListener threw: " + ex.getMessage());
            }
        }
    }

    private static Color blend(Color c, Color target, float ratio) {
        ratio = Math.max(0f, Math.min(1f, ratio));
        int r = (int) (c.getRed()   * (1 - ratio) + target.getRed()   * ratio);
        int g = (int) (c.getGreen() * (1 - ratio) + target.getGreen() * ratio);
        int b = (int) (c.getBlue()  * (1 - ratio) + target.getBlue()  * ratio);
        return new Color(
            Math.max(0, Math.min(255, r)),
            Math.max(0, Math.min(255, g)),
            Math.max(0, Math.min(255, b)));
    }
}
