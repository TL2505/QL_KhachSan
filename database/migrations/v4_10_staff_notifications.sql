CREATE TABLE IF NOT EXISTS staff_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_key VARCHAR(190) NOT NULL,
    booking_id INT NULL,
    title VARCHAR(160) NOT NULL,
    message TEXT NOT NULL,
    action_url VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_staff_notification_event(event_key),
    INDEX ix_staff_notifications_created(created_at),
    CONSTRAINT fk_staff_notification_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS staff_notification_reads (
    notification_id BIGINT NOT NULL,
    user_id INT NOT NULL,
    read_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(notification_id,user_id),
    INDEX ix_staff_notification_reads_user(user_id),
    CONSTRAINT fk_staff_notification_read_notification FOREIGN KEY (notification_id) REFERENCES staff_notifications(id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_notification_read_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
