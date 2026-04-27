package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import quanlykhachsan.backend.model.Customer;
import quanlykhachsan.frontend.api.CustomerAPI;
import quanlykhachsan.frontend.utils.ThemeManager;

public class CustomerSelectDialog extends JDialog {

    private JTable table;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> rowSorter;
    private JTextField txtSearch;
    
    private Customer selectedCustomer = null;
    private List<Customer> customersList;

    private final Color PRIMARY = ThemeManager.getPrimary();
    private final Color SUCCESS = ThemeManager.getSuccess();
    private final Color BG_PANEL = ThemeManager.getBgPanel();
    private final Color CARD_BG = ThemeManager.getCardBg();
    private final Color TEXT_MAIN = ThemeManager.getTextMain();
    private final Color MUTED = ThemeManager.getTextMuted();
    private final Color BORDER_CLR = ThemeManager.getBorderColor();
    private final Color ROW_EVEN = ThemeManager.isDarkMode() ? new Color(15, 23, 42) : new Color(249, 250, 251);
    private final Color ROW_SELECT = new Color(219, 234, 254);

    public CustomerSelectDialog(Frame parent) {
        super(parent, "Tìm kiếm & Chọn Khách Hàng", true);
        setSize(700, 500);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG_PANEL);

        initUI();
        loadData();
    }

    private void initUI() {
        // --- TOP: Search and Add ---
        JPanel pnlTop = new JPanel(new BorderLayout(15, 0));
        pnlTop.setBackground(CARD_BG);
        pnlTop.setBorder(new EmptyBorder(15, 20, 15, 20));

        txtSearch = new JTextField();
        txtSearch.putClientProperty("JTextField.placeholderText", " Nhập tên hoặc số điện thoại để tìm...");
        txtSearch.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        txtSearch.setPreferredSize(new Dimension(300, 40));
        txtSearch.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_CLR, 1, true),
            new EmptyBorder(0, 10, 0, 10)
        ));

        // Add DocumentListener to txtSearch for live filtering
        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { filterData(); }
            @Override
            public void removeUpdate(DocumentEvent e) { filterData(); }
            @Override
            public void changedUpdate(DocumentEvent e) { filterData(); }
        });

        JButton btnAddNew = createSolidBtn("+ Thêm Khách Mới", PRIMARY);
        btnAddNew.setPreferredSize(new Dimension(160, 40));
        btnAddNew.addActionListener(e -> actionAddNewCustomer());

        pnlTop.add(txtSearch, BorderLayout.CENTER);
        pnlTop.add(btnAddNew, BorderLayout.EAST);

        add(pnlTop, BorderLayout.NORTH);

        // --- CENTER: Table ---
        tableModel = new DefaultTableModel(new String[]{"ID", "Họ Tên", "SĐT", "CCCD"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        table.setRowHeight(40);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        if(!ThemeManager.isDarkMode()) {
            table.setSelectionBackground(ROW_SELECT);
            table.setSelectionForeground(TEXT_MAIN);
        }

        // Table Header Styling
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.getTableHeader().setBackground(ThemeManager.isDarkMode() ? new Color(30, 41, 59) : new Color(241, 245, 249));
        table.getTableHeader().setForeground(new Color(71, 85, 105));
        table.getTableHeader().setBorder(new MatteBorder(0, 0, 1, 0, BORDER_CLR));
        table.getTableHeader().setPreferredSize(new Dimension(0, 40));

        // Zebra striping
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                if (!sel) setBackground(row % 2 == 0 ? ROW_EVEN : CARD_BG);
                setBorder(new EmptyBorder(0, 10, 0, 10));
                return this;
            }
        });

        // Column widths
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(2).setMaxWidth(150);
        table.getColumnModel().getColumn(3).setMaxWidth(150);

        rowSorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(rowSorter);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() != -1) {
                    confirmSelection();
                }
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new MatteBorder(1, 0, 1, 0, BORDER_CLR));
        scroll.getViewport().setBackground(CARD_BG);
        add(scroll, BorderLayout.CENTER);

        // --- BOTTOM: Buttons ---
        JPanel pnlBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        pnlBottom.setBackground(CARD_BG);
        
        JButton btnCancel = createGhostBtn("Hủy");
        btnCancel.setPreferredSize(new Dimension(100, 40));
        btnCancel.addActionListener(e -> dispose());
        
        JButton btnSelect = createSolidBtn("Xác Nhận Chọn", SUCCESS);
        btnSelect.setPreferredSize(new Dimension(150, 40));
        btnSelect.addActionListener(e -> confirmSelection());

        pnlBottom.add(btnCancel);
        pnlBottom.add(btnSelect);
        add(pnlBottom, BorderLayout.SOUTH);
    }

    private void loadData() {
        tableModel.setRowCount(0);
        customersList = CustomerAPI.getAllCustomers();
        if (customersList != null) {
            for (Customer c : customersList) {
                tableModel.addRow(new Object[]{
                        c.getId(),
                        c.getFullName(),
                        c.getPhone(),
                        c.getIdentityCard() == null ? "" : c.getIdentityCard()
                });
            }
        }
    }

    private void filterData() {
        String text = txtSearch.getText().trim().toLowerCase();
        if (text.isEmpty()) {
            rowSorter.setRowFilter(null);
        } else {
            // Filter across multiple columns: Name (1) and Phone (2)
            rowSorter.setRowFilter(RowFilter.regexFilter("(?i)" + text, 1, 2));
        }
    }

    private void confirmSelection() {
        int viewRow = table.getSelectedRow();
        if (viewRow == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một khách hàng!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        int selectedId = (int) tableModel.getValueAt(modelRow, 0);

        if (customersList != null) {
            for (Customer c : customersList) {
                if (c.getId() == selectedId) {
                    this.selectedCustomer = c;
                    dispose();
                    return;
                }
            }
        }
    }

    private void actionAddNewCustomer() {
        // Simple prompt modal to add new customer
        JPanel pnlAdd = new JPanel(new GridLayout(3, 2, 10, 15));
        JTextField txtName = new JTextField();
        JTextField txtPhone = new JTextField();
        JTextField txtCCCD = new JTextField();
        
        pnlAdd.add(new JLabel("Họ & Tên (*):")); pnlAdd.add(txtName);
        pnlAdd.add(new JLabel("Số Điện Thoại (*):")); pnlAdd.add(txtPhone);
        pnlAdd.add(new JLabel("CCCD/CMND:")); pnlAdd.add(txtCCCD);

        int res = JOptionPane.showConfirmDialog(this, pnlAdd, "Thêm Khách Hàng Nhanh", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            String name = txtName.getText().trim();
            String phone = txtPhone.getText().trim();
            String cccd = txtCCCD.getText().trim();

            if (name.isEmpty() || phone.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Họ Tên và Số Điện Thoại không được để trống!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Customer newC = new Customer();
            newC.setFullName(name);
            newC.setPhone(phone);
            newC.setIdentityCard(cccd);

            String result = CustomerAPI.addCustomer(newC);
            if (result.startsWith("Success")) {
                JOptionPane.showMessageDialog(this, "Thêm khách hàng thành công!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                // Reload list to get the new customer
                loadData();
                
                // Auto-select the newly added (try to find by phone)
                for (int i=0; i<tableModel.getRowCount(); i++) {
                    if (tableModel.getValueAt(i, 2).toString().equals(phone)) {
                        int viewIdx = table.convertRowIndexToView(i);
                        table.setRowSelectionInterval(viewIdx, viewIdx);
                        break;
                    }
                }
            } else {
                JOptionPane.showMessageDialog(this, result, "Lỗi thêm mới", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public Customer getSelectedCustomer() {
        return selectedCustomer;
    }

    // --- Helpers (matching BookingForm styles) ---
    private JButton createSolidBtn(String text, Color bg) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? (getModel().isRollover() ? bg.darker() : bg) : new Color(209, 213, 219));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setForeground(Color.WHITE);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton createGhostBtn(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setForeground(MUTED);
        btn.setFocusPainted(false);
        btn.setBackground(ThemeManager.isDarkMode() ? new Color(30, 41, 59) : new Color(241, 245, 249));
        btn.setBorder(new LineBorder(BORDER_CLR, 1, true));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
