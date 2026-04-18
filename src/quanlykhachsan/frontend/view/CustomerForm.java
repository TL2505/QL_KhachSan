package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.ArrayList;
import quanlykhachsan.backend.model.Customer;
import quanlykhachsan.frontend.api.CustomerAPI;

public class CustomerForm extends JPanel {

    // ─── State ────────────────────────────────────────────────────────────
    private JTable customerTable;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> sorter;

    private JTextField txtSearch;
    private JTextField txtName, txtPhone, txtCard, txtLoyaltyLevel, txtLoyaltyPoints;
    private JButton btnSave, btnReset, btnDelete, btnRefresh, btnViewHistory;
    private JLabel lblFormTitle, lblStatus;

    private int editingId = -1; // -1 = đang thêm mới, >0 = ID thực tế trong DB
    private List<Customer> customersList = new ArrayList<>();

    private final Color PRIMARY    = new Color(37, 99, 235);
    private final Color SUCCESS    = new Color(5, 150, 105);
    private final Color DANGER     = new Color(220, 38, 38);
    private final Color MUTED      = new Color(107, 114, 128);
    private final Color BG_PANEL   = quanlykhachsan.frontend.utils.ThemeManager.getBgPanel();
    private final Color BORDER_CLR = quanlykhachsan.frontend.utils.ThemeManager.getBorderColor();
    private final Color ROW_EVEN   = quanlykhachsan.frontend.utils.ThemeManager.isDarkMode() ? new Color(15, 23, 42) : new Color(249, 250, 251);
    private final Color ROW_ODD    = quanlykhachsan.frontend.utils.ThemeManager.getCardBg();
    private final Color ROW_SELECT = new Color(219, 234, 254);

    public CustomerForm() {
        setLayout(new BorderLayout(0, 0));
        setBackground(BG_PANEL);
        initUI();
        loadCustomers();
    }

    private void initUI() {
        // ── Status bar (top) ──────────────────────────────────────────────
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER_CLR),
            new EmptyBorder(10, 16, 10, 16)
        ));
        JLabel pageTitle = new JLabel("Quản lý Khách hàng");
        pageTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        pageTitle.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        lblStatus = new JLabel(" ");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        statusBar.add(pageTitle, BorderLayout.WEST);
        statusBar.add(lblStatus, BorderLayout.EAST);
        add(statusBar, BorderLayout.NORTH);

        // ── Main content ──────────────────────────────────────────────────
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(300);
        splitPane.setDividerSize(1);
        splitPane.setBorder(null);
        splitPane.setBackground(BG_PANEL);

        splitPane.setLeftComponent(buildFormPanel());
        splitPane.setRightComponent(buildTablePanel());
        add(splitPane, BorderLayout.CENTER);
    }

    private JPanel buildFormPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        wrapper.setBorder(new MatteBorder(0, 0, 0, 1, BORDER_CLR));

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        form.setBorder(new EmptyBorder(20, 20, 20, 20));

        lblFormTitle = new JLabel("📝 Thông tin chi tiết");
        lblFormTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblFormTitle.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        lblFormTitle.setAlignmentX(LEFT_ALIGNMENT);

        JLabel lblSub = new JLabel("Nhập thông tin khách hàng");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(MUTED);
        lblSub.setAlignmentX(LEFT_ALIGNMENT);

        form.add(lblFormTitle);
        form.add(Box.createVerticalStrut(2));
        form.add(lblSub);
        form.add(Box.createVerticalStrut(20));
        form.add(createSeparator());
        form.add(Box.createVerticalStrut(16));

        // Ho ten
        form.add(createFieldLabel("Họ và Tên *"));
        txtName = createField("Nguyễn Văn A");
        txtName.setAlignmentX(LEFT_ALIGNMENT);
        txtName.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        form.add(txtName);
        form.add(Box.createVerticalStrut(12));

        // SDT
        form.add(createFieldLabel("Số Điện Thoại *"));
        txtPhone = createField("0901 234 567  (10 số)");
        txtPhone.setAlignmentX(LEFT_ALIGNMENT);
        txtPhone.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        form.add(txtPhone);
        form.add(Box.createVerticalStrut(12));

        // CCCD
        form.add(createFieldLabel("Số CCCD / CMND *"));
        txtCard = createField("012345678901  (12 số)");
        txtCard.setAlignmentX(LEFT_ALIGNMENT);
        txtCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        form.add(txtCard);
        form.add(Box.createVerticalStrut(12));

        // Loyalty Info (Read-only)
        form.add(createFieldLabel("Hạng Thành Viên"));
        txtLoyaltyLevel = createField("Silver");
        txtLoyaltyLevel.setEditable(false);
        txtLoyaltyLevel.setBackground(new Color(243, 244, 246));
        txtLoyaltyLevel.setAlignmentX(LEFT_ALIGNMENT);
        txtLoyaltyLevel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        form.add(txtLoyaltyLevel);
        form.add(Box.createVerticalStrut(12));

        form.add(createFieldLabel("Điểm Tích Lũy"));
        txtLoyaltyPoints = createField("0");
        txtLoyaltyPoints.setEditable(false);
        txtLoyaltyPoints.setBackground(new Color(243, 244, 246));
        txtLoyaltyPoints.setAlignmentX(LEFT_ALIGNMENT);
        txtLoyaltyPoints.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        form.add(txtLoyaltyPoints);

        form.add(Box.createVerticalStrut(20));
        form.add(createSeparator());
        form.add(Box.createVerticalStrut(16));

        // Buttons
        btnSave = createActionButton("💾 Lưu Khách Hàng", SUCCESS);
        btnSave.setAlignmentX(LEFT_ALIGNMENT);
        btnSave.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnSave.addActionListener(e -> actionSave());

        btnReset = createGhostButton("🔄 Làm Mới Form");
        btnReset.setOpaque(false);
        btnReset.setAlignmentX(LEFT_ALIGNMENT);
        btnReset.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        btnReset.addActionListener(e -> resetForm());

        btnDelete = createActionButton("🗑️ Xóa Khách Hàng", DANGER);
        btnDelete.setAlignmentX(LEFT_ALIGNMENT);
        btnDelete.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnDelete.setEnabled(false);
        btnDelete.addActionListener(e -> actionDelete());

        btnViewHistory = createActionButton("📜 Xem Lịch Sử", new Color(14, 165, 233));
        btnViewHistory.setAlignmentX(LEFT_ALIGNMENT);
        btnViewHistory.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnViewHistory.setEnabled(false);
        btnViewHistory.addActionListener(e -> actionViewHistory());

        form.add(btnSave);
        form.add(Box.createVerticalStrut(8));
        form.add(btnReset);
        form.add(Box.createVerticalStrut(8));
        form.add(btnDelete);
        form.add(Box.createVerticalStrut(8));
        form.add(btnViewHistory);

        // BỌC form vào JScrollPane để có thanh cuộn
        JScrollPane formScroll = new JScrollPane(form,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        formScroll.setBorder(null);
        formScroll.getVerticalScrollBar().setUnitIncrement(16);

        wrapper.add(formScroll, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());

        // Thanh tìm kiếm
        JPanel searchBar = new JPanel(new BorderLayout(8, 0));
        searchBar.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        searchBar.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel searchIcon = new JLabel("TÌM: ");
        searchIcon.setFont(new Font("Segoe UI", Font.BOLD, 13));
        txtSearch = new JTextField();
        txtSearch.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtSearch.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_CLR, 1, true),
            new EmptyBorder(6, 10, 6, 10)
        ));
        txtSearch.putClientProperty("JTextField.placeholderText", "Tìm kiếm theo tên hoặc số điện thoại...");

        btnRefresh = new JButton("Tải Lại");
        btnRefresh.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnRefresh.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRefresh.addActionListener(e -> loadCustomers());

        searchBar.add(searchIcon, BorderLayout.WEST);
        searchBar.add(txtSearch, BorderLayout.CENTER);
        searchBar.add(btnRefresh, BorderLayout.EAST);
        panel.add(searchBar, BorderLayout.NORTH);

        // Bảng
        tableModel = new DefaultTableModel(
            new String[]{"RealID", "ID", "Họ và Tên", "Số Điện Thoại", "CCCD / CMND", "Hạng", "Điểm"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };

        customerTable = new JTable(tableModel);
        customerTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        customerTable.setRowHeight(38);
        customerTable.setShowGrid(false);
        customerTable.setIntercellSpacing(new Dimension(0, 0));
        customerTable.setSelectionBackground(ROW_SELECT);
        customerTable.setSelectionForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        customerTable.setFocusable(false);

        // Header style
        customerTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        customerTable.getTableHeader().setBackground(quanlykhachsan.frontend.utils.ThemeManager.isDarkMode() ? new Color(30, 41, 59) : new Color(241, 245, 249));
        customerTable.getTableHeader().setForeground(new Color(71, 85, 105));
        customerTable.getTableHeader().setBorder(new MatteBorder(0, 0, 1, 0, BORDER_CLR));
        customerTable.getTableHeader().setPreferredSize(new Dimension(0, 40));

        // Cấu hình các cột
        // Cột 0: RealID (Ẩn)
        customerTable.getColumnModel().getColumn(0).setMinWidth(0);
        customerTable.getColumnModel().getColumn(0).setMaxWidth(0);
        customerTable.getColumnModel().getColumn(0).setPreferredWidth(0);
        
        // Cột 1: STT nhỏ
        customerTable.getColumnModel().getColumn(1).setPreferredWidth(40);
        customerTable.getColumnModel().getColumn(1).setMaxWidth(50);

        // Name, Phone, ID Card widths
        customerTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        customerTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        customerTable.getColumnModel().getColumn(4).setPreferredWidth(110);
        
        // Cột 5: Hạng
        customerTable.getColumnModel().getColumn(5).setPreferredWidth(80);
        customerTable.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                JLabel lbl = new JLabel(value != null ? value.toString() : "Silver");
                lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
                lbl.setOpaque(true);
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                String lvl = value != null ? value.toString() : "Silver";
                boolean isDark = quanlykhachsan.frontend.utils.ThemeManager.isDarkMode();
                if ("VIP".equals(lvl)) {
                    lbl.setBackground(isDark ? new Color(76, 29, 149) : new Color(245, 243, 255));
                    lbl.setForeground(isDark ? new Color(221, 214, 254) : new Color(109, 40, 217));
                } else if ("Gold".equals(lvl)) {
                    lbl.setBackground(isDark ? new Color(146, 64, 14) : new Color(255, 251, 235));
                    lbl.setForeground(isDark ? new Color(253, 230, 138) : new Color(180, 83, 9));
                } else {
                    lbl.setBackground(isDark ? new Color(30, 41, 59) : new Color(241, 245, 249));
                    lbl.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMuted());
                }
                if (isSelected) lbl.setBackground(new Color(219, 234, 254));
                return lbl;
            }
        });

        // Cột 6: Điểm
        customerTable.getColumnModel().getColumn(6).setPreferredWidth(70);
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        customerTable.getColumnModel().getColumn(6).setCellRenderer(rightRenderer);

        // Zebra stripes
        customerTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                if (!sel) setBackground(row % 2 == 0 ? ROW_EVEN : ROW_ODD);
                setBorder(new EmptyBorder(0, 12, 0, 12));
                return this;
            }
        });

        // Sorter để filter
        sorter = new TableRowSorter<>(tableModel);
        customerTable.setRowSorter(sorter);

        // Click hàng → điền form
        customerTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onRowSelected();
        });

        // Filter khi gõ tìm kiếm
        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        JScrollPane scroll = new JScrollPane(customerTable);
        scroll.setBorder(new MatteBorder(1, 0, 0, 0, BORDER_CLR));
        scroll.getViewport().setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    // ── LOGIC ─────────────────────────────────────────────────────────────

    public void loadCustomers() {
        setStatus("Đang tải dữ liệu...", MUTED);
        customerTable.clearSelection();
        
        SwingWorker<List<Customer>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Customer> doInBackground() throws Exception {
                return CustomerAPI.getAllCustomers();
            }
            @Override
            protected void done() {
                try {
                    customersList = get();
                    tableModel.setRowCount(0);
                    if (customersList != null) {
                        int stt = 1;
                        for (Customer c : customersList) {
                            tableModel.addRow(new Object[]{
                                c.getId(), 
                                stt++, 
                                c.getFullName(), 
                                c.getPhone(), 
                                c.getIdentityCard(),
                                c.getLoyaltyLevel() != null ? c.getLoyaltyLevel() : "Silver",
                                c.getLoyaltyPoints()
                            });
                        }
                        setStatus(customersList.size() + " khách hàng", SUCCESS);
                    } else {
                        setStatus("Không có dữ liệu", MUTED);
                    }
                } catch (Exception ex) {
                    setStatus("Lỗi tải dữ liệu", DANGER);
                }
            }
        };
        worker.execute();
    }

    private void onRowSelected() {
        int viewRow = customerTable.getSelectedRow();
        if (viewRow < 0) return;
        
        int modelRow = customerTable.convertRowIndexToModel(viewRow);
        int realId = (int) tableModel.getValueAt(modelRow, 0); // Lấy RealID từ cột 0
        
        Customer selected = null;
        for (Customer c : customersList) {
            if (c.getId() == realId) {
                selected = c;
                break;
            }
        }
        
        if (selected != null) {
            editingId = selected.getId();
            txtName.setText(selected.getFullName());
            txtPhone.setText(selected.getPhone());
            txtCard.setText(selected.getIdentityCard());
            txtLoyaltyLevel.setText(selected.getLoyaltyLevel() != null ? selected.getLoyaltyLevel() : "Silver");
            txtLoyaltyPoints.setText(String.valueOf(selected.getLoyaltyPoints()));
            
            lblFormTitle.setText("Cập Nhật Thông Tin");
            btnSave.setText("Cập Nhật");
            btnDelete.setEnabled(true);
            btnViewHistory.setEnabled(true);
        }
    }

    private void applyFilter() {
        String text = txtSearch.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            // Column indices changed: STT=1, Name=2, Phone=3
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text, 2, 3));
        }
    }

    private void actionSave() {
        String name  = txtName.getText().trim();
        String phone = txtPhone.getText().replaceAll("\\s+", "").trim();
        String card  = txtCard.getText().trim();

        if (name.isEmpty() || phone.isEmpty() || card.isEmpty()) {
            showWarn("Vui lòng nhập đầy đủ thông tin!"); return;
        }
        if (!phone.matches("\\d{10}")) {
            showWarn("Số điện thoại phải là 10 chữ số!"); return;
        }
        if (!card.matches("\\d{12}")) {
            showWarn("Số CCCD phải là 12 chữ số!"); return;
        }

        btnSave.setEnabled(false);
        setStatus("Đang xử lý...", MUTED);

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                Customer c = new Customer();
                c.setFullName(name);
                c.setPhone(phone);
                c.setIdentityCard(card);
                
                if (editingId != -1) {
                    c.setId(editingId);
                    return CustomerAPI.updateCustomer(c);
                } else {
                    return CustomerAPI.addCustomer(c);
                }
            }
            @Override
            protected void done() {
                btnSave.setEnabled(true);
                try {
                    String res = get();
                    if (res != null && res.startsWith("Success")) {
                        setStatus(editingId != -1 ? "Đã cập nhật!" : "Đã thêm mới!", SUCCESS);
                        resetForm();
                        loadCustomers();
                    } else {
                        setStatus("Thất bại: " + res, DANGER);
                    }
                } catch (Exception ex) {
                    setStatus("Lỗi kết nối server!", DANGER);
                }
            }
        };
        worker.execute();
    }

    private void actionDelete() {
        if (editingId == -1) return;
        
        int confirm = JOptionPane.showConfirmDialog(this,
            "Bạn có chắc chắn muốn xóa khách hàng ID: " + editingId + " ?\nLưu ý: Không thể xóa nếu khách hàng này đang có lịch sử đặt phòng.",
            "Xác nhận Xóa", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            
        if (confirm == JOptionPane.YES_OPTION) {
            btnDelete.setEnabled(false);
            setStatus("Đang xóa...", MUTED);
            
            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override
                protected String doInBackground() throws Exception {
                    return CustomerAPI.deleteCustomer(editingId);
                }
                @Override
                protected void done() {
                    btnDelete.setEnabled(true);
                    try {
                        String res = get();
                        if (res != null && res.startsWith("Success")) {
                            setStatus("Đã xóa khách hàng!", SUCCESS);
                            resetForm();
                            loadCustomers();
                        } else {
                            // Kiểm tra nội dung lỗi từ server
                            String errMsg = res != null ? res : "";
                            if (errMsg.contains("foreign key") || errMsg.contains("constraint") || errMsg.contains("Cannot delete")) {
                                JOptionPane.showMessageDialog(CustomerForm.this,
                                    "Không thể xóa khách hàng này!\n\n"
                                    + "Lý do: Khách hàng đang có lịch sử đặt phòng trong hệ thống.\n"
                                    + "Hãy xóa các đơn đặt phòng liên quan trước, sau đó thử lại.",
                                    "Không Thể Xóa", JOptionPane.WARNING_MESSAGE);
                            } else {
                                JOptionPane.showMessageDialog(CustomerForm.this,
                                    "Không thể xóa: " + res, "Lỗi Hệ Thống", JOptionPane.ERROR_MESSAGE);
                            }
                            setStatus("Xóa thất bại", DANGER);
                        }
                    } catch (Exception ex) {
                        // Kiểm tra nếu là lỗi Foreign Key
                        String errMsg = ex.getMessage() != null ? ex.getMessage() : "";
                        if (errMsg.contains("foreign key") || errMsg.contains("constraint") || errMsg.contains("Cannot delete")) {
                            JOptionPane.showMessageDialog(CustomerForm.this,
                                "Không thể xóa khách hàng này!\n\n"
                                + "Lý do: Khách hàng này đang có lịch sử đặt phòng trong hệ thống.\n"
                                + "Hãy xóa các đơn đặt phòng liên quan trước, sau đó thử lại.",
                                "Không Thể Xóa", JOptionPane.WARNING_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(CustomerForm.this,
                                "Lỗi kết nối server: " + ex.getMessage(),
                                "Lỗi Hệ Thống", JOptionPane.ERROR_MESSAGE);
                        }
                        setStatus("Xóa thất bại", DANGER);
                    }
                }
            };
            worker.execute();
        }
    }

    private void actionViewHistory() {
        if (editingId == -1) return;
        String customerName = txtName.getText();
        JFrame topFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
        CustomerHistoryDialog dialog = new CustomerHistoryDialog(topFrame, editingId, customerName);
        dialog.setVisible(true);
    }

    private void resetForm() {
        editingId = -1;
        txtName.setText("");
        txtPhone.setText("");
        txtCard.setText("");
        txtLoyaltyLevel.setText("Member");
        txtLoyaltyPoints.setText("0");
        lblFormTitle.setText("Thêm Khách Hàng Mới");
        btnSave.setText("Lưu Khách Hàng");
        btnDelete.setEnabled(false);
        btnViewHistory.setEnabled(false);
        customerTable.clearSelection();
    }

    private void setStatus(String msg, Color color) {
        lblStatus.setText(msg);
        lblStatus.setForeground(color);
    }

    private void showWarn(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Cảnh báo", JOptionPane.WARNING_MESSAGE);
    }

    private JLabel createFieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        return lbl;
    }

    private JTextField createField(String placeholder) {
        JTextField f = new JTextField();
        f.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        f.putClientProperty("JTextField.placeholderText", placeholder);
        f.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_CLR, 1, true) {
                @Override
                public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(BORDER_CLR);
                    g2.drawRoundRect(x, y, width-1, height-1, 10, 10);
                }
            },
            new EmptyBorder(5, 12, 5, 10)
        ));
        return f;
    }

    private JButton createActionButton(String text, Color bg) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? (getModel().isRollover() ? bg.darker() : bg) : new Color(209,213,219));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton createGhostButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btn.setForeground(MUTED);
        btn.setBackground(quanlykhachsan.frontend.utils.ThemeManager.isDarkMode() ? new Color(30, 41, 59) : new Color(241, 245, 249));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JSeparator createSeparator() {
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setForeground(BORDER_CLR);
        return sep;
    }
}
