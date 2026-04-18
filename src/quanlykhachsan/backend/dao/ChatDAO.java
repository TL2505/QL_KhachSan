package quanlykhachsan.backend.dao;

import quanlykhachsan.backend.model.Message;
import java.util.List;

public interface ChatDAO {
    void addMessage(Message msg);
    List<Message> getConversation(int user1Id, int user2Id);
    List<Message> getInboxes(int staffId); // Returns list of latest messages from different customers
    void markAsRead(int conversationId, int readerId);
}
