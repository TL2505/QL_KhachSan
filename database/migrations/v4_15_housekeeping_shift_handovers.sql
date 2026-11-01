CREATE TABLE IF NOT EXISTS housekeeping_shift_handovers (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    outgoing_user_id INT NOT NULL,
    accepted_by_user_id INT NULL,
    status ENUM('pending','accepted','cancelled') NOT NULL DEFAULT 'pending',
    notes VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    accepted_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    INDEX ix_hk_handovers_pending(status,created_at),
    INDEX ix_hk_handovers_outgoing(outgoing_user_id,created_at),
    INDEX ix_hk_handovers_accepting(accepted_by_user_id,created_at),
    CONSTRAINT fk_hk_handover_outgoing_user FOREIGN KEY (outgoing_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_hk_handover_accepting_user FOREIGN KEY (accepted_by_user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS housekeeping_shift_handover_tasks (
    handover_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    room_number_at_handover VARCHAR(30) NOT NULL,
    room_type_at_handover VARCHAR(120) NULL,
    task_status_at_handover ENUM('pending','in_progress') NOT NULL,
    PRIMARY KEY(handover_id,task_id),
    INDEX ix_hk_handover_task_task(task_id,handover_id),
    CONSTRAINT fk_hk_handover_task_handover FOREIGN KEY (handover_id) REFERENCES housekeeping_shift_handovers(id) ON DELETE CASCADE,
    CONSTRAINT fk_hk_handover_task_cleaning FOREIGN KEY (task_id) REFERENCES cleaning_tasks(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
