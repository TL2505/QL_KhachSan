-- ============================================================================
-- Migration: 04_social_auth.sql
-- Description: OAuth 2.0 Social Authentication support (Google & Facebook)
-- Compatible with MariaDB 11.2+ / MySQL 8.0+
-- ============================================================================

USE hotel_prod_db;

-- 1. Extend `users` table with OAuth provider tracking
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
  ADD COLUMN IF NOT EXISTS provider_id VARCHAR(120) NULL;

-- 2. Extend `customers` table with OAuth provider tracking
ALTER TABLE customers
  ADD COLUMN IF NOT EXISTS auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
  ADD COLUMN IF NOT EXISTS provider_id VARCHAR(120) NULL;

-- 3. Clean up empty string emails to NULL to prevent UNIQUE constraint collisions
UPDATE users SET email = NULL WHERE email = '';
UPDATE customers SET email = NULL WHERE email = '';

-- 4. Idempotently add UNIQUE constraint on users(email)
SET @dbname = DATABASE();
SET @tablename = "users";
SET @indexname = "uq_users_email";
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE table_schema = @dbname
      AND table_name = @tablename
      AND index_name = @indexname
  ) > 0,
  "SELECT 1",
  "CREATE UNIQUE INDEX uq_users_email ON users(email)"
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 5. Idempotently add UNIQUE constraint on customers(email)
SET @tablename = "customers";
SET @indexname = "uq_customers_email";
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE table_schema = @dbname
      AND table_name = @tablename
      AND index_name = @indexname
  ) > 0,
  "SELECT 1",
  "CREATE UNIQUE INDEX uq_customers_email ON customers(email)"
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 6. Add composite index for provider lookups
CREATE INDEX IF NOT EXISTS idx_users_provider ON users(auth_provider, provider_id);
CREATE INDEX IF NOT EXISTS idx_customers_provider ON customers(auth_provider, provider_id);
