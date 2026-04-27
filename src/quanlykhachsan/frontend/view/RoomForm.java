package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Collections;
import quanlykhachsan.backend.model.Room;
import quanlykhachsan.backend.model.User;
import quanlykhachsan.frontend.api.RoomAPI;
import quanlykhachsan.frontend.utils.WrapLayout;

public class RoomForm extends JPanel {

    // ── Widgets ──────────────────────────────────────────────────────────
    private JPanel gridPanel;        // Panel chứa các card phòng (wrap layout)
    private JLabel lblStatus;        // Thanh trạng thái phía trên
    private Room selectedRoom;       // Phòng đang được chọn (để xóa)
    private User currentUser;        // User hiện tại (để phân quyền)

    // ── Màu sắc ──────────────────────────────────────────────────────────
    private final Color C_AVAILABLE   = new Color(34, 197, 94);   // xanh lá
    private final Color C_BOOKED      = new Color(234, 179, 8);   // vàng
    private final Color C_OCCUPIED    = new Color(239, 68, 68);   // đỏ
    private final Color C_MAINTENANCE = new Color(107, 114, 128); // xám
    private final Color C_CLEANING    = new Color(56, 189, 248);  // xanh dương nhạt (sky)
    private final Color C_SERVICE     = new Color(156, 163, 175); // xám trung tính

    private final Color PRIMARY   = new Color(37, 99, 235);
    private final Color DANGER    = new Color(220, 38, 38);
    private final Color SUCCESS   = new Color(5, 150, 105);
    private final Color MUTED     = quanlykhachsan.frontend.utils.ThemeManager.getTextMuted();
    private final Color BG        = quanlykhachsan.frontend.utils.ThemeManager.getBgPanel();
    private final Color BORDER_C  = quanlykhachsan.frontend.utils.ThemeManager.getBorderColor();

    // Kích thước mỗi card phòng
    private static final int CARD_W = 160;
    private static final int CARD_H = 170;

    public RoomForm(User user) {
        this.currentUser = user;
        setLayout(new BorderLayout(0, 0));
        setBackground(BG);
        initUI();
        loadRooms();
    }

    private void initUI() {
        // ── Main Header Wrapper ───────────────────────────────────────────
        JPanel mainHeader = new JPanel();
        mainHeader.setLayout(new BoxLayout(mainHeader, BoxLayout.Y_AXIS));
        mainHeader.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        mainHeader.setBorder(new MatteBorder(0, 0, 1, 0, BORDER_C));

        // ── Row 1: Title & Action Buttons ────────────────────────────────
        JPanel topRow = new JPanel(new BorderLayout(15, 0));
        topRow.setOpaque(false);
        topRow.setBorder(new EmptyBorder(12, 20, 8, 20));

        JLabel pageTitle = new JLabel("Sơ đồ phòng khách sạn");
        pageTitle.setFont(new Font("Segoe UI", Font.BOLD, 20));
        pageTitle.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        topRow.add(pageTitle, BorderLayout.WEST);

        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionButtons.setOpaque(false);

        lblStatus = new JLabel(" ");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblStatus.setForeground(MUTED);
        actionButtons.add(lblStatus);

        JButton btnRefresh = makeToolBtn("Làm mới", MUTED, false);
        btnRefresh.addActionListener(e -> loadRooms());
        actionButtons.add(btnRefresh);

        if (currentUser.getRoleId() == 1) { // ADMIN
            JButton btnAdd = makeToolBtn("Thêm phòng", PRIMARY, true);
            btnAdd.addActionListener(e -> showAddRoomDialog());
            actionButtons.add(btnAdd);

            JButton btnChangeStatus = makeToolBtn("Đổi trạng thái", new Color(124, 58, 237), true);
            btnChangeStatus.addActionListener(e -> {
                if (selectedRoom == null) {
                    JOptionPane.showMessageDialog(this, "Vui lòng chọn một phòng!", "Chưa chọn phòng", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    showChangeStatusDialog(selectedRoom);
                }
            });
            actionButtons.add(btnChangeStatus);
        }
        topRow.add(actionButtons, BorderLayout.EAST);

        // ── Row 2: Legend (Simplified) ───────────────────────────────────
        JPanel legendRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        legendRow.setOpaque(false);
        legendRow.setBorder(new EmptyBorder(0, 20, 12, 20));

        legendRow.add(new JLabel("Chú thích:"));
        legendRow.add(makeLegend("Trống",     C_AVAILABLE));
        legendRow.add(makeLegend("Đã đặt",    C_BOOKED));
        legendRow.add(makeLegend("Có khách",  C_OCCUPIED));
        legendRow.add(makeLegend("Bảo trì",   C_MAINTENANCE));

        mainHeader.add(topRow);
        mainHeader.add(legendRow);
        add(mainHeader, BorderLayout.NORTH);

        // ── Grid panel (WrapFlowLayout) ──────────────────────────────────
        gridPanel = new JPanel(new WrapLayout(FlowLayout.LEADING, 12, 12));
        gridPanel.setBackground(BG);
        gridPanel.setBorder(new EmptyBorder(4, 8, 8, 8));

        JScrollPane scroll = new JScrollPane(gridPanel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(scroll, BorderLayout.CENTER);
    }

    // ── Load rooms ────────────────────────────────────────────────────────

    public void loadRooms() {
        selectedRoom = null;
        gridPanel.removeAll();
        setStatus("Đang tải dữ liệu...", MUTED);

        // Loading placeholder
        JLabel loading = new JLabel("Đang tải...");
        loading.setFont(new Font("Segoe UI", Font.ITALIC, 14));
        loading.setForeground(MUTED);
        gridPanel.add(loading);
        gridPanel.revalidate();
        gridPanel.repaint();

        SwingWorker<List<Room>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Room> doInBackground() throws Exception {
                return RoomAPI.getAllRooms();
            }
            @Override
            protected void done() {
                gridPanel.removeAll();
                try {
                    List<Room> roomsList = get();
                    if (roomsList == null || roomsList.isEmpty()) {
                        JLabel empty = new JLabel("Chưa có phòng nào. Nhấn 'Thêm phòng' để bắt đầu!");
                        empty.setFont(new Font("Segoe UI", Font.ITALIC, 14));
                        empty.setForeground(MUTED);
                        gridPanel.add(empty);
                        setStatus("0 phòng", MUTED);
                    } else {
                        // Sắp xếp phòng theo số hiệu (Số - chuyển sang int nếu có thể để sort: 101, 102, 201...)
                        roomsList.sort((r1, r2) -> {
                            try {
                                return Integer.compare(Integer.parseInt(r1.getRoomNumber()), Integer.parseInt(r2.getRoomNumber()));
                            } catch (Exception ex) {
                                return r1.getRoomNumber().compareTo(r2.getRoomNumber());
                            }
                        });

                        for (Room r : roomsList) {
                            gridPanel.add(makeRoomCard(r));
                        }
                        setStatus(roomsList.size() + " phòng", SUCCESS);
                    }
                } catch (Exception ex) {
                    JLabel err = new JLabel("Lỗi kết nối server!");
                    err.setForeground(DANGER);
                    gridPanel.add(err);
                    setStatus("Lỗi tải dữ liệu", DANGER);
                }
                gridPanel.revalidate();
                gridPanel.repaint();
            }
        };
        worker.execute();
    }

    // ── Tạo card phòng ────────────────────────────────────────────────────

    private JPanel makeRoomCard(Room room) {
        Color statusColor = getStatusColor(room.getStatus());
        
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Shadow
                g2.setColor(new Color(0, 0, 0, 10));
                g2.fillRoundRect(3, 3, getWidth()-6, getHeight()-6, 15, 15);
                
                // Main Card with soft tint background if selected
                if (room.equals(selectedRoom)) {
                    g2.setColor(new Color(PRIMARY.getRed(), PRIMARY.getGreen(), PRIMARY.getBlue(), 20));
                    g2.fillRoundRect(0, 0, getWidth()-5, getHeight()-5, 15, 15);
                    g2.setColor(PRIMARY);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRoundRect(0, 0, getWidth()-5, getHeight()-5, 15, 15);
                } else {
                    g2.setColor(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
                    g2.fillRoundRect(0, 0, getWidth()-5, getHeight()-5, 15, 15);
                    g2.setColor(quanlykhachsan.frontend.utils.ThemeManager.getBorderColor());
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawRoundRect(0, 0, getWidth()-5, getHeight()-5, 15, 15);
                }
            }
        };
        card.setPreferredSize(new Dimension(CARD_W, CARD_H));
        card.setLayout(new BorderLayout());
        card.setOpaque(false);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Content
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);
        inner.setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel lblIcon = new JLabel("Phòng");
        lblIcon.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblIcon.setAlignmentX(LEFT_ALIGNMENT);

        JLabel lblNumber = new JLabel(room.getRoomNumber());
        lblNumber.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblNumber.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        lblNumber.setAlignmentX(LEFT_ALIGNMENT);

        JLabel lblCap = new JLabel("Sức chứa: " + (room.getRoomTypeId() == 1 ? "2-4" : "1-2") + " người");
        lblCap.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblCap.setForeground(MUTED);
        lblCap.setAlignmentX(LEFT_ALIGNMENT);

        JPanel statusPill = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Soft background tint for status
                g2.setColor(new Color(statusColor.getRed(), statusColor.getGreen(), statusColor.getBlue(), 35));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                // Accent border
                g2.setColor(new Color(statusColor.getRed(), statusColor.getGreen(), statusColor.getBlue(), 120));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 8, 8);
            }
        };
        statusPill.setLayout(new FlowLayout(FlowLayout.CENTER, 8, 2));
        statusPill.setOpaque(false);
        statusPill.setMaximumSize(new Dimension(90, 20));
        statusPill.setAlignmentX(LEFT_ALIGNMENT);
        
        JLabel lblStatusTxt = new JLabel(toVietnamese(room.getStatus()));
        lblStatusTxt.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblStatusTxt.setForeground(statusColor.darker());
        statusPill.add(lblStatusTxt);

        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        JLabel lblPrice = new JLabel(nf.format(room.getPrice()) + " đ/đêm");
        lblPrice.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblPrice.setForeground(PRIMARY);
        lblPrice.setAlignmentX(LEFT_ALIGNMENT);

        inner.add(lblIcon);
        inner.add(lblNumber);
        inner.add(lblCap);
        inner.add(Box.createVerticalStrut(10));
        inner.add(statusPill);
        inner.add(Box.createVerticalGlue());
        inner.add(lblPrice);

        card.add(inner, BorderLayout.CENTER);

        // ── Status-based interaction tooltip ──
        String tooltip = null;
        switch (room.getStatus().toLowerCase()) {
            case "available": tooltip = "Nhấn để đặt phòng cho khách"; break;
            case "booked":    tooltip = "Nhấn để xác nhận nhận phòng (Check-in)"; break;
            case "occupied":  tooltip = "Nhấn để trả phòng & thanh toán (Check-out)"; break;
        }
        if (tooltip != null) card.setToolTipText(tooltip);

        // Click logic
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (currentUser.getRoleId() == 3) {
                    // Khách hàng: xem chi tiết phòng
                    showRoomDetailDialog(room);
                } else if (currentUser.getRoleId() == 1) {
                    // Admin: chọn để đổi trạng thái
                    selectedRoom = room;
                    gridPanel.repaint();
                } else {
                    // Nhân viên (role 2): tương tác trực tiếp theo trạng thái
                    String status = room.getStatus().toLowerCase();
                    if (status.equals("available") || status.equals("booked") || status.equals("occupied") || status.equals("cleaning")) {
                        Window w = SwingUtilities.getWindowAncestor(RoomForm.this);
                        new RoomActionDialog(w, room, currentUser, () -> SwingUtilities.invokeLater(() -> loadRooms())).setVisible(true);
                    } else {
                        selectedRoom = room;
                        gridPanel.repaint();
                    }
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (currentUser.getRoleId() == 2) {
                    String st = room.getStatus().toLowerCase();
                    if (st.equals("available") || st.equals("booked") || st.equals("occupied")) {
                        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    }
                }
            }
        });
        return card;
    }

    // ── Dialog Thêm Phòng ─────────────────────────────────────────────────

    private void showAddRoomDialog() {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Thêm phòng mới", true);
        dialog.setSize(380, 420);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(24, 28, 24, 28));
        panel.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());

        JLabel title = new JLabel("Thêm phòng mới");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        title.setAlignmentX(LEFT_ALIGNMENT);

        panel.add(title);
        panel.add(Box.createVerticalStrut(16));

        JTextField txtRoomNumber = addDialogField(panel, "Số phòng (vd: 101, 202)");
        panel.add(Box.createVerticalStrut(10));
        JTextField txtPrice = addDialogField(panel, "Giá / Đêm (VNĐ, vd: 500000)");
        panel.add(Box.createVerticalStrut(10));

        panel.add(makeDialogLabel("Trạng thái ban đầu"));
        String[] statuses = {"available", "maintenance"};
        JComboBox<String> cbStatus = new JComboBox<>(statuses);
        cbStatus.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, toVietnamese((String)value), index, isSelected, cellHasFocus);
                return this;
            }
        });
        cbStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        cbStatus.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        cbStatus.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(cbStatus);
        panel.add(Box.createVerticalStrut(18));

        JLabel lblErr = new JLabel(" ");
        lblErr.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblErr.setForeground(DANGER);
        lblErr.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(lblErr);

        JButton btnSave = new JButton("Lưu phòng") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? PRIMARY : BORDER_C);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                super.paintComponent(g);
            }
        };
        btnSave.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnSave.setForeground(Color.WHITE);
        btnSave.setContentAreaFilled(false);
        btnSave.setBorderPainted(false);
        btnSave.setFocusPainted(false);
        btnSave.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnSave.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnSave.setAlignmentX(LEFT_ALIGNMENT);

        btnSave.addActionListener(e -> {
            String roomNum = txtRoomNumber.getText().trim();
            String priceStr = txtPrice.getText().trim();
            String status = (String) cbStatus.getSelectedItem();

            if (roomNum.isEmpty()) { lblErr.setText("Số phòng không được để trống!"); return; }
            double price;
            try {
                price = Double.parseDouble(priceStr.replaceAll("[^\\d.]", ""));
                if (price <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                lblErr.setText("Giá phòng phải là số dương!"); return;
            }

            btnSave.setEnabled(false);
            Room newRoom = new Room();
            newRoom.setRoomNumber(roomNum);
            newRoom.setRoomTypeId(1);
            newRoom.setPrice(price);
            newRoom.setStatus(status);

            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override protected String doInBackground() { return RoomAPI.addRoom(newRoom); }
                @Override
                protected void done() {
                    btnSave.setEnabled(true);
                    try {
                        String res = get();
                        if ("Success".equals(res)) {
                            dialog.dispose();
                            loadRooms();
                        } else {
                            lblErr.setText(res);
                        }
                    } catch (Exception ex) {
                        lblErr.setText("Lỗi kết nối server!");
                    }
                }
            };
            worker.execute();
        });

        panel.add(btnSave);
        dialog.setContentPane(panel);
        dialog.setVisible(true);
    }

    // ── Dialog Đổi Trạng Thái Phòng ─────────────────────────────────────

    private void showChangeStatusDialog(Room room) {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
            "Đổi trạng thái phòng", true);
        dialog.setSize(380, 420);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(24, 28, 24, 28));
        panel.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());

        JLabel title = new JLabel("Đổi trạng thái — Phòng " + room.getRoomNumber());
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        title.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        title.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(4));

        JLabel currentLbl = new JLabel("Trạng thái hiện tại: " + toVietnamese(room.getStatus()));
        currentLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        currentLbl.setForeground(MUTED);
        currentLbl.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(currentLbl);
        panel.add(Box.createVerticalStrut(18));

        String[][] statusOptions = {
            {"available",       "Trống — Sẵn sàng nhận khách mới"},
            {"cleaning",        "Đang dọn dẹp — Vừa có khách trả"},
            {"maintenance",     "Bảo trì — Phủ phòng / sửa chữa"},
            {"out_of_service",  "Ngừng hoạt động — Tạm thời đóng cửa"}
        };

        ButtonGroup group = new ButtonGroup();
        for (String[] entry : statusOptions) {
            JRadioButton rb = new JRadioButton(entry[1]);
            rb.setActionCommand(entry[0]);
            rb.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            rb.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
            rb.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
            rb.setAlignmentX(LEFT_ALIGNMENT);
            if (entry[0].equals(room.getStatus())) rb.setSelected(true);
            group.add(rb);
            panel.add(rb);
            panel.add(Box.createVerticalStrut(8));
        }

        panel.add(Box.createVerticalStrut(8));
        JLabel lblErr = new JLabel(" ");
        lblErr.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblErr.setForeground(DANGER);
        lblErr.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(lblErr);

        JButton btnSave = new JButton("Lưu thay đổi") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color purple = new Color(124, 58, 237);
                g2.setColor(isEnabled() ? (getModel().isRollover() ? purple.darker() : purple) : BORDER_C);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                super.paintComponent(g);
            }
        };
        btnSave.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnSave.setForeground(Color.WHITE);
        btnSave.setContentAreaFilled(false);
        btnSave.setBorderPainted(false);
        btnSave.setFocusPainted(false);
        btnSave.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnSave.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnSave.setAlignmentX(LEFT_ALIGNMENT);

        btnSave.addActionListener(e -> {
            String chosen = group.getSelection() != null ? group.getSelection().getActionCommand() : null;
            if (chosen == null) { lblErr.setText("Vui lòng chọn trạng thái!"); return; }
            if (chosen.equals(room.getStatus())) { lblErr.setText("Đây đã là trạng thái hiện tại!"); return; }

            btnSave.setEnabled(false);
            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override protected String doInBackground() {
                    return RoomAPI.updateRoomStatus(room.getId(), chosen);
                }
                @Override
                protected void done() {
                    btnSave.setEnabled(true);
                    try {
                        String res = get();
                        if ("Success".equals(res)) {
                            dialog.dispose();
                            selectedRoom = null;
                            setStatus("Phòng " + room.getRoomNumber() + " \u2192 " + toVietnamese(chosen), SUCCESS);
                            loadRooms();
                        } else {
                            lblErr.setText(res);
                        }
                    } catch (Exception ex) {
                        lblErr.setText("Lỗi kết nối server!");
                    }
                }
            };
            worker.execute();
        });

        panel.add(btnSave);
        dialog.setContentPane(panel);
        dialog.setVisible(true);
    }

    private void showRoomDetailDialog(Room room) {
        new RoomDetailDialog(
            (Frame) SwingUtilities.getWindowAncestor(this),
            room,
            currentUser
        ).setVisible(true);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void setStatus(String msg, Color color) {
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText(msg);
            lblStatus.setForeground(color);
        });
    }

    private JPanel makeLegend(String text, Color color) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setOpaque(false);
        
        JPanel indicator = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillOval(0, 0, getWidth(), getHeight());
            }
        };
        indicator.setPreferredSize(new Dimension(10, 10));
        p.add(indicator);

        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setForeground(new Color(71, 85, 105));
        p.add(lbl);
        return p;
    }

    private JLabel makeSep() {
        JLabel sep = new JLabel("|");
        sep.setForeground(new Color(203, 213, 225));
        return sep;
    }

    private JButton makeToolBtn(String text, Color color, boolean filled) {
        JButton btn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                if (filled) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getModel().isRollover() ? color.darker() : color);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                }
                super.paintComponent(g);
            }
        };
        
        btn.setText(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFocusPainted(false);
        
        if (filled) {
            btn.setForeground(Color.WHITE);
            btn.setContentAreaFilled(false);
            btn.setBorder(new EmptyBorder(7, 15, 7, 15));
        } else {
            btn.setForeground(color);
            btn.setContentAreaFilled(false);
            btn.setBorder(BorderFactory.createCompoundBorder(
                new RoundBorder(color, 8),
                new EmptyBorder(6, 14, 6, 14)
            ));
        }
        return btn;
    }

    private JTextField addDialogField(JPanel parent, String placeholder) {
        JLabel lbl = makeDialogLabel(placeholder.contains("(") ?
            placeholder.substring(0, placeholder.indexOf("(")).trim() : placeholder);
        parent.add(lbl);
        parent.add(Box.createVerticalStrut(4));
        JTextField f = new JTextField();
        f.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        f.putClientProperty("JTextField.placeholderText", placeholder);
        f.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_C, 1, true),
            new EmptyBorder(6, 10, 6, 10)
        ));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        f.setAlignmentX(LEFT_ALIGNMENT);
        parent.add(f);
        return f;
    }

    private JLabel makeDialogLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private Color getStatusColor(String status) {
        if (status == null) return Color.LIGHT_GRAY;
        switch (status.toLowerCase()) {
            case "available":      return C_AVAILABLE;
            case "booked":         return C_BOOKED;
            case "occupied":       return C_OCCUPIED;
            case "maintenance":    return C_MAINTENANCE;
            case "cleaning":       return C_CLEANING;
            case "out_of_service": return C_SERVICE;
            default:               return new Color(200, 200, 200);
        }
    }

    private String toVietnamese(String status) {
        if (status == null) return "N/A";
        switch (status.toLowerCase()) {
            case "available":      return "Trống";
            case "booked":         return "Đã đặt";
            case "occupied":       return "Có khách";
            case "maintenance":    return "Bảo trì";
            case "cleaning":       return "Dọn dẹp";
            case "out_of_service": return "Ngừng bán";
            default:               return status;
        }
    }

    private boolean isDark(Color c) {
        double luminance = (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255;
        return luminance < 0.5;
    }


    private static class RoundBorder extends AbstractBorder {
        private final Color color;
        private final int radius;
        public RoundBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
        }
        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(radius / 3, radius / 2, radius / 3, radius / 2);
        }
    }
}
