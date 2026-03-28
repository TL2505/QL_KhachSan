package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.List;
import quanlykhachsan.backend.model.Room;
import quanlykhachsan.backend.model.RoomType;
import quanlykhachsan.frontend.api.RoomAPI;
import quanlykhachsan.frontend.api.RoomTypeAPI;

public class RoomManagementForm extends JPanel {

    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField txtRoomNumber, txtPrice;
    private JComboBox<String> cbRoomType, cbStatus;
    private List<RoomType> roomTypes;
    private JButton btnAdd;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField txtSearch;

    public RoomManagementForm() {
        setLayout(new BorderLayout());
        initUI();
        loadRoomTypes();
        loadRooms();
    }

    private void initUI() {
        // 1. Table
        tableModel = new DefaultTableModel(new String[]{"ID", "Số phòng", "Loại phòng", "Giá", "Trạng thái"}, 0);
        table = new JTable(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
        
        // --- Thanh tìm kiếm ---
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.add(new JLabel("Tìm kiếm nhanh: "));
        txtSearch = new JTextField(20);
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
        searchPanel.add(txtSearch);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(searchPanel, BorderLayout.NORTH);
        centerPanel.add(new JScrollPane(table), BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // 2. Form Panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("Thông tin phòng"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; formPanel.add(new JLabel("Số phòng:"), gbc);
        gbc.gridx = 1; txtRoomNumber = new JTextField(15); formPanel.add(txtRoomNumber, gbc);

        gbc.gridx = 0; gbc.gridy = 1; formPanel.add(new JLabel("Loại phòng:"), gbc);
        gbc.gridx = 1; cbRoomType = new JComboBox<>(); formPanel.add(cbRoomType, gbc);

        gbc.gridx = 0; gbc.gridy = 2; formPanel.add(new JLabel("Giá:"), gbc);
        gbc.gridx = 1; txtPrice = new JTextField(15); formPanel.add(txtPrice, gbc);

        gbc.gridx = 0; gbc.gridy = 3; formPanel.add(new JLabel("Trạng thái:"), gbc);
        gbc.gridx = 1; cbStatus = new JComboBox<>(new String[]{"available", "booked", "occupied", "maintenance", "dirty"}); formPanel.add(cbStatus, gbc);

        // 3. Button Panel
        JPanel btnPanel = new JPanel();
        btnAdd = new JButton("Thêm mới");
        JButton btnUpdate = new JButton("Cập nhật");
        JButton btnDelete = new JButton("Xóa");
        JButton btnClear = new JButton("Làm mới");

        btnPanel.add(btnAdd);
        btnPanel.add(btnUpdate);
        btnPanel.add(btnDelete);
        btnPanel.add(btnClear);

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; formPanel.add(btnPanel, gbc);

        add(formPanel, BorderLayout.SOUTH);

        // Action Listeners
        btnAdd.addActionListener(e -> addRoom());
        btnUpdate.addActionListener(e -> updateRoom());
        btnDelete.addActionListener(e -> deleteRoom());
        btnClear.addActionListener(e -> clearForm());

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() != -1) {
                fillForm();
            }
        });
    }

    private void loadRoomTypes() {
        roomTypes = RoomTypeAPI.getAllRoomTypes();
        cbRoomType.removeAllItems();
        for (RoomType rt : roomTypes) {
            cbRoomType.addItem(rt.getName());
        }
    }

    private void loadRooms() {
        tableModel.setRowCount(0);
        new SwingWorker<List<Room>, Void>() {
            @Override protected List<Room> doInBackground() { return RoomAPI.getAllRooms(); }
            @Override protected void done() {
                try {
                    List<Room> rooms = get();
                    for (Room r : rooms) {
                        String typeName = "Unknown";
                        for(RoomType rt : roomTypes) if(rt.getId() == r.getRoomTypeId()) typeName = rt.getName();
                        tableModel.addRow(new Object[]{r.getId(), r.getRoomNumber(), typeName, r.getPrice(), r.getStatus()});
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
        }.execute();
    }

    private void fillForm() {
        int row = table.getSelectedRow();
        if (row == -1) return;
        txtRoomNumber.setText(tableModel.getValueAt(row, 1).toString());
        cbRoomType.setSelectedItem(tableModel.getValueAt(row, 2).toString());
        txtPrice.setText(tableModel.getValueAt(row, 3).toString());
        cbStatus.setSelectedItem(tableModel.getValueAt(row, 4).toString());
        btnAdd.setEnabled(false);
    }

    private void addRoom() {
        Room r = getRoomFromForm();
        if (r != null) {
            String msg = RoomAPI.addRoom(r);
            JOptionPane.showMessageDialog(this, msg);
            loadRooms();
            clearForm();
        }
    }

    private void updateRoom() {
        int row = table.getSelectedRow();
        if (row == -1) return;
        Room r = getRoomFromForm();
        if (r != null) {
            r.setId((int) tableModel.getValueAt(row, 0));
            String msg = RoomAPI.updateRoom(r);
            JOptionPane.showMessageDialog(this, msg);
            loadRooms();
        }
    }

    private void deleteRoom() {
        int row = table.getSelectedRow();
        if (row == -1) return;
        int id = (int) tableModel.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn xóa phòng này không?", "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            String msg = RoomAPI.deleteRoom(id);
            JOptionPane.showMessageDialog(this, msg);
            loadRooms();
            clearForm();
        }
    }

    private void clearForm() {
        txtRoomNumber.setText("");
        txtPrice.setText("");
        cbRoomType.setSelectedIndex(0);
        cbStatus.setSelectedIndex(0);
        table.clearSelection();
        btnAdd.setEnabled(true);
    }

    private Room getRoomFromForm() {
        try {
            Room r = new Room();
            r.setRoomNumber(txtRoomNumber.getText());
            r.setPrice(Double.parseDouble(txtPrice.getText()));
            r.setStatus(cbStatus.getSelectedItem().toString());
            
            String typeName = cbRoomType.getSelectedItem().toString();
            for (RoomType rt : roomTypes) {
                if (rt.getName().equals(typeName)) {
                    r.setRoomTypeId(rt.getId());
                    break;
                }
            }
            return r;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Dữ liệu không hợp lệ: " + e.getMessage());
            return null;
        }
    }
}
