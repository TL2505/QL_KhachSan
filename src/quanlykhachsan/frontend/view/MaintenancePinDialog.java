package quanlykhachsan.frontend.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

/**
 * TASK C7.3: MaintenancePinDialog
 * Requires Manager PIN when changing a room status to "Out-of-Order" if the room has an existing booking.
 * Displays a clear error message if an incorrect PIN is entered.
 */
public class MaintenancePinDialog extends JDialog {
    private final JPasswordField pinField = new JPasswordField(15);
    private final JLabel lblError = new JLabel(" ");
    private final JButton btnConfirm = new JButton("Xác nhận PIN");
    private final JButton btnCancel = new JButton("Hủy bỏ");
    private String enteredPin = null;
    private final Consumer<String> onPinConfirmed;

    public MaintenancePinDialog(Window owner, int roomId, Consumer<String> onPinConfirmed) {
        super(owner, "Yêu cầu PIN Quản lý ca trực (Manager PIN)", ModalityType.APPLICATION_MODAL);
        this.onPinConfirmed = onPinConfirmed;
        initUI(roomId);
    }

    private void initUI(int roomId) {
        setLayout(new BorderLayout(12, 12));
        setSize(480, 260);
        setLocationRelativeTo(getOwner());
        setResizable(false);

        // Header warning
        JPanel pnlHeader = new JPanel(new BorderLayout(8, 8));
        pnlHeader.setBackground(new Color(254, 243, 199)); // Amber-100
        pnlHeader.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel lblWarningTitle = new JLabel("CẢNH BÁO: Phòng #" + roomId + " có lịch đặt trước!");
        lblWarningTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblWarningTitle.setForeground(new Color(180, 83, 9)); // Amber-700
        pnlHeader.add(lblWarningTitle, BorderLayout.NORTH);

        JLabel lblWarningSub = new JLabel("<html>Thao tác đưa phòng vào trạng thái <b>Bảo trì (Out-of-Order)</b> khi đang có khách đặt yêu cầu thẩm quyền phê duyệt của Quản lý.</html>");
        lblWarningSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblWarningSub.setForeground(new Color(146, 64, 14));
        pnlHeader.add(lblWarningSub, BorderLayout.CENTER);

        add(pnlHeader, BorderLayout.NORTH);

        // Center PIN input form
        JPanel pnlCenter = new JPanel(new GridBagLayout());
        pnlCenter.setBorder(BorderFactory.createEmptyBorder(12, 24, 8, 24));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);

        gbc.gridx = 0; gbc.gridy = 0;
        JLabel lblPrompt = new JLabel("Nhập mã PIN Quản lý:");
        lblPrompt.setFont(new Font("Segoe UI", Font.BOLD, 13));
        pnlCenter.add(lblPrompt, gbc);

        gbc.gridx = 1; gbc.gridy = 0;
        pinField.setFont(new Font("Segoe UI", Font.BOLD, 14));
        pnlCenter.add(pinField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        gbc.gridwidth = 2;
        lblError.setForeground(new Color(220, 38, 38)); // Red-600
        lblError.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        pnlCenter.add(lblError, gbc);

        add(pnlCenter, BorderLayout.CENTER);

        // Footer buttons
        JPanel pnlFooter = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        pnlFooter.add(btnCancel);
        pnlFooter.add(btnConfirm);
        add(pnlFooter, BorderLayout.SOUTH);

        btnCancel.addActionListener(e -> {
            enteredPin = null;
            dispose();
        });

        btnConfirm.addActionListener(e -> submitPin());
        pinField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    submitPin();
                }
            }
        });
    }

    private void submitPin() {
        char[] chars = pinField.getPassword();
        String pin = new String(chars).trim();
        java.util.Arrays.fill(chars, '\0');

        if (pin.isEmpty()) {
            showErrorMessage("Vui lòng nhập mã PIN quản lý trước khi tiếp tục.");
            pinField.requestFocusInWindow();
            return;
        }

        this.enteredPin = pin;
        if (onPinConfirmed != null) {
            onPinConfirmed.accept(pin);
        }
    }

    /**
     * TASK C7.3: Displays error message if an incorrect PIN is entered.
     */
    public void showErrorMessage(String errorMsg) {
        lblError.setText(errorMsg);
        pinField.selectAll();
        pinField.requestFocusInWindow();
    }

    public String getEnteredPin() {
        return enteredPin;
    }

    public JPasswordField getPinField() {
        return pinField;
    }

    public JLabel getErrorLabel() {
        return lblError;
    }

    public JButton getConfirmButton() {
        return btnConfirm;
    }
}
