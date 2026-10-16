CREATE TABLE IF NOT EXISTS ai_chat_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    sender ENUM('GUEST','BOT','STAFF') NOT NULL,
    prompt TEXT NOT NULL,
    response TEXT NOT NULL,
    confidence_score DECIMAL(3,2) NULL,
    correlation_id CHAR(36) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session(session_id,created_at)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS ai_escalation_tickets (
    id INT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    room_id INT NULL,
    guest_name VARCHAR(100) NULL,
    issue_summary VARCHAR(255) NOT NULL,
    status ENUM('OPEN','ACKNOWLEDGED','RESOLVED') NOT NULL DEFAULT 'OPEN',
    assigned_staff_id INT NULL,
    correlation_id CHAR(36) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at DATETIME NULL,
    resolved_at DATETIME NULL,
    INDEX idx_ticket_status(status,created_at)
) ENGINE=InnoDB;
ALTER TABLE ai_escalation_tickets ADD COLUMN IF NOT EXISTS owner_id INT NULL;
ALTER TABLE ai_escalation_tickets ADD COLUMN IF NOT EXISTS owner_role VARCHAR(16) NULL;
CREATE TABLE IF NOT EXISTS ai_ticket_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id INT NOT NULL,
    sender ENUM('GUEST','STAFF') NOT NULL,
    encrypted_body BLOB NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ticket_message FOREIGN KEY(ticket_id) REFERENCES ai_escalation_tickets(id) ON DELETE CASCADE,
    INDEX idx_ticket_message(ticket_id,id)
) ENGINE=InnoDB;
