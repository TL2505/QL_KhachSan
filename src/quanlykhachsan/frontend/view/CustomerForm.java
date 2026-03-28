package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.List;
import quanlykhachsan.backend.model.Customer;
import quanlykhachsan.frontend.api.CustomerAPI;

public class CustomerForm extends JPanel {

    private JTable customerTable;
    private DefaultTableModel tableModel;
    private JTextField txtName, txtPhone, txtCard, txtSearch;
    private JButton btnAdd, btnUpdate, btnDelete, btnClear, btnRefresh;
    private TableRowSorter<DefaultTableModel> sorter;

    public CustomerForm() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        initUI();
        loadCustomers();
    }

    public void setCustomerName(String name) {
        txtName.setText(name);
        txtName.requestFocus();
    }

    private void initUI() {
        // PANEL NHẬP LIỆU BÊN TRÁI
        JPanel leftPanel = new JPanel(new GridBagLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Thông tin Khách hàng"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0; gbc.gridy = 0; leftPanel.add(new JLabel("Họ tên:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; txtName = new JTextField(15); leftPanel.add(txtName, gbc);

        gbc.gridx = 0; gbc.gridy = 1; leftPanel.add(new JLabel("SĐT:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; txtPhone = new JTextField(15); leftPanel.add(txtPhone, gbc);

        gbc.gridx = 0; gbc.gridy = 2; leftPanel.add(new JLabel("CCCD:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; txtCard = new JTextField(15); leftPanel.add(txtCard, gbc);

        // Nút bấm cho CRUD
        JPanel btnCrudPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        btnAdd = new JButton("Thêm mới");
        btnUpdate = new JButton("Cập nhật");
        btnDelete = new JButton("Xóa");
        btnClear = new JButton("Làm mới");

        btnCrudPanel.add(btnAdd);
        btnCrudPanel.add(btnUpdate);
        btnCrudPanel.add(btnDelete);
        btnCrudPanel.add(btnClear);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.insets = new Insets(15, 5, 5, 5);
        leftPanel.add(btnCrudPanel, gbc);
        
        add(leftPanel, BorderLayout.WEST);

        // PANEL DANH SÁCH BÊN PHẢI
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.setBorder(BorderFactory.createTitledBorder("Danh sách khách hàng"));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        btnRefresh = new JButton("Tải lại");
        btnRefresh.addActionListener(e -> loadCustomers());
        topBar.add(btnRefresh);
        
        // --- Thanh tìm kiếm ---
        topBar.add(new JLabel("Tìm kiếm nhanh: "));
        txtSearch = new JTextField(15);
        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { search(); }
            public void removeUpdate(DocumentEvent e) { search(); }
            public void changedUpdate(DocumentEvent e) { search(); }
            private void search() {
                String text = txtSearch.getText();
                if (text.trim().length() == 0) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
                }
            }
        });
        topBar.add(txtSearch);
        
        rightPanel.add(topBar, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(new String[]{"ID", "Họ Tên", "SĐT", "CCCD"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        customerTable = new JTable(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        customerTable.setRowSorter(sorter);
        customerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        JScrollPane scrollPane = new JScrollPane(customerTable);
        rightPanel.add(scrollPane, BorderLayout.CENTER);

        add(rightPanel, BorderLayout.CENTER);

        // --- Action Listeners ---
        btnAdd.addActionListener(e -> addCustomer());
        btnUpdate.addActionListener(e -> updateCustomer());
        btnDelete.addActionListener(e -> deleteCustomer());
        btnClear.addActionListener(e -> clearForm());

        customerTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && customerTable.getSelectedRow() != -1) {
                fillForm();
            }
        });
    }

    public void loadCustomers() {
        tableModel.setRowCount(0);
        new SwingWorker<List<Customer>, Void>() {
            @Override protected List<Customer> doInBackground() { return CustomerAPI.getAllCustomers(); }
            @Override protected void done() {
                try {
                    List<Customer> list = get();
                    if (list != null) {
                        for (Customer c : list) {
                            tableModel.addRow(new Object[]{c.getId(), c.getFullName(), c.getPhone(), c.getIdentityCard()});
                        }
                    }
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        }.execute();
    }

    private void fillForm() {
        int row = customerTable.getSelectedRow();
        if (row != -1) {
            // Cần lấy index thực tế trong model nếu bảng đang được sort
            int modelRow = customerTable.convertRowIndexToModel(row);
            txtName.setText(tableModel.getValueAt(modelRow, 1).toString());
            txtPhone.setText(tableModel.getValueAt(modelRow, 2).toString());
            txtCard.setText(tableModel.getValueAt(modelRow, 3).toString());
            btnAdd.setEnabled(false);
        }
    }

    private void clearForm() {
        txtName.setText("");
        txtPhone.setText("");
        txtCard.setText("");
        customerTable.clearSelection();
        btnAdd.setEnabled(true);
    }

    private void addCustomer() {
        String name = txtName.getText().trim();
        String phone = txtPhone.getText().trim();
        String cccd = txtCard.getText().trim();

        if (name.isEmpty() || phone.isEmpty() || cccd.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đủ thông tin", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Customer c = new Customer();
        c.setFullName(name);
        c.setPhone(phone);
        c.setIdentityCard(cccd);

        String result = CustomerAPI.addCustomer(c);
        handleApiResponse(result);
    }

    private void updateCustomer() {
        int row = customerTable.getSelectedRow();
        if (row == -1) return;

        int modelRow = customerTable.convertRowIndexToModel(row);
        int id = (int) tableModel.getValueAt(modelRow, 0);

        Customer c = new Customer();
        c.setId(id);
        c.setFullName(txtName.getText().trim());
        c.setPhone(txtPhone.getText().trim());
        c.setIdentityCard(txtCard.getText().trim());

        String result = CustomerAPI.updateCustomer(c);
        handleApiResponse(result);
    }

    private void deleteCustomer() {
        int row = customerTable.getSelectedRow();
        if (row == -1) return;

        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắn chắn muốn xóa khách hàng này?", "Xác nhận xóa", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        int modelRow = customerTable.convertRowIndexToModel(row);
        int id = (int) tableModel.getValueAt(modelRow, 0);

        String result = CustomerAPI.deleteCustomer(id);
        handleApiResponse(result);
    }

    private void handleApiResponse(String result) {
        if (result.startsWith("Success")) {
            JOptionPane.showMessageDialog(this, result.substring(9), "Thao tác thành công", JOptionPane.INFORMATION_MESSAGE);
            loadCustomers();
            clearForm();
        } else {
            JOptionPane.showMessageDialog(this, result, "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}
