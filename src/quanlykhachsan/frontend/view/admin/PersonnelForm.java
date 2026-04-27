package quanlykhachsan.frontend.view.admin;

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
import quanlykhachsan.backend.user.User;
import quanlykhachsan.backend.user.Role;
import quanlykhachsan.frontend.api.UserAPI;

public class PersonnelForm extends JPanel {

    private JTable userTable;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> sorter;

    private JTextField txtSearch;
    private JTextField txtUsername, txtPassword, txtFullName, txtEmail, txtPhone;
    private JComboBox<String> cbRole, cbStatus;
    private JButton btnSave, btnReset, btnDelete, btnRefresh;
    private JLabel lblFormTitle, lblStatus;

    private int editingId = -1;
    private List<User> usersList = new ArrayList<>();
    private List<Role> cachedRoles = new ArrayList<>();

    private final Color PRIMARY    = new Color(37, 99, 235);
    private final Color SUCCESS    = new Color(5, 150, 105);
    private final Color DANGER     = new Color(220, 38, 38);
    private final Color MUTED      = new Color(107, 114, 128);
    private final Color BG_PANEL   = quanlykhachsan.frontend.utils.ThemeManager.getBgPanel();
    private final Color BORDER_CLR = quanlykhachsan.frontend.utils.ThemeManager.getBorderColor();
    private final Color ROW_EVEN   = quanlykhachsan.frontend.utils.ThemeManager.isDarkMode() ? new Color(15, 23, 42) : new Color(249, 250, 251);
    private final Color ROW_ODD    = quanlykhachsan.frontend.utils.ThemeManager.getCardBg();
    private final Color ROW_SELECT = new Color(219, 234, 254);

    public PersonnelForm() {
        setLayout(new BorderLayout(0, 0));
        setBackground(BG_PANEL);
        initUI();
        loadInitialData();
    }

    private void loadInitialData() {
        SwingWorker<List<Role>, Void> roleWorker = new SwingWorker<>() {
            @Override
            protected List<Role> doInBackground() throws Exception {
                return UserAPI.getRoles();
            }
            @Override
            protected void done() {
                try {
                    cachedRoles = get();
                    cbRole.removeAllItems();
                    if (cachedRoles != null) {
                        for (Role r : cachedRoles) {
                            cbRole.addItem(r.getName());
                        }
                    }
                    loadUsers();
                } catch (Exception ex) {
                    setStatus("Lỗi nạp quyền!", DANGER);
                }
            }
        };
        roleWorker.execute();
    }

    private void initUI() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER_CLR),
            new EmptyBorder(10, 16, 10, 16)
        ));
        JLabel pageTitle = new JLabel("Quản lý Người dùng & Phân quyền");
        pageTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        pageTitle.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        lblStatus = new JLabel(" ");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        statusBar.add(pageTitle, BorderLayout.WEST);
        statusBar.add(lblStatus, BorderLayout.EAST);
        add(statusBar, BorderLayout.NORTH);

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

        lblFormTitle = new JLabel("Hồ sơ người dùng");
        lblFormTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblFormTitle.setForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        lblFormTitle.setAlignmentX(LEFT_ALIGNMENT);
        
        JLabel lblSub = new JLabel("Quản lý tài khoản Admin/Staff/Customer");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(MUTED);
        lblSub.setAlignmentX(LEFT_ALIGNMENT);

        form.add(lblFormTitle);
        form.add(Box.createVerticalStrut(2));
        form.add(lblSub);
        form.add(Box.createVerticalStrut(15));

        form.add(createFieldLabel("Tài khoản đăng nhập *"));
        txtUsername = createField("admin_01");
        form.add(txtUsername);
        form.add(Box.createVerticalStrut(10));

        form.add(createFieldLabel("Mật khẩu * (bỏ trống nếu ko đổi)"));
        txtPassword = createField("******");
        form.add(txtPassword);
        form.add(Box.createVerticalStrut(10));

        form.add(createFieldLabel("Họ và Tên"));
        txtFullName = createField("Nguyễn Văn A");
        form.add(txtFullName);
        form.add(Box.createVerticalStrut(10));

        form.add(createFieldLabel("Email"));
        txtEmail = createField("email@hotel.com");
        form.add(txtEmail);
        form.add(Box.createVerticalStrut(10));

        form.add(createFieldLabel("Số Điện Thoại"));
        txtPhone = createField("0901234567");
        form.add(txtPhone);
        form.add(Box.createVerticalStrut(10));

        form.add(createFieldLabel("Phân quyền *"));
        cbRole = new JComboBox<>();
        cbRole.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        cbRole.setAlignmentX(LEFT_ALIGNMENT);
        form.add(cbRole);
        form.add(Box.createVerticalStrut(10));

        form.add(createFieldLabel("Trạng thái *"));
        cbStatus = new JComboBox<>(new String[]{"active", "inactive", "banned"});
        cbStatus.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        cbStatus.setAlignmentX(LEFT_ALIGNMENT);
        form.add(cbStatus);
        form.add(Box.createVerticalStrut(15));

        btnSave = createActionButton("Lưu Người Dùng", SUCCESS);
        btnSave.setAlignmentX(LEFT_ALIGNMENT);
        btnSave.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnSave.addActionListener(e -> actionSave());

        btnReset = createGhostButton("Làm Mới");
        btnReset.setOpaque(false);
        btnReset.setAlignmentX(LEFT_ALIGNMENT);
        btnReset.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        btnReset.addActionListener(e -> resetForm());

        btnDelete = createActionButton("Xóa Người Dùng", DANGER);
        btnDelete.setAlignmentX(LEFT_ALIGNMENT);
        btnDelete.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnDelete.setEnabled(false);
        btnDelete.addActionListener(e -> actionDelete());

        form.add(btnSave);
        form.add(Box.createVerticalStrut(8));
        form.add(btnReset);
        form.add(Box.createVerticalStrut(8));
        form.add(btnDelete);

        JScrollPane scrollForm = new JScrollPane(form);
        scrollForm.setBorder(null);
        wrapper.add(scrollForm, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());

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
        txtSearch.putClientProperty("JTextField.placeholderText", "Tìm kiếm nhân viên...");

        btnRefresh = new JButton("Tải Lại");
        btnRefresh.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btnRefresh.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRefresh.addActionListener(e -> loadUsers());

        searchBar.add(searchIcon, BorderLayout.WEST);
        searchBar.add(txtSearch, BorderLayout.CENTER);
        searchBar.add(btnRefresh, BorderLayout.EAST);
        panel.add(searchBar, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(
            new String[]{"ID", "Tên Đăng Nhập", "Họ và Tên", "Vai Trò", "Trạng Thái"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };

        userTable = new JTable(tableModel);
        userTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        userTable.setRowHeight(38);
        userTable.setShowGrid(false);
        userTable.setIntercellSpacing(new Dimension(0, 0));
        userTable.setSelectionBackground(ROW_SELECT);
        userTable.setSelectionForeground(quanlykhachsan.frontend.utils.ThemeManager.getTextMain());
        userTable.setFocusable(false);

        userTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        userTable.getTableHeader().setBackground(quanlykhachsan.frontend.utils.ThemeManager.isDarkMode() ? new Color(30, 41, 59) : new Color(241, 245, 249));
        userTable.getTableHeader().setForeground(new Color(71, 85, 105));
        userTable.getTableHeader().setBorder(new MatteBorder(0, 0, 1, 0, BORDER_CLR));
        userTable.getTableHeader().setPreferredSize(new Dimension(0, 40));

        userTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                if (!sel) setBackground(row % 2 == 0 ? ROW_EVEN : ROW_ODD);
                setBorder(new EmptyBorder(0, 12, 0, 12));
                return this;
            }
        });

        sorter = new TableRowSorter<>(tableModel);
        userTable.setRowSorter(sorter);

        userTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onRowSelected();
        });

        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        JScrollPane scroll = new JScrollPane(userTable);
        scroll.setBorder(new MatteBorder(1, 0, 0, 0, BORDER_CLR));
        scroll.getViewport().setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    public void loadUsers() {
        setStatus("Đang tải dữ liệu...", MUTED);
        userTable.clearSelection();
        
        SwingWorker<List<User>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<User> doInBackground() throws Exception {
                return UserAPI.getAllUsers();
            }
            @Override
            protected void done() {
                try {
                    usersList = get();
                    tableModel.setRowCount(0);
                    if (usersList != null) {
                        for (User u : usersList) {
                            String roleStr = "Quyền #" + u.getRoleId();
                            if (cachedRoles != null) {
                                for (Role r : cachedRoles) {
                                    if (r.getId() == u.getRoleId()) {
                                        roleStr = r.getName();
                                        break;
                                    }
                                }
                            }
                            
                            tableModel.addRow(new Object[]{
                                u.getId(), 
                                u.getUsername(), 
                                u.getFullName(), 
                                roleStr,
                                u.getStatus()
                            });
                        }
                        setStatus(usersList.size() + " người dùng", SUCCESS);
                    } else {
                        setStatus("Không có dữ liệu", MUTED);
                    }
                } catch (Exception ex) {
                    setStatus("Lỗi tải dữ liệu. Bạn có đủ quyền?", DANGER);
                }
            }
        };
        worker.execute();
    }

    private void onRowSelected() {
        int viewRow = userTable.getSelectedRow();
        if (viewRow < 0 || viewRow >= userTable.getRowCount()) return;
        
        try {
            int modelRow = userTable.convertRowIndexToModel(viewRow);
            if (modelRow < 0 || modelRow >= tableModel.getRowCount()) return;
            
            int realId = (int) tableModel.getValueAt(modelRow, 0); 
            
            User selected = null;
            for (User u : usersList) {
                if (u.getId() == realId) {
                    selected = u;
                    break;
                }
            }
            
            if (selected != null) {
                editingId = selected.getId();
                txtUsername.setText(selected.getUsername());
                txtUsername.setEditable(false);
                txtPassword.setText("");
                txtFullName.setText(selected.getFullName());
                txtEmail.setText(selected.getEmail());
                txtPhone.setText(selected.getPhone());
                
                // Set role in combo box
                if (cachedRoles != null) {
                    for (int i = 0; i < cachedRoles.size(); i++) {
                        if (cachedRoles.get(i).getId() == selected.getRoleId()) {
                            cbRole.setSelectedIndex(i);
                            break;
                        }
                    }
                }
                cbStatus.setSelectedItem(selected.getStatus());

                lblFormTitle.setText("Cập Nhật Thông Tin");
                btnSave.setText("Cập Nhật");
                btnDelete.setEnabled(true);
            }
        } catch (IndexOutOfBoundsException ex) {
            return; // Bỏ qua nếu bảng đang trong trạng thái cập nhật
        }
    }

    private void applyFilter() {
        String text = txtSearch.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text, 1, 2));
        }
    }

    private void actionSave() {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText().trim();
        String fullName = txtFullName.getText().trim();
        String email = txtEmail.getText().trim();
        String phone = txtPhone.getText().trim();
        
        int roleId = -1;
        int roleIdx = cbRole.getSelectedIndex();
        if (cachedRoles != null && roleIdx >= 0 && roleIdx < cachedRoles.size()) {
            roleId = cachedRoles.get(roleIdx).getId();
        }
        
        String status = cbStatus.getSelectedItem().toString();

        if (username.isEmpty()) {
            showWarn("Vui lòng nhập tài khoản đăng nhập!"); return;
        }
        if (roleId == -1) {
            showWarn("Vui lòng chọn phân quyền hợp lệ!"); return;
        }
        if (editingId == -1 && password.isEmpty()) {
            showWarn("Tạo mới bắt buộc phải có mật khẩu!"); return;
        }

        btnSave.setEnabled(false);
        setStatus("Đang xử lý...", MUTED);

        final int finalRoleId = roleId;
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                User u = new User();
                u.setUsername(username);
                u.setPassword(password);
                u.setFullName(fullName);
                u.setEmail(email);
                u.setPhone(phone);
                u.setRoleId(finalRoleId);
                u.setStatus(status);
                
                if (editingId != -1) {
                    u.setId(editingId);
                    return UserAPI.updateUser(u);
                } else {
                    return UserAPI.createUser(u);
                }
            }
            @Override
            protected void done() {
                btnSave.setEnabled(true);
                try {
                    String res = get();
                    if (res != null) {
                        setStatus(editingId != -1 ? "Đã cập nhật!" : "Đã thêm mới!", SUCCESS);
                        resetForm();
                        loadUsers();
                    }
                } catch (Exception ex) {
                    setStatus("Thất bại hoặc lỗi kết nối!", DANGER);
                    JOptionPane.showMessageDialog(PersonnelForm.this, ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void actionDelete() {
        if (editingId == -1) return;
        
        int confirm = JOptionPane.showConfirmDialog(this,
            "Bạn có chắc chắn muốn xóa nhân viên ID: " + editingId + " ?\nLưu ý: Chỉ thực sự xóa nếu không ảnh hưởng ràng buộc.",
            "Xác nhận Xóa", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            
        if (confirm == JOptionPane.YES_OPTION) {
            btnDelete.setEnabled(false);
            setStatus("Đang xóa...", MUTED);
            
            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override
                protected String doInBackground() throws Exception {
                    return UserAPI.deleteUser(editingId);
                }
                @Override
                protected void done() {
                    btnDelete.setEnabled(true);
                    try {
                        String res = get();
                        if (res != null) {
                            setStatus("Đã xóa người dùng!", SUCCESS);
                            resetForm();
                            loadUsers();
                        }
                    } catch (Exception ex) {
                        setStatus("Xóa thất bại!", DANGER);
                        JOptionPane.showMessageDialog(PersonnelForm.this, ex.getMessage(), "Lỗi Hệ Thống", JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
            worker.execute();
        }
    }

    private void resetForm() {
        editingId = -1;
        txtUsername.setText("");
        txtUsername.setEditable(true);
        txtPassword.setText("");
        txtFullName.setText("");
        txtEmail.setText("");
        txtPhone.setText("");
        cbRole.setSelectedIndex(0);
        cbStatus.setSelectedIndex(0);
        
        lblFormTitle.setText("Thêm Người Dùng Mới");
        btnSave.setText("Lưu Người Dùng");
        btnDelete.setEnabled(false);
        userTable.clearSelection();
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
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        f.setAlignmentX(LEFT_ALIGNMENT);
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
}
