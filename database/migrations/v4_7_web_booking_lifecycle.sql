-- Auditable refund state for web booking cancellation. Payment receipts remain
-- immutable in folio_transactions/payment_reconciliations.
CREATE TABLE IF NOT EXISTS booking_refunds (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_id INT NOT NULL,
    customer_id INT NOT NULL,
    provider VARCHAR(24) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(24) NOT NULL,
    provider_reference VARCHAR(120) NULL,
    reason VARCHAR(190) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at DATETIME(6) NULL,
    UNIQUE KEY uq_booking_refund_provider (booking_id,provider),
    INDEX ix_booking_refund_customer (customer_id,created_at),
    CONSTRAINT fk_booking_refund_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE RESTRICT,
    CONSTRAINT fk_booking_refund_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
