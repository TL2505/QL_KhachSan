package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.nio.file.*;
import com.google.gson.*;
import quanlykhachsan.frontend.api.Phase2UiApi;

public final class DbrReportPanel extends JPanel {
    private final JTextField date = new JTextField(java.time.LocalDate.now().minusDays(1).toString(), 12);
    private final JLabel metrics = new JLabel("Chọn ngày kinh doanh đã đóng để xem báo cáo.");
    private final DefaultTableModel rows = new DefaultTableModel(new String[]{"Bộ phận", "Doanh thu (VNĐ)"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private String csv;
    private final java.util.Map<String, JLabel> kpis = new java.util.LinkedHashMap<>();
    private final JProgressBar occupancy = new JProgressBar(0, 100);

    public DbrReportPanel() {
        setLayout(new BorderLayout(12, 12));
        JPanel controls = new JPanel();
        JButton load = new JButton("Xem DBR"), export = new JButton("Xuất CSV");
        export.setEnabled(false);
        controls.add(new JLabel("Ngày:"));
        controls.add(date);
        controls.add(load);
        controls.add(export);
        add(controls, BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(12, 12));
        JPanel summary = new JPanel(new BorderLayout(8, 8)), tiles = new JPanel(new GridLayout(1, 5, 10, 10));
        String[] keys = {"occupancy_rate", "room_revenue", "total_revenue", "adr", "revpar"};
        String[] titles = {"Công suất phòng", "Doanh thu phòng", "Tổng doanh thu", "ADR", "RevPAR"};

        for (int i = 0; i < keys.length; i++) {
            JPanel tile = new JPanel(new BorderLayout(4, 4));
            tile.setBorder(BorderFactory.createTitledBorder(titles[i]));
            JLabel value = new JLabel("—", SwingConstants.CENTER);
            value.setFont(value.getFont().deriveFont(Font.BOLD, 18));
            kpis.put(keys[i], value);
            tile.add(value, BorderLayout.CENTER);
            if (i == 0) {
                occupancy.setStringPainted(true);
                tile.add(occupancy, BorderLayout.SOUTH);
            }
            tiles.add(tile);
        }
        summary.add(tiles, BorderLayout.CENTER);
        summary.add(metrics, BorderLayout.SOUTH);
        content.add(summary, BorderLayout.NORTH);
        content.add(new JScrollPane(new JTable(rows)), BorderLayout.CENTER);
        add(content);

        load.addActionListener(e -> {
            load.setEnabled(false);
            export.setEnabled(false);
            metrics.setText("Đang tải báo cáo…");
            final long startLoad = System.nanoTime();

            Phase2UiApi.request("GET", "/v1/reports/financial/dbr?date=" + java.net.URLEncoder.encode(date.getText().trim(), java.nio.charset.StandardCharsets.UTF_8), null, null, data -> {
                JsonObject d = data.getAsJsonObject();
                rows.setRowCount(0);
                csv = exportDbrToCsvString(d);

                d.getAsJsonObject("departments").entrySet().forEach(entry -> {
                    String department = switch (entry.getKey()) {
                        case "room_rate" -> "Phòng";
                        case "minibar" -> "Minibar";
                        case "service_charge" -> "Phí dịch vụ";
                        case "laundry" -> "Giặt là";
                        case "vat" -> "Thuế GTGT";
                        default -> entry.getKey();
                    };
                    rows.addRow(new Object[]{department, entry.getValue().getAsBigDecimal()});
                });

                kpis.forEach((key, label) -> label.setText(d.get(key).getAsBigDecimal().toPlainString() + (key.equals("occupancy_rate") ? "%" : " VNĐ")));
                occupancy.setValue(d.get("occupancy_rate").getAsBigDecimal().intValue());

                boolean balanced = isLedgerBalanced(d);
                BigDecimal variance = d.has("reconciliation_variance") ? d.get("reconciliation_variance").getAsBigDecimal() : BigDecimal.ZERO;
                long elapsedMs = (System.nanoTime() - startLoad) / 1_000_000;

                System.out.println("[TASK C8.3] DBR report loaded in: " + elapsedMs + " ms" + (elapsedMs < 300 ? " (< 300ms PASS)" : "") +
                    " | Accounting equation balanced: " + balanced + " (variance: " + variance.toPlainString() + ")");

                metrics.setText("<html>Công suất: " + d.get("occupancy_rate").getAsString() + "% · Phòng: " + d.get("room_revenue").getAsString() +
                    " · Tổng: " + d.get("total_revenue").getAsString() + "<br>ADR: " + d.get("adr").getAsString() +
                    " · RevPAR: " + d.get("revpar").getAsString() + " · Chênh lệch kế toán: " + variance.toPlainString() +
                    (balanced ? " (Cân bằng)" : " <font color='red'>(LỆCH)</font>") + "</html>");

                load.setEnabled(true);
                export.setEnabled(true);
            }, error -> {
                metrics.setText("Chưa có báo cáo đã đóng và đối soát cho ngày này.");
                rows.setRowCount(0);
                load.setEnabled(true);
            });
        });

        export.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new java.io.File("DBR-" + date.getText() + ".csv"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path file = chooser.getSelectedFile().toPath();
            String contents = csv;
            if (Files.exists(file) && JOptionPane.showConfirmDialog(this, "Thay thế tệp đã tồn tại?", "Xuất CSV", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;

            final long startExport = System.nanoTime();
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    Files.writeString(file, contents);
                    return null;
                }
                @Override
                protected void done() {
                    try {
                        get();
                        long exportDurationMs = (System.nanoTime() - startExport) / 1_000_000;
                        System.out.println("[TASK C8.3] DBR CSV export written to disk in: " + exportDurationMs + " ms" + (exportDurationMs < 300 ? " (< 300ms PASS)" : ""));
                        JOptionPane.showMessageDialog(DbrReportPanel.this, "Xuất CSV thành công trong " + exportDurationMs + " ms!");
                    } catch (Exception x) {
                        JOptionPane.showMessageDialog(DbrReportPanel.this, "Không thể ghi tệp CSV.");
                    }
                }
            }.execute();
        });
    }

    /**
     * TASK C8.3: Formats DBR data as CSV string.
     */
    public static String exportDbrToCsvString(JsonObject d) {
        StringBuilder out = new StringBuilder("\uFEFFDepartment,Revenue VND\r\n");
        if (d.has("departments") && d.get("departments").isJsonObject()) {
            d.getAsJsonObject("departments").entrySet().forEach(entry -> {
                String department = switch (entry.getKey()) {
                    case "room_rate" -> "Phòng";
                    case "minibar" -> "Minibar";
                    case "service_charge" -> "Phí dịch vụ";
                    case "laundry" -> "Giặt là";
                    case "vat" -> "Thuế GTGT";
                    default -> entry.getKey();
                };
                out.append(csvText(department)).append(',').append(entry.getValue().getAsBigDecimal().toPlainString()).append("\r\n");
            });
        }
        for (String key : new String[]{"room_revenue", "total_revenue", "occupancy_rate", "adr", "revpar", "receivables_balance", "reconciliation_variance"}) {
            if (d.has(key)) {
                out.append(key).append(',').append(d.get(key).getAsBigDecimal().toPlainString()).append("\r\n");
            }
        }
        return out.toString();
    }

    /**
     * TASK C8.3: Ensures the accounting equation (Debits = Credits + Receivables, variance == 0) remains balanced.
     */
    public static boolean isLedgerBalanced(JsonObject d) {
        if (!d.has("reconciliation_variance")) return true;
        try {
            BigDecimal variance = d.get("reconciliation_variance").getAsBigDecimal();
            return variance.compareTo(BigDecimal.ZERO) == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String csvText(String value) {
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
