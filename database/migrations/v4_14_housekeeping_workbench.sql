ALTER TABLE cleaning_tasks
    ADD COLUMN IF NOT EXISTS needs_reinspection TINYINT(1) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS housekeeping_checklist_items (
    task_id BIGINT NOT NULL,
    item_key VARCHAR(40) NOT NULL,
    item_label VARCHAR(120) NOT NULL,
    is_required TINYINT(1) NOT NULL DEFAULT 1,
    completed_by_user_id INT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY(task_id,item_key),
    CONSTRAINT fk_hk_checklist_task FOREIGN KEY (task_id) REFERENCES cleaning_tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_hk_checklist_user FOREIGN KEY (completed_by_user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS housekeeping_incidents (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    room_id INT NOT NULL,
    reported_by_user_id INT NOT NULL,
    incident_type ENUM('damage','missing_item','maintenance','cleanliness','other') NOT NULL,
    description VARCHAR(1000) NOT NULL,
    photo_url VARCHAR(500) NULL,
    needs_reinspection TINYINT(1) NOT NULL DEFAULT 0,
    status ENUM('open','in_progress','resolved') NOT NULL DEFAULT 'open',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at DATETIME(6) NULL,
    INDEX ix_hk_incidents_room_status(room_id,status,created_at),
    INDEX ix_hk_incidents_task(task_id,created_at),
    CONSTRAINT fk_hk_incident_task FOREIGN KEY (task_id) REFERENCES cleaning_tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_hk_incident_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE RESTRICT,
    CONSTRAINT fk_hk_incident_user FOREIGN KEY (reported_by_user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS housekeeping_task_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    actor_user_id INT NULL,
    event_type VARCHAR(40) NOT NULL,
    details VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX ix_hk_task_events_history(task_id,created_at),
    CONSTRAINT fk_hk_task_event_task FOREIGN KEY (task_id) REFERENCES cleaning_tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_hk_task_event_actor FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed repeatable task checks. Legacy completed jobs are shown as completed rather
-- than appearing to have an unfinished checklist after this feature is installed.
INSERT IGNORE INTO housekeeping_checklist_items(task_id,item_key,item_label,is_required,completed_by_user_id,completed_at)
SELECT t.id, items.item_key, items.item_label, 1,
       CASE WHEN t.status='completed' THEN t.assigned_to_user_id ELSE NULL END,
       CASE WHEN t.status='completed' THEN t.completed_at ELSE NULL END
FROM cleaning_tasks t
CROSS JOIN (
    SELECT 'linens' AS item_key,'Thay ga giường và vỏ gối' AS item_label UNION ALL
    SELECT 'bathroom','Vệ sinh phòng tắm' UNION ALL
    SELECT 'towels','Bổ sung khăn và vật dụng' UNION ALL
    SELECT 'trash','Đổ rác và thay túi rác' UNION ALL
    SELECT 'equipment','Kiểm tra thiết bị và đèn' UNION ALL
    SELECT 'final_check','Kiểm tra tổng thể trước bàn giao'
) items
WHERE t.status<>'cancelled';

INSERT INTO housekeeping_task_events(task_id,actor_user_id,event_type,details,created_at)
SELECT t.id,t.assigned_to_user_id,'task_started','Nhận dọn phòng (lịch sử trước khi bật nhật ký)',t.started_at
FROM cleaning_tasks t
WHERE t.started_at IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM housekeeping_task_events e WHERE e.task_id=t.id AND e.event_type='task_started');

INSERT INTO housekeeping_task_events(task_id,actor_user_id,event_type,details,created_at)
SELECT t.id,t.assigned_to_user_id,'task_completed','Hoàn tất dọn phòng (lịch sử trước khi bật nhật ký)',t.completed_at
FROM cleaning_tasks t
WHERE t.status='completed' AND t.completed_at IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM housekeeping_task_events e WHERE e.task_id=t.id AND e.event_type='task_completed');
