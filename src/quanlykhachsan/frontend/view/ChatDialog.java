package quanlykhachsan.frontend.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import quanlykhachsan.backend.model.User;

public class ChatDialog extends JDialog {
    private ChatPanel chatPanel;

    public ChatDialog(Frame owner, User currentUser) {
        super(owner, "Hỗ trợ khách hàng - Chat", false);
        setSize(400, 550);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        // ID 0 đại diện cho hàng đợi hỗ trợ chung (Unassigned Support)
        chatPanel = new ChatPanel(currentUser, 0, "Hỗ trợ khách hàng");
        add(chatPanel, BorderLayout.CENTER);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                chatPanel.stopPolling();
            }
        });
    }
}
