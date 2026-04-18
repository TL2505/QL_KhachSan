package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import quanlykhachsan.backend.model.Message;
import quanlykhachsan.backend.model.User;
import quanlykhachsan.frontend.api.ChatAPI;

public class SupportManagementForm extends JPanel {
    private User currentUser;
    private JPanel listContainer;
    private JPanel chatCardPanel;
    private CardLayout cardLayout;
    private ChatPanel activeChatPanel;

    private static final Color BG = new Color(248, 250, 252);
    private static final Color BORDER = new Color(226, 232, 240);

    public SupportManagementForm(User admin) {
        this.currentUser = admin;
        setLayout(new BorderLayout());
        setBackground(BG);

        initUI();
        loadInboxes();
    }

    private void initUI() {
        // --- Sidebar (Inbox List) ---
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(300, 0));
        sidebar.setBackground(Color.WHITE);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER));

        JLabel lblTitle = new JLabel("Hỗ trợ trực tuyến");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setBorder(new EmptyBorder(20, 20, 15, 20));
        sidebar.add(lblTitle, BorderLayout.NORTH);

        listContainer = new JPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setBackground(Color.WHITE);

        JScrollPane scrollList = new JScrollPane(listContainer);
        scrollList.setBorder(null);
        sidebar.add(scrollList, BorderLayout.CENTER);

        JButton btnRefresh = new JButton("Làm mới danh sách");
        btnRefresh.addActionListener(e -> loadInboxes());
        sidebar.add(btnRefresh, BorderLayout.SOUTH);

        add(sidebar, BorderLayout.WEST);

        // --- Chat Area (CardLayout) ---
        cardLayout = new CardLayout();
        chatCardPanel = new JPanel(cardLayout);
        chatCardPanel.setBackground(BG);

        JPanel placeholder = new JPanel(new GridBagLayout());
        placeholder.setBackground(BG);
        JLabel lblEmpty = new JLabel("Chọn một hội thoại để bắt đầu hỗ trợ");
        lblEmpty.setFont(new Font("Segoe UI", Font.ITALIC, 14));
        lblEmpty.setForeground(new Color(148, 163, 184));
        placeholder.add(lblEmpty);
        
        chatCardPanel.add(placeholder, "EMPTY");
        cardLayout.show(chatCardPanel, "EMPTY");

        add(chatCardPanel, BorderLayout.CENTER);
    }

    private void loadInboxes() {
        new SwingWorker<List<Message>, Void>() {
            @Override protected List<Message> doInBackground() {
                return ChatAPI.getInbox(currentUser.getId());
            }
            @Override protected void done() {
                try {
                    List<Message> inbox = get();
                    listContainer.removeAll();
                    for (Message m : inbox) {
                        listContainer.add(createInboxItem(m));
                    }
                    if (inbox.isEmpty()) {
                        JLabel lblNone = new JLabel("Chưa có yêu cầu hỗ trợ nào mới");
                        lblNone.setFont(new Font("Segoe UI", Font.ITALIC, 14));
                        lblNone.setForeground(new Color(100, 116, 139));
                        lblNone.setBorder(new EmptyBorder(30, 20, 0, 0));
                        listContainer.add(lblNone);
                    }
                    listContainer.revalidate();
                    listContainer.repaint();
                } catch (Exception e) {}
            }
        }.execute();
    }

    private JPanel createInboxItem(Message m) {
        int otherId = (m.getSenderId() == currentUser.getId()) ? m.getReceiverId() : m.getSenderId();
        String name = m.getSenderName() != null ? m.getSenderName() : "Khách hàng #" + otherId;

        JPanel item = new JPanel(new BorderLayout(10, 5));
        item.setBackground(Color.WHITE);
        item.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
            new EmptyBorder(15, 20, 15, 20)
        ));
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel lblName = new JLabel(name);
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 14));
        
        JLabel lblPreview = new JLabel(m.getContent());
        lblPreview.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblPreview.setForeground(new Color(100, 116, 139));

        item.add(lblName, BorderLayout.NORTH);
        item.add(lblPreview, BorderLayout.CENTER);

        // Gắn listener cho cả các label con để đảm bảo bấm vào đâu cũng mở được chat
        java.awt.event.MouseAdapter clickAdapter = new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) { openChat(otherId, name); }
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) { item.setBackground(new Color(248, 250, 252)); }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) { item.setBackground(Color.WHITE); }
        };

        item.addMouseListener(clickAdapter);
        lblName.addMouseListener(clickAdapter);
        lblPreview.addMouseListener(clickAdapter);

        return item;
    }

    private void openChat(int userId, String name) {
        if (activeChatPanel != null) {
            activeChatPanel.stopPolling();
            chatCardPanel.remove(activeChatPanel);
        }

        activeChatPanel = new ChatPanel(currentUser, userId, name);
        chatCardPanel.add(activeChatPanel, "CHAT");
        cardLayout.show(chatCardPanel, "CHAT");
        chatCardPanel.revalidate();
    }
}
