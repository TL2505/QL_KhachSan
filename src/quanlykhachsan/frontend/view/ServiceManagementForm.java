package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import quanlykhachsan.backend.model.Service;
import quanlykhachsan.frontend.api.ServiceAPI;

public class ServiceManagementForm extends JPanel {
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField txtName, txtPrice, txtDesc;
    private JComboBox<String> cbStatus;
    private JButton btnAdd, btnUpdate, btnDelete, btnRefresh;
    private int selectedServiceId = -1;

    public ServiceManagementForm() {
        setLayout(new BorderLayout());
        setBackground(quanlykhachsan.frontend.utils.ThemeManager.getBgPanel());
        initUI();
        loadData();
    }

    private void initUI() {
        // --- Form nhập liệu (NORTH) ---
        JPanel panelNorth = new JPanel(new GridLayout(2, 4, 10, 10));
        panelNorth.setBorder(BorderFactory.createTitledBorder("Nhập thông tin Dịch vụ/Minibar"));
        panelNorth.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());

        panelNorth.add(new JLabel(" Tên Dịch Vụ:"));
        txtName = new JTextField();
        panelNorth.add(txtName);

        panelNorth.add(new JLabel(" Giá Tiền (VNĐ):"));
        txtPrice = new JTextField();
        panelNorth.add(txtPrice);

        panelNorth.add(new JLabel(" Mô tả (Đơn vị):"));
        txtDesc = new JTextField();
        panelNorth.add(txtDesc);

        panelNorth.add(new JLabel(" Trạng thái:"));
        cbStatus = new JComboBox<>(new String[]{"active", "inactive"});
        panelNorth.add(cbStatus);

        add(panelNorth, BorderLayout.NORTH);

        // --- Bảng dữ liệu (CENTER) ---
        tableModel = new DefaultTableModel(new String[]{"ID", "Tên Dịch Vụ", "Mô tả", "Đơn Giá", "Trạng Thái"}, 0);
        table = new JTable(tableModel);
        table.setRowHeight(30);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() != -1) {
                int row = table.getSelectedRow();
                selectedServiceId = (int) tableModel.getValueAt(row, 0);
                txtName.setText((String) tableModel.getValueAt(row, 1));
                txtDesc.setText((String) tableModel.getValueAt(row, 2));
                txtPrice.setText(String.valueOf(tableModel.getValueAt(row, 3)));
                cbStatus.setSelectedItem(tableModel.getValueAt(row, 4));
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(new EmptyBorder(10, 10, 10, 10));
        add(scrollPane, BorderLayout.CENTER);

        // --- Thao tác (SOUTH) ---
        JPanel panelSouth = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        panelSouth.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());

        btnAdd = new JButton("➕ Thêm mới");
        btnUpdate = new JButton("✏️ Cập nhật");
        btnDelete = new JButton("❌ Xóa");
        btnRefresh = new JButton("🔄 Làm mới");

        btnAdd.addActionListener(e -> addService());
        btnUpdate.addActionListener(e -> updateService());
        btnDelete.addActionListener(e -> deleteService());
        btnRefresh.addActionListener(e -> { clearForm(); loadData(); });

        panelSouth.add(btnAdd);
        panelSouth.add(btnUpdate);
        panelSouth.add(btnDelete);
        panelSouth.add(btnRefresh);

        add(panelSouth, BorderLayout.SOUTH);
    }

    private void loadData() {
        tableModel.setRowCount(0);
        List<Service> list = ServiceAPI.getAllServices();
        for (Service s : list) {
            tableModel.addRow(new Object[]{
                s.getId(), s.getName(), s.getDescription(), s.getPrice(), s.getStatus()
            });
        }
    }

    private void addService() {
        try {
            Service s = new Service();
            s.setName(txtName.getText().trim());
            s.setDescription(txtDesc.getText().trim());
            s.setPrice(Double.parseDouble(txtPrice.getText().trim()));
            s.setStatus(cbStatus.getSelectedItem().toString());

            String res = ServiceAPI.addService(s);
            JOptionPane.showMessageDialog(this, res);
            loadData();
            clearForm();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Lỗi nhập liệu: " + e.getMessage());
        }
    }

    private void updateService() {
        if (selectedServiceId == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 mục trên bảng");
            return;
        }
        try {
            Service s = new Service();
            s.setId(selectedServiceId);
            s.setName(txtName.getText().trim());
            s.setDescription(txtDesc.getText().trim());
            s.setPrice(Double.parseDouble(txtPrice.getText().trim()));
            s.setStatus(cbStatus.getSelectedItem().toString());

            String res = ServiceAPI.updateService(s);
            JOptionPane.showMessageDialog(this, res);
            loadData();
            clearForm();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Lỗi nhập liệu: " + e.getMessage());
        }
    }

    private void deleteService() {
        if (selectedServiceId == -1) return;
        int conf = JOptionPane.showConfirmDialog(this, "Xác nhận xóa?", "Xóa", JOptionPane.YES_NO_OPTION);
        if (conf == JOptionPane.YES_OPTION) {
            String res = ServiceAPI.deleteService(selectedServiceId);
            JOptionPane.showMessageDialog(this, res);
            loadData();
            clearForm();
        }
    }

    private void clearForm() {
        selectedServiceId = -1;
        txtName.setText("");
        txtDesc.setText("");
        txtPrice.setText("");
        cbStatus.setSelectedIndex(0);
        table.clearSelection();
    }
}
