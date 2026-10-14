CREATE TABLE IF NOT EXISTS minibar_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    room_id INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL CHECK(unit_price>=0),
    standard_stock INT NOT NULL CHECK(standard_stock>=0),
    current_stock INT NOT NULL CHECK(current_stock>=0),
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_minibar_rooms FOREIGN KEY(room_id) REFERENCES rooms(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS room_status_audits (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id INT NOT NULL,
    from_status VARCHAR(30) NOT NULL,
    to_status VARCHAR(30) NOT NULL,
    updated_by_user_id INT NOT NULL,
    reason VARCHAR(255),
    cleaning_duration_seconds INT NULL,
    inspection_notes VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_room_audit_room FOREIGN KEY(room_id) REFERENCES rooms(id),
    CONSTRAINT fk_room_audit_user FOREIGN KEY(updated_by_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
