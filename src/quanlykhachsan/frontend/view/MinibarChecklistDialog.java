package quanlykhachsan.frontend.view;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;
import com.google.gson.*;
import quanlykhachsan.frontend.api.Phase2UiApi;

public final class MinibarChecklistDialog extends JDialog {
    private final JPanel items = new JPanel(new GridLayout(0, 2, 12, 12));
    private final JLabel total = new JLabel("Đang tải kho Minibar…");
    private final JButton submit = new JButton("Ghi nợ vào Folio");
    private final java.util.List<Line> lines = new ArrayList<>();
    private String key, lastBody;

    public MinibarChecklistDialog(Window owner, int roomId, Runnable refresh) {
        super(owner, "Kiểm kho Minibar", ModalityType.APPLICATION_MODAL);
        items.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        add(new JScrollPane(items), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(total);
        bottom.add(submit);
        add(bottom, BorderLayout.SOUTH);

        setSize(560, 360);
        setLocationRelativeTo(owner);
        submit.setEnabled(false);

        Phase2UiApi.request("GET", "/v1/rooms/" + roomId + "/minibar", null, null, data -> {
            if (!isDisplayable()) return;
            for (JsonElement element : data.getAsJsonArray()) {
                JsonObject item = element.getAsJsonObject();
                int stock = item.get("current_stock").getAsInt();
                JSpinner quantity = new JSpinner(new SpinnerNumberModel(0, 0, stock, 1));
                items.add(new JLabel(item.get("name").getAsString()));
                items.add(quantity);
                lines.add(new Line(item.get("id").getAsInt(), item.get("unit_price").getAsBigDecimal(), quantity));
                // TASK C7.2: Automatically recalculate total whenever quantities change
                quantity.addChangeListener(e -> updateTotal());
            }
            updateTotal();
            items.revalidate();
            items.repaint();
        }, error -> {
            if (isDisplayable()) total.setText("Chưa thể tải kho. Vui lòng thử lại.");
        });

        // TASK C7.2: Post charge directly to sub-folio with Idempotency-Key
        submit.addActionListener(e -> {
            JsonArray selected = new JsonArray();
            for (Line line : lines) {
                int quantity = ((Number) line.quantity().getValue()).intValue();
                if (quantity > 0) {
                    JsonObject item = new JsonObject();
                    item.addProperty("item_id", line.id());
                    item.addProperty("quantity", quantity);
                    selected.add(item);
                }
            }
            if (selected.isEmpty()) return;

            JsonObject body = new JsonObject();
            body.add("items", selected);
            String payload = body.toString();
            if (!payload.equals(lastBody)) {
                key = UUID.randomUUID().toString();
                lastBody = payload;
            }

            submit.setEnabled(false);
            lines.forEach(line -> line.quantity().setEnabled(false));

            final long startNano = System.nanoTime();
            Phase2UiApi.request("POST", "/v1/rooms/" + roomId + "/minibar", payload, key, data -> {
                long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;
                System.out.println("[TASK C7.2] Minibar items total cost calculated and charged to sub-folio in: " + elapsedMs + " ms");
                if (isDisplayable()) {
                    refresh.run();
                    dispose();
                }
            }, error -> {
                if (isDisplayable()) {
                    total.setText("Chưa thể ghi nợ. Kiểm tra kho và thử lại.");
                    submit.setEnabled(true);
                    lines.forEach(line -> line.quantity().setEnabled(true));
                }
            });
        });
    }

    /**
     * TASK C7.2: Automatically calculates total cost of selected minibar items.
     */
    public BigDecimal calculateTotal() {
        BigDecimal amount = BigDecimal.ZERO;
        for (Line line : lines) {
            long qty = ((Number) line.quantity().getValue()).longValue();
            amount = amount.add(line.price().multiply(BigDecimal.valueOf(qty)));
        }
        return amount;
    }

    private void updateTotal() {
        BigDecimal amount = calculateTotal();
        total.setText(NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN")).format(amount));
        submit.setEnabled(amount.signum() > 0);
    }

    public record Line(int id, BigDecimal price, JSpinner quantity) { }
}
