-- Folio PK/child-key widening and code/check additions are metadata guarded by
-- UpdateDbUtility. Audit deliveries separately from unique bank transactions.
CREATE TABLE IF NOT EXISTS bank_webhook_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bank_trans_id VARCHAR(100) NOT NULL UNIQUE,
    folio_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    signature VARCHAR(128) NOT NULL,
    status ENUM('PROCESSED','REJECTED','DUPLICATE') NOT NULL,
    raw_payload LONGTEXT,
    response_body LONGTEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bank_webhook_folio FOREIGN KEY(folio_id) REFERENCES folios(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS bank_webhook_deliveries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bank_trans_id VARCHAR(100) NOT NULL,
    status ENUM('PROCESSED','DUPLICATE') NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_delivery_bank_transaction FOREIGN KEY(bank_trans_id) REFERENCES bank_webhook_logs(bank_trans_id),
    INDEX idx_delivery_bank_transaction(bank_trans_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
