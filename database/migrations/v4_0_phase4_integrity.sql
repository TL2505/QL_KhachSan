ALTER TABLE services ADD COLUMN IF NOT EXISTS category ENUM('FNB','SPA','HOUSEKEEPING') NULL;
INSERT IGNORE INTO roles(name,description) VALUES('manager','Hotel management'),('gm','General manager'),('revenue_manager','Revenue recommendations'),('housekeeping','Housekeeping'),('maintenance','Maintenance'),('f&b','Food and beverage');
ALTER TABLE bank_webhook_logs ADD COLUMN IF NOT EXISTS business_date DATE NULL;
CREATE TABLE IF NOT EXISTS staff_collections (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    folio_id BIGINT NOT NULL,
    reference_id VARCHAR(64) NOT NULL UNIQUE,
    amount DECIMAL(12,2) NOT NULL CHECK(amount>0),
    payment_method ENUM('cash','POS') NOT NULL,
    collected_by INT NULL,
    business_date DATE NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_collection_folio FOREIGN KEY(folio_id) REFERENCES folios(id),
    INDEX idx_collection_date(business_date)
) ENGINE=InnoDB;
