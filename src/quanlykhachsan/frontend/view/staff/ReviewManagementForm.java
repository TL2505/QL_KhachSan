package quanlykhachsan.frontend.view.staff;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;

import quanlykhachsan.backend.interaction.Review;
import quanlykhachsan.frontend.api.ReviewAPI;

public class ReviewManagementForm extends JPanel {

    private JTable table;
    private DefaultTableModel tableModel;
    private JLabel lblStatus;

    private final Color PRIMARY   = new Color(37, 99, 235);
    private final Color BG        = quanlykhachsan.frontend.utils.ThemeManager.getBgPanel();
    private final Color CARD_BG   = quanlykhachsan.frontend.utils.ThemeManager.getCardBg();
    private final Color BORDER_C  = quanlykhachsan.frontend.utils.ThemeManager.getBorderColor();
    private final Color DANGER    = new Color(239, 68, 68);

    private SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public ReviewManagementForm() {
        setLayout(new BorderLayout(20, 20));
        setBackground(BG);
        setBorder(new EmptyBorder(25, 25, 25, 25));

        initUI();
        loadData();
    }

    private void initUI() {
        // --- Header ---
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        
        JLabel lblTitle = new JLabel("Chăm sóc & Phản hồi Khách hàng");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        header.add(lblTitle, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        
        JButton btnRefresh = new JButton("Làm mới dữ liệu");
        btnRefresh.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRefresh.addActionListener(e -> loadData());
        actions.add(btnRefresh);

        JButton btnDelete = new JButton("Xóa đánh giá") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(DANGER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                super.paintComponent(g);
            }
        };
        btnDelete.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnDelete.setForeground(Color.WHITE);
        btnDelete.setContentAreaFilled(false);
        btnDelete.setBorderPainted(false);
        btnDelete.setFocusPainted(false);
        btnDelete.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnDelete.addActionListener(e -> deleteSelected());
        actions.add(btnDelete);

        header.add(actions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // --- Table ---
        String[] columns = {"ID", "Khách hàng", "Phòng ID", "Sao", "Bình luận", "Ngày gửi"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        styleTable(table);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new LineBorder(BORDER_C, 1, true));
        scroll.getViewport().setBackground(CARD_BG);
        
        add(scroll, BorderLayout.CENTER);

        lblStatus = new JLabel("Sẵn sàng");
        lblStatus.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        add(lblStatus, BorderLayout.SOUTH);
    }

    private void styleTable(JTable table) {
        table.setRowHeight(40);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        table.getTableHeader().setBackground(quanlykhachsan.frontend.utils.ThemeManager.isDarkMode() ? new Color(30, 41, 59) : new Color(241, 245, 249));
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        
        DefaultTableCellRenderer centerRender = new DefaultTableCellRenderer();
        centerRender.setHorizontalAlignment(JLabel.CENTER);
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(3).setCellRenderer(centerRender);
    }

    private void loadData() {
        lblStatus.setText("Đang tải dữ liệu...");
        new SwingWorker<List<Review>, Void>() {
            @Override protected List<Review> doInBackground() { return ReviewAPI.getAllReviews(); }
            @Override protected void done() {
                try {
                    List<Review> list = get();
                    tableModel.setRowCount(0);
                    for (Review r : list) {
                        tableModel.addRow(new Object[]{
                            r.getId(),
                            r.getCustomerName(),
                            r.getRoomId(),
                            r.getRating() + " sao",
                            r.getComment(),
                            sdf.format(r.getCreatedAt())
                        });
                    }
                    lblStatus.setText("Đã tải " + list.size() + " đánh giá.");
                } catch (Exception e) {
                    lblStatus.setText("Lỗi: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một đánh giá để xóa!");
            return;
        }

        int id = (int) tableModel.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn xóa đánh giá ID: " + id + "?", "Xác nhận", JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            new SwingWorker<String, Void>() {
                @Override protected String doInBackground() { return ReviewAPI.deleteReview(id); }
                @Override protected void done() {
                    try {
                        if ("Success".equals(get())) {
                            loadData();
                        } else {
                            JOptionPane.showMessageDialog(ReviewManagementForm.this, get());
                        }
                    } catch (Exception e) {}
                }
            }.execute();
        }
    }
}
