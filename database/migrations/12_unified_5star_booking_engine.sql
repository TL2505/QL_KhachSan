-- ==============================================================================
-- AURELIA GRAND PALACE 5★ PMS - DATABASE MIGRATION SCRIPT
-- Migration: 12_unified_5star_booking_engine.sql
-- Module: Unified 5-Star Booking & Front Desk Engine (Back-to-Back Turnover)
-- Standard: 4.1.0-REALISTIC-BASELINE (SOC 2 Type II, HTNG 2026, ISO 27001)
-- Engine Target: MariaDB 11.2 Jammy
-- 
-- STRICT INVARIANTS:
-- 1. Idempotency: All DDL statements use IF NOT EXISTS / DROP PROCEDURE IF EXISTS.
-- 2. Data Preservation: Zero DROP TABLE / TRUNCATE operations.
-- 3. Identity Protection: chk_no_plain_identity CHECK (identity_card IS NULL).
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. Table: room_locks (Distributed 10-Minute TTL Concurrency Guard)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS room_locks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id INT NOT NULL,
    channel ENUM('WEB_GUEST', 'STAFF_POS', 'OTA_CHANNEL') NOT NULL,
    session_token VARCHAR(64) NOT NULL,
    locked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    CONSTRAINT fk_room_locks_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE,
    INDEX idx_room_locks_lookup (room_id, expires_at),
    INDEX idx_room_locks_session (session_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 2. Stored Procedure: sp_acquire_room_lock (Atomic Lock Acquisition with TTL)
-- ------------------------------------------------------------------------------
DELIMITER //

DROP PROCEDURE IF EXISTS sp_acquire_room_lock //

CREATE PROCEDURE sp_acquire_room_lock(
    IN p_room_id INT,
    IN p_channel VARCHAR(20),       -- 'WEB_GUEST' or 'STAFF_POS'
    IN p_session_token VARCHAR(64),
    IN p_lock_duration_seconds INT,
    OUT p_lock_acquired BOOLEAN
)
BEGIN
    DECLARE v_now DATETIME;
    DECLARE v_active_lock_count INT;
    SET v_now = NOW();
    SET p_lock_acquired = FALSE;

    START TRANSACTION;
    
    -- Purge stale locks for this specific room
    DELETE FROM room_locks 
    WHERE room_id = p_room_id 
      AND expires_at <= v_now;

    -- Check if active unexpired lock exists using pessimistic row locking
    SELECT COUNT(*) INTO v_active_lock_count
    FROM room_locks
    WHERE room_id = p_room_id
      AND expires_at > v_now
    FOR UPDATE;

    -- If room is completely unlocked, acquire the lock
    IF v_active_lock_count = 0 THEN
        INSERT INTO room_locks (
            room_id, channel, session_token, locked_at, expires_at
        ) VALUES (
            p_room_id, 
            p_channel, 
            p_session_token, 
            v_now, 
            DATE_ADD(v_now, INTERVAL p_lock_duration_seconds SECOND)
        );
        SET p_lock_acquired = TRUE;
    END IF;

    COMMIT;
END //

DELIMITER ;

-- ------------------------------------------------------------------------------
-- 3. Table: guest_identity_vault (Zero-Plaintext AES-256-GCM Secure Storage)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS guest_identity_vault (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL UNIQUE,
    encrypted_id_number VARBINARY(512) NOT NULL,
    encrypted_raw_payload MEDIUMBLOB NULL,
    face_embedding_blob BLOB NULL,
    captured_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vault_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 4. Identity Constraint Enforcement (Invariant 2 - P0)
-- ------------------------------------------------------------------------------
-- Do not clear plaintext identities here: they must first be encrypted into
-- guest_identity_vault by IdentityVaultMigration --apply with the approved keys.
-- If legacy values remain, adding chk_no_plain_identity below intentionally
-- fails and stops this migration without discarding customer data.
ALTER TABLE customers MODIFY identity_card VARCHAR(20) NULL;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS identity_hash BINARY(32) NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_customer_identity_hash ON customers(identity_hash);

-- Idempotent check constraint injection for MariaDB 11.2
SELECT COUNT(*) AS plaintext_identity_rows_before_constraint
FROM customers WHERE identity_card IS NOT NULL;
SET @chk_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS 
    WHERE CONSTRAINT_SCHEMA = DATABASE() 
      AND TABLE_NAME = 'customers' 
      AND CONSTRAINT_NAME = 'chk_no_plain_identity'
);
SET @sql = IF(@chk_exists = 0, 'ALTER TABLE customers ADD CONSTRAINT chk_no_plain_identity CHECK (identity_card IS NULL);', 'SELECT 1;');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------------------
-- 5. Table: folios (Master & Incidental Double-Entry Ledger)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS folios (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id INT NOT NULL,
    folio_type ENUM('master', 'guest', 'incidental') NOT NULL DEFAULT 'master',
    balance DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    status ENUM('open', 'closed', 'settled') NOT NULL DEFAULT 'open',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_folios_bookings FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_folio_booking_id (booking_id),
    INDEX idx_folio_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 6. Table: folio_transactions (Double-Entry Debit/Credit Ledger)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS folio_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    folio_id BIGINT NOT NULL,
    transaction_type ENUM('debit', 'credit') NOT NULL,
    charge_type VARCHAR(50) NOT NULL,             -- 'room_rate', 'vat', 'service_charge', 'payment', 'deposit'
    description VARCHAR(255) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL CHECK(amount > 0),
    business_date DATE NOT NULL,
    reference_id VARCHAR(64) NULL,                -- External Payment Trans ID or Idempotency Key
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ft_folio FOREIGN KEY (folio_id) REFERENCES folios(id) ON DELETE CASCADE,
    INDEX idx_ft_folio_id (folio_id),
    INDEX idx_ft_business_date (business_date),
    INDEX idx_ft_reference (reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 7. Table: guest_preferences (Pre-Arrival Concierge Data Store)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS guest_preferences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id INT NOT NULL UNIQUE,
    customer_id INT NOT NULL,
    pillow_type ENUM('GOOSE_DOWN', 'MEMORY_FOAM', 'BUCKWHEAT', 'LATEX') NOT NULL DEFAULT 'GOOSE_DOWN',
    welcome_drink ENUM('CHAMPAGNE_DOM_PERIGNON', 'COCONUT_WATER', 'ORGANIC_HERBAL_TEA') NOT NULL DEFAULT 'CHAMPAGNE_DOM_PERIGNON',
    airport_limousine BOOLEAN NOT NULL DEFAULT FALSE,
    flight_number VARCHAR(20) NULL,
    dietary_allergies JSON NULL,
    special_notes TEXT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_pref_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    CONSTRAINT fk_pref_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 8. Updates to bookings Table (Audit Columns & Back-to-Back Turnover Flags)
-- ------------------------------------------------------------------------------
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS checkin_code VARCHAR(32) NULL;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS digital_pass_url VARCHAR(255) NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_booking_checkin_code ON bookings(checkin_code);

ALTER TABLE bookings ADD COLUMN IF NOT EXISTS is_back_to_back BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS turnover_priority ENUM('STANDARD', 'RUSH', 'VIP_RUSH') NOT NULL DEFAULT 'STANDARD';

-- Keep existing B2B arrivals visible to the live Housekeeping Portal queue.
UPDATE bookings b
SET b.is_back_to_back = EXISTS (
    SELECT 1 FROM bookings next_booking
    WHERE next_booking.room_id = b.room_id
      AND next_booking.id <> b.id
      AND next_booking.status IN ('pending', 'confirmed')
      AND next_booking.check_in_date >= b.check_out_date
      AND DATE(next_booking.check_in_date) = DATE(b.check_out_date)
)
WHERE b.status IN ('confirmed', 'checked_in', 'checked_out');

ALTER TABLE cleaning_tasks ADD COLUMN IF NOT EXISTS turnover_priority
    ENUM('STANDARD', 'RUSH', 'VIP_RUSH') NOT NULL DEFAULT 'STANDARD';

-- Accelerate date-range interval overlap queries: (B_in < A_out) AND (B_out > A_in)
CREATE INDEX IF NOT EXISTS idx_bookings_dates ON bookings (room_id, check_in_date, check_out_date, status);

-- ------------------------------------------------------------------------------
-- 9. Table: housekeeping_turnover_tasks (90-Minute SLA High-Velocity Room Turnover Queue)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS housekeeping_turnover_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id INT NOT NULL,
    booking_out_id INT NOT NULL,
    booking_in_id INT NOT NULL,
    priority ENUM('STANDARD', 'RUSH', 'VIP_RUSH') NOT NULL DEFAULT 'RUSH',
    target_ready_time DATETIME NOT NULL,
    turnover_started_at DATETIME NULL,
    turnover_completed_at DATETIME NULL,
    inspected_at DATETIME NULL,
    status ENUM('PENDING', 'IN_PROGRESS', 'READY_FOR_INSPECTION', 'APPROVED', 'DELAYED') NOT NULL DEFAULT 'PENDING',
    assigned_housekeeper_id INT NULL,
    inspected_by_staff_id INT NULL,
    notes TEXT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_turnover_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_turnover_booking_out FOREIGN KEY (booking_out_id) REFERENCES bookings(id) ON DELETE CASCADE,
    CONSTRAINT fk_turnover_booking_in FOREIGN KEY (booking_in_id) REFERENCES bookings(id) ON DELETE CASCADE,
    INDEX idx_turnover_status_target (status, target_ready_time),
    INDEX idx_turnover_room_status (room_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
