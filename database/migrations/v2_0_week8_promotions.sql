CREATE TABLE IF NOT EXISTS promotion_redemptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    promotion_id INT NOT NULL,
    customer_id INT NOT NULL,
    booking_id INT NOT NULL UNIQUE,
    folio_id BIGINT NOT NULL UNIQUE,
    discount_amount DECIMAL(12,2) NOT NULL CHECK(discount_amount>=0),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_redemption_promotion FOREIGN KEY(promotion_id) REFERENCES promotions(id),
    CONSTRAINT fk_redemption_customer FOREIGN KEY(customer_id) REFERENCES customers(id),
    CONSTRAINT fk_redemption_booking FOREIGN KEY(booking_id) REFERENCES bookings(id),
    CONSTRAINT fk_redemption_folio FOREIGN KEY(folio_id) REFERENCES folios(id),
    INDEX idx_redemption_guest(promotion_id,customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
