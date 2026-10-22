-- Inventory of browsers authenticated to each account; device IDs are not credentials.
CREATE TABLE IF NOT EXISTS user_login_devices (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    device_key_hash CHAR(64) NOT NULL,
    user_agent VARCHAR(512) NOT NULL,
    last_ip VARCHAR(64) NOT NULL,
    first_login_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_user_login_device(user_id,device_key_hash),
    INDEX idx_user_device_seen(user_id,last_seen_at),
    CONSTRAINT fk_login_device_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
