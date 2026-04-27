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
        // --- Search Bar Section ---
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 15));
        searchBar.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        searchBar.setBorder(new MatteBorder(0, 0, 1, 0, BORDER_C));

        // Dates
        searchBar.add(new JLabel("Ngày nhận:"));
        txtCheckIn = new JTextField(10);
        txtCheckIn.setText(sdf.format(new Date()));
        searchBar.add(txtCheckIn);

        searchBar.add(new JLabel("Ngày trả:"));
        txtCheckOut = new JTextField(10);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        txtCheckOut.setText(sdf.format(cal.getTime()));
        searchBar.add(txtCheckOut);

        // Room Type
        searchBar.add(new JLabel("Loại phòng:"));
        cbRoomType = new JComboBox<>(new String[]{"Tất cả", "Standard", "Deluxe", "Family"});
        searchBar.add(cbRoomType);

        // Status Filter
        searchBar.add(new JLabel("Trạng thái:"));
        cbStatus = new JComboBox<>(new String[]{"Tất cả", "Trống", "Đang có khách", "Bảo trì", "Đang dọn dẹp"});
        searchBar.add(cbStatus);

        // Price Filter
        searchBar.add(new JLabel("Giá:"));
        cbPrice = new JComboBox<>(new String[]{"Mọi mức giá", "Dưới 500.000đ", "500k - 1 Triệu", "Trên 1 Triệu"});
        searchBar.add(cbPrice);

        JButton btnSearch = new JButton("Tìm phòng trống");
        btnSearch.setBackground(PRIMARY);
        btnSearch.setForeground(Color.WHITE);
        btnSearch.setFocusPainted(false);
        btnSearch.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnSearch.addActionListener(e -> performSearch());
        searchBar.add(btnSearch);

        // Auto-search on Enter or changes
        txtCheckIn.addActionListener(e -> performSearch());
        txtCheckOut.addActionListener(e -> performSearch());
        cbRoomType.addActionListener(e -> performSearch());
        cbStatus.addActionListener(e -> performSearch());
        cbPrice.addActionListener(e -> performSearch());

        // --- Results Section ---
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);
        contentPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        lblResults = new JLabel("Vui lòng chọn ngày để tìm kiếm các phòng khả dụng.");
        lblResults.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        lblResults.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMuted());
        contentPanel.add(lblResults, BorderLayout.NORTH);

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
        JPanel card = new JPanel();
        card.setPreferredSize(new Dimension(280, 360));
        card.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        card.setLayout(new BorderLayout());
        card.setBorder(new LineBorder(BORDER_C, 1, true));

        // Top: Image placeholder/Icon
        JPanel imgPanel = new JPanel(new GridBagLayout());
        imgPanel.setPreferredSize(new Dimension(280, 160));
        imgPanel.setBackground(quanlykhachsan.frontend.utils.ThemeManager.isDarkMode() ? new Color(30, 41, 59) : new Color(241, 245, 249));
        JLabel lblIcon = new JLabel("HOTEL ROOM"); 
        lblIcon.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblIcon.setForeground(new Color(148, 163, 184));
        imgPanel.add(lblIcon);
        card.add(imgPanel, BorderLayout.NORTH);

        // Center: Details
        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setBorder(new EmptyBorder(15, 15, 15, 15));
        details.setOpaque(false);

        JLabel name = new JLabel("Phòng " + r.getRoomNumber());
        name.setFont(new Font("Segoe UI", Font.BOLD, 18));
        details.add(name);
        details.add(Box.createVerticalStrut(5));

        JLabel price = new JLabel(nf.format(r.getPrice()) + " / đêm");
        price.setFont(new Font("Segoe UI", Font.BOLD, 16));
        price.setForeground(PRIMARY);
        details.add(price);
        details.add(Box.createVerticalStrut(10));

        JLabel amenities = new JLabel("<html>Tiện nội: Wifi, Điều hòa, TV, Nước nóng...</html>");
        amenities.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        amenities.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMuted());
        details.add(amenities);

        card.add(details, BorderLayout.CENTER);

        // Bottom: Action
        JButton btnBook = new JButton("Đặt ngay");
        btnBook.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnBook.setPreferredSize(new Dimension(0, 45));
        btnBook.setFocusPainted(false);
        
        String st = r.getStatus() != null ? r.getStatus().toLowerCase() : "available";
        if (st.equals("available")) {
            btnBook.setBackground(PRIMARY);
            btnBook.setForeground(Color.WHITE);
            btnBook.addActionListener(e -> {
                Window owner = SwingUtilities.getWindowAncestor(this);
                BookingWizardDialog dialog = new BookingWizardDialog(owner, currentUser, r, lastCin, lastCout);
                dialog.setVisible(true);
                performSearch(); // Refresh list after potential booking
            });
            // Also color the top image badge
            imgPanel.setBackground(quanlykhachsan.frontend.utils.ThemeManager.isDarkMode() ? new Color(30, 41, 59) : new Color(241, 245, 249)); // pale blue
        } else {
            btnBook.setEnabled(false);
            btnBook.setForeground(Color.WHITE);
            boolean isDark = quanlykhachsan.frontend.utils.ThemeManager.isDarkMode();
            if (st.equals("booked")) {
               btnBook.setText("ĐÃ TẠO ĐẶT CHỖ");
               btnBook.setBackground(new Color(234, 179, 8)); // Yellow
               imgPanel.setBackground(isDark ? new Color(133, 77, 14) : new Color(254, 249, 195));
            } else if (st.equals("occupied")) {
               btnBook.setText("ĐANG CÓ KHÁCH");
               btnBook.setBackground(new Color(220, 38, 38)); // Red
               imgPanel.setBackground(isDark ? new Color(127, 29, 29) : new Color(254, 226, 226));
            } else if (st.equals("maintenance")) {
               btnBook.setText("ĐANG BẢO TRÌ");
               btnBook.setBackground(new Color(156, 163, 175)); // Gray
               imgPanel.setBackground(isDark ? new Color(55, 65, 81) : new Color(243, 244, 246));
            } else if (st.equals("cleaning")) {
               btnBook.setText("ĐANG DỌN DẸP");
               btnBook.setBackground(new Color(56, 189, 248)); // Sky Blue
               imgPanel.setBackground(isDark ? new Color(12, 74, 110) : new Color(224, 242, 254));
            } else {
               btnBook.setText("KHÔNG KHẢ DỤNG");
               btnBook.setBackground(Color.GRAY);
            }
        }
        
        card.add(btnBook, BorderLayout.SOUTH);

        return card;
    }


}
