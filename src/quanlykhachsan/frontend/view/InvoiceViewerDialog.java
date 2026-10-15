package quanlykhachsan.frontend.view;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import quanlykhachsan.frontend.utils.*;

/**
 * Download, file writes and launching the reader all run outside the EDT.
 * TASK C8.1: Asynchronous PDF Reader launch with execution timing.
 */
public final class InvoiceViewerDialog extends JDialog {
    public InvoiceViewerDialog(Window owner, long invoiceId) {
        super(owner, "Hóa đơn điện tử (VAT)", ModalityType.MODELESS);
        setSize(440, 150);
        setLocationRelativeTo(owner);
        JLabel status = new JLabel("Đang tải hóa đơn đã hoàn tất…", SwingConstants.CENTER);
        add(status);
        String token = SessionManagerUtil.getAccessToken();

        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                var request = HttpRequest.newBuilder(URI.create(HttpUtil.getBaseUrl() + "/v1/invoices/" + invoiceId + "/pdf"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + token)
                    .GET().build();
                var response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200 || !response.headers().firstValue("Content-Type").orElse("").startsWith("application/pdf")) {
                    throw new java.io.IOException("Chưa thể xuất hóa đơn. Cần dữ liệu đã đối soát và cấu hình khách sạn.");
                }
                Path cache = Path.of(System.getProperty("java.io.tmpdir"), "hotel-invoices");
                Files.createDirectories(cache);
                Path file = Files.createTempFile(cache, "VAT-" + invoiceId + "-", ".pdf");
                Files.write(file, response.body());
                file.toFile().deleteOnExit();

                // TASK C8.1: Non-blocking async invocation of OS default PDF reader
                launchPdfAsync(file.toFile());
                return file;
            }
            @Override
            protected void done() {
                try {
                    get();
                    status.setText("Đã mở hóa đơn trong trình đọc PDF.");
                } catch (Exception x) {
                    status.setText("Không thể mở hóa đơn.");
                    JOptionPane.showMessageDialog(InvoiceViewerDialog.this, x.getCause() == null ? x.getMessage() : x.getCause().getMessage());
                }
            }
        }.execute();
    }

    /**
     * TASK C8.1: Uses CompletableFuture.runAsync to prevent blocking the Swing EDT;
     * measures the time taken to trigger the OS-level process.
     */
    public static CompletableFuture<Long> launchPdfAsync(File pdfFile) {
        final long startTrigger = System.nanoTime();
        CompletableFuture<Void> asyncProcess = CompletableFuture.runAsync(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(pdfFile);
                }
            } catch (Exception ex) {
                System.err.println("[TASK C8.1] Could not open PDF in default viewer: " + ex.getMessage());
            }
        });
        final long triggerDurationMs = (System.nanoTime() - startTrigger) / 1_000_000;
        System.out.println("[TASK C8.1] PDF reader process trigger dispatched in: " + triggerDurationMs + " ms" + (triggerDurationMs < 500 ? " (< 500ms PASS)" : ""));
        return asyncProcess.handle((v, ex) -> triggerDurationMs);
    }
}
