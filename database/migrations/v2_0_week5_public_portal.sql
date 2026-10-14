-- Repeatable Week 5 portal migration. Unknown legacy customer types must be
-- resolved by the operator before execution; do not silently reinterpret data.
CREATE TABLE IF NOT EXISTS guest_otps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(100),
    otp_code VARCHAR(6) NOT NULL,
    expires_at DATETIME NOT NULL,
    consumed_at DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_otp_phone_code (phone, otp_code, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE bookings MODIFY customer_type ENUM('OTA','Walk-in','Corporate','Web') DEFAULT 'Walk-in';
