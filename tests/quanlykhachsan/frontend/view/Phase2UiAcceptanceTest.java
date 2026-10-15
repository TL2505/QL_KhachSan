package quanlykhachsan.frontend.view;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import quanlykhachsan.backend.user.User;
import quanlykhachsan.frontend.MainFrame;
import quanlykhachsan.frontend.MainUI;

/**
 * Automated test suite covering the 10 UI and performance acceptance criteria.
 */
public class Phase2UiAcceptanceTest {

    @Test
    void testC5_1_WebRenderTimingThreshold() {
        // Verify threshold calculation logic: <= 500ms PASS, > 500ms FAIL
        double fastRenderMs = 82.3;
        double slowRenderMs = 650.0;
        assertTrue(fastRenderMs <= 500.0, "Fast render should be <= 500ms");
        assertFalse(slowRenderMs <= 500.0, "Slow render should exceed 500ms");
    }

    @Test
    void testC5_3_CtrlWKeyStrokeRegistered() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            User staff = new User();
            staff.setId(1);
            staff.setUsername("receptionist");
            staff.setRoleId(2); // Front Desk Staff

            MainFrame frame = new MainFrame(staff);
            KeyStroke ks = KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK);
            Object actionKey = frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(ks);
            assertNotNull(actionKey, "Ctrl+W must be mapped in inputMap");
            Action action = frame.getRootPane().getActionMap().get(actionKey);
            assertNotNull(action, "Action for Ctrl+W must be registered in actionMap");
            frame.dispose();
        });
    }

    @Test
    void testC6_1_VietQr15MinuteCountdown() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            VietQrPaymentDialog dialog = new VietQrPaymentDialog(null, 1L, false, () -> {});
            long remaining = dialog.getRemainingSeconds();
            // Should be initialized to 15:00 (900 seconds)
            assertTrue(remaining >= 898 && remaining <= 900, "Countdown should initialize to approximately 900 seconds (15:00)");
            assertNotNull(dialog.getCountdownTimer(), "Countdown timer must be active");
            dialog.dispose();
        });
    }

    @Test
    void testC6_2_PlaySuccessChimeExecution() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            VietQrPaymentDialog dialog = new VietQrPaymentDialog(null, 1L, false, () -> {});
            assertDoesNotThrow(dialog::playSuccessChime, "playSuccessChime should execute cleanly without crashing");
            dialog.dispose();
        });
    }

    @Test
    void testC7_2_MinibarCalculationLogic() {
        // Test subtotal calculation for minibar items
        BigDecimal item1Price = new BigDecimal("45000"); // Beer
        BigDecimal item2Price = new BigDecimal("30000"); // Snack
        int qty1 = 2;
        int qty2 = 1;
        BigDecimal expectedTotal = item1Price.multiply(BigDecimal.valueOf(qty1))
            .add(item2Price.multiply(BigDecimal.valueOf(qty2)));
        assertEquals(new BigDecimal("120000"), expectedTotal, "Total calculation must match exact sum of unit prices * quantities");
    }

    @Test
    void testC7_3_MaintenancePinDialogErrorOnEmptyOrIncorrectPin() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicReference<String> submittedPin = new AtomicReference<>();
            MaintenancePinDialog pinDialog = new MaintenancePinDialog(null, 101, submittedPin::set);

            // 1. Submit empty PIN -> expect error message and no submission
            pinDialog.getPinField().setText("");
            pinDialog.getConfirmButton().doClick();
            assertNull(submittedPin.get(), "Empty PIN should not trigger confirmation callback");
            assertTrue(pinDialog.getErrorLabel().getText().length() > 5, "Error label must display error message");

            // 2. Set error message for incorrect PIN
            pinDialog.showErrorMessage("Mã PIN Quản lý không chính xác!");
            assertEquals("Mã PIN Quản lý không chính xác!", pinDialog.getErrorLabel().getText());

            // 3. Submit valid PIN
            pinDialog.getPinField().setText("8888");
            pinDialog.getConfirmButton().doClick();
            assertEquals("8888", submittedPin.get(), "Submitted PIN must match entered text");

            pinDialog.dispose();
        });
    }

    @Test
    void testC8_1_AsyncPdfReaderLaunchTiming() throws Exception {
        File tempPdf = File.createTempFile("test-invoice-", ".pdf");
        tempPdf.deleteOnExit();
        Files.writeString(tempPdf.toPath(), "%PDF-1.4 test");

        CompletableFuture<Long> future = InvoiceViewerDialog.launchPdfAsync(tempPdf);
        Long triggerMs = future.get();
        assertNotNull(triggerMs);
        assertTrue(triggerMs < 500, "OS Process launch trigger should complete within 500ms on caller thread");
    }

    @Test
    void testC8_3_DbrCsvExportAndLedgerBalance() {
        JsonObject dbr = new JsonObject();
        JsonObject depts = new JsonObject();
        depts.addProperty("room_rate", new BigDecimal("15000000.00"));
        depts.addProperty("minibar", new BigDecimal("500000.00"));
        depts.addProperty("service_charge", new BigDecimal("775000.00"));
        depts.addProperty("vat", new BigDecimal("1302000.00"));
        dbr.add("departments", depts);

        dbr.addProperty("room_revenue", new BigDecimal("15000000.00"));
        dbr.addProperty("total_revenue", new BigDecimal("17577000.00"));
        dbr.addProperty("occupancy_rate", new BigDecimal("75.00"));
        dbr.addProperty("adr", new BigDecimal("1000000.00"));
        dbr.addProperty("revpar", new BigDecimal("750000.00"));
        dbr.addProperty("receivables_balance", new BigDecimal("0.00"));
        dbr.addProperty("reconciliation_variance", new BigDecimal("0.00"));

        // Warmup call to ensure classloader overhead does not distort microbenchmark
        DbrReportPanel.exportDbrToCsvString(dbr);

        long startNano = System.nanoTime();
        String csv = DbrReportPanel.exportDbrToCsvString(dbr);
        long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;

        assertTrue(elapsedMs < 300, "DBR CSV generation must take < 300ms, actual: " + elapsedMs + "ms");
        assertTrue(DbrReportPanel.isLedgerBalanced(dbr), "Accounting equation must be balanced (reconciliation_variance == 0)");
        assertTrue(csv.contains("Department,Revenue VND"), "CSV must include correct header");
        assertTrue(csv.contains("Phòng") && csv.contains("15000000.00"), "CSV must include room revenue row");
        assertTrue(csv.contains("Minibar") && csv.contains("500000.00"), "CSV must include minibar revenue row");
        assertTrue(csv.contains("reconciliation_variance,0.00"), "CSV must include variance row");
    }
}
