package quanlykhachsan.frontend.view;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.sound.sampled.*;
import com.google.gson.*;
import quanlykhachsan.frontend.api.*;
import quanlykhachsan.frontend.utils.SoundNotifier;

public final class VietQrPaymentDialog extends JDialog {
    private final JLabel image = new JLabel("Đang tạo VietQR…", SwingConstants.CENTER);
    private final JLabel details = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel status = new JLabel(" ", SwingConstants.CENTER);
    private Timer countdown, poll;
    private long expires;
    private boolean polling, settled;
    private final String checkoutKey = java.util.UUID.randomUUID().toString();

    public VietQrPaymentDialog(Window owner, long folio, boolean checkout, Runnable refresh) {
        super(owner, "Thanh toán VietQR Napas 247", ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout(12, 12));
        add(image, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new GridLayout(0, 1, 8, 8));
        bottom.setBorder(BorderFactory.createEmptyBorder(12, 12, 20, 12));
        bottom.add(details);
        bottom.add(status);
        add(bottom, BorderLayout.SOUTH);

        setSize(560, 480);
        setLocationRelativeTo(owner);

        // TASK C6.1: 15:00 minute countdown (900 seconds)
        expires = System.currentTimeMillis() + (15 * 60 * 1000);
        countdown = new Timer(1000, e -> {
            long seconds = Math.max(0, (expires - System.currentTimeMillis()) / 1000);
            long m = seconds / 60;
            long s = seconds % 60;
            status.setText(String.format("Đang chờ xác nhận VietQR · %02d:%02d", m, s));
            if (seconds == 0) {
                if (poll != null) poll.stop();
                countdown.stop();
                status.setText("Mã QR đã hết hạn (15:00). Vui lòng tạo mã mới.");
            }
        });

        poll = new Timer(2000, e -> {
            if (polling || settled || !isDisplayable()) return;
            polling = true;
            Phase2UiApi.request("GET", "/v1/folios/" + folio + "/payment-status", null, null, data -> {
                polling = false;
                if (!isDisplayable()) return;
                if (data.getAsJsonObject().get("status").getAsString().equals("settled")) {
                    settled = true;
                    poll.stop();
                    countdown.stop();
                    if (checkout) {
                        JsonObject body = new JsonObject();
                        body.addProperty("amount", 0);
                        body.addProperty("payment_method", "bank_transfer");
                        body.addProperty("checkout", true);
                        Phase2UiApi.request("POST", "/v1/folios/" + folio + "/pay", body.toString(), checkoutKey,
                            result -> confirm(refresh, result.getAsJsonObject().has("checked_out") && result.getAsJsonObject().get("checked_out").getAsBoolean()),
                            error -> {
                                status.setText("Đã nhận thanh toán. Lễ tân cần xác nhận trả phòng.");
                                refresh.run();
                            });
                    } else {
                        confirm(refresh, false);
                    }
                }
            }, error -> {
                polling = false;
                if (isDisplayable()) status.setText("Đang chờ xác minh thanh toán…");
            });
        });

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) {
                if (poll != null) poll.stop();
                if (countdown != null) countdown.stop();
            }
        });
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        Phase2UiApi.request("GET", "/v1/folios/" + folio + "/vietqr", null, null, data -> {
            if (!isDisplayable()) return;
            JsonObject qr = data.getAsJsonObject();
            details.setText("<html><div style='text-align:center'>" + safe(qr.get("beneficiary_bank").getAsString()) + " · " + safe(qr.get("account_number").getAsString()) + "<br>" + safe(qr.get("account_holder").getAsString()) + "<br>" + safe(qr.get("amount").getAsString()) + " VND · " + safe(qr.get("reference_code").getAsString()) + "</div></html>");
            new SwingWorker<java.awt.image.BufferedImage, Void>() {
                @Override protected java.awt.image.BufferedImage doInBackground() throws Exception {
                    return javax.imageio.ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(qr.get("qr_image_base64").getAsString().replace("data:image/png;base64,", ""))));
                }
                @Override protected void done() {
                    if (!isDisplayable()) return;
                    try {
                        var png = get();
                        if (png == null) throw new IllegalStateException();
                        image.setText("");
                        image.setIcon(new ImageIcon(png));
                    } catch (Exception ex) {
                        image.setText("Chưa thể hiển thị mã QR.");
                    }
                }
            }.execute();
            countdown.start();
            poll.start();
        }, error -> {
            if (isDisplayable()) {
                image.setText("Chưa thể hiển thị QR. Đang kiểm tra thanh toán…");
                poll.start();
                countdown.start();
            }
        });
    }

    private static String safe(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * TASK C6.2: Plays a pleasant success chime upon settlement signal using Clip,
     * falling back to Toolkit.getDefaultToolkit().beep() if audio hardware is unavailable.
     */
    public void playSuccessChime() {
        try {
            float rate = 22050f;
            int samples = (int) (rate * 0.35);
            byte[] pcm = new byte[samples * 2];
            for (int i = 0; i < samples; i++) {
                double envelope = Math.sin(Math.PI * i / samples);
                short sample = (short) ((Math.sin(2 * Math.PI * 1046.5 * i / rate) + 0.5 * Math.sin(2 * Math.PI * 1318.5 * i / rate)) * envelope * 4000);
                pcm[i * 2] = (byte) sample;
                pcm[i * 2 + 1] = (byte) (sample >> 8);
            }
            Clip clip = AudioSystem.getClip();
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) clip.close();
            });
            clip.open(new AudioFormat(rate, 16, 1, true, false), pcm, 0, pcm.length);
            clip.start();
        } catch (Exception ex) {
            try {
                Toolkit.getDefaultToolkit().beep();
            } catch (Exception ignored) {}
        }
    }

    public Timer getCountdownTimer() {
        return countdown;
    }

    public long getRemainingSeconds() {
        return Math.max(0, (expires - System.currentTimeMillis()) / 1000);
    }

    private void confirm(Runnable refresh, boolean checkedOut) {
        playSuccessChime();
        SoundNotifier.settled();

        JPanel overlay = new JPanel(new BorderLayout());
        overlay.setBackground(new Color(238, 250, 242));
        final float[] progress = {0};
        JComponent tick = new JComponent() {
            @Override
            protected void paintComponent(Graphics source) {
                Graphics2D g = (Graphics2D) source.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(40, 155, 85));
                g.setStroke(new BasicStroke(12, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = getWidth() / 2, cy = getHeight() / 2;
                float first = Math.min(1, progress[0] / 0.4f), second = Math.max(0, (progress[0] - 0.4f) / 0.6f);
                g.drawLine(cx - 65, cy, cx - 65 + (int) (45 * first), cy + (int) (45 * first));
                if (second > 0) g.drawLine(cx - 20, cy + 45, cx - 20 + (int) (90 * second), cy + 45 - (int) (105 * second));
                g.dispose();
            }
        };
        overlay.add(tick, BorderLayout.CENTER);
        JLabel label = new JLabel(checkedOut ? "Thanh toán thành công! Sổ cái đã cân bằng." : "Thanh toán Folio đã được xác nhận.", SwingConstants.CENTER);
        label.setBorder(BorderFactory.createEmptyBorder(12, 12, 36, 12));
        overlay.add(label, BorderLayout.SOUTH);
        setContentPane(overlay);
        revalidate();
        repaint();

        Timer animation = new Timer(16, e -> {
            if (!isDisplayable()) {
                ((Timer) e.getSource()).stop();
                return;
            }
            progress[0] = Math.min(1, progress[0] + 0.025f);
            tick.repaint();
            if (progress[0] >= 1) ((Timer) e.getSource()).stop();
        });
        animation.start();
        refresh.run();

        Timer close = new Timer(1500, e -> dispose());
        close.setRepeats(false);
        close.start();
    }
}
