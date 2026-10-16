CREATE TABLE IF NOT EXISTS privacy_audit_head (
    id TINYINT PRIMARY KEY CHECK(id=1),
    event_hash BINARY(32) NOT NULL
) ENGINE=InnoDB;
INSERT IGNORE INTO privacy_audit_head(id,event_hash) VALUES (1,UNHEX(REPEAT('00',32)));
CREATE TABLE IF NOT EXISTS privacy_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    previous_hash BINARY(32) NOT NULL,
    event_hash BINARY(32) NOT NULL,
    INDEX idx_privacy_subject(customer_id,occurred_at)
) ENGINE=InnoDB;
