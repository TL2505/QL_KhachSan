-- Snapshot rental policy on each booking so later room configuration changes do not rewrite history.
ALTER TABLE rooms
    ADD COLUMN IF NOT EXISTS buffer_hourly SMALLINT UNSIGNED NOT NULL DEFAULT 30,
    ADD COLUMN IF NOT EXISTS buffer_overnight SMALLINT UNSIGNED NOT NULL DEFAULT 60,
    ADD COLUMN IF NOT EXISTS buffer_daily SMALLINT UNSIGNED NOT NULL DEFAULT 120,
    ADD COLUMN IF NOT EXISTS ready_not_before DATETIME(6) NULL;

-- hourly_base_price is the amount for the initial two-hour block; the extra-hour column is per hour.
ALTER TABLE room_types
    ADD COLUMN IF NOT EXISTS hourly_base_price DECIMAL(10,2) NULL,
    ADD COLUMN IF NOT EXISTS hourly_extra_hour_price DECIMAL(10,2) NULL,
    ADD COLUMN IF NOT EXISTS overnight_price DECIMAL(10,2) NULL;

ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS rental_type ENUM('HOURLY','OVERNIGHT','DAILY') NOT NULL DEFAULT 'DAILY',
    ADD COLUMN IF NOT EXISTS check_in_actual DATETIME(6) NULL,
    ADD COLUMN IF NOT EXISTS check_out_actual DATETIME(6) NULL,
    ADD COLUMN IF NOT EXISTS buffer_minutes SMALLINT UNSIGNED NOT NULL DEFAULT 120;

ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS room_release_at DATETIME(6)
        AS (TIMESTAMPADD(MINUTE,buffer_minutes,COALESCE(check_out_actual,check_out_date))) PERSISTENT;

CREATE INDEX IF NOT EXISTS idx_booking_calendar_release
    ON bookings(room_id, status, check_in_date, room_release_at);

CREATE TABLE IF NOT EXISTS cleaning_tasks (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_id INT NOT NULL,
    room_id INT NOT NULL,
    rental_type ENUM('HOURLY','OVERNIGHT','DAILY') NOT NULL,
    estimated_duration_minutes SMALLINT UNSIGNED NOT NULL,
    status ENUM('pending','in_progress','completed','cancelled') NOT NULL DEFAULT 'pending',
    due_at DATETIME(6) NULL,
    expected_finish_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_cleaning_task_booking (booking_id),
    INDEX idx_cleaning_task_room_status_due (room_id, status, due_at),
    CONSTRAINT fk_cleaning_task_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cleaning_task_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE cleaning_tasks
    ADD COLUMN IF NOT EXISTS expected_finish_at DATETIME(6) NULL;
