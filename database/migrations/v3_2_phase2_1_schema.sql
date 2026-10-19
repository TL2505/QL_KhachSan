-- ======================================================================================
-- MIGRATION SCRIPT: v3_2_phase2_1_schema.sql
-- PROJECT: AURELIA GRAND PALACE 5★ PMS
-- PHASE: 2.1 - Guest Journey, In-Stay Services, Lifecycle Notifications & Reputation
-- TARGET DATABASE: MariaDB 11.2+ / MySQL 8.0+ (hotel_prod_db)
-- POLICY: Zero-Regression, Non-blocking, Idempotent execution
-- ======================================================================================

USE hotel_prod_db;

-- --------------------------------------------------------------------------------------
-- 1. BẢNG MỚI: customer_notifications
-- Quản lý thông báo vòng đời, voucher chào mừng, hóa đơn điện tử và nhắc nhở dịch vụ
-- --------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customer_notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    booking_id INT NULL,
    notification_type ENUM('booking_confirmed', 'checkin_welcome', 'checkout_gratitude', 'service_update', 'promo_alert') NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    voucher_code VARCHAR(50) NULL,
    action_url VARCHAR(255) NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notif_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_notif_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_notif_cust_unread (customer_id, is_read, created_at),
    INDEX idx_notif_booking (booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------------------------------------
-- 2. BẢNG MỚI: service_orders
-- Quản lý các yêu cầu tự phục vụ trong kỳ nghỉ của khách (F&B tại phòng, Spa, Buồng phòng)
-- --------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS service_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id INT NOT NULL,
    customer_id INT NOT NULL,
    room_id INT NOT NULL,
    category ENUM('FNB', 'SPA', 'HOUSEKEEPING') NOT NULL,
    service_id INT NULL, -- NULL nếu là yêu cầu buồng phòng tùy biến
    item_name VARCHAR(150) NOT NULL,
    quantity INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_price DECIMAL(12,2) NOT NULL DEFAULT 0.00 CHECK (unit_price >= 0),
    subtotal DECIMAL(12,2) NOT NULL DEFAULT 0.00 CHECK (subtotal >= 0),
    discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 CHECK (discount_amount >= 0),
    final_price DECIMAL(12,2) NOT NULL DEFAULT 0.00 CHECK (final_price >= 0),
    voucher_code VARCHAR(50) NULL,
    special_instructions TEXT NULL,
    scheduled_time DATETIME NULL, -- Dành riêng cho Spa hoặc giờ hẹn ăn sáng
    order_status ENUM('PENDING', 'ACCEPTED', 'DELIVERED', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
    folio_transaction_id INT NULL, -- Liên kết giao dịch nợ trên Folio phòng
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_sorder_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_sorder_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_sorder_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_sorder_service FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_sorder_folio_trans FOREIGN KEY (folio_transaction_id) REFERENCES folio_transactions(id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_sorder_active_polling (order_status, created_at),
    INDEX idx_sorder_booking_cat (booking_id, category),
    INDEX idx_sorder_room (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------------------------------------
-- 2b. BẢNG MỚI: loyalty_histories (Lịch sử tích lũy & tiêu dùng điểm thưởng)
-- --------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS loyalty_histories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    points INT NOT NULL,
    type ENUM('earn', 'redeem') NOT NULL DEFAULT 'earn',
    description VARCHAR(255) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lh_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE,
    INDEX idx_lh_customer (customer_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------------------------------------
-- 3. NÂNG CẤP BẢNG: promotions
-- Bổ sung phạm vi áp dụng đa dịch vụ (Room, F&B, Spa), sự kiện đặc biệt và hạn retention
-- --------------------------------------------------------------------------------------
ALTER TABLE promotions
    ADD COLUMN IF NOT EXISTS code VARCHAR(50) NOT NULL DEFAULT '' AFTER id,
    ADD COLUMN IF NOT EXISTS applicable_scope ENUM('ROOM', 'FNB', 'SPA', 'ALL') NOT NULL DEFAULT 'ALL' AFTER discount_type,
    ADD COLUMN IF NOT EXISTS special_occasion VARCHAR(50) NULL AFTER condition_value,
    ADD COLUMN IF NOT EXISTS max_discount DECIMAL(12,2) NOT NULL DEFAULT 99999999.00 AFTER discount_value,
    ADD COLUMN IF NOT EXISTS minimum_spend DECIMAL(12,2) NOT NULL DEFAULT 0.00 AFTER max_discount,
    ADD COLUMN IF NOT EXISTS max_uses_per_user INT NOT NULL DEFAULT 1 AFTER minimum_spend,
    ADD COLUMN IF NOT EXISTS valid_after_checkout_days INT NULL AFTER end_date;

-- Thiết lập Unique Key cho code nếu chưa có
SET @exist_idx = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'promotions' AND index_name = 'uq_promo_code');
SET @sql_idx = IF(@exist_idx = 0, 'ALTER TABLE promotions ADD UNIQUE INDEX uq_promo_code (code)', 'SELECT "Index uq_promo_code already exists"');
PREPARE stmt_idx FROM @sql_idx;
EXECUTE stmt_idx;
DEALLOCATE PREPARE stmt_idx;

-- --------------------------------------------------------------------------------------
-- 4. NÂNG CẤP BẢNG: reviews
-- Mở rộng cấu trúc đánh giá 4 khía cạnh, liên kết booking xác thực, phản hồi ban giám đốc
-- --------------------------------------------------------------------------------------
ALTER TABLE reviews
    ADD COLUMN IF NOT EXISTS booking_id INT NULL AFTER id,
    ADD COLUMN IF NOT EXISTS rating TINYINT NOT NULL DEFAULT 5 AFTER booking_id,
    ADD COLUMN IF NOT EXISTS rating_room TINYINT NOT NULL DEFAULT 5 AFTER customer_id,
    ADD COLUMN IF NOT EXISTS rating_fnb TINYINT NOT NULL DEFAULT 5 AFTER rating_room,
    ADD COLUMN IF NOT EXISTS rating_service TINYINT NOT NULL DEFAULT 5 AFTER rating_fnb,
    ADD COLUMN IF NOT EXISTS rating_cleanliness TINYINT NOT NULL DEFAULT 5 AFTER rating_service,
    ADD COLUMN IF NOT EXISTS rating_overall DECIMAL(2,1) NOT NULL DEFAULT 5.0 AFTER rating_cleanliness,
    ADD COLUMN IF NOT EXISTS management_response TEXT NULL AFTER comment,
    ADD COLUMN IF NOT EXISTS responded_by_user_id INT NULL AFTER management_response,
    ADD COLUMN IF NOT EXISTS responded_at DATETIME NULL AFTER responded_by_user_id,
    ADD COLUMN IF NOT EXISTS reward_granted BOOLEAN NOT NULL DEFAULT FALSE AFTER responded_at,
    ADD COLUMN IF NOT EXISTS reward_points INT NOT NULL DEFAULT 200 AFTER reward_granted;

-- Thêm khóa ngoại booking và liên kết người phản hồi nếu chưa có
SET @exist_fk_booking = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'reviews' AND constraint_name = 'fk_reviews_booking');
SET @sql_fk_booking = IF(@exist_fk_booking = 0, 'ALTER TABLE reviews ADD CONSTRAINT fk_reviews_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE ON UPDATE CASCADE', 'SELECT "FK fk_reviews_booking already exists"');
PREPARE stmt_fk1 FROM @sql_fk_booking;
EXECUTE stmt_fk1;
DEALLOCATE PREPARE stmt_fk1;

SET @exist_fk_resp = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'reviews' AND constraint_name = 'fk_reviews_responder');
SET @sql_fk_resp = IF(@exist_fk_resp = 0, 'ALTER TABLE reviews ADD CONSTRAINT fk_reviews_responder FOREIGN KEY (responded_by_user_id) REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE', 'SELECT "FK fk_reviews_responder already exists"');
PREPARE stmt_fk2 FROM @sql_fk_resp;
EXECUTE stmt_fk2;
DEALLOCATE PREPARE stmt_fk2;

-- Đảm bảo bảng customers có đầy đủ loyalty_points và total_loyalty_points
ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS loyalty_points INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_loyalty_points INT NOT NULL DEFAULT 0;

-- Dọn dẹp dữ liệu trùng lặp (nếu có từ test data) trước khi tạo UNIQUE INDEX uq_booking_review
DELETE r1 FROM reviews r1
INNER JOIN reviews r2
WHERE r1.id < r2.id AND r1.booking_id = r2.booking_id AND r1.booking_id IS NOT NULL;

-- Thêm Ràng buộc Duy nhất: Mỗi booking chỉ được review tối đa 1 lần (chống fake review / spam điểm thưởng)
SET @exist_uq_review = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'reviews' AND index_name = 'uq_booking_review');
SET @sql_uq_review = IF(@exist_uq_review = 0, 'ALTER TABLE reviews ADD UNIQUE INDEX uq_booking_review (booking_id)', 'SELECT "Index uq_booking_review already exists"');
PREPARE stmt_uq FROM @sql_uq_review;
EXECUTE stmt_uq;
DEALLOCATE PREPARE stmt_uq;

-- --------------------------------------------------------------------------------------
-- 5. SEED DATA MẪU (Chuẩn hóa Voucher Vòng Đời & Dịch Vụ Mới)
-- --------------------------------------------------------------------------------------
INSERT INTO promotions (code, name, description, discount_type, applicable_scope, discount_value, max_discount, minimum_spend, start_date, end_date, condition_type, condition_value, special_occasion, status)
VALUES
('AURELIA_DINE15', 'Ưu Đãi Ẩm Thực Kỳ Nghỉ 15%', 'Giảm 15% tổng hóa đơn gọi món tại phòng hoặc quầy bar trong suốt kỳ nghỉ', 'percentage', 'FNB', 15.00, 500000.00, 200000.00, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 'none', NULL, 'WELCOME', 'active'),
('WELCOME_SPA20', 'Đặc Quyền Spa Hoàng Gia 20%', 'Giảm 20% các liệu trình massage và trị liệu chăm sóc sức khỏe', 'percentage', 'SPA', 20.00, 1000000.00, 500000.00, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 'none', NULL, 'WELCOME', 'active'),
('MEMORIAL_ROOM25', 'Tri Ân Nghỉ Dưỡng Trở Lại 25%', 'Mã tri ân sau trả phòng dành cho lần đặt phòng tiếp theo hoặc dịp sinh nhật', 'percentage', 'ROOM', 25.00, 2500000.00, 1500000.00, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 'none', NULL, 'RETENTION', 'active')
ON DUPLICATE KEY UPDATE 
    name = VALUES(name),
    applicable_scope = VALUES(applicable_scope),
    discount_value = VALUES(discount_value),
    status = 'active';

-- Đảm bảo danh mục dịch vụ có các mục F&B và Spa cơ bản
INSERT INTO services (id, name, description, price, status) VALUES
(101, 'Bữa Sáng Kiểu Lục Địa (Continental Breakfast Set)', 'Bánh mì nướng bơ Pháp, trứng trần, mứt quả mọng, cà phê Cappuccino và nước cam ép tươi', 350000.00, 'active'),
(102, 'Bít Tết Bò Wagyu A5 Dùng Tại Phòng', 'Thăn ngoại bò Wagyu sốt tiêu đen kèm măng tây áp chảo và khoai tây nghiền nấm Truffle', 1250000.00, 'active'),
(103, 'Cocktail Aurelia Royal Sunset', 'Rượu Gin thượng hạng, nước ép bưởi hồng, mật ong hoa rừng và nhũ vàng 24K thực phẩm', 280000.00, 'active'),
(104, 'Trị Liệu Massage Thụy Điển Toàn Thân (60 phút)', 'Liệu trình thư giãn cơ bắp chuyên sâu với tinh dầu oải hương nguyên chất', 890000.00, 'active'),
(105, 'Liệu Trình Tắm Thảo Dược Hoàng Cung (45 phút)', 'Ngâm bồn sục Jacuzzi với 12 loại thảo mộc cung đình cổ truyền', 650000.00, 'active'),
(106, 'Dịch Vụ Buồng Phòng: Đổi Khăn Tắm & Chăn Ga Thêm', 'Cung cấp bộ chăn ga gối lông ngỗng và khăn tắm bông Ai Cập mới', 0.00, 'active'),
(107, 'Dịch Vụ Buồng Phòng: Mượn Bàn Ủi & Cầu Là Hơi Nước', 'Giao bàn ủi hơi nước chuyên dụng lên tận phòng', 0.00, 'active')
ON DUPLICATE KEY UPDATE 
    price = VALUES(price),
    description = VALUES(description),
    status = 'active';
