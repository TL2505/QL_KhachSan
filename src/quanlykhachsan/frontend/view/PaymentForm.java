package quanlykhachsan.frontend.view;

import javax.swing.*;
import java.awt.*;
import quanlykhachsan.frontend.api.PaymentAPI;
import quanlykhachsan.frontend.api.BookingAPI;
import quanlykhachsan.frontend.MainUI;

public class PaymentForm extends JPanel {

    private JTextField txtBookingID, txtAmount;
    private JComboBox<String> cbPaymentMethod;
    private JButton btnPay;

    public PaymentForm() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        initUI();
    }

    public void setData(int bookingId, double amount) {
        txtBookingID.setText(String.valueOf(bookingId));
        txtAmount.setText(String.valueOf(amount));
    }

    private void initUI() {
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBorder(BorderFactory.createTitledBorder("Thanh Toán Giao Dịch"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0; gbc.gridy = 0; centerPanel.add(new JLabel("Booking ID:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; txtBookingID = new JTextField(15); centerPanel.add(txtBookingID, gbc);

        gbc.gridx = 0; gbc.gridy = 1; centerPanel.add(new JLabel("Tổng Tiền (Amount):"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; txtAmount = new JTextField(15); centerPanel.add(txtAmount, gbc);

        gbc.gridx = 0; gbc.gridy = 2; centerPanel.add(new JLabel("Phương thức:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2;
        String[] methods = {"cash", "credit_card", "bank_transfer", "e_wallet"};
        cbPaymentMethod = new JComboBox<>(methods);
        centerPanel.add(cbPaymentMethod, gbc);

        gbc.gridx = 1; gbc.gridy = 3; gbc.anchor = GridBagConstraints.EAST;
        btnPay = new JButton("Xác nhận Thanh toán");
        btnPay.addActionListener(e -> actionPay());
        centerPanel.add(btnPay, gbc);

        add(centerPanel, BorderLayout.NORTH);
    }

    private void actionPay() {
        try {
            final int bookingId = Integer.parseInt(txtBookingID.getText().trim());
            double amount = Double.parseDouble(txtAmount.getText().trim());
            String method = (String) cbPaymentMethod.getSelectedItem();

            btnPay.setEnabled(false);
            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() {
                    return PaymentAPI.pay(bookingId, amount, method);
                }

                @Override
                protected void done() {
                    btnPay.setEnabled(true);
                    try {
                        String msg = get();
                        if (msg.startsWith("Success")) {
                            // Sau khi thanh toán thành công -> Tự động thực hiện thủ tục Check-out để đổi trạng thái phòng
                            new SwingWorker<String, Void>() {
                                @Override protected String doInBackground() { return BookingAPI.checkOut(bookingId); }
                                @Override protected void done() {
                                    try {
                                        String coMsg = get();
                                        JOptionPane.showMessageDialog(PaymentForm.this, "Thanh toán & Trả phòng thành công!\n" + coMsg);
                                        
                                        // Quay lại sơ đồ phòng và Refresh
                                        MainUI mainUI = (MainUI) SwingUtilities.getWindowAncestor(PaymentForm.this);
                                        if (mainUI != null) {
                                            mainUI.getRoomForm().loadRooms();
                                            mainUI.switchTab("Sơ đồ Phòng");
                                        }
                                        
                                        txtBookingID.setText("");
                                        txtAmount.setText("");
                                    } catch (Exception ex) { ex.printStackTrace(); }
                                }
                            }.execute();
                        } else {
                            JOptionPane.showMessageDialog(PaymentForm.this, msg, "Lỗi Thanh toán", JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }.execute();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Dữ liệu Booking ID hoặc Tổng tiền không đúng định dạng số!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            btnPay.setEnabled(true);
        }
    }
}
