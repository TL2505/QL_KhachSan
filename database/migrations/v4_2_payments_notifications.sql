-- Additive migration. Never change identity vault keys when applying this file.
ALTER TABLE folio_transactions MODIFY reference_id VARCHAR(190) NULL;
ALTER TABLE customer_notifications MODIFY notification_type VARCHAR(64) NOT NULL;
ALTER TABLE customer_notifications ADD COLUMN IF NOT EXISTS event_key VARCHAR(190) NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_event ON customer_notifications(event_key);

CREATE TABLE IF NOT EXISTS payment_links (
 order_code BIGINT PRIMARY KEY AUTO_INCREMENT,
 folio_id BIGINT NOT NULL,
 provider VARCHAR(24) NOT NULL DEFAULT 'payos',
 payment_link_id VARCHAR(100) NULL,
 amount DECIMAL(12,2) NOT NULL,
 reference_code VARCHAR(25) NOT NULL,
 account_number VARCHAR(40) NOT NULL,
 account_name VARCHAR(150) NOT NULL,
 bank_bin VARCHAR(20) NOT NULL,
 state VARCHAR(24) NOT NULL DEFAULT 'CREATING',
 qr_payload TEXT NULL,
 checkout_url TEXT NULL,
 expires_at DATETIME NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX ix_payment_link_folio(folio_id,state),
 UNIQUE KEY uq_gateway_link(provider,payment_link_id),
 FOREIGN KEY(folio_id) REFERENCES folios(id)
) AUTO_INCREMENT=100000;

CREATE TABLE IF NOT EXISTS payment_reconciliations (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 provider VARCHAR(24) NOT NULL,
 payment_method VARCHAR(24) NOT NULL DEFAULT 'bank_transfer',
 direction VARCHAR(8) NOT NULL DEFAULT 'IN',
 transaction_id VARCHAR(100) NOT NULL,
 fact_hash CHAR(64) NOT NULL,
 folio_id BIGINT NULL,
 order_code BIGINT NOT NULL,
 amount DECIMAL(12,2) NOT NULL,
 applied_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
 excess_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
 payment_status VARCHAR(32) NOT NULL,
 reason VARCHAR(64) NOT NULL,
 transaction_at DATETIME NOT NULL,
 response_body TEXT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uq_provider_transaction(provider,transaction_id),
 INDEX ix_payment_history(folio_id,created_at),
 FOREIGN KEY(folio_id) REFERENCES folios(id)
);
CREATE TABLE IF NOT EXISTS payment_webhook_deliveries (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 reconciliation_id BIGINT NOT NULL,
 result VARCHAR(32) NOT NULL,
 received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(reconciliation_id) REFERENCES payment_reconciliations(id)
);

CREATE TABLE IF NOT EXISTS notification_outbox (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 event_key VARCHAR(190) NOT NULL UNIQUE,
 customer_id INT NOT NULL,
 booking_id INT NULL,
 event_type VARCHAR(64) NOT NULL,
 recipient VARCHAR(254) NULL,
 subject VARCHAR(255) NOT NULL,
 body TEXT NOT NULL,
 state VARCHAR(24) NOT NULL DEFAULT 'PENDING',
 attempts INT NOT NULL DEFAULT 0,
 next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 lease_token CHAR(36) NULL,
 lease_until DATETIME NULL,
 last_error VARCHAR(64) NULL,
 sent_at DATETIME NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX ix_notification_due(state,next_attempt_at),
 FOREIGN KEY(customer_id) REFERENCES customers(id),
 FOREIGN KEY(booking_id) REFERENCES bookings(id) ON DELETE SET NULL
);
CREATE TABLE IF NOT EXISTS notification_delivery_attempts (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 outbox_id BIGINT NOT NULL,
 attempt_number INT NOT NULL,
 state VARCHAR(24) NOT NULL,
 error_code VARCHAR(64) NULL,
 started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 finished_at DATETIME NULL,
 UNIQUE KEY uq_notification_attempt(outbox_id,attempt_number),
 FOREIGN KEY(outbox_id) REFERENCES notification_outbox(id)
);

-- Import existing authoritative receipts into the common audit without touching the ledger.
INSERT IGNORE INTO payment_reconciliations(provider,payment_method,transaction_id,fact_hash,folio_id,order_code,amount,applied_amount,excess_amount,payment_status,reason,transaction_at,response_body,created_at)
SELECT IF(s.payment_method='cash','cash','pos'),s.payment_method,s.reference_id,SHA2(CONCAT('backfill:',s.reference_id),256),s.folio_id,0,s.amount,s.amount,0,IF((SELECT f.balance FROM folios f WHERE f.id=s.folio_id)=0,'PAID','PARTIAL'),'HISTORICAL_COLLECTION',s.created_at,'{}',s.created_at FROM staff_collections s;
INSERT IGNORE INTO payment_reconciliations(provider,payment_method,transaction_id,fact_hash,folio_id,order_code,amount,applied_amount,excess_amount,payment_status,reason,transaction_at,response_body,created_at)
SELECT 'legacy_bank','bank_transfer',l.bank_trans_id,SHA2(CONCAT('backfill:',l.bank_trans_id),256),l.folio_id,0,l.amount,
 COALESCE((SELECT SUM(t.amount) FROM folio_transactions t WHERE t.folio_id=l.folio_id AND t.reference_id=l.bank_trans_id AND t.charge_type='payment' AND t.transaction_type='credit'),0),
 COALESCE((SELECT SUM(t.amount) FROM folio_transactions t WHERE t.folio_id=l.folio_id AND t.reference_id=l.bank_trans_id AND t.charge_type='credit_adjustment' AND t.transaction_type='credit'),0),
 IF(JSON_UNQUOTE(JSON_EXTRACT(l.response_body,'$.data.status'))='SETTLED','PAID','PARTIAL'),'HISTORICAL_COLLECTION',l.created_at,l.response_body,l.created_at FROM bank_webhook_logs l WHERE l.status='PROCESSED';
