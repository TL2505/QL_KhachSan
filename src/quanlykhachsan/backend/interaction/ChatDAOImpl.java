package quanlykhachsan.backend.interaction;

import quanlykhachsan.backend.interaction.ChatDAO;
import quanlykhachsan.backend.interaction.Message;
import quanlykhachsan.backend.utils.DBconn;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ChatDAOImpl implements ChatDAO {

    @Override
    public void addMessage(Message msg) {
        String sql = "INSERT INTO messages (sender_id, receiver_id, content) VALUES (?, ?, ?)";
        try (Connection con = DBconn.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, msg.getSenderId());
            if (msg.getReceiverId() == 0) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, msg.getReceiverId());
            }
            ps.setString(3, msg.getContent());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<Message> getConversation(int user1Id, int user2Id) {
        List<Message> messages = new ArrayList<>();
        String sql;

        if (user2Id == 0) {
            // Khách hàng (user1Id) xem hội thoại hỗ trợ:
            // - Tin nhắn KHÁCH gửi vào hàng đợi (sender=khách, receiver IS NULL)
            // - Tin nhắn NHÂN VIÊN trả lời cho khách (sender=bất kỳ, receiver=khách)
            sql = "SELECT m.*, u.full_name as sender_name FROM messages m " +
                  "JOIN users u ON m.sender_id = u.id " +
                  "WHERE (m.sender_id = ? AND m.receiver_id IS NULL) " +
                  "   OR (m.receiver_id = ?) " +
                  "ORDER BY m.created_at ASC";
        } else {
            // Nhân viên (user1Id) xem hội thoại với một khách cụ thể (user2Id):
            // - Tin nhắn KHÁCH gửi vào hàng đợi chung (sender=khách, receiver IS NULL)
            // - Tin nhắn NHÂN VIÊN trả lời cho khách (sender=nhân viên, receiver=khách)
            // - Tin nhắn KHÁCH gửi trực tiếp cho nhân viên
            sql = "SELECT m.*, u.full_name as sender_name FROM messages m " +
                  "JOIN users u ON m.sender_id = u.id " +
                  "WHERE (m.sender_id = ? AND m.receiver_id IS NULL) " +
                  "   OR (m.sender_id = ? AND m.receiver_id = ?) " +
                  "   OR (m.sender_id = ? AND m.receiver_id = ?) " +
                  "ORDER BY m.created_at ASC";
        }

        try (Connection con = DBconn.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            if (user2Id == 0) {
                ps.setInt(1, user1Id); // khách gửi vào hàng đợi
                ps.setInt(2, user1Id); // nhân viên reply cho khách
            } else {
                ps.setInt(1, user2Id); // khách gửi vào hàng đợi
                ps.setInt(2, user1Id); // nhân viên reply cho khách
                ps.setInt(3, user2Id);
                ps.setInt(4, user2Id); // khách reply cho nhân viên
                ps.setInt(5, user1Id);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Message m = new Message();
                    m.setId(rs.getInt("id"));
                    m.setSenderId(rs.getInt("sender_id"));
                    m.setReceiverId(rs.getInt("receiver_id"));
                    m.setContent(rs.getString("content"));
                    m.setRead(rs.getBoolean("is_read"));
                    m.setCreatedAt(rs.getTimestamp("created_at"));
                    m.setSenderName(rs.getString("sender_name"));
                    messages.add(m);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }

    @Override
    public List<Message> getInboxes(int staffId) {
        List<Message> inboxes = new ArrayList<>();
        // Logic: Find the latest message for each unique user that interacted with staff
        String sql = "SELECT m.*, u.full_name as sender_name FROM messages m " +
                     "JOIN users u ON (m.sender_id = u.id OR m.receiver_id = u.id) " +
                     "WHERE (m.receiver_id = ? OR m.sender_id = ?) " +
                     "AND u.id != ? " +
                     "AND m.id IN (SELECT MAX(id) FROM messages WHERE sender_id = ? OR receiver_id = ? GROUP BY IF(sender_id = ?, receiver_id, sender_id)) " +
                     "GROUP BY u.id ORDER BY m.created_at DESC";
        
        // Simpler approach for this demo: Just get all unique users who sent messages to staff
        String sqlSimple = "SELECT m1.*, u.full_name as sender_name " +
                           "FROM messages m1 " +
                           "JOIN users u ON u.id = IF(m1.sender_id = ?, m1.receiver_id, m1.sender_id) " +
                           "WHERE m1.id IN ( " +
                           "  SELECT MAX(id) FROM messages " +
                           "  WHERE (sender_id = ? OR receiver_id = ? OR receiver_id IS NULL) " +
                           "  GROUP BY IF(sender_id = ?, (IF(receiver_id IS NULL, 0, receiver_id)), sender_id) " +
                           ") AND u.id != ? AND u.id IS NOT NULL ORDER BY m1.created_at DESC";

        try (Connection con = DBconn.getConnection();
             PreparedStatement ps = con.prepareStatement(sqlSimple)) {
            ps.setInt(1, staffId);
            ps.setInt(2, staffId);
            ps.setInt(3, staffId);
            ps.setInt(4, staffId);
            ps.setInt(5, staffId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Message m = new Message();
                    m.setId(rs.getInt("id"));
                    m.setSenderId(rs.getInt("sender_id"));
                    m.setReceiverId(rs.getInt("receiver_id"));
                    m.setContent(rs.getString("content"));
                    m.setRead(rs.getBoolean("is_read"));
                    m.setCreatedAt(rs.getTimestamp("created_at"));
                    m.setSenderName(rs.getString("sender_name"));
                    inboxes.add(m);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return inboxes;
    }

    @Override
    public void markAsRead(int conversationId, int readerId) {
        String sql = "UPDATE messages SET is_read = TRUE WHERE (receiver_id = ? OR receiver_id IS NULL) AND is_read = FALSE";
        try (Connection con = DBconn.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, readerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
