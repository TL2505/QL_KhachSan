package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ArrayList;
import quanlykhachsan.backend.model.Message;
import quanlykhachsan.backend.model.User;
import quanlykhachsan.frontend.api.ChatAPI;

public class ChatPanel extends JPanel {
    private User currentUser;
    private int otherUserId;
    private String otherUserName;

    private JPanel messagesContainer;
    private JScrollPane scrollPane;
    private JTextField txtInput;
    private JButton btnSend;
    private Timer refreshTimer;
    private List<Integer> loadedMessageIds = new ArrayList<>();

    private final Color MSG_SELF = new Color(37, 99, 235); // Blue
    private final Color MSG_OTHER = new Color(241, 245, 249); // Light gray
    private final Color TEXT_OTHER = new Color(51, 65, 85);

    public ChatPanel(User currentUser, int otherUserId, String otherUserName) {
        this.currentUser = currentUser;
        this.otherUserId = otherUserId;
        this.otherUserName = otherUserName;

        setLayout(new BorderLayout());
        setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());

        initUI();
        startPolling();
    }

    private void initUI() {
        // --- Messages Area ---
        messagesContainer = new JPanel();
        messagesContainer.setLayout(new BoxLayout(messagesContainer, BoxLayout.Y_AXIS));
        messagesContainer.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        messagesContainer.setBorder(new EmptyBorder(10, 10, 10, 10));

        scrollPane = new JScrollPane(messagesContainer);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        // --- Input Area ---
        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
        inputPanel.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getCardBg());
        inputPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, quanlykhachsan.frontend.utils.ThemeManager.getBorderColor()),
            new EmptyBorder(12, 12, 12, 12)
        ));

        txtInput = new JTextField();
        txtInput.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtInput.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(quanlykhachsan.frontend.utils.ThemeManager.getBorderColor(), 1, true),
            new EmptyBorder(8, 12, 8, 12)
        ));
        txtInput.addActionListener(e -> sendMessage());

        btnSend = new JButton("Gửi >>");
        btnSend.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnSend.setBackground(MSG_SELF);
        btnSend.setForeground(Color.WHITE);
        btnSend.setFocusPainted(false);
        btnSend.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnSend.addActionListener(e -> sendMessage());

        inputPanel.add(txtInput, BorderLayout.CENTER);
        inputPanel.add(btnSend, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);
    }

    private void sendMessage() {
        String content = txtInput.getText().trim();
        if (content.isEmpty()) return;

        Message msg = new Message();
        msg.setSenderId(currentUser.getId());
        msg.setReceiverId(otherUserId);
        msg.setContent(content);

        txtInput.setText("");
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() {
                try {
                    ChatAPI.sendMessage(msg);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
            @Override protected void done() {
                try {
                    if (!get()) {
                        JOptionPane.showMessageDialog(ChatPanel.this, 
                            "Lỗi: Không thể gửi tin nhắn. Vui lòng kiểm tra lại kết nối!", 
                            "Lỗi gửi tin", JOptionPane.ERROR_MESSAGE);
                        txtInput.setText(content); // Trả lại text để khách không phải gõ lại
                    }
                } catch (Exception e) {}
                loadMessages();
            }
        }.execute();
    }

    private void startPolling() {
        refreshTimer = new Timer(3000, e -> loadMessages());
        refreshTimer.start();
        loadMessages();
    }

    public void stopPolling() {
        if (refreshTimer != null) refreshTimer.stop();
    }

    public synchronized void loadMessages() {
        new SwingWorker<List<Message>, Void>() {
            @Override protected List<Message> doInBackground() {
                return ChatAPI.getHistory(currentUser.getId(), otherUserId);
            }
            @Override protected void done() {
                try {
                    List<Message> messages = get();
                    boolean hasNew = false;
                    for (Message m : messages) {
                        if (!loadedMessageIds.contains(m.getId())) {
                            addMessageBubble(m);
                            loadedMessageIds.add(m.getId());
                            hasNew = true;
                        }
                    }
                    if (hasNew) {
                        scrollToBottom();
                    }

                    // Chỉ hiện lời chào nếu DB thực sự không có tin nhắn nào
                    // (không dùng loadedMessageIds.isEmpty() vì có thể đã có marker -1)
                    if (messages.isEmpty() && !loadedMessageIds.contains(-1)
                            && currentUser.getCustomerId() != null) {
                        showWelcomeMessage();
                        loadedMessageIds.add(-1); // Không lặp lại
                    }
                } catch (Exception e) {}
            }
        }.execute();
    }

    private void showWelcomeMessage() {
        Message welcome = new Message();
        welcome.setSenderId(-1);
        welcome.setSenderName("Quản trị viên");
        welcome.setContent("Chào bạn! Bộ phận hỗ trợ có thể giúp gì cho bạn?");
        addMessageBubble(welcome);
    }

    private void addMessageBubble(Message msg) {
        boolean isSelf = msg.getSenderId() == currentUser.getId();
        
        JPanel row = new JPanel(new FlowLayout(isSelf ? FlowLayout.RIGHT : FlowLayout.LEFT));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

        JPanel bubble = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isSelf ? MSG_SELF : MSG_OTHER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            }
        };
        bubble.setOpaque(false);
        bubble.setBorder(new EmptyBorder(8, 12, 8, 12));

        JLabel lblText = new JLabel("<html><p style='width: 180px;'>" + msg.getContent() + "</p></html>");
        lblText.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblText.setForeground(isSelf ? Color.WHITE : TEXT_OTHER);
        bubble.add(lblText);

        row.add(bubble);
        messagesContainer.add(row);
        messagesContainer.add(Box.createVerticalStrut(5));
        messagesContainer.revalidate();
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    public void setOtherUser(int id, String name) {
        this.otherUserId = id;
        this.otherUserName = name;
        this.loadedMessageIds.clear();
        this.messagesContainer.removeAll();
        this.messagesContainer.revalidate();
        this.messagesContainer.repaint();
        loadMessages();
    }
}
