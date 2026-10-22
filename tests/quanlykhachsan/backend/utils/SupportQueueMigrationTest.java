package quanlykhachsan.backend.utils;

import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupportQueueMigrationTest {
    @Test void sharedQueueAcceptsMessagesAndPreservesDirectRecipients() throws Exception {
        Assumptions.assumeTrue("true".equals(System.getenv("HOTEL_RUN_SUPPORT_DB_TESTS")));
        String schema = "hotel_support_" + UUID.randomUUID().toString().replace("-", "") + "_test";
        try (Connection c = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/", "root", "");
             Statement admin = c.createStatement()) {
            admin.executeUpdate("CREATE DATABASE " + schema);
            c.setCatalog(schema);
            try (Statement s = c.createStatement()) {
            try {
                s.executeUpdate("CREATE TABLE users (id INT PRIMARY KEY) ENGINE=InnoDB");
                s.executeUpdate("INSERT INTO users VALUES (1),(2)");
                s.executeUpdate("CREATE TABLE messages (id INT AUTO_INCREMENT PRIMARY KEY, sender_id INT NOT NULL, receiver_id INT NOT NULL, content TEXT NOT NULL, FOREIGN KEY(sender_id) REFERENCES users(id), FOREIGN KEY(receiver_id) REFERENCES users(id)) ENGINE=InnoDB");
                s.executeUpdate("INSERT INTO messages(sender_id,receiver_id,content) VALUES (1,2,'existing')");
                assertThrows(SQLException.class, () -> s.executeUpdate("INSERT INTO messages(sender_id,receiver_id,content) VALUES (1,NULL,'queue')"));
                UpdateDbUtility.migrateSupportQueue(c);
                UpdateDbUtility.migrateSupportQueue(c);
                s.executeUpdate("INSERT INTO messages(sender_id,receiver_id,content) VALUES (1,NULL,'queue'),(2,1,'reply')");
                try (ResultSet rows = s.executeQuery("SELECT receiver_id,content FROM messages ORDER BY id")) {
                    assertTrue(rows.next()); assertEquals(2, rows.getInt(1)); assertEquals("existing", rows.getString(2));
                    assertTrue(rows.next()); assertNull(rows.getObject(1)); assertEquals("queue", rows.getString(2));
                    assertTrue(rows.next()); assertEquals(1, rows.getInt(1)); assertEquals("reply", rows.getString(2));
                    assertFalse(rows.next());
                }
                assertThrows(SQLException.class, () -> s.executeUpdate("INSERT INTO messages(sender_id,receiver_id,content) VALUES (1,999,'invalid recipient')"));
                assertThrows(SQLException.class, () -> s.executeUpdate("INSERT INTO messages(sender_id,receiver_id,content) VALUES (999,NULL,'invalid sender')"));
            } finally {
                s.executeUpdate("DROP DATABASE " + schema);
            }
            }
        }
    }
}
