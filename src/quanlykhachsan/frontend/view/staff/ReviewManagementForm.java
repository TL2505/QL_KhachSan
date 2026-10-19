package quanlykhachsan.frontend.view.staff;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import quanlykhachsan.backend.interaction.Review;
import quanlykhachsan.frontend.api.ReviewAPI;
import quanlykhachsan.frontend.utils.ThemeManager;
import quanlykhachsan.frontend.utils.UIThemeTokens;

/**
 * Staff and Management PMS Review Management Screen.
 * Fully EDT-safe, asynchronous network dispatch via SwingWorker,
 * adhering to the FlatLaf Dark Luxury aesthetic.
 */
public class ReviewManagementForm extends JPanel {

    private JTable table;
    private DefaultTableModel tableModel;
    private JLabel lblStatus;
    private List<Review> cachedReviews = new ArrayList<>();

    private final Color GOLD_ACCENT    = new Color(212, 175, 55);
    private final Color GOLD_HOVER     = new Color(180, 145, 35);
    private final Color SUCCESS_COLOR  = new Color(16, 185, 129);
    private final Color WARNING_COLOR  = new Color(245, 158, 11);
    private final Color DANGER_COLOR   = new Color(239, 68, 68);

    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public ReviewManagementForm() {
        setLayout(new BorderLayout(20, 20));
        setBackground(ThemeManager.getBgPanel());
        setBorder(new EmptyBorder(25, 25, 25, 25));

        initUI();
        loadData();
    }

    private void initUI() {
        // --- Header ---
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setOpaque(false);

        JLabel lblTitle = new JLabel("Quản Lý Đánh Giá & Danh Tiếng 5 Sao");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitle.setForeground(ThemeManager.getTextMain());
        titlePanel.add(lblTitle);

        JLabel lblSubtitle = new JLabel("Theo dõi đánh giá lưu trú thực tế, tiêu chí 4 thành phần & phản hồi quản lý");
        lblSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblSubtitle.setForeground(ThemeManager.getTextMuted());
        titlePanel.add(Box.createVerticalStrut(4));
        titlePanel.add(lblSubtitle);

        header.add(titlePanel, BorderLayout.WEST);

        // --- Actions Toolbar ---
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);

        JButton btnRefresh = createStyledButton("Làm mới dữ liệu", ThemeManager.getCardBg(), ThemeManager.getTextMain(), false);
        btnRefresh.addActionListener(e -> loadData());
        actions.add(btnRefresh);

        JButton btnReply = createStyledButton("Phản hồi Đánh giá", GOLD_ACCENT, Color.BLACK, true);
        btnReply.addActionListener(e -> openReplyDialog());
        actions.add(btnReply);

        JButton btnDelete = createStyledButton("Xóa đánh giá", DANGER_COLOR, Color.WHITE, true);
        btnDelete.addActionListener(e -> deleteSelected());
        actions.add(btnDelete);

        header.add(actions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // --- Table Definition ---
        String[] columns = {
            "ID", "Khách hàng", "Booking #", "Phòng", "Điểm tổng",
            "Phòng", "F&B", "Dịch vụ", "Vệ sinh", "Bình luận",
            "Trạng thái phản hồi", "Phản hồi BQL", "Ngày gửi"
        };

        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        table = new JTable(tableModel);
        styleTable(table);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new LineBorder(ThemeManager.getBorderColor(), 1, true));
        scroll.getViewport().setBackground(ThemeManager.getCardBg());

        add(scroll, BorderLayout.CENTER);

        // --- Status Bar ---
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        lblStatus = new JLabel("Sẵn sàng");
        lblStatus.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblStatus.setForeground(ThemeManager.getTextMuted());
        footer.add(lblStatus, BorderLayout.WEST);

        add(footer, BorderLayout.SOUTH);
    }

    private JButton createStyledButton(String text, Color bg, Color fg, boolean customPaint) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                if (customPaint) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(bg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setForeground(fg);
        if (customPaint) {
            btn.setContentAreaFilled(false);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
        }
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(btn.getPreferredSize().width + 16, 36));
        return btn;
    }

    private void styleTable(JTable table) {
        table.setRowHeight(UIThemeTokens.TABLE_ROW_HEIGHT > 0 ? UIThemeTokens.TABLE_ROW_HEIGHT : 42);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.getTableHeader().setBackground(ThemeManager.isDarkMode() ? new Color(25, 33, 48) : new Color(241, 245, 249));
        table.getTableHeader().setForeground(ThemeManager.getTextMain());
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        DefaultTableCellRenderer centerRender = new DefaultTableCellRenderer();
        centerRender.setHorizontalAlignment(JLabel.CENTER);

        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(0).setCellRenderer(centerRender);

        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(70);
        table.getColumnModel().getColumn(2).setCellRenderer(centerRender);

        table.getColumnModel().getColumn(3).setPreferredWidth(60);
        table.getColumnModel().getColumn(3).setCellRenderer(centerRender);

        table.getColumnModel().getColumn(4).setPreferredWidth(80);
        table.getColumnModel().getColumn(4).setCellRenderer(centerRender);

        table.getColumnModel().getColumn(5).setPreferredWidth(60);
        table.getColumnModel().getColumn(5).setCellRenderer(centerRender);
        table.getColumnModel().getColumn(6).setPreferredWidth(60);
        table.getColumnModel().getColumn(6).setCellRenderer(centerRender);
        table.getColumnModel().getColumn(7).setPreferredWidth(60);
        table.getColumnModel().getColumn(7).setCellRenderer(centerRender);
        table.getColumnModel().getColumn(8).setPreferredWidth(60);
        table.getColumnModel().getColumn(8).setCellRenderer(centerRender);

        table.getColumnModel().getColumn(9).setPreferredWidth(220);
        table.getColumnModel().getColumn(10).setPreferredWidth(130);

        // Render response status badge
        table.getColumnModel().getColumn(10).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);
                setHorizontalAlignment(JLabel.CENTER);
                setFont(new Font("Segoe UI", Font.BOLD, 12));
                if (value != null && value.toString().contains("Đã phản hồi")) {
                    setForeground(SUCCESS_COLOR);
                } else {
                    setForeground(WARNING_COLOR);
                }
                return c;
            }
        });

        table.getColumnModel().getColumn(11).setPreferredWidth(220);
        table.getColumnModel().getColumn(12).setPreferredWidth(130);
        table.getColumnModel().getColumn(12).setCellRenderer(centerRender);
    }

    public void loadData() {
        lblStatus.setText("Đang tải dữ liệu từ máy chủ...");
        new SwingWorker<List<Review>, Void>() {
            @Override
            protected List<Review> doInBackground() {
                return ReviewAPI.getAllReviews();
            }

            @Override
            protected void done() {
                try {
                    List<Review> list = get();
                    cachedReviews = list != null ? list : new ArrayList<>();
                    tableModel.setRowCount(0);

                    for (Review r : cachedReviews) {
                        String bookingStr = r.getBookingId() != null && r.getBookingId() > 0 ? "#" + r.getBookingId() : "-";
                        String roomStr = r.getRoomNumber() != null && !r.getRoomNumber().isBlank() ? r.getRoomNumber() : String.valueOf(r.getRoomId());
                        String ratingOverallStr = (r.getRatingOverall() != null ? r.getRatingOverall().toString() : String.valueOf(r.getRating())) + " ★";
                        String hasReply = (r.getManagementResponse() != null && !r.getManagementResponse().isBlank()) ? "Đã phản hồi" : "Chờ phản hồi";
                        String replyContent = r.getManagementResponse() != null ? r.getManagementResponse() : "-";
                        String dateStr = r.getCreatedAt() != null ? sdf.format(r.getCreatedAt()) : "-";

                        tableModel.addRow(new Object[]{
                            r.getId(),
                            r.getCustomerName() != null ? r.getCustomerName() : "Khách #" + r.getCustomerId(),
                            bookingStr,
                            roomStr,
                            ratingOverallStr,
                            r.getRatingRoom() + " ★",
                            r.getRatingFnb() + " ★",
                            r.getRatingService() + " ★",
                            r.getRatingCleanliness() + " ★",
                            r.getComment() != null ? r.getComment() : "",
                            hasReply,
                            replyContent,
                            dateStr
                        });
                    }
                    lblStatus.setText("Đã tải " + cachedReviews.size() + " đánh giá từ hệ thống.");
                } catch (Exception e) {
                    lblStatus.setText("Lỗi nạp dữ liệu: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void openReplyDialog() {
        int row = table.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng chọn một đánh giá trong bảng để phản hồi!",
                    "Chưa chọn đánh giá",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int reviewId = (int) tableModel.getValueAt(row, 0);
        Review selected = null;
        for (Review r : cachedReviews) {
            if (r.getId() == reviewId) {
                selected = r;
                break;
            }
        }
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Không tìm thấy dữ liệu chi tiết đánh giá!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        ManagementReplyDialog dialog = new ManagementReplyDialog(parentWindow, selected, this);
        dialog.setVisible(true);
    }

    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng chọn một đánh giá để xóa!",
                    "Chưa chọn đánh giá",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int id = (int) tableModel.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc muốn xóa đánh giá ID: " + id + "?",
                "Xác nhận xóa đánh giá",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            lblStatus.setText("Đang xóa đánh giá #" + id + "...");
            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() {
                    return ReviewAPI.deleteReview(id);
                }

                @Override
                protected void done() {
                    try {
                        String res = get();
                        if ("Success".equals(res)) {
                            JOptionPane.showMessageDialog(ReviewManagementForm.this,
                                    "Đã xóa đánh giá thành công!",
                                    "Thông báo",
                                    JOptionPane.INFORMATION_MESSAGE);
                            loadData();
                        } else {
                            JOptionPane.showMessageDialog(ReviewManagementForm.this, res, "Lỗi", JOptionPane.ERROR_MESSAGE);
                            lblStatus.setText("Lỗi: " + res);
                        }
                    } catch (Exception e) {
                        lblStatus.setText("Lỗi xóa: " + e.getMessage());
                    }
                }
            }.execute();
        }
    }

    /**
     * Modal dialog allowing Receptionists and General Managers to draft and publish
     * official responses to guest reviews using SwingWorker.
     */
    public static class ManagementReplyDialog extends JDialog {
        private final Review review;
        private final ReviewManagementForm parentForm;
        private JTextArea txtReply;
        private JButton btnSubmit;
        private JButton btnCancel;
        private JLabel lblDialogStatus;

        public ManagementReplyDialog(Window owner, Review review, ReviewManagementForm parentForm) {
            super(owner, "Phản Hồi Đánh Giá Của Khách Hàng", ModalityType.APPLICATION_MODAL);
            this.review = review;
            this.parentForm = parentForm;

            setSize(620, 540);
            setLocationRelativeTo(owner);
            setResizable(false);

            initDialogUI();
        }

        private void initDialogUI() {
            JPanel root = new JPanel(new BorderLayout(15, 15));
            root.setBackground(ThemeManager.getBgPanel());
            root.setBorder(new EmptyBorder(20, 24, 20, 24));

            // --- Top Header ---
            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);

            JLabel title = new JLabel("Phản hồi chính thức từ Ban Quản Lý");
            title.setFont(new Font("Segoe UI", Font.BOLD, 18));
            title.setForeground(ThemeManager.getTextMain());
            top.add(title, BorderLayout.NORTH);

            JLabel sub = new JLabel("Khách hàng: " + (review.getCustomerName() != null ? review.getCustomerName() : "Khách #" + review.getCustomerId())
                    + "  |  Phòng: " + (review.getRoomNumber() != null ? review.getRoomNumber() : String.valueOf(review.getRoomId()))
                    + "  |  Đánh giá: " + (review.getRatingOverall() != null ? review.getRatingOverall() : review.getRating()) + " ★");
            sub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            sub.setForeground(new Color(212, 175, 55));
            sub.setBorder(new EmptyBorder(6, 0, 0, 0));
            top.add(sub, BorderLayout.CENTER);

            root.add(top, BorderLayout.NORTH);

            // --- Content Panel ---
            JPanel content = new JPanel();
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setOpaque(false);

            // Guest Comment Display (Read-only)
            JLabel lblGuestComment = new JLabel("Bình luận của khách hàng:");
            lblGuestComment.setFont(new Font("Segoe UI", Font.BOLD, 13));
            lblGuestComment.setForeground(ThemeManager.getTextMain());
            content.add(lblGuestComment);
            content.add(Box.createVerticalStrut(6));

            JTextArea txtGuestComment = new JTextArea(review.getComment() != null ? review.getComment() : "(Không có bình luận văn bản)");
            txtGuestComment.setLineWrap(true);
            txtGuestComment.setWrapStyleWord(true);
            txtGuestComment.setEditable(false);
            txtGuestComment.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            txtGuestComment.setBackground(ThemeManager.getCardBg());
            txtGuestComment.setForeground(ThemeManager.getTextMain());
            txtGuestComment.setBorder(new EmptyBorder(8, 8, 8, 8));

            JScrollPane scrollGuest = new JScrollPane(txtGuestComment);
            scrollGuest.setPreferredSize(new Dimension(570, 85));
            scrollGuest.setBorder(new LineBorder(ThemeManager.getBorderColor(), 1, true));
            content.add(scrollGuest);
            content.add(Box.createVerticalStrut(14));

            // Management Reply Input Area
            JLabel lblReply = new JLabel("Nội dung phản hồi công khai của Ban Quản Lý:");
            lblReply.setFont(new Font("Segoe UI", Font.BOLD, 13));
            lblReply.setForeground(ThemeManager.getTextMain());
            content.add(lblReply);
            content.add(Box.createVerticalStrut(6));

            txtReply = new JTextArea(review.getManagementResponse() != null ? review.getManagementResponse() : "");
            txtReply.setLineWrap(true);
            txtReply.setWrapStyleWord(true);
            txtReply.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            txtReply.setBackground(ThemeManager.getCardBg());
            txtReply.setForeground(ThemeManager.getTextMain());
            txtReply.setCaretColor(ThemeManager.getTextMain());
            txtReply.setBorder(new EmptyBorder(8, 8, 8, 8));

            JScrollPane scrollReply = new JScrollPane(txtReply);
            scrollReply.setPreferredSize(new Dimension(570, 140));
            scrollReply.setBorder(new LineBorder(ThemeManager.getBorderColor(), 1, true));
            content.add(scrollReply);

            root.add(content, BorderLayout.CENTER);

            // --- Footer Buttons & Status ---
            JPanel footer = new JPanel(new BorderLayout());
            footer.setOpaque(false);

            lblDialogStatus = new JLabel("");
            lblDialogStatus.setFont(new Font("Segoe UI", Font.ITALIC, 12));
            lblDialogStatus.setForeground(ThemeManager.getTextMuted());
            footer.add(lblDialogStatus, BorderLayout.WEST);

            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            btnPanel.setOpaque(false);

            btnCancel = new JButton("Đóng");
            btnCancel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            btnCancel.addActionListener(e -> dispose());
            btnPanel.add(btnCancel);

            btnSubmit = new JButton("Xuất bản Phản hồi") {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(212, 175, 55));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            btnSubmit.setFont(new Font("Segoe UI", Font.BOLD, 13));
            btnSubmit.setForeground(Color.BLACK);
            btnSubmit.setContentAreaFilled(false);
            btnSubmit.setBorderPainted(false);
            btnSubmit.setFocusPainted(false);
            btnSubmit.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btnSubmit.setPreferredSize(new Dimension(170, 36));
            btnSubmit.addActionListener(e -> submitManagementReply());
            btnPanel.add(btnSubmit);

            footer.add(btnPanel, BorderLayout.EAST);
            root.add(footer, BorderLayout.SOUTH);

            setContentPane(root);
        }

        private void submitManagementReply() {
            String replyText = txtReply.getText().trim();
            if (replyText.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Vui lòng nhập nội dung phản hồi của khách sạn trước khi xuất bản!",
                        "Nội dung trống",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Disable buttons and show loading indicator
            btnSubmit.setEnabled(false);
            btnCancel.setEnabled(false);
            txtReply.setEnabled(false);
            lblDialogStatus.setText("Đang xuất bản phản hồi lên máy chủ...");

            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() {
                    return ReviewAPI.replyReview(review.getId(), replyText);
                }

                @Override
                protected void done() {
                    btnSubmit.setEnabled(true);
                    btnCancel.setEnabled(true);
                    txtReply.setEnabled(true);
                    try {
                        String res = get();
                        if ("Success".equalsIgnoreCase(res)) {
                            JOptionPane.showMessageDialog(ManagementReplyDialog.this,
                                    "Phản hồi của Ban Quản Lý đã được xuất bản công khai lên hệ thống!",
                                    "Xuất bản thành công",
                                    JOptionPane.INFORMATION_MESSAGE);
                            dispose();
                            parentForm.loadData();
                        } else {
                            JOptionPane.showMessageDialog(ManagementReplyDialog.this,
                                    "Lỗi xuất bản: " + res,
                                    "Lỗi",
                                    JOptionPane.ERROR_MESSAGE);
                            lblDialogStatus.setText("Lỗi: " + res);
                        }
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(ManagementReplyDialog.this,
                                "Lỗi kết nối máy chủ: " + ex.getMessage(),
                                "Lỗi mạng",
                                JOptionPane.ERROR_MESSAGE);
                        lblDialogStatus.setText("Lỗi: " + ex.getMessage());
                    }
                }
            }.execute();
        }
    }
}
