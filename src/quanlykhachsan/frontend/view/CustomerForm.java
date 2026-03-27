package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import quanlykhachsan.backend.model.Customer;
import quanlykhachsan.frontend.api.CustomerAPI;

public class CustomerForm extends JPanel {

    private JTable customerTable;
    private DefaultTableModel tableModel;
    private JTextField txtName, txtPhone, txtCard;
    private JButton btnAdd, btnRefresh;

    public CustomerForm() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        initUI();
        loadCustomers();
    }

    private void initUI() {
        // PANEL NHẬP LIỆU BÊN TRÁI
        JPanel leftPanel = new JPanel(new GridBagLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Thêm Khách Hàng"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0; gbc.gridy = 0; leftPanel.add(new JLabel("Họ tên:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; txtName = new JTextField(15); leftPanel.add(txtName, gbc);

        gbc.gridx = 0; gbc.gridy = 1; leftPanel.add(new JLabel("SĐT:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; txtPhone = new JTextField(15); leftPanel.add(txtPhone, gbc);

        gbc.gridx = 0; gbc.gridy = 2; leftPanel.add(new JLabel("CCCD:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; txtCard = new JTextField(15); leftPanel.add(txtCard, gbc);

        gbc.gridx = 1; gbc.gridy = 3; gbc.anchor = GridBagConstraints.EAST;
        btnAdd = new JButton("Thêm Khách");
        btnAdd.addActionListener(e -> addCustomerAction());
        leftPanel.add(btnAdd, gbc);
        
        add(leftPanel, BorderLayout.WEST);

        // PANEL DANH SÁCH BÊN PHẢI
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.setBorder(BorderFactory.createTitledBorder("Danh sách khách hàng"));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnRefresh = new JButton("Làm mới danh sách");
        btnRefresh.addActionListener(e -> loadCustomers());
        topBar.add(btnRefresh);
        rightPanel.add(topBar, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(new String[]{"ID", "Họ Tên", "SĐT", "CCCD"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Ko cho sửa dữ liệu trực tiếp trên bảng
            }
        };
        customerTable = new JTable(tableModel);
        customerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(customerTable);
        rightPanel.add(scrollPane, BorderLayout.CENTER);

        add(rightPanel, BorderLayout.CENTER);
    }

    public void loadCustomers() {
        // Gọi API trong một Worker thread
        SwingWorker<List<Customer>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Customer> doInBackground() throws Exception {
                return CustomerAPI.getAllCustomers();
            }

            @Override
            protected void done() {
                try {
                    List<Customer> list = get();
                    tableModel.setRowCount(0); // clear data
                    if (list != null) {
                        for (Customer c : list) {
                            tableModel.addRow(new Object[]{
                                    c.getId(), c.getFullName(), c.getPhone(), c.getIdentityCard()
                            });
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(CustomerForm.this, "Lỗi lấy dữ liệu khách hàng", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void addCustomerAction() {
        String name = txtName.getText().trim();
        String phone = txtPhone.getText().trim();
        String cccd = txtCard.getText().trim();

        if (name.isEmpty() || phone.isEmpty() || cccd.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đủ thông tin", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnAdd.setEnabled(false);
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                Customer c = new Customer();
                c.setFullName(name);
                c.setPhone(phone);
                c.setIdentityCard(cccd);
                return CustomerAPI.addCustomer(c);
            }

            @Override
            protected void done() {
                btnAdd.setEnabled(true);
                try {
                    String resultMsg = get();
                    if (resultMsg.startsWith("Success")) {
                        JOptionPane.showMessageDialog(CustomerForm.this, "Thêm khách hàng thành công!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                        txtName.setText(""); txtPhone.setText(""); txtCard.setText("");
                        loadCustomers(); // refresh danh sách
                    } else {
                        JOptionPane.showMessageDialog(CustomerForm.this, resultMsg, "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        worker.execute();
    }
}
