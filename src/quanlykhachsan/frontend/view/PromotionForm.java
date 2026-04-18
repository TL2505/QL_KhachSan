package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ArrayList;
import quanlykhachsan.backend.model.Promotion;
import quanlykhachsan.frontend.api.PromotionAPI;

public class PromotionForm extends JPanel {

    private JTable promotionTable;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> sorter;

    private JTextField txtSearch;
    private JTextField txtName, txtDescription, txtDiscountValue, txtConditionValue;
    private JTextField txtStartDate, txtEndDate;
    private JComboBox<String> cbDiscountType, cbConditionType, cbStatus;

    private JButton btnSave, btnReset, btnDelete, btnRefresh;
    private JLabel lblFormTitle, lblStatus;

    private int editingId = -1;
    private List<Promotion> promotionsList = new ArrayList<>();
    private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static final Color PRIMARY = new Color(37, 99, 235);
    private static final Color SUCCESS = new Color(5, 150, 105);
    private static final Color DANGER = new Color(220, 38, 38);
    private static final Color MUTED = new Color(107, 114, 128);
    private static final Color BG_PANEL = new Color(248, 250, 252);
    private static final Color BORDER_CLR = new Color(226, 232, 240);
    private static final Color ROW_EVEN = new Color(249, 250, 251);
    private static final Color ROW_ODD = Color.WHITE;
    private static final Color ROW_SELECT = new Color(219, 234, 254);

    public PromotionForm() {
        setLayout(new BorderLayout(0, 0));
        setBackground(BG_PANEL);
        initUI();
        loadPromotions();
    }

    private void initUI() {
        // Status bar
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(Color.WHITE);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, BORDER_CLR),
                new EmptyBorder(10, 16, 10, 16)));
        JLabel pageTitle = new JLabel("Quản lý Chương trình Khuyến mãi");
        pageTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        pageTitle.setForeground(new Color(15, 23, 42));
        lblStatus = new JLabel(" ");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        statusBar.add(pageTitle, BorderLayout.WEST);
        statusBar.add(lblStatus, BorderLayout.EAST);
        add(statusBar, BorderLayout.NORTH);

        // Main content
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(350);
        splitPane.setDividerSize(1);
        splitPane.setBorder(null);
        splitPane.setBackground(BG_PANEL);

        splitPane.setLeftComponent(buildFormPanel());
        splitPane.setRightComponent(buildTablePanel());
        add(splitPane, BorderLayout.CENTER);
    }

    private JPanel buildFormPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Color.WHITE);
        wrapper.setBorder(new MatteBorder(0, 0, 0, 1, BORDER_CLR));

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(Color.WHITE);
        form.setBorder(new EmptyBorder(20, 20, 20, 20));

        lblFormTitle = new JLabel("Thêm Khuyến Mãi Mới");
        lblFormTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblFormTitle.setForeground(new Color(17, 24, 39));
        lblFormTitle.setAlignmentX(LEFT_ALIGNMENT);
        form.add(lblFormTitle);
        form.add(Box.createVerticalStrut(15));

        form.add(createFieldLabel("Tên chương trình *"));
        txtName = createField("Ví dụ: Giảm giá mùa hè");
        form.add(txtName);
        form.add(Box.createVerticalStrut(8));

        form.add(createFieldLabel("Mô tả"));
        txtDescription = createField("Mô tả chi tiết...");
        form.add(txtDescription);
        form.add(Box.createVerticalStrut(8));

        form.add(createFieldLabel("Loại giảm giá *"));
        cbDiscountType = new JComboBox<>(new String[] { "percentage", "fixed_amount" });
        cbDiscountType.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        cbDiscountType.setAlignmentX(LEFT_ALIGNMENT);
        form.add(cbDiscountType);
        form.add(Box.createVerticalStrut(8));

        form.add(createFieldLabel("Giá trị giảm *"));
        txtDiscountValue = createField("Ví dụ: 10 (nếu là %,) hoặc 50000");
        form.add(txtDiscountValue);
        form.add(Box.createVerticalStrut(8));

        form.add(createFieldLabel("Ngày bắt đầu *"));
        txtStartDate = createField("yyyy-MM-dd HH:mm:ss");
        form.add(txtStartDate);
        form.add(Box.createVerticalStrut(8));

        form.add(createFieldLabel("Ngày kết thúc *"));
        txtEndDate = createField("yyyy-MM-dd HH:mm:ss");
        form.add(txtEndDate);
        form.add(Box.createVerticalStrut(8));

        form.add(createFieldLabel("Điều kiện áp dụng"));
        cbConditionType = new JComboBox<>(new String[] { "none", "room_type", "min_stay", "vip_only" });
        cbConditionType.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        cbConditionType.setAlignmentX(LEFT_ALIGNMENT);
        form.add(cbConditionType);
        form.add(Box.createVerticalStrut(8));

        form.add(createFieldLabel("Giá trị điều kiện"));
        txtConditionValue = createField("Ví dụ: 3 (nếu là min_stay)");
        form.add(txtConditionValue);
        form.add(Box.createVerticalStrut(8));

        form.add(createFieldLabel("Trạng thái"));
        cbStatus = new JComboBox<>(new String[] { "active", "inactive" });
        cbStatus.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        cbStatus.setAlignmentX(LEFT_ALIGNMENT);
        form.add(cbStatus);
        form.add(Box.createVerticalStrut(15));

        btnSave = createActionButton("Lưu", SUCCESS);
        btnSave.addActionListener(e -> actionSave());
        form.add(btnSave);
        form.add(Box.createVerticalStrut(5));

        btnReset = createGhostButton("Làm Mới Form");
        btnReset.addActionListener(e -> resetForm());
        form.add(btnReset);
        form.add(Box.createVerticalStrut(5));

        btnDelete = createActionButton("Xóa", DANGER);
        btnDelete.setEnabled(false);
        btnDelete.addActionListener(e -> actionDelete());
        form.add(btnDelete);

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(null);
        wrapper.add(scroll, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(Color.WHITE);

        JPanel searchBar = new JPanel(new BorderLayout(8, 0));
        searchBar.setBackground(Color.WHITE);
        searchBar.setBorder(new EmptyBorder(12, 16, 12, 16));

        txtSearch = new JTextField();
        txtSearch.putClientProperty("JTextField.placeholderText", "Tìm kiếm...");

        btnRefresh = new JButton("Tải Lại");
        btnRefresh.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnRefresh.addActionListener(e -> loadPromotions());

        searchBar.add(new JLabel("Tìm kiếm: "), BorderLayout.WEST);
        searchBar.add(txtSearch, BorderLayout.CENTER);
        searchBar.add(btnRefresh, BorderLayout.EAST);
        panel.add(searchBar, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(
                new String[] { "ID", "Tên KM", "Giảm giá", "Điều kiện", "Bắt đầu", "Kết thúc", "Trạng thái" }, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        promotionTable = new JTable(tableModel);
        promotionTable.setRowHeight(38);
        promotionTable.getTableHeader().setPreferredSize(new Dimension(0, 40));

        sorter = new TableRowSorter<>(tableModel);
        promotionTable.setRowSorter(sorter);

        promotionTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting())
                onRowSelected();
        });

        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });

        panel.add(new JScrollPane(promotionTable), BorderLayout.CENTER);
        return panel;
    }

    public void loadPromotions() {
        setStatus("Đang tải dữ liệu...", MUTED);
        promotionTable.clearSelection();

        SwingWorker<List<Promotion>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Promotion> doInBackground() {
                return PromotionAPI.getAllPromotions();
            }

            @Override
            protected void done() {
                try {
                    promotionsList = get();
                    tableModel.setRowCount(0);
                    if (promotionsList != null) {
                        for (Promotion p : promotionsList) {
                            String discountStr = p.getDiscountType().equals("percentage") ? p.getDiscountValue() + "%"
                                    : p.getDiscountValue() + "đ";
                            tableModel.addRow(new Object[] {
                                    p.getId(), p.getName(), discountStr, p.getConditionType(),
                                    df.format(p.getStartDate()), df.format(p.getEndDate()), p.getStatus()
                            });
                        }
                        setStatus(promotionsList.size() + " khuyến mãi", SUCCESS);
                    }
                } catch (Exception ex) {
                    setStatus("Lỗi tải dữ liệu", DANGER);
                }
            }
        };
        worker.execute();
    }

    private void onRowSelected() {
        int viewRow = promotionTable.getSelectedRow();
        if (viewRow < 0)
            return;

        int modelRow = promotionTable.convertRowIndexToModel(viewRow);
        int realId = (int) tableModel.getValueAt(modelRow, 0);

        Promotion selected = null;
        for (Promotion p : promotionsList) {
            if (p.getId() == realId) {
                selected = p;
                break;
            }
        }

        if (selected != null) {
            editingId = selected.getId();
            txtName.setText(selected.getName());
            txtDescription.setText(selected.getDescription());
            cbDiscountType.setSelectedItem(selected.getDiscountType());
            txtDiscountValue.setText(String.valueOf(selected.getDiscountValue()));
            txtStartDate.setText(df.format(selected.getStartDate()));
            txtEndDate.setText(df.format(selected.getEndDate()));
            cbConditionType.setSelectedItem(selected.getConditionType());
            txtConditionValue.setText(selected.getConditionValue());
            cbStatus.setSelectedItem(selected.getStatus());

            lblFormTitle.setText("Cập Nhật Thông Tin");
            btnSave.setText("Cập Nhật");
            btnDelete.setEnabled(true);
        }
    }

    private void applyFilter() {
        String text = txtSearch.getText().trim();
        if (text.isEmpty())
            sorter.setRowFilter(null);
        else
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text, 1));
    }

    private void actionSave() {
        try {
            Promotion p = new Promotion();
            p.setName(txtName.getText().trim());
            p.setDescription(txtDescription.getText().trim());
            p.setDiscountType((String) cbDiscountType.getSelectedItem());
            p.setDiscountValue(Double.parseDouble(txtDiscountValue.getText().trim()));
            p.setStartDate(df.parse(txtStartDate.getText().trim()));
            p.setEndDate(df.parse(txtEndDate.getText().trim()));
            p.setConditionType((String) cbConditionType.getSelectedItem());
            p.setConditionValue(txtConditionValue.getText().trim());
            p.setStatus((String) cbStatus.getSelectedItem());

            setStatus("Đang xử lý...", MUTED);

            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override
                protected String doInBackground() {
                    if (editingId != -1) {
                        p.setId(editingId);
                        return PromotionAPI.updatePromotion(p);
                    } else {
                        return PromotionAPI.createPromotion(p);
                    }
                }

                @Override
                protected void done() {
                    try {
                        String res = get();
                        if (res != null && res.startsWith("Success")) {
                            setStatus(editingId != -1 ? "Đã cập nhật!" : "Đã thêm mới!", SUCCESS);
                            resetForm();
                            loadPromotions();
                        } else {
                            setStatus("Thất bại: " + res, DANGER);
                        }
                    } catch (Exception ex) {
                        setStatus("Lỗi kết nối server!", DANGER);
                    }
                }
            };
            worker.execute();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đúng định dạng ngày (yyyy-MM-dd HH:mm:ss) và số!", "Lỗi",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void actionDelete() {
        if (editingId == -1)
            return;
        if (JOptionPane.showConfirmDialog(this, "Xóa khuyến mãi này?", "Xác nhận",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            btnDelete.setEnabled(false);
            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override
                protected String doInBackground() {
                    return PromotionAPI.deletePromotion(editingId);
                }

                @Override
                protected void done() {
                    btnDelete.setEnabled(true);
                    try {
                        if (get().startsWith("Success")) {
                            resetForm();
                            loadPromotions();
                        } else
                            setStatus("Xóa thất bại", DANGER);
                    } catch (Exception ex) {
                    }
                }
            };
            worker.execute();
        }
    }

    private void resetForm() {
        editingId = -1;
        txtName.setText("");
        txtDescription.setText("");
        txtDiscountValue.setText("");
        txtStartDate.setText("");
        txtEndDate.setText("");
        txtConditionValue.setText("");
        lblFormTitle.setText("Thêm Khuyến Mãi Mới");
        btnSave.setText("Lưu");
        btnDelete.setEnabled(false);
        promotionTable.clearSelection();
    }

    private void setStatus(String msg, Color color) {
        lblStatus.setText(msg);
        lblStatus.setForeground(color);
    }

    private JLabel createFieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setForeground(new Color(55, 65, 81));
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        return lbl;
    }

    private JTextField createField(String placeholder) {
        JTextField f = new JTextField();
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        f.setAlignmentX(LEFT_ALIGNMENT);
        f.putClientProperty("JTextField.placeholderText", placeholder);
        f.setBorder(
                BorderFactory.createCompoundBorder(new LineBorder(BORDER_CLR, 1, true), new EmptyBorder(5, 10, 5, 10)));
        return f;
    }

    private JButton createActionButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btn.setAlignmentX(LEFT_ALIGNMENT);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton createGhostButton(String text) {
        JButton btn = new JButton(text);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btn.setAlignmentX(LEFT_ALIGNMENT);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setForeground(PRIMARY);
        btn.setContentAreaFilled(false);
        btn.setBorder(new LineBorder(PRIMARY, 1, true));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
