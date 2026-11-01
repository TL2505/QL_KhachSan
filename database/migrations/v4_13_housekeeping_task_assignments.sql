ALTER TABLE cleaning_tasks
    ADD COLUMN IF NOT EXISTS assigned_to_user_id INT NULL,
    ADD COLUMN IF NOT EXISTS started_at DATETIME(6) NULL;

CREATE INDEX IF NOT EXISTS idx_cleaning_task_assignee_status
    ON cleaning_tasks(assigned_to_user_id, status);
