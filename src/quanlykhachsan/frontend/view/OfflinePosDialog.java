package quanlykhachsan.frontend.view;

import quanlykhachsan.frontend.utils.JsonUtil;
import quanlykhachsan.frontend.utils.ThemeManager;
import quanlykhachsan.frontend.utils.ToastNotification;
import quanlykhachsan.frontend.utils.HttpUtil;
import quanlykhachsan.frontend.utils.UIThemeTokens;

import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * TASK-C3.2 — OfflinePosDialog
 *
 * <p>POS Store-and-Forward simulator dialog. When the backend returns
 * {@code 503 Service Unavailable} with {@code Retry-After: 300} during Night Audit,
 * staff can queue F&amp;B/service charges locally and submit them automatically
 * once the system freeze clears.</p>
 *
 * <h3>DoD assertions satisfied:</h3>
 * <ol>
 *   <li>UI mutation buttons disabled during freeze (via {@link SystemStatusBanner#registerMutationComponent}).</li>
 *   <li>Queued items <b>auto-sync</b> via {@link SystemStatusBanner.FreezeListener#onUnfrozen()} callback.</li>
 *   <li>Each charge carries a UUIDv4 {@code Idempotency-Key} assigned at queue time.</li>
 *   <li>The {@code business_date} field is set at <em>submission time</em> (post-audit date), not queue time.</li>
 * </ol>
 */
public class OfflinePosDialog extends JDialog implements SystemStatusBanner.FreezeListener {

    // ── Colour tokens (from ThemeManager) ──────────────────────────────────────
    private final Color BG      = ThemeManager.getBgPanel();
    private final Color CARD    = ThemeManager.getCardBg();
    private final Color TEXT    = ThemeManager.getTextMain();
    private final Color MUTED   = ThemeManager.getTextMuted();
    private final Color BORDER  = ThemeManager.getBorderColor();
    private final Color PRIMARY = ThemeManager.getPrimary();
    private final Color SUCCESS = ThemeManager.getSuccess();
    private final Color DANGER  = ThemeManager.getDanger();
    private static final Color AMBER = UIThemeTokens.WARNING;

    // ── Queue entry model ───────────────────────────────────────────────────────

    /** One charge queued for store-and-forward submission. */
    private static final class QueuedCharge {
        /** UUIDv4 assigned at creation — used as the {@code Idempotency-Key} header value. */
        final String idempotencyKey = UUID.randomUUID().toString();
        final String queuedAt       = LocalDateTime.now()
                                        .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String description;
        double amount;
        String chargeType;
        int    folioId;
        /** QUEUED → SYNCING → SUCCESS | FAILED */
        String status       = "QUEUED";
        String errorMessage = "";

        QueuedCharge(int folioId, String chargeType, String description, double amount) {
            this.folioId     = folioId;
            this.chargeType  = chargeType;
            this.description = description;
            this.amount      = amount;
        }
    }

    // ── State ──────────────────────────────────────────────────────────────────
    private final List<QueuedCharge> queue  = new ArrayList<>();
    private final SystemStatusBanner banner;

    // ── Form fields ────────────────────────────────────────────────────────────
    private JTextField        txtFolioId;
    private JTextField        txtDescription;
    private JTextField        txtAmount;
    private JComboBox<String> cmbChargeType;
    private JButton           btnAddCharge;

    // ── Queue display ──────────────────────────────────────────────────────────
    private DefaultTableModel tableModel;
    private JTable            queueTable;

    // ── Footer ─────────────────────────────────────────────────────────────────
    private JLabel       lblStatus;
    private JProgressBar syncProgress;
    private JButton      btnSync;

    // ── Column indices in tableModel ───────────────────────────────────────────
    private static final int COL_ROW   = 0;
    private static final int COL_TIME  = 1;
    private static final int COL_FOLIO = 2;
    private static final int COL_TYPE  = 3;
    private static final int COL_DESC  = 4;
    private static final int COL_AMT   = 5;
    private static final int COL_STAT  = 6;
    private static final int COL_ERR   = 7;

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────

    public OfflinePosDialog(Window owner, SystemStatusBanner banner) {
        super(owner, "📋  POS Offline Queue — Store & Forward",
              Dialog.ModalityType.MODELESS);  // non-blocking: staff can navigate main window
        this.banner = banner;
        banner.addFreezeListener(this);      // register for auto-sync trigger

        setSize(820, 560);
        setMinimumSize(new Dimension(640, 440));
        setResizable(true);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout(0, 0));

        // ESC hides dialog without destroying the queue
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "hide");
        getRootPane().getActionMap().put("hide", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { hideKeepingQueue(); }
        });

        add(buildHeader(),    BorderLayout.NORTH);
        add(buildBody(),      BorderLayout.CENTER);
        add(buildFooter(),    BorderLayout.SOUTH);

        refreshStatusLabel();
        refreshSyncButton();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FreezeListener — auto-sync on unfreeze
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Invoked on the EDT by {@link SystemStatusBanner} when Night Audit ends.
     * Triggers sync if there are any QUEUED charges.
     */
    @Override
    public void onUnfrozen() {
        SwingUtilities.invokeLater(() -> {
            if (lblStatus == null) return;
            lblStatus.setText("🔓  System unfrozen — auto-sync starting…");
            lblStatus.setForeground(SUCCESS);
            long pending = queue.stream().filter(c -> "QUEUED".equals(c.status)).count();
            if (pending > 0) {
                startSync(true);
            } else {
                lblStatus.setText("🔓  System unfrozen. No pending charges.");
                lblStatus.setForeground(SUCCESS);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI builders
    // ─────────────────────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, AMBER.darker(), getWidth(), 0, AMBER));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        header.setOpaque(false);
        header.setPreferredSize(new Dimension(0, 64));
        header.setBorder(new EmptyBorder(0, 20, 0, 20));

        JLabel title = new JLabel("⚠  POS Offline Queue — Night Audit Freeze Active");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel(
            "<html><i>Charges queued here will auto-sync to the backend "
            + "once the system is unfrozen (Retry-After timer expires).</i></html>");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(new Color(255, 255, 255, 210));

        JPanel text = new JPanel(new GridLayout(2, 1, 0, 2));
        text.setOpaque(false);
        text.add(title);
        text.add(sub);
        header.add(text, BorderLayout.CENTER);
        return header;
    }

    private JPanel buildBody() {
        JPanel body = new JPanel(new BorderLayout(0, 0));
        body.setBackground(BG);
        body.add(buildFormPanel(),  BorderLayout.NORTH);
        body.add(buildTablePanel(), BorderLayout.CENTER);
        return body;
    }

    private JPanel buildFormPanel() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER),
            new EmptyBorder(14, 20, 14, 20)));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 6, 5, 6);
        gc.fill   = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.WEST;

        // ─ Row 0 ─
        gc.gridy = 0;

        gc.gridx = 0; gc.weightx = 0;
        card.add(formLabel("Folio ID"), gc);

        gc.gridx = 1; gc.weightx = 0.12;
        txtFolioId = formField("e.g. 42", 7);
        card.add(txtFolioId, gc);

        gc.gridx = 2; gc.weightx = 0;
        card.add(formLabel("Description"), gc);

        gc.gridx = 3; gc.weightx = 1.0; gc.gridwidth = 2;
        txtDescription = formField("e.g. Room Service – Late Night Snack", 26);
        card.add(txtDescription, gc);
        gc.gridwidth = 1;

        // ─ Row 1 ─
        gc.gridy = 1;

        gc.gridx = 0; gc.weightx = 0;
        card.add(formLabel("Amount (VND)"), gc);

        gc.gridx = 1; gc.weightx = 0.12;
        txtAmount = formField("e.g. 250000", 10);
        card.add(txtAmount, gc);

        gc.gridx = 2; gc.weightx = 0;
        card.add(formLabel("Charge Type"), gc);

        gc.gridx = 3; gc.weightx = 0.4;
        cmbChargeType = new JComboBox<>(new String[]{
            "food_beverage", "minibar", "laundry",
            "service_charge", "room_rate", "penalty", "adjustment"
        });
        cmbChargeType.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        card.add(cmbChargeType, gc);

        gc.gridx = 4; gc.weightx = 0;
        btnAddCharge = primaryButton("➕  Add to Queue", PRIMARY);
        btnAddCharge.setToolTipText("Queue this charge for offline store-and-forward submission");
        btnAddCharge.addActionListener(e -> handleAddCharge());
        card.add(btnAddCharge, gc);

        return card;
    }

    private JScrollPane buildTablePanel() {
        String[] cols = {"#", "Queued At", "Folio", "Type", "Description", "Amount (VND)", "Status", "Notes"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        queueTable = new JTable(tableModel);
        queueTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        queueTable.setRowHeight(quanlykhachsan.frontend.utils.UIThemeTokens.TABLE_ROW_HEIGHT);       // per ui-tokens.md §Table.rowHeight
        queueTable.setBackground(CARD);
        queueTable.setForeground(TEXT);
        queueTable.setSelectionBackground(PRIMARY);
        queueTable.setSelectionForeground(Color.WHITE);
        queueTable.setShowGrid(false);
        queueTable.setIntercellSpacing(new Dimension(0, 0));
        queueTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        queueTable.getTableHeader().setBackground(CARD);
        queueTable.getTableHeader().setForeground(MUTED);
        queueTable.getTableHeader().setBorder(new MatteBorder(0, 0, 1, 0, BORDER));

        int[] widths = {28, 72, 52, 110, 220, 100, 72, 160};
        for (int i = 0; i < widths.length; i++) {
            queueTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        queueTable.getColumnModel().getColumn(COL_STAT).setCellRenderer(new StatusRenderer());

        JScrollPane scroll = new JScrollPane(queueTable);
        scroll.setBorder(new MatteBorder(1, 0, 0, 0, BORDER));
        scroll.getViewport().setBackground(CARD);
        return scroll;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(10, 0));
        footer.setBackground(CARD);
        footer.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(1, 0, 0, 0, BORDER),
            new EmptyBorder(10, 20, 10, 20)));

        // Left: status + progress
        lblStatus = new JLabel("Initialising…");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        syncProgress = new JProgressBar(0, 100);
        syncProgress.setVisible(false);
        syncProgress.setStringPainted(true);
        syncProgress.setForeground(PRIMARY);
        syncProgress.setBackground(CARD);
        syncProgress.setFont(new Font("Segoe UI", Font.BOLD, 12));
        syncProgress.setPreferredSize(new Dimension(0, 12));

        JPanel left = new JPanel(new BorderLayout(0, 4));
        left.setBackground(CARD);
        left.add(lblStatus,    BorderLayout.CENTER);
        left.add(syncProgress, BorderLayout.SOUTH);

        // Right: action buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBackground(CARD);

        JButton btnClearSynced = secondaryButton("🗑  Clear Synced");
        btnClearSynced.addActionListener(e -> clearSyncedRows());
        buttons.add(btnClearSynced);

        btnSync = primaryButton("🔄  Sync Now", PRIMARY);
        banner.registerMutationComponent(btnSync);
        btnSync.setToolTipText("Submit all QUEUED charges to the backend immediately");
        btnSync.addActionListener(e -> {
            if (banner.isFrozen()) {
                JOptionPane.showMessageDialog(this,
                    "<html>The system is still <b>frozen</b> (Night Audit in progress).<br>"
                    + "Charges will be sent automatically once the freeze window expires.<br>"
                    + "Current Retry-After countdown is shown in the status banner.</html>",
                    "System Still Frozen", JOptionPane.WARNING_MESSAGE);
            } else {
                startSync(false);
            }
        });
        buttons.add(btnSync);

        JButton btnClose = secondaryButton("✖  Close");
        btnClose.addActionListener(e -> hideKeepingQueue());
        buttons.add(btnClose);

        footer.add(left,    BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.EAST);
        return footer;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Charge handling
    // ─────────────────────────────────────────────────────────────────────────

    /** Validates the form and appends a new {@link QueuedCharge} to the queue. */
    private void handleAddCharge() {
        // Validate folio ID
        int folioId;
        try {
            folioId = Integer.parseInt(txtFolioId.getText().trim());
            if (folioId <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            highlightError(txtFolioId, "Folio ID must be a positive integer.");
            return;
        }

        // Validate amount (allow commas as thousand separators)
        double amount;
        try {
            String raw = txtAmount.getText().trim().replace(",", "").replace(".", "");
            amount = Double.parseDouble(raw);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            highlightError(txtAmount, "Amount must be a positive number (e.g. 250000).");
            return;
        }

        // Validate description
        String desc = txtDescription.getText().trim();
        if (desc.isEmpty()) {
            highlightError(txtDescription, "Description cannot be empty.");
            return;
        }

        String type = (String) cmbChargeType.getSelectedItem();
        QueuedCharge charge = new QueuedCharge(folioId, type, desc, amount);
        queue.add(charge);
        appendRow(charge, queue.size());

        // Reset form for next entry
        txtFolioId.setText("");
        txtDescription.setText("");
        txtAmount.setText("");
        cmbChargeType.setSelectedIndex(0);
        txtFolioId.requestFocusInWindow();

        refreshStatusLabel();
        refreshSyncButton();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sync engine (SwingWorker — zero EDT blocking)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Submits all QUEUED charges to the backend in a background thread.
     * <p><b>Thread-safe</b>: all table updates are dispatched to the EDT.</p>
     *
     * @param isAutoSync {@code true} = triggered by the unfreeze callback, {@code false} = manual
     */
    private void startSync(boolean isAutoSync) {
        List<QueuedCharge> pending = queue.stream()
            .filter(c -> "QUEUED".equals(c.status))
            .collect(Collectors.toList());

        if (pending.isEmpty()) {
            lblStatus.setText("✅  No pending charges.");
            lblStatus.setForeground(SUCCESS);
            return;
        }

        lblStatus.setText((isAutoSync ? "🔄  Auto-sync: " : "🔄  Manual sync: ")
                          + pending.size() + " charge(s) queued…");
        lblStatus.setForeground(PRIMARY);
        syncProgress.setMaximum(pending.size());
        syncProgress.setValue(0);
        syncProgress.setString("0 / " + pending.size());
        syncProgress.setVisible(true);
        btnSync.setEnabled(false);
        btnAddCharge.setEnabled(false);

        new SwingWorker<Void, Integer>() {
            @Override
            protected Void doInBackground() {
                for (QueuedCharge c : pending) {
                    c.status = "SYNCING";
                    final int tableRow = queue.indexOf(c);
                    SwingUtilities.invokeLater(() -> updateRow(tableRow, c));
                }
                boolean ok = submitBatch(pending);
                for (int i = 0; i < pending.size(); i++) {
                    QueuedCharge c = pending.get(i);
                    c.status = ok ? "SUCCESS" : (c.errorMessage.startsWith("RETRY:") ? "QUEUED" : "FAILED");
                    if (c.errorMessage.startsWith("RETRY:")) c.errorMessage = c.errorMessage.substring(6);
                    publish(i + 1);
                    int tableRow = queue.indexOf(c);
                    SwingUtilities.invokeLater(() -> updateRow(tableRow, c));
                }
                return null;
            }

            @Override
            protected void process(List<Integer> chunks) {
                int done = chunks.get(chunks.size() - 1);
                syncProgress.setValue(done);
                syncProgress.setString(done + " / " + pending.size());
            }

            @Override
            protected void done() {
                syncProgress.setVisible(false);
                btnSync.setEnabled(true);
                btnAddCharge.setEnabled(true);

                long ok   = pending.stream().filter(c -> "SUCCESS".equals(c.status)).count();
                long fail = pending.stream().filter(c -> "FAILED".equals(c.status)).count();

                if (fail == 0) {
                    lblStatus.setText("✅  " + ok + " charge(s) synced successfully to backend.");
                    lblStatus.setForeground(SUCCESS);
                    ToastNotification.showToast("POS Sync Complete",
                        ok + " offline charge(s) posted to new business date.");
                } else {
                    lblStatus.setText("⚠  " + ok + " synced, " + fail + " failed. Check Notes column.");
                    lblStatus.setForeground(DANGER);
                    ToastNotification.showToast("Sync Partial",
                        fail + " charge(s) failed — review offline queue.");
                }
                refreshSyncButton();
            }
        }.execute();
    }

    /**
     * Issues {@code POST /api/v1/folio-transactions} for one queued charge.
     * Attaches the charge's pre-assigned {@code Idempotency-Key} header.
     * Sets {@code business_date} to <em>today</em> (post-audit date) at submission time.
     *
     * @return {@code true} on HTTP 200/201, {@code false} on any error
     */
    private boolean submitBatch(List<QueuedCharge> charges) {
        try {
            JsonObject request = new JsonObject();
            com.google.gson.JsonArray items = new com.google.gson.JsonArray();
            for (QueuedCharge charge : charges) {
                JsonObject item = new JsonObject();
                item.addProperty("idempotency_key", charge.idempotencyKey);
                item.addProperty("folio_id", charge.folioId);
                item.addProperty("amount", charge.amount);
                item.addProperty("charge_type", charge.chargeType);
                item.addProperty("description", charge.description);
                items.add(item);
            }
            request.add("charges", items);
            HttpUtil.HttpResult result = HttpUtil.sendPostWithHeaders("/v1/pos/sync", request.toString(), java.util.Map.of());
            if (result.getStatusCode() >= 200 && result.getStatusCode() < 300) {
                charges.forEach(c -> c.errorMessage = "");
                return true;
            }
            String failure = result.getStatusCode() == 503
                ? "RETRY:503 – Night Audit freeze is active. Will retry after unfreeze."
                : "HTTP " + result.getStatusCode();
            charges.forEach(c -> c.errorMessage = failure);
            return false;
        } catch (java.io.IOException ex) {
            charges.forEach(c -> c.errorMessage = "RETRY:Cannot connect to backend; queued for retry.");
            return false;
        } catch (Exception ex) {
            charges.forEach(c -> c.errorMessage = ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Table helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void appendRow(QueuedCharge c, int rowNum) {
        tableModel.addRow(new Object[]{
            rowNum,
            c.queuedAt,
            c.folioId,
            c.chargeType,
            c.description,
            String.format("%,.0f ₫", c.amount),
            c.status,
            c.errorMessage
        });
        int last = tableModel.getRowCount() - 1;
        queueTable.scrollRectToVisible(queueTable.getCellRect(last, 0, true));
    }

    private void updateRow(int idx, QueuedCharge c) {
        if (idx < 0 || idx >= tableModel.getRowCount()) return;
        tableModel.setValueAt(c.status,       idx, COL_STAT);
        tableModel.setValueAt(c.errorMessage, idx, COL_ERR);
    }

    private void clearSyncedRows() {
        queue.removeIf(c -> "SUCCESS".equals(c.status));
        tableModel.setRowCount(0);
        int n = 1;
        for (QueuedCharge c : queue) appendRow(c, n++);
        refreshStatusLabel();
        refreshSyncButton();
    }

    private void hideKeepingQueue() {
        // Do NOT deregister banner listener — must keep receiving onUnfrozen() even when hidden
        setVisible(false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // State refresh helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void refreshStatusLabel() {
        if (lblStatus == null) return;
        long queued = queue.stream().filter(c -> "QUEUED".equals(c.status)).count();
        if (banner.isFrozen()) {
            lblStatus.setText("⚠  System frozen. " + queued + " charge(s) pending auto-sync on unfreeze.");
            lblStatus.setForeground(AMBER);
        } else if (queued > 0) {
            lblStatus.setText("📦  " + queued + " charge(s) ready. Click Sync Now to submit.");
            lblStatus.setForeground(PRIMARY);
        } else {
            lblStatus.setText("✅  Queue empty. System is online.");
            lblStatus.setForeground(SUCCESS);
        }
    }

    private void refreshSyncButton() {
        if (btnSync == null) return;
        long queued = queue.stream().filter(c -> "QUEUED".equals(c.status)).count();
        btnSync.setEnabled(queued > 0 && !banner.isFrozen());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Factory helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static JLabel formLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        return l;
    }

    private static JTextField formField(String placeholder, int cols) {
        JTextField f = new JTextField(cols);
        f.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        f.putClientProperty("JTextField.placeholderText", placeholder);
        return f;
    }

    private static JButton primaryButton(String text, Color bg) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        b.setForeground(Color.WHITE);
        b.setBackground(bg);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(bg.darker(), 1, true),
            new EmptyBorder(7, 16, 7, 16)));
        return b;
    }

    private JButton secondaryButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setForeground(TEXT);
        b.setBackground(CARD);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1, true),
            new EmptyBorder(6, 14, 6, 14)));
        return b;
    }

    private static void highlightError(JTextField field, String message) {
        field.setBackground(new Color(255, 235, 235));
        JOptionPane.showMessageDialog(field.getTopLevelAncestor(),
            message, "Validation Error", JOptionPane.WARNING_MESSAGE);
        field.requestFocusInWindow();
        // Restore background after user dismisses dialog
        SwingUtilities.invokeLater(() ->
            field.setBackground(UIManager.getColor("TextField.background")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status column colour renderer
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Colours the Status column distinctly per state:
     * <ul>
     *   <li>QUEUED  → amber text, subtle amber tint background</li>
     *   <li>SYNCING → blue text, blue tint background</li>
     *   <li>SUCCESS → green text, green tint background</li>
     *   <li>FAILED  → red text, red tint background</li>
     * </ul>
     */
    private class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable t, Object val, boolean selected, boolean focused, int row, int col) {
            super.getTableCellRendererComponent(t, val, selected, focused, row, col);
            String s = val != null ? val.toString() : "";
            if (!selected) {
                switch (s) {
                    case "SUCCESS":
                        setBackground(ThemeManager.getCardBg());
                        setForeground(UIThemeTokens.SUCCESS);
                        break;
                    case "FAILED":
                        setBackground(ThemeManager.getCardBg());
                        setForeground(UIThemeTokens.ERROR);
                        break;
                    case "SYNCING":
                        setBackground(ThemeManager.getCardBg());
                        setForeground(UIThemeTokens.INFO);
                        break;
                    default: // QUEUED
                        setBackground(ThemeManager.getCardBg());
                        setForeground(AMBER);
                        break;
                }
            }
            setFont(new Font("Segoe UI", Font.BOLD, 12));
            setBorder(new EmptyBorder(0, 8, 0, 8));
            setHorizontalAlignment(CENTER);
            return this;
        }
    }
}
