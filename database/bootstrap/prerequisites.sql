-- Fresh-schema prerequisites shared by the container initializer and its JDBC test.
-- Only execute after the schema-only portion of hotel.sql on a new database.
INSERT INTO roles(id,name,description) VALUES(1,'admin','PMS administrator'),(2,'staff','Front desk'),(3,'customer','Guest member');
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS maintenance_until DATETIME NULL;
ALTER TABLE rooms MODIFY status ENUM('vacant_clean','vacant_dirty','occupied_clean','occupied_dirty','out_of_order','out_of_service','reserved') NOT NULL DEFAULT 'vacant_clean';
CREATE INDEX IF NOT EXISTS idx_room_number_version ON rooms(room_number,version);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS check_out_actual DATETIME NULL;
ALTER TABLE bookings MODIFY status ENUM('pending','confirmed','checked_in','checked_out','cancelled','no_show') DEFAULT 'pending';
ALTER TABLE users ADD COLUMN IF NOT EXISTS google_subject VARCHAR(255) NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS theme VARCHAR(20) DEFAULT 'light';
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_google_subject ON users(google_subject);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS company_name VARCHAR(150) NULL;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS tax_code VARCHAR(20) NULL;
ALTER TABLE folio_transactions DROP FOREIGN KEY IF EXISTS fk_folio_transactions_folios;
ALTER TABLE folios MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE folio_transactions MODIFY folio_id BIGINT NOT NULL;
ALTER TABLE folio_transactions MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE folio_transactions ADD CONSTRAINT fk_folio_transactions_folios FOREIGN KEY(folio_id) REFERENCES folios(id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE folios ADD COLUMN IF NOT EXISTS code VARCHAR(64) NOT NULL DEFAULT (CONCAT('FOLIO-',UUID()));
CREATE UNIQUE INDEX IF NOT EXISTS uq_folio_code ON folios(code);
ALTER TABLE folios ADD CONSTRAINT chk_folio_nonnegative CHECK(balance>=0);
ALTER TABLE daily_business_reports ADD COLUMN IF NOT EXISTS snapshot_json LONGTEXT NULL;
ALTER TABLE promotions ADD COLUMN IF NOT EXISTS used_count INT NOT NULL DEFAULT 0;
