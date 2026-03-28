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
import quanlykhachsan.frontend.api.RoomAPI;

public class RoomForm extends JPanel {

    // ── Widgets ──────────────────────────────────────────────────────────
    private JPanel gridPanel;        // Panel chứa các card phòng (wrap layout)
    private JLabel lblStatus;        // Thanh trạng thái phía trên
    private Room selectedRoom;       // Phòng đang được chọn (để xóa)

    // ── Màu sắc ──────────────────────────────────────────────────────────
    private static final Color C_AVAILABLE   = new Color(34, 197, 94);   // xanh lá
    private static final Color C_BOOKED      = new Color(234, 179, 8);   // vàng
    private static final Color C_OCCUPIED    = new Color(239, 68, 68);   // đỏ
    private static final Color C_MAINTENANCE = new Color(107, 114, 128); // xám
    private static final Color C_CLEANING    = new Color(56, 189, 248);  // xanh dương nhạt (sky)
    private static final Color C_SERVICE     = new Color(156, 163, 175); // xám trung tính

    private static final Color PRIMARY   = new Color(37, 99, 235);
    private static final Color DANGER    = new Color(220, 38, 38);
    private static final Color SUCCESS   = new Color(5, 150, 105);
    private static final Color MUTED     = new Color(107, 114, 128);
    private static final Color BG        = new Color(248, 250, 252);
    private static final Color BORDER_C  = new Color(226, 232, 240);

    // Kích thước mỗi card phòng
    private static final int CARD_W = 140;
    private static final int CARD_H = 140;

    public RoomForm() {
        setLayout(new BorderLayout(0, 0));
        setBackground(BG);
        initUI();
        loadRooms();
    }

    private void initUI() {
        // ── Toolbar ──────────────────────────────────────────────────────
        JPanel toolbar = new JPanel(new BorderLayout(12, 0));
        toolbar.setBackground(Color.WHITE);
        toolbar.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER_C),
            new EmptyBorder(10, 16, 10, 16)
        ));

        // Bên trái: Tiêu đề + chú thích màu
        JPanel leftBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        leftBar.setOpaque(false);

        JLabel pageTitle = new JLabel("Sơ đồ Phòng");
        pageTitle.setFont(new Font("Segoe UI", Font.BOLD, 17));
        pageTitle.setForeground(new Color(17, 24, 39));
        leftBar.add(pageTitle);

        leftBar.add(makeSep());
        leftBar.add(makeLegend("Trống",     C_AVAILABLE));
        leftBar.add(makeLegend("Đã đặt",    C_BOOKED));
        leftBar.add(makeLegend("Có khách",  C_OCCUPIED));
        leftBar.add(makeLegend("Bảo trì",   C_MAINTENANCE));

        // Bên phải: Nút bấm
        JPanel rightBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightBar.setOpaque(false);

        lblStatus = new JLabel(" ");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblStatus.setForeground(MUTED);
        rightBar.add(lblStatus);

        JButton btnRefresh = makeToolBtn("\u21BA Làm Mới", MUTED, false);
        btnRefresh.addActionListener(e -> loadRooms());

        JButton btnAdd = makeToolBtn("+ Thêm Phòng", PRIMARY, true);
        btnAdd.addActionListener(e -> showAddRoomDialog());

        JButton btnChangeStatus = makeToolBtn("✎ Đổi Trạng Thái", new Color(124, 58, 237), true);
        btnChangeStatus.addActionListener(e -> {
            if (selectedRoom == null) {
                JOptionPane.showMessageDialog(this,
                    "Vui lòng nhấn chọn phòng muốn thay đổi trạng thái!",
                    "Chưa chọn phòng", JOptionPane.INFORMATION_MESSAGE);
            } else {
                showChangeStatusDialog(selectedRoom);
            }
        });

        rightBar.add(btnRefresh);
        rightBar.add(btnAdd);
        rightBar.add(btnChangeStatus);

        toolbar.add(leftBar, BorderLayout.WEST);
        toolbar.add(rightBar, BorderLayout.EAST);
        add(toolbar, BorderLayout.NORTH);

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
                        JLabel empty = new JLabel("Chưa có phòng nào. Nhấn '+ Thêm Phòng' để bắt đầu!");
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
        Color bg = getStatusColor(room.getStatus());
        boolean dark = isDark(bg);

        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Bóng đổ nhẹ
                g2.setColor(new Color(0, 0, 0, 20));
                g2.fillRoundRect(3, 5, getWidth() - 3, getHeight() - 3, 16, 16);
                // Card nền
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth() - 3, getHeight() - 3, 16, 16);
                // Viền highlight nếu đang selected
                if (room.equals(selectedRoom)) {
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(3f));
                    g2.drawRoundRect(2, 2, getWidth() - 8, getHeight() - 8, 12, 12);
                }
            }
        };
        card.setPreferredSize(new Dimension(CARD_W, CARD_H));
        card.setLayout(new GridBagLayout());
        card.setOpaque(false);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Nội dung card
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        JLabel lblNumber = new JLabel(room.getRoomNumber(), SwingConstants.CENTER);
        lblNumber.setFont(new Font("Segoe UI", Font.BOLD, 24));
        lblNumber.setForeground(dark ? Color.WHITE : new Color(17, 24, 39));
        lblNumber.setAlignmentX(CENTER_ALIGNMENT);

        String statusVn = toVietnamese(room.getStatus());
        JLabel lblStatus2 = new JLabel(statusVn.toUpperCase(), SwingConstants.CENTER);
        lblStatus2.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblStatus2.setForeground(dark ? new Color(255, 255, 255, 220) : new Color(55, 65, 81));
        lblStatus2.setAlignmentX(CENTER_ALIGNMENT);

        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        JLabel lblPrice = new JLabel(nf.format(room.getPrice()) + " đ/đêm", SwingConstants.CENTER);
        lblPrice.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblPrice.setForeground(dark ? new Color(255, 255, 255, 180) : new Color(75, 85, 99));
        lblPrice.setAlignmentX(CENTER_ALIGNMENT);

        content.add(lblNumber);
        content.add(Box.createVerticalStrut(2));
        content.add(lblStatus2);
        content.add(Box.createVerticalStrut(4));
        content.add(lblPrice);

        card.add(content);

        // Click để chọn (highlight)
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                selectedRoom = room;
                for (Component c : gridPanel.getComponents()) {
                    c.repaint();
                }
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                card.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                card.setBorder(null);
            }
        });

        return card;
    }

    // ── Dialog Thêm Phòng ─────────────────────────────────────────────────

    private void showAddRoomDialog() {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Thêm Phòng Mới", true);
        dialog.setSize(380, 420);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(24, 28, 24, 28));
        panel.setBackground(Color.WHITE);

        JLabel title = new JLabel("Thêm Phòng Mới");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(new Color(17, 24, 39));
        title.setAlignmentX(LEFT_ALIGNMENT);

        panel.add(title);
        panel.add(Box.createVerticalStrut(16));

        JTextField txtRoomNumber = addDialogField(panel, "Số Phòng (vd: 101, 202)");
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

        JButton btnSave = new JButton("Lưu Phòng") {
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
            "Đổi Trạng Thái Phòng", true);
        dialog.setSize(380, 420);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(24, 28, 24, 28));
        panel.setBackground(Color.WHITE);

        JLabel title = new JLabel("✎ Đổi Trạng Thái — Phòng " + room.getRoomNumber());
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        title.setForeground(new Color(17, 24, 39));
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
            {"available",       "🟢  Trống — Sẵn sàng nhận khách mới"},
            {"cleaning",        "🧹  Đang dọn dẹp — Vừa có khách trả"},
            {"maintenance",     "🔧  Bảo trì — Phủ phòng / sửa chữa"},
            {"out_of_service",  "🚫  Ngừng hoạt động — Tạm thời đóng cửa"}
        };

        ButtonGroup group = new ButtonGroup();
        for (String[] entry : statusOptions) {
            JRadioButton rb = new JRadioButton(entry[1]);
            rb.setActionCommand(entry[0]);
            rb.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            rb.setForeground(new Color(17, 24, 39));
            rb.setBackground(Color.WHITE);
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

        JButton btnSave = new JButton("Lưu Thay Đổi") {
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

    // ── Helpers ───────────────────────────────────────────────────────────

    private void setStatus(String msg, Color color) {
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText(msg);
            lblStatus.setForeground(color);
        });
    }

    private JPanel makeLegend(String text, Color color) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        p.setOpaque(false);
        JLabel dot = new JLabel("  ");
        dot.setOpaque(true);
        dot.setBackground(color);
        dot.setBorder(new LineBorder(new Color(0,0,0,40), 1, true));
        p.add(dot);
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setForeground(new Color(55, 65, 81));
        p.add(lbl);
        return p;
    }

    private JLabel makeSep() {
        JLabel sep = new JLabel("|");
        sep.setForeground(new Color(203, 213, 225));
        return sep;
    }

    private JButton makeToolBtn(String text, Color fg, boolean solid) {
        JButton btn = solid ? new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? fg.darker() : fg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                super.paintComponent(g);
            }
        } : new JButton(text);

        btn.setFont(new Font("Segoe UI", solid ? Font.BOLD : Font.PLAIN, 13));
        btn.setForeground(solid ? Color.WHITE : fg);
        if (solid) {
            btn.setContentAreaFilled(false);
            btn.setBorderPainted(false);
        }
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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
        l.setForeground(new Color(55, 65, 81));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private Color getStatusColor(String status) {
        if (status == null) return Color.LIGHT_GRAY;
        return switch (status.toLowerCase()) {
            case "available"      -> C_AVAILABLE;
            case "booked"         -> C_BOOKED;
            case "occupied"       -> C_OCCUPIED;
            case "maintenance"    -> C_MAINTENANCE;
            case "cleaning"       -> C_CLEANING;
            case "out_of_service" -> C_SERVICE;
            default               -> new Color(200, 200, 200);
        };
    }

    private String toVietnamese(String status) {
        if (status == null) return "N/A";
        return switch (status.toLowerCase()) {
            case "available"      -> "Trống";
            case "booked"         -> "Đã đặt";
            case "occupied"       -> "Có khách";
            case "maintenance"    -> "Bảo trì";
            case "cleaning"       -> "Dọn dẹp";
            case "out_of_service" -> "Ngừng bán";
            default               -> status;
        };
    }

    private boolean isDark(Color c) {
        double luminance = (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255;
        return luminance < 0.5;
    }

    static class WrapLayout extends FlowLayout {
        WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }
        @Override
        public Dimension minimumLayoutSize(Container target) {
            Dimension minimum = layoutSize(target, false);
            minimum.width -= (getHgap() + 1);
            return minimum;
        }

        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getSize().width;
                Container container = target;
                while (container.getSize().width == 0 && container.getParent() != null) {
                    container = container.getParent();
                }
                targetWidth = container.getSize().width;
                if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;

                int hgap = getHgap(), vgap = getVgap();
                Insets insets = target.getInsets();
                int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
                int maxWidth = targetWidth - horizontalInsetsAndGap;

                Dimension dim = new Dimension(0, 0);
                int rowWidth = 0, rowHeight = 0;

                int nmembers = target.getComponentCount();
                for (int i = 0; i < nmembers; i++) {
                    Component m = target.getComponent(i);
                    if (m.isVisible()) {
                        Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                        if (rowWidth + d.width > maxWidth) {
                            addRow(dim, rowWidth, rowHeight);
                            rowWidth = 0;
                            rowHeight = 0;
                        }
                        if (rowWidth != 0) rowWidth += hgap;
                        rowWidth += d.width;
                        rowHeight = Math.max(rowHeight, d.height);
                    }
                }
                addRow(dim, rowWidth, rowHeight);
                dim.width += horizontalInsetsAndGap;
                dim.height += insets.top + insets.bottom + vgap * 2;
                return dim;
            }
        }

        private void addRow(Dimension dim, int rowWidth, int rowHeight) {
            dim.width = Math.max(dim.width, rowWidth);
            if (dim.height > 0) dim.height += getVgap();
            dim.height += rowHeight;
        }
    }
}
