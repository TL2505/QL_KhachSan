package quanlykhachsan.frontend.view.customer;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import quanlykhachsan.backend.room.Room;
import quanlykhachsan.backend.user.User;
import quanlykhachsan.frontend.api.RoomAPI;
import quanlykhachsan.frontend.utils.WrapLayout;
import quanlykhachsan.frontend.view.staff.BookingWizardDialog;

public class RoomDiscoveryPanel extends JPanel {

    private User currentUser;
    private JTextField txtCheckIn, txtCheckOut;
    private JComboBox<String> cbRoomType, cbStatus, cbPrice;
    private JPanel gridPanel;
    private JLabel lblResults;
    private String lastCin, lastCout;

    private final Color PRIMARY = new Color(13, 148, 136); // Teal 600
    private final Color BG = quanlykhachsan.frontend.utils.ThemeManager.getBgPanel();
    private final Color BORDER_C = quanlykhachsan.frontend.utils.ThemeManager.getBorderColor();
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    private final NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

    public RoomDiscoveryPanel(User user) {
        this.currentUser = user;
        setLayout(new BorderLayout());
        setBackground(BG);

        initUI();
        performSearch(); // Tự động tìm kiếm ngay khi mở tab
    }

    private void initUI() {
        JPanel searchBar = new JPanel(new GridBagLayout());
        searchBar.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        searchBar.setBorder(new CompoundBorder(new LineBorder(BORDER_C, 1, true), new EmptyBorder(16, 16, 16, 16)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy = 0;

        gbc.gridx = 0;
        gbc.weightx = 0;
        searchBar.add(createInlineLabel("Ngày nhận:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.18;
        txtCheckIn = createSearchField(12);
        txtCheckIn.setText(sdf.format(new Date()));
        searchBar.add(txtCheckIn, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        searchBar.add(createInlineLabel("Ngày trả:"), gbc);
        gbc.gridx = 3;
        gbc.weightx = 0.18;
        txtCheckOut = createSearchField(12);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        txtCheckOut.setText(sdf.format(cal.getTime()));
        searchBar.add(txtCheckOut, gbc);

        gbc.gridx = 4;
        gbc.weightx = 0;
        searchBar.add(createInlineLabel("Loại phòng:"), gbc);
        gbc.gridx = 5;
        gbc.weightx = 0.14;
        cbRoomType = createSearchCombo(new String[]{"Tất cả", "Standard", "Deluxe", "Family"});
        searchBar.add(cbRoomType, gbc);

        gbc.gridx = 6;
        gbc.weightx = 0;
        searchBar.add(createInlineLabel("Trạng thái:"), gbc);
        gbc.gridx = 7;
        gbc.weightx = 0.14;
        cbStatus = createSearchCombo(new String[]{"Tất cả", "Trống", "Đang có khách", "Bảo trì", "Đang dọn dẹp"});
        searchBar.add(cbStatus, gbc);

        gbc.gridy = 1;
        gbc.gridx = 0;
        gbc.weightx = 0;
        searchBar.add(createInlineLabel("Giá:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.16;
        cbPrice = createSearchCombo(new String[]{"Mọi mức giá", "Dưới 500.000đ", "500k - 1 Triệu", "Trên 1 Triệu"});
        searchBar.add(cbPrice, gbc);

        gbc.gridx = 2;
        gbc.weightx = 1;
        gbc.gridwidth = 4;
        JPanel empty = new JPanel();
        empty.setOpaque(false);
        searchBar.add(empty, gbc);

        gbc.gridx = 6;
        gbc.weightx = 0;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.EAST;
        JButton btnSearch = new JButton("Tìm phòng");
        btnSearch.setBackground(PRIMARY);
        btnSearch.setForeground(Color.WHITE);
        btnSearch.setFocusPainted(false);
        btnSearch.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnSearch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnSearch.addActionListener(e -> performSearch());
        searchBar.add(btnSearch, gbc);

        // Auto-search on Enter or changes
        txtCheckIn.addActionListener(e -> performSearch());
        txtCheckOut.addActionListener(e -> performSearch());
        cbRoomType.addActionListener(e -> performSearch());
        cbStatus.addActionListener(e -> performSearch());
        cbPrice.addActionListener(e -> performSearch());

        // Auto-search on Enter or changes
        txtCheckIn.addActionListener(e -> performSearch());
        txtCheckOut.addActionListener(e -> performSearch());
        cbRoomType.addActionListener(e -> performSearch());
        cbStatus.addActionListener(e -> performSearch());
        cbPrice.addActionListener(e -> performSearch());

        JPanel contentPanel = new JPanel(new BorderLayout(18, 18));
        contentPanel.setOpaque(false);
        contentPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        JLabel heading = new JLabel("Sơ đồ phòng hiện tại");
        heading.setFont(new Font("Segoe UI", Font.BOLD, 18));
        heading.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        JLabel desc = new JLabel("Xem trạng thái phòng và chọn phòng phù hợp với nhu cầu của bạn.");
        desc.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        desc.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMuted());
        titleRow.add(heading, BorderLayout.WEST);
        titleRow.add(desc, BorderLayout.SOUTH);

        lblResults = new JLabel("Vui lòng chọn ngày để tìm kiếm các phòng khả dụng.");
        lblResults.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        lblResults.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMuted());

        contentPanel.add(titleRow, BorderLayout.NORTH);
        contentPanel.add(lblResults, BorderLayout.SOUTH);

        gridPanel = new JPanel(new WrapLayout(FlowLayout.LEADING, 20, 20));
        gridPanel.setOpaque(false);

        JScrollPane scroll = new JScrollPane(gridPanel);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        contentPanel.add(scroll, BorderLayout.CENTER);

        add(contentPanel, BorderLayout.CENTER);
        add(searchBar, BorderLayout.NORTH);
    }

    private JLabel createInlineLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lbl.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        return lbl;
    }

    private JTextField createSearchField(int cols) {
        JTextField field = new JTextField(cols);
        field.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        field.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        field.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        field.setBorder(new CompoundBorder(new LineBorder(BORDER_C, 1, true), new EmptyBorder(8, 8, 8, 8)));
        field.setPreferredSize(new Dimension(120, 32));
        return field;
    }

    private JComboBox<String> createSearchCombo(String[] options) {
        JComboBox<String> combo = new JComboBox<>(options);
        combo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        combo.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        combo.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        combo.setBorder(new CompoundBorder(new LineBorder(BORDER_C, 1, true), new EmptyBorder(6, 8, 6, 8)));
        combo.setPreferredSize(new Dimension(140, 32));
        return combo;
    }

    private void performSearch() {
        lastCin = txtCheckIn.getText();
        lastCout = txtCheckOut.getText();
        String selectedType = (String) cbRoomType.getSelectedItem();
        String selectedStatus = (String) cbStatus.getSelectedItem();
        String selectedPrice = (String) cbPrice.getSelectedItem();
        
        lblResults.setText("Đang tải dữ liệu sơ đồ phòng...");
        gridPanel.removeAll();

        new SwingWorker<List<Room>, Void>() {
            @Override
            protected List<Room> doInBackground() {
                List<Room> allRooms = RoomAPI.getAllRooms();
                List<Room> filtered = new java.util.ArrayList<>();
                
                for (Room r : allRooms) {
                    // Filter Status
                    String st = r.getStatus() != null ? r.getStatus().toLowerCase() : "available";
                    boolean matchStatus = false;
                    if ("Tất cả".equals(selectedStatus)) matchStatus = true;
                    else if ("Trống".equals(selectedStatus) && st.equals("available")) matchStatus = true;
                    else if ("Đang có khách".equals(selectedStatus) && (st.equals("booked") || st.equals("occupied"))) matchStatus = true;
                    else if ("Bảo trì".equals(selectedStatus) && st.equals("maintenance")) matchStatus = true;
                    else if ("Đang dọn dẹp".equals(selectedStatus) && st.equals("cleaning")) matchStatus = true;
                    
                    if (!matchStatus) continue;

                    // Filter Price
                    double price = r.getPrice();
                    boolean matchPrice = false;
                    if ("Mọi mức giá".equals(selectedPrice)) matchPrice = true;
                    else if ("Dưới 500.000đ".equals(selectedPrice) && price < 500000) matchPrice = true;
                    else if ("500k - 1 Triệu".equals(selectedPrice) && price >= 500000 && price <= 1000000) matchPrice = true;
                    else if ("Trên 1 Triệu".equals(selectedPrice) && price > 1000000) matchPrice = true;
                    
                    if (!matchPrice) continue;

                    filtered.add(r);
                }
                
                // Room Type (we assume cbRoomType is just illustrative right now, or we can check simple name if type exists)
                // Note: Assuming 'Tất cả' is selected if no RoomType ID mapping exists.
                
                return filtered;
            }

            @Override
            protected void done() {
                try {
                    List<Room> rooms = get();
                    lblResults.setText("Hiển thị sơ đồ trạng thái " + rooms.size() + " phòng.");
                    for (Room r : rooms) {
                        gridPanel.add(createRoomCard(r));
                    }
                    gridPanel.revalidate();
                    gridPanel.repaint();
                } catch (Exception e) {
                    lblResults.setText("Lỗi: " + e.getMessage());
                }
            }
        }.execute();
    }

    private JPanel createRoomCard(Room r) {
        JPanel card = new JPanel(new BorderLayout());
        card.setPreferredSize(new Dimension(200, 140));
        card.setBorder(new EmptyBorder(6, 6, 6, 6));

        String st = r.getStatus() != null ? r.getStatus().toLowerCase() : "available";
        Color tileBg;
        Color tileFg = Color.WHITE;
        switch (st) {
            case "booked":
                tileBg = new Color(203, 103, 2);
                break;
            case "occupied":
                tileBg = new Color(220, 38, 38);
                break;
            case "maintenance":
                tileBg = new Color(107, 114, 128);
                break;
            case "cleaning":
                tileBg = new Color(59, 130, 246);
                break;
            default:
                tileBg = new Color(16, 185, 129);
                break;
        }

        JPanel tile = new JPanel(new BorderLayout(8, 8));
        tile.setBackground(tileBg);
        tile.setOpaque(true);
        tile.setBorder(new LineBorder(quanlykhachsan.frontend.utils.ThemeManager.getBorderColor(), 1, true));

        JLabel lblNumber = new JLabel(r.getRoomNumber(), SwingConstants.CENTER);
        lblNumber.setFont(new Font("Segoe UI", Font.BOLD, 24));
        lblNumber.setForeground(tileFg);
        tile.add(lblNumber, BorderLayout.CENTER);

        String typeName = "";
        try {
            if (r.getRoomTypeId() > 0) {
                quanlykhachsan.backend.room.RoomType rt = RoomAPI.getRoomType(r.getRoomTypeId());
                if (rt != null && rt.getName() != null) typeName = rt.getName();
            }
        } catch (Exception ex) {
            // fallback to empty
        }

        String statusText;
        switch (st) {
            case "booked":
                statusText = "Đã đặt";
                break;
            case "occupied":
                statusText = "Có khách";
                break;
            case "maintenance":
                statusText = "Bảo trì";
                break;
            case "cleaning":
                statusText = "Đang dọn";
                break;
            default:
                statusText = "Trống";
                break;
        }

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottom.setOpaque(false);

        JLabel lblStatus = new JLabel(statusText, SwingConstants.CENTER);
        lblStatus.setOpaque(true);
        lblStatus.setBackground(new Color(255, 255, 255, 90));
        lblStatus.setForeground(tileFg);
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblStatus.setBorder(new EmptyBorder(6, 12, 6, 12));
        bottom.add(lblStatus);

        tile.add(bottom, BorderLayout.SOUTH);
        tile.setToolTipText("Phòng " + r.getRoomNumber() + " - " + statusText);

        card.add(tile, BorderLayout.CENTER);

        // Keep click behavior: open details (and booking available from detail dialog)
        java.awt.event.MouseAdapter cardClickListener = new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                System.out.println("[UI] room tile clicked: " + r.getRoomNumber());
                try {
                    Window w = SwingUtilities.getWindowAncestor(RoomDiscoveryPanel.this);
                    Frame ownerFrame = (w instanceof Frame) ? (Frame) w : null;
                    RoomDetailDialog dialog = new RoomDetailDialog(ownerFrame, r, currentUser);
                    dialog.setVisible(true);
                    performSearch(); // Refresh list after dialog closes
                } catch (Throwable ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(RoomDiscoveryPanel.this, "Lỗi khi mở chi tiết phòng: " + ex.getMessage());
                }
            }
        };
        tile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tile.addMouseListener(cardClickListener);
        lblNumber.addMouseListener(cardClickListener);
        lblStatus.addMouseListener(cardClickListener);

        return card;
    }


}
