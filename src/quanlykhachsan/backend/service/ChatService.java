package quanlykhachsan.backend.service;

import quanlykhachsan.backend.dao.ChatDAO;
import quanlykhachsan.backend.daoimpl.ChatDAOImpl;
import quanlykhachsan.backend.model.Message;
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
