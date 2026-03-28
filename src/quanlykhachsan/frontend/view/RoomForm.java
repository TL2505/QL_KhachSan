package quanlykhachsan.frontend.view;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import quanlykhachsan.backend.model.Room;
import quanlykhachsan.frontend.api.RoomAPI;

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
        // Cần gọi API trong Thread riêng để không block UI 
        SwingWorker<List<Room>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Room> doInBackground() throws Exception {
                // Gọi API lấy room
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
                                default:
                                    btnRoom.setBackground(Color.WHITE);
                            }
                            gridPanel.add(btnRoom);
                        }
                    }
                    gridPanel.revalidate();
                    gridPanel.repaint();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(RoomForm.this, "Lỗi khi lấy dữ liệu phòng", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
}
