package quanlykhachsan.backend.interaction;

import quanlykhachsan.backend.interaction.ChatDAO;
import quanlykhachsan.backend.interaction.ChatDAOImpl;
import quanlykhachsan.backend.interaction.Message;
import java.util.List;

public class ChatService {
    private ChatDAO chatDAO = new ChatDAOImpl();

    public void sendMessage(Message msg) {
        chatDAO.addMessage(msg);
    }

    public List<Message> getConversation(int u1, int u2) {
        return chatDAO.getConversation(u1, u2);
    }

    public List<Message> getInboxes(int staffId) {
        return chatDAO.getInboxes(staffId);
    }

    public void markAsRead(int conversationId, int readerId) {
        chatDAO.markAsRead(conversationId, readerId);
    }
}
