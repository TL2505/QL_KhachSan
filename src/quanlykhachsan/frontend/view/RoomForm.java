package quanlykhachsan.frontend.view;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import quanlykhachsan.backend.model.Room;
import quanlykhachsan.frontend.api.RoomAPI;
import quanlykhachsan.frontend.api.BookingAPI;
import quanlykhachsan.frontend.MainUI;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class RoomForm extends JPanel {

    private JPanel gridPanel;
    private JButton btnRefresh;

    public RoomForm() {
        setLayout(new BorderLayout());
        initUI();
        loadRooms();
    }

    private void initUI() {
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnRefresh = new JButton("Làm mới danh sách phòng");
        btnRefresh.addActionListener(e -> loadRooms());
        topPanel.add(btnRefresh);

        // Chú thích màu
        topPanel.add(new JLabel("  |  "));
        topPanel.add(createLegend("Trống", Color.GREEN));
        topPanel.add(createLegend("Đã đặt", Color.YELLOW));
        topPanel.add(createLegend("Có khách", Color.RED));
        topPanel.add(createLegend("Cần dọn", Color.ORANGE));
        topPanel.add(createLegend("Bảo trì", Color.GRAY));

        add(topPanel, BorderLayout.NORTH);

        gridPanel = new JPanel(new GridLayout(0, 5, 10, 10)); // 5 cột, row tự do
        gridPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(gridPanel);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createLegend(String text, Color color) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JLabel lblColor = new JLabel("   ");
        lblColor.setOpaque(true);
        lblColor.setBackground(color);
        lblColor.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        p.add(lblColor);
        p.add(new JLabel(text));
        return p;
    }

    public void loadRooms() {
        gridPanel.removeAll();
        SwingWorker<List<Room>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Room> doInBackground() throws Exception {
                return RoomAPI.getAllRooms();
            }

            @Override
            protected void done() {
                try {
                    List<Room> rooms = get();
                    if (rooms == null || rooms.isEmpty()) {
                        gridPanel.add(new JLabel("Không có dữ liệu phòng!"));
                    } else {
                        for (Room r : rooms) {
                            JButton btnRoom = new JButton("<html><center><h1>" + r.getRoomNumber() + "</h1>" + r.getStatus() + "</center></html>");
                            btnRoom.setPreferredSize(new Dimension(100, 100));
                            
                            switch (r.getStatus().toLowerCase()) {
                                case "available":
                                    btnRoom.setBackground(Color.GREEN);
                                    btnRoom.setForeground(Color.BLACK);
                                    break;
                                case "booked":
                                    btnRoom.setBackground(Color.YELLOW);
                                    btnRoom.setForeground(Color.BLACK);
                                    break;
                                case "occupied":
                                    btnRoom.setBackground(Color.RED);
                                    btnRoom.setForeground(Color.WHITE);
                                    break;
                                case "maintenance":
                                    btnRoom.setBackground(Color.GRAY);
                                    btnRoom.setForeground(Color.WHITE);
                                    break;
                                case "dirty":
                                    btnRoom.setBackground(Color.ORANGE);
                                    btnRoom.setForeground(Color.BLACK);
                                    break;
                                default:
                                    btnRoom.setBackground(Color.WHITE);
                            }

                            // TOOLTIP (HOVER)
                            StringBuilder tooltip = new StringBuilder("<html><body style='width: 250px; padding: 10px; font-family: sans-serif;'>");
                            tooltip.append("<h2 style='margin: 0 0 5px 0; color: #2980b9;'>Phòng ").append(r.getRoomNumber()).append("</h2>");
                            tooltip.append("<div style='margin-bottom: 5px;'><b>Trạng thái:</b> <span style='color: #e67e22;'>").append(r.getStatus()).append("</span></div>");
                            tooltip.append("<div style='margin-bottom: 5px;'><b>Giá phòng:</b> <span style='color: #27ae60;'>").append(String.format("%,.0f VNĐ", r.getPrice())).append("</span></div>");
                            
                            if (r.getCustomerName() != null) {
                                tooltip.append("<hr style='border: 0; border-top: 1px solid #ccc; margin: 10px 0;'>");
                                tooltip.append("<b style='color: #c0392b;'>THÔNG TIN KHÁCH:</b><br/>");
                                tooltip.append("<div style='margin-top: 5px;'><b>Khách hàng:</b> ").append(r.getCustomerName()).append("</div>");
                                tooltip.append("<div><b>SĐT:</b> ").append(r.getCustomerPhone()).append("</div>");
                                tooltip.append("<div><b>Check-in:</b> ").append(r.getCheckInDate()).append("</div>");
                                tooltip.append("<div><b>Check-out:</b> ").append(r.getCheckOutDate()).append("</div>");
                            }
                            tooltip.append("</body></html>");
                            btnRoom.setToolTipText(tooltip.toString());

                            // CONTEXT MENU (CHUỘT PHẢI)
                            btnRoom.addMouseListener(new MouseAdapter() {
                                @Override
                                public void mousePressed(MouseEvent e) {
                                    if (SwingUtilities.isRightMouseButton(e)) {
                                        showContextMenu(e, r);
                                    }
                                }
                            });

                            gridPanel.add(btnRoom);
                        }
                    }
                    gridPanel.revalidate();
                    gridPanel.repaint();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private void showContextMenu(MouseEvent e, Room r) {
        JPopupMenu menu = new JPopupMenu();
        MainUI mainUI = (MainUI) SwingUtilities.getWindowAncestor(this);
        
        // 1. TRƯỜNG HỢP PHÒNG TRỐNG -> ĐẶT PHÒNG
        if ("available".equalsIgnoreCase(r.getStatus())) {
            JMenuItem itemBook = new JMenuItem("Đặt phòng nhanh");
            itemBook.addActionListener(al -> {
                if (mainUI != null) {
                    mainUI.getBookingForm().preFillRoom(r.getRoomNumber());
                    mainUI.switchTab("Đặt / Nhận phòng");
                }
            });
            menu.add(itemBook);
            
            JMenuItem itemMaintenance = new JMenuItem("Bảo trì phòng");
            itemMaintenance.addActionListener(al -> {
                 RoomAPI.updateRoomStatus(r.getId(), "maintenance");
                 loadRooms();
            });
            menu.add(itemMaintenance);
        }

        // 2. TRƯỜNG HỢP PHÒNG ĐÃ ĐẶT -> NHẬN PHÒNG
        if ("booked".equalsIgnoreCase(r.getStatus())) {
            JMenuItem itemCheckIn = new JMenuItem("Nhận phòng (Check-in)");
            itemCheckIn.addActionListener(al -> {
                if (r.getBookingId() != null) {
                    String res = BookingAPI.checkIn(r.getBookingId());
                    JOptionPane.showMessageDialog(this, res);
                    loadRooms();
                }
            });
            menu.add(itemCheckIn);
        }

        // 3. TRƯỜNG HỢP PHÒNG ĐANG Ở -> THANH TOÁN & CHECK-OUT
        if ("occupied".equalsIgnoreCase(r.getStatus())) {
            JMenuItem itemPayment = new JMenuItem("Thanh toán & Check-out");
            itemPayment.addActionListener(al -> {
                if (mainUI != null && r.getBookingId() != null) {
                    mainUI.getPaymentForm().setData(r.getBookingId(), r.getPrice()); // Giả định dùng giá phòng làm tiền tạm tính
                    mainUI.switchTab("Thanh toán");
                }
            });
            menu.add(itemPayment);
        }

        // 4. TRƯỜNG HỢP PHÒNG CẦN DỌN -> HOÀN TẤT DỌN DẸP
        if ("dirty".equalsIgnoreCase(r.getStatus())) {
            JMenuItem itemClean = new JMenuItem("Đã dọn dẹp xong");
            itemClean.addActionListener(al -> {
                RoomAPI.updateRoomStatus(r.getId(), "available");
                loadRooms();
            });
            menu.add(itemClean);
        }
        
        // 5. TRƯỜNG HỢP CẦN XẢ BẢO TRÌ
        if ("maintenance".equalsIgnoreCase(r.getStatus())) {
             JMenuItem itemFinish = new JMenuItem("Hoàn tất bảo trì");
             itemFinish.addActionListener(al -> {
                RoomAPI.updateRoomStatus(r.getId(), "available");
                loadRooms();
            });
            menu.add(itemFinish);
        }

        menu.show(e.getComponent(), e.getX(), e.getY());
    }
}
