package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import com.toedter.calendar.JDateChooser;
import quanlykhachsan.backend.model.Booking;
import quanlykhachsan.backend.model.Customer;
import quanlykhachsan.backend.model.Room;
import quanlykhachsan.backend.model.User;
import quanlykhachsan.frontend.api.BookingAPI;
import quanlykhachsan.frontend.api.CustomerAPI;
import quanlykhachsan.frontend.utils.ThemeManager;

/**
 * RoomActionDialog — Hộp thoại tương tác nhanh trực tiếp từ sơ đồ phòng.
 *
 * - Phòng TRỐNG (available) → Form Đặt phòng nhanh
 * - Phòng ĐÃ ĐẶT (booked)   → Xác nhận Nhận phòng (Check-in)
 * - Phòng CÓ KHÁCH (occupied) → Form Trả phòng + Thanh toán (Check-out)
 */
public class RoomActionDialog extends JDialog {

    private final Room room;
    private final User currentUser;
    private Runnable onSuccessCallback; // Called when an action succeeds, refresh map

    private final Color PRIMARY   = ThemeManager.getPrimary();
    private final Color SUCCESS   = ThemeManager.getSuccess();
    private final Color WARNING   = ThemeManager.getWarning();
    private final Color DANGER    = ThemeManager.getDanger();
    private final Color BG        = ThemeManager.getBgPanel();
    private final Color CARD_BG   = ThemeManager.getCardBg();
    private final Color TEXT_MAIN = ThemeManager.getTextMain();
    private final Color MUTED     = ThemeManager.getTextMuted();
    private final Color BORDER_C  = ThemeManager.getBorderColor();

    private final NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    private final SimpleDateFormat displaySdf = new SimpleDateFormat("dd/MM/yyyy");

    public RoomActionDialog(Window owner, Room room, User user, Runnable onSuccess) {
        super(owner, buildTitle(room), Dialog.ModalityType.APPLICATION_MODAL);
        this.room = room;
        this.currentUser = user;
        this.onSuccessCallback = onSuccess;

        setSize(480, 520);
        setResizable(false);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        buildHeader();
        buildBody();
    }

    private static String buildTitle(Room room) {
        switch (room.getStatus().toLowerCase()) {
            case "available": return "Đặt Phòng — Phòng " + room.getRoomNumber();
            case "booked":    return "Nhận Phòng — Phòng " + room.getRoomNumber();
            case "occupied":  return "Trả Phòng & Thanh Toán — Phòng " + room.getRoomNumber();
            case "cleaning":  return "Xác nhận Dọn dẹp — Phòng " + room.getRoomNumber();
            default:          return "Phòng " + room.getRoomNumber();
        }
    }

    private void buildHeader() {
        Color accentColor;
        String statusText;
        switch (room.getStatus().toLowerCase()) {
            case "available": accentColor = SUCCESS;                  statusText = "Phòng Trống — Sẵn sàng nhận khách"; break;
            case "booked":    accentColor = WARNING;                  statusText = "Đã Đặt — Chờ nhận phòng"; break;
            case "occupied":  accentColor = DANGER;                   statusText = "Có Khách — Đang lưu trú"; break;
            case "cleaning":  accentColor = new Color(56, 189, 248);  statusText = "Đang Dọn dẹp — Chờ xác nhận hoàn tất"; break;
            default:          accentColor = MUTED;                    statusText = room.getStatus(); break;
        }

        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setBackground(accentColor);
        header.setBorder(new EmptyBorder(20, 24, 20, 24));

        JLabel lblRoom = new JLabel("Phòng " + room.getRoomNumber());
        lblRoom.setFont(new Font("Segoe UI", Font.BOLD, 28));
        lblRoom.setForeground(Color.WHITE);

        JLabel lblStatus = new JLabel(statusText);
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblStatus.setForeground(new Color(255, 255, 255, 200));

        JLabel lblPrice = new JLabel(nf.format(room.getPrice()) + " đ / đêm");
        lblPrice.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblPrice.setForeground(Color.WHITE);

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);
        left.add(lblRoom);
        left.add(Box.createVerticalStrut(4));
        left.add(lblStatus);

        header.add(left, BorderLayout.WEST);
        header.add(lblPrice, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
    }

    private void buildBody() {
        switch (room.getStatus().toLowerCase()) {
            case "available": buildBookingPanel(); break;
            case "booked":    buildCheckInPanel(); break;
            case "occupied":  buildCheckOutPanel(); break;
            case "cleaning":  buildCleaningPanel(); break;
            default:
                JLabel msg = new JLabel("Phòng này đang " + room.getStatus() + ", không có thao tác.", SwingConstants.CENTER);
                msg.setForeground(MUTED);
                add(msg, BorderLayout.CENTER);
        }
    }

    // ─────────────────────────────────────────────────
    // Panel 1: ĐẶT PHÒNG (available)
    // ─────────────────────────────────────────────────

    private void buildBookingPanel() {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(BG);
        body.setBorder(new EmptyBorder(20, 24, 20, 24));

        // Customer selection
        body.add(makeLabel("Khách hàng *"));
        body.add(Box.createVerticalStrut(6));

        JButton btnSelectCustomer = new JButton("  Nhấn để chọn khách hàng...");
        btnSelectCustomer.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btnSelectCustomer.setHorizontalAlignment(SwingConstants.LEFT);
        btnSelectCustomer.setForeground(MUTED);
        btnSelectCustomer.setBackground(CARD_BG);
        btnSelectCustomer.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_C, 1, true), new EmptyBorder(10, 12, 10, 12)));
        btnSelectCustomer.setFocusPainted(false);
        btnSelectCustomer.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnSelectCustomer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        btnSelectCustomer.setAlignmentX(LEFT_ALIGNMENT);

        final Customer[] selectedCustomer = {null};

        btnSelectCustomer.addActionListener(e -> {
            CustomerSelectDialog dialog = new CustomerSelectDialog((Frame) SwingUtilities.getWindowAncestor(this));
            dialog.setVisible(true);
            Customer c = dialog.getSelectedCustomer();
            if (c != null) {
                selectedCustomer[0] = c;
                btnSelectCustomer.setText("  ✓ " + c.getFullName() + " — " + c.getPhone());
                btnSelectCustomer.setForeground(SUCCESS);
                btnSelectCustomer.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(SUCCESS, 2, true), new EmptyBorder(9, 11, 9, 11)));
            }
        });

        body.add(btnSelectCustomer);
        body.add(Box.createVerticalStrut(16));

        // Date row
        JPanel dateRow = new JPanel(new GridLayout(1, 2, 12, 0));
        dateRow.setOpaque(false);
        dateRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        dateRow.setAlignmentX(LEFT_ALIGNMENT);

        JPanel pIn = new JPanel(new BorderLayout(0, 6));
        pIn.setOpaque(false);
        pIn.add(makeLabel("Ngày Check-in *"), BorderLayout.NORTH);
        JDateChooser dateCheckIn = new JDateChooser();
        dateCheckIn.setDateFormatString("dd/MM/yyyy");
        dateCheckIn.setDate(new Date());
        pIn.add(dateCheckIn, BorderLayout.CENTER);

        JPanel pOut = new JPanel(new BorderLayout(0, 6));
        pOut.setOpaque(false);
        pOut.add(makeLabel("Ngày Check-out *"), BorderLayout.NORTH);
        JDateChooser dateCheckOut = new JDateChooser();
        dateCheckOut.setDateFormatString("dd/MM/yyyy");
        // Default 1 night
        dateCheckOut.setDate(new Date(System.currentTimeMillis() + 86400000L));
        pOut.add(dateCheckOut, BorderLayout.CENTER);

        dateRow.add(pIn);
        dateRow.add(pOut);
        body.add(dateRow);
        body.add(Box.createVerticalStrut(16));

        // Estimate display
        JLabel lblEstimate = new JLabel(" ");
        lblEstimate.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblEstimate.setForeground(PRIMARY);
        lblEstimate.setAlignmentX(LEFT_ALIGNMENT);
        body.add(lblEstimate);
        body.add(Box.createVerticalGlue());

        // Update estimate on date change
        Runnable updateEstimate = () -> {
            Date di = dateCheckIn.getDate();
            Date co = dateCheckOut.getDate();
            if (di != null && co != null && co.after(di)) {
                long nights = TimeUnit.MILLISECONDS.toDays(co.getTime() - di.getTime());
                double total = nights * room.getPrice();
                lblEstimate.setText("  " + nights + " đêm × " + nf.format(room.getPrice()) + " = " + nf.format(total) + " đ");
                lblEstimate.setForeground(PRIMARY);
            } else {
                lblEstimate.setText("  ⚠ Ngày check-out phải sau check-in");
                lblEstimate.setForeground(DANGER);
            }
        };
        dateCheckIn.addPropertyChangeListener("date", e -> updateEstimate.run());
        dateCheckOut.addPropertyChangeListener("date", e -> updateEstimate.run());
        updateEstimate.run();

        add(body, BorderLayout.CENTER);

        // Footer button
        JButton btnBook = makeActionBtn("Xác nhận Đặt Phòng", SUCCESS);
        btnBook.addActionListener(e -> {
            if (selectedCustomer[0] == null) {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn khách hàng!", "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Date di = dateCheckIn.getDate();
            Date co = dateCheckOut.getDate();
            if (di == null || co == null || !co.after(di)) {
                JOptionPane.showMessageDialog(this, "Ngày check-out phải sau ngày check-in!", "Ngày không hợp lệ", JOptionPane.WARNING_MESSAGE);
                return;
            }
            btnBook.setEnabled(false);
            btnBook.setText("Đang xử lý...");
            String ciStr = sdf.format(di);
            String coStr = sdf.format(co);
            int cId = selectedCustomer[0].getId();

            new SwingWorker<com.google.gson.JsonObject, Void>() {
                @Override protected com.google.gson.JsonObject doInBackground() {
                    return BookingAPI.bookRoom(cId, room.getId(), ciStr, coStr);
                }
                @Override protected void done() {
                    try {
                        com.google.gson.JsonObject res = get();
                        if (res != null && "success".equals(res.get("status").getAsString())) {
                            JOptionPane.showMessageDialog(RoomActionDialog.this,
                                "Đặt phòng thành công cho " + selectedCustomer[0].getFullName() + "!\nPhòng đã được cập nhật trạng thái → Đã đặt.",
                                "Thành công", JOptionPane.INFORMATION_MESSAGE);
                            if (onSuccessCallback != null) onSuccessCallback.run();
                            dispose();
                        } else {
                            String msg = res != null ? res.get("message").getAsString() : "Lỗi không xác định";
                            JOptionPane.showMessageDialog(RoomActionDialog.this, "Lỗi: " + msg, "Thất bại", JOptionPane.ERROR_MESSAGE);
                            btnBook.setEnabled(true);
                            btnBook.setText("Xác nhận Đặt Phòng");
                        }
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(RoomActionDialog.this, "Lỗi kết nối server!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                        btnBook.setEnabled(true);
                        btnBook.setText("Xác nhận Đặt Phòng");
                    }
                }
            }.execute();
        });
        add(makeFooter(btnBook), BorderLayout.SOUTH);
    }

    // ─────────────────────────────────────────────────
    // Panel 2: NHẬN PHÒNG (booked → check-in)
    // ─────────────────────────────────────────────────

    private void buildCheckInPanel() {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(BG);
        body.setBorder(new EmptyBorder(20, 24, 20, 24));

        JLabel lblLoading = new JLabel("Đang tải thông tin đặt phòng...");
        lblLoading.setForeground(MUTED);
        lblLoading.setAlignmentX(CENTER_ALIGNMENT);
        body.add(Box.createVerticalGlue());
        body.add(lblLoading);
        body.add(Box.createVerticalGlue());
        add(body, BorderLayout.CENTER);

        JButton btnCheckIn = makeActionBtn("Xác nhận Nhận Phòng (Check-in)", WARNING);
        btnCheckIn.setEnabled(false);
        add(makeFooter(btnCheckIn), BorderLayout.SOUTH);

        final Booking[] booking = {null};

        new SwingWorker<Booking, Void>() {
            @Override protected Booking doInBackground() {
                return BookingAPI.getActiveBookingByRoom(room.getId());
            }
            @Override protected void done() {
                try {
                    Booking b = get();
                    booking[0] = b;
                    body.remove(lblLoading);

                    if (b == null) {
                        JLabel err = new JLabel("Không tìm thấy thông tin đặt phòng cho phòng này.", SwingConstants.CENTER);
                        err.setForeground(DANGER);
                        body.add(err);
                    } else {
                        // Show booking summary
                        body.add(makeLabel("Thông tin Đặt Phòng"));
                        body.add(Box.createVerticalStrut(12));
                        body.add(makeInfoRow("Mã đặt phòng:", "#" + b.getId()));
                        body.add(Box.createVerticalStrut(8));
                        body.add(makeInfoRow("Ngày Check-in:", b.getCheckInDate() != null ? displaySdf.format(b.getCheckInDate()) : "N/A"));
                        body.add(Box.createVerticalStrut(8));
                        body.add(makeInfoRow("Ngày Check-out:", b.getCheckOutDate() != null ? displaySdf.format(b.getCheckOutDate()) : "N/A"));
                        body.add(Box.createVerticalStrut(8));
                        body.add(makeInfoRow("Tổng tiền dự kiến:", nf.format(b.getTotalPrice()) + " đ"));
                        body.add(Box.createVerticalStrut(24));

                        JLabel hint = new JLabel("Nhấn xác nhận để chuyển trạng thái phòng → Có khách");
                        hint.setFont(new Font("Segoe UI", Font.ITALIC, 12));
                        hint.setForeground(MUTED);
                        hint.setAlignmentX(LEFT_ALIGNMENT);
                        body.add(hint);

                        btnCheckIn.setEnabled(true);
                        btnCheckIn.addActionListener(e -> {
                            btnCheckIn.setEnabled(false);
                            btnCheckIn.setText("Đang xử lý...");
                            new SwingWorker<String, Void>() {
                                @Override protected String doInBackground() { return BookingAPI.checkIn(b.getId()); }
                                @Override protected void done() {
                                    try {
                                        String res = get();
                                        if (res != null && res.startsWith("Success")) {
                                            JOptionPane.showMessageDialog(RoomActionDialog.this,
                                                "Check-in thành công!\nKhách đã vào phòng " + room.getRoomNumber() + ".",
                                                "Thành công", JOptionPane.INFORMATION_MESSAGE);
                                            if (onSuccessCallback != null) onSuccessCallback.run();
                                            dispose();
                                        } else {
                                            JOptionPane.showMessageDialog(RoomActionDialog.this, "Lỗi: " + res, "Thất bại", JOptionPane.ERROR_MESSAGE);
                                            btnCheckIn.setEnabled(true);
                                            btnCheckIn.setText("Xác nhận Nhận Phòng (Check-in)");
                                        }
                                    } catch (Exception ex) {
                                        btnCheckIn.setEnabled(true);
                                        btnCheckIn.setText("Xác nhận Nhận Phòng (Check-in)");
                                    }
                                }
                            }.execute();
                        });
                    }
                    body.revalidate();
                    body.repaint();
                } catch (Exception ex) {
                    lblLoading.setText("Lỗi tải thông tin: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // ─────────────────────────────────────────────────
    // Panel 3: TRẢ PHÒNG + THANH TOÁN (occupied → check-out + payment)
    // ─────────────────────────────────────────────────

    private void buildCheckOutPanel() {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(BG);
        body.setBorder(new EmptyBorder(20, 24, 20, 24));

        JLabel lblLoading = new JLabel("Đang tải thông tin lưu trú...");
        lblLoading.setForeground(MUTED);
        lblLoading.setAlignmentX(CENTER_ALIGNMENT);
        body.add(Box.createVerticalGlue());
        body.add(lblLoading);
        body.add(Box.createVerticalGlue());
        add(body, BorderLayout.CENTER);

        JButton btnCheckOut = makeActionBtn("Xác nhận Trả Phòng & Thanh Toán", DANGER);
        btnCheckOut.setEnabled(false);
        add(makeFooter(btnCheckOut), BorderLayout.SOUTH);

        new SwingWorker<Booking, Void>() {
            @Override protected Booking doInBackground() {
                return BookingAPI.getActiveBookingByRoom(room.getId());
            }
            @Override protected void done() {
                try {
                    Booking b = get();
                    body.remove(lblLoading);

                    if (b == null) {
                        JLabel err = new JLabel("Không tìm thấy thông tin lưu trú của phòng này.", SwingConstants.CENTER);
                        err.setForeground(DANGER);
                        body.add(err);
                    } else {
                        // Calculate nights and amount
                        long nights = 1;
                        if (b.getCheckInDate() != null && b.getCheckOutDate() != null) {
                            nights = Math.max(1, TimeUnit.MILLISECONDS.toDays(
                                b.getCheckOutDate().getTime() - b.getCheckInDate().getTime()));
                        }
                        final double totalAmount = b.getTotalPrice() > 0 ? b.getTotalPrice() : nights * room.getPrice();

                        body.add(makeLabel("Thông tin Lưu trú"));
                        body.add(Box.createVerticalStrut(12));
                        body.add(makeInfoRow("Mã đặt phòng:", "#" + b.getId()));
                        body.add(Box.createVerticalStrut(6));
                        body.add(makeInfoRow("Check-in:", b.getCheckInDate() != null ? displaySdf.format(b.getCheckInDate()) : "N/A"));
                        body.add(Box.createVerticalStrut(6));
                        body.add(makeInfoRow("Check-out (dự kiến):", b.getCheckOutDate() != null ? displaySdf.format(b.getCheckOutDate()) : "N/A"));
                        body.add(Box.createVerticalStrut(6));
                        body.add(makeInfoRow("Số đêm:", nights + " đêm"));
                        body.add(Box.createVerticalStrut(12));

                        // Total with highlight
                        JPanel totalRow = new JPanel(new BorderLayout());
                        totalRow.setOpaque(false);
                        totalRow.setBorder(BorderFactory.createCompoundBorder(
                            new LineBorder(DANGER, 1, true), new EmptyBorder(10, 14, 10, 14)));
                        totalRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
                        totalRow.setAlignmentX(LEFT_ALIGNMENT);
                        JLabel lblTotalText = new JLabel("Tổng tiền thanh toán:");
                        lblTotalText.setFont(new Font("Segoe UI", Font.BOLD, 14));
                        lblTotalText.setForeground(TEXT_MAIN);
                        JLabel lblTotalAmt = new JLabel(nf.format(totalAmount) + " đ");
                        lblTotalAmt.setFont(new Font("Segoe UI", Font.BOLD, 16));
                        lblTotalAmt.setForeground(DANGER);
                        totalRow.add(lblTotalText, BorderLayout.WEST);
                        totalRow.add(lblTotalAmt, BorderLayout.EAST);
                        body.add(totalRow);
                        body.add(Box.createVerticalStrut(16));

                        // Payment method
                        body.add(makeLabel("Phương thức thanh toán"));
                        body.add(Box.createVerticalStrut(6));
                        JComboBox<String> cbPayment = new JComboBox<>(new String[]{"Tiền mặt", "Chuyển khoản ngân hàng", "Thẻ tín dụng"});
                        cbPayment.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                        cbPayment.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
                        cbPayment.setAlignmentX(LEFT_ALIGNMENT);
                        body.add(cbPayment);

                        btnCheckOut.setEnabled(true);
                        btnCheckOut.addActionListener(e -> {
                            String method = (String) cbPayment.getSelectedItem();
                            int confirm = JOptionPane.showConfirmDialog(RoomActionDialog.this,
                                "Xác nhận trả phòng và thanh toán " + nf.format(totalAmount) + " đ\nbằng " + method + "?",
                                "Xác nhận Trả Phòng", JOptionPane.YES_NO_OPTION);
                            if (confirm != JOptionPane.YES_OPTION) return;

                            btnCheckOut.setEnabled(false);
                            btnCheckOut.setText("Đang xử lý...");

                            new SwingWorker<Boolean, Void>() {
                                @Override protected Boolean doInBackground() throws Exception {
                                    // 1. Process payment (which internally also updates room status to cleaning)
                                    String dbMethod;
                                    if (method.contains("khoản")) dbMethod = "bank_transfer";
                                    else if (method.contains("tín dụng")) dbMethod = "credit_card";
                                    else dbMethod = "cash";
                                    return BookingAPI.processPayment(b.getId(), b.getCustomerId(), totalAmount, dbMethod);
                                }
                                @Override protected void done() {
                                    try {
                                        boolean ok = get();
                                        if (ok) {
                                            JOptionPane.showMessageDialog(RoomActionDialog.this,
                                                "✓ Trả phòng & Thanh toán thành công!\n" +
                                                "Tổng: " + nf.format(totalAmount) + " đ — " + method + "\n" +
                                                "Phòng đã chuyển sang trạng thái Dọn dẹp.",
                                                "Hoàn tất", JOptionPane.INFORMATION_MESSAGE);
                                            if (onSuccessCallback != null) onSuccessCallback.run();
                                            dispose();
                                        } else {
                                            JOptionPane.showMessageDialog(RoomActionDialog.this,
                                                "Lỗi xử lý thanh toán! Vui lòng thử lại.",
                                                "Thất bại", JOptionPane.ERROR_MESSAGE);
                                            btnCheckOut.setEnabled(true);
                                            btnCheckOut.setText("Xác nhận Trả Phòng & Thanh Toán");
                                        }
                                    } catch (Exception ex) {
                                        JOptionPane.showMessageDialog(RoomActionDialog.this, "Lỗi kết nối!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                                        btnCheckOut.setEnabled(true);
                                        btnCheckOut.setText("Xác nhận Trả Phòng & Thanh Toán");
                                    }
                                }
                            }.execute();
                        });
                    }
                    body.revalidate();
                    body.repaint();
                } catch (Exception ex) {
                    lblLoading.setText("Lỗi tải thông tin: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // ─────────────────────────────────────────────────
    // Panel 4: DỌN DẸP (cleaning → available)
    // ─────────────────────────────────────────────────

    private void buildCleaningPanel() {
        Color CLEANING = new Color(56, 189, 248);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(BG);
        body.setBorder(new EmptyBorder(30, 28, 30, 28));

        // Icon area
        JLabel lblIcon = new JLabel("🧹", SwingConstants.CENTER);
        lblIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 52));
        lblIcon.setAlignmentX(CENTER_ALIGNMENT);
        body.add(lblIcon);
        body.add(Box.createVerticalStrut(16));

        JLabel lblTitle = new JLabel("Phòng đang được dọn dẹp", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(TEXT_MAIN);
        lblTitle.setAlignmentX(CENTER_ALIGNMENT);
        body.add(lblTitle);
        body.add(Box.createVerticalStrut(8));

        JLabel lblSub = new JLabel("Phòng " + room.getRoomNumber() + " hiện đang trong quá trình vệ sinh.", SwingConstants.CENTER);
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblSub.setForeground(MUTED);
        lblSub.setAlignmentX(CENTER_ALIGNMENT);
        body.add(lblSub);
        body.add(Box.createVerticalStrut(6));

        JLabel lblHint = new JLabel("Xác nhận khi dọn dẹp hoàn tất → phòng sẽ chuyển sang Trống.", SwingConstants.CENTER);
        lblHint.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblHint.setForeground(MUTED);
        lblHint.setAlignmentX(CENTER_ALIGNMENT);
        body.add(lblHint);
        body.add(Box.createVerticalGlue());

        add(body, BorderLayout.CENTER);

        // Footer button
        JButton btnDone = makeActionBtn("✔ Xác nhận Dọn dẹp Hoàn tất", CLEANING);
        btnDone.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                RoomActionDialog.this,
                "Xác nhận dọn dẹp hoàn tất?\nPhòng sẽ được chuyển sang trạng thái Trống.",
                "Xác nhận", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;

            btnDone.setEnabled(false);
            btnDone.setText("Đang cập nhật...");

            new SwingWorker<String, Void>() {
                @Override protected String doInBackground() {
                    return quanlykhachsan.frontend.api.RoomAPI.updateRoomStatus(room.getId(), "available");
                }
                @Override protected void done() {
                    try {
                        String res = get();
                        if ("Success".equals(res)) {
                            JOptionPane.showMessageDialog(RoomActionDialog.this,
                                "Phòng " + room.getRoomNumber() + " đã sẵn sàng!\nTrạng thái → Phòng Trống.",
                                "Hoàn tất", JOptionPane.INFORMATION_MESSAGE);
                            if (onSuccessCallback != null) onSuccessCallback.run();
                            dispose();
                        } else {
                            JOptionPane.showMessageDialog(RoomActionDialog.this,
                                "Lỗi cập nhật: " + res, "Lỗi", JOptionPane.ERROR_MESSAGE);
                            btnDone.setEnabled(true);
                            btnDone.setText("✔ Xác nhận Dọn dẹp Hoàn tất");
                        }
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(RoomActionDialog.this, "Lỗi kết nối!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                        btnDone.setEnabled(true);
                        btnDone.setText("✔ Xác nhận Dọn dẹp Hoàn tất");
                    }
                }
            }.execute();
        });
        add(makeFooter(btnDone), BorderLayout.SOUTH);
    }

    // ─────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────

    private JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 13));
        l.setForeground(TEXT_MAIN);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JPanel makeInfoRow(String label, String value) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setAlignmentX(LEFT_ALIGNMENT);
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lbl.setForeground(MUTED);
        JLabel val = new JLabel(value);
        val.setFont(new Font("Segoe UI", Font.BOLD, 13));
        val.setForeground(TEXT_MAIN);
        row.add(lbl, BorderLayout.WEST);
        row.add(val, BorderLayout.EAST);
        return row;
    }

    private JButton makeActionBtn(String text, Color bg) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? (getModel().isRollover() ? bg.darker() : bg) : new Color(150, 150, 150));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setForeground(Color.WHITE);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(0, 48));
        return btn;
    }

    private JPanel makeFooter(JButton btn) {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(CARD_BG);
        footer.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(1, 0, 0, 0, BORDER_C),
            new EmptyBorder(12, 24, 12, 24)
        ));

        JButton btnCancel = new JButton("Hủy");
        btnCancel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btnCancel.setForeground(MUTED);
        btnCancel.setFocusPainted(false);
        btnCancel.setBackground(CARD_BG);
        btnCancel.setBorder(new LineBorder(BORDER_C, 1, true));
        btnCancel.setPreferredSize(new Dimension(90, 40));
        btnCancel.addActionListener(e -> dispose());

        footer.add(btnCancel, BorderLayout.WEST);
        footer.add(btn, BorderLayout.CENTER);
        footer.add(Box.createHorizontalStrut(16), BorderLayout.EAST);
        return footer;
    }
}
