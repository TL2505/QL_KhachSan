-- CREATE DATABASE
CREATE DATABASE IF NOT EXISTS hotel_prod_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE hotel_prod_db;

-- CLEANUP OLD TABLES
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS loyalty_histories, reviews, promotions, messages, payments, invoices, service_usage, services, booking_details, bookings, customers, rooms, room_types, users, roles;
DROP VIEW IF EXISTS v_monthly_revenue;
SET FOREIGN_KEY_CHECKS = 1;

-- --------------------------------------------------------
-- Table: roles
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS roles (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(50) NOT NULL UNIQUE,
  description VARCHAR(255),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- --------------------------------------------------------
-- Table: users
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  role_id INT NOT NULL,
  status ENUM('active', 'inactive', 'banned') DEFAULT 'active',
  full_name VARCHAR(100) NULL,
  email VARCHAR(100) NULL,
  phone VARCHAR(20) NULL,
  customer_id INT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  -- RESTRICT: Không cho xóa role nếu còn user đang dùng để tránh invalid user
  CONSTRAINT fk_users_roles FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT ON UPDATE CASCADE,
  INDEX idx_username (username),
  INDEX idx_status (status)
) ENGINE=InnoDB;

-- --------------------------------------------------------
-- Table: room_types
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS room_types (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL UNIQUE,
  description TEXT,
  base_price DECIMAL(10, 2) NOT NULL,
  capacity INT NOT NULL DEFAULT 2,
  image_url VARCHAR(255) NULL,
  amenities VARCHAR(500) DEFAULT 'Wifi, TV, Air Conditioning',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- --------------------------------------------------------
-- Table: rooms
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS rooms (
  id INT AUTO_INCREMENT PRIMARY KEY,
  room_number VARCHAR(20) NOT NULL UNIQUE,
  room_type_id INT NOT NULL,
  price DECIMAL(10, 2) NOT NULL,
  status ENUM('available', 'booked', 'occupied', 'maintenance', 'cleaning', 'out_of_service') DEFAULT 'available',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  -- RESTRICT: Không cho xóa loại phòng nếu có phòng đang cấu hình loại này
  CONSTRAINT fk_rooms_room_types FOREIGN KEY (room_type_id) REFERENCES room_types(id) ON DELETE RESTRICT ON UPDATE CASCADE,
  INDEX idx_room_number (room_number),
  INDEX idx_room_status (status)
) ENGINE=InnoDB;

-- --------------------------------------------------------
-- Table: customers
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS customers (
  id INT AUTO_INCREMENT PRIMARY KEY,
  full_name VARCHAR(100) NOT NULL,
  identity_card VARCHAR(20) NOT NULL UNIQUE,
  phone VARCHAR(20) NOT NULL,
  email VARCHAR(100),
  address VARCHAR(255),
  is_vip BOOLEAN DEFAULT FALSE,
  loyalty_points INT DEFAULT 0,
  total_loyalty_points INT DEFAULT 0,
  loyalty_level VARCHAR(50) DEFAULT 'Member',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_customer_identity (identity_card),
  INDEX idx_customer_phone (phone),
  INDEX idx_customer_name (full_name)
) ENGINE=InnoDB;

-- --------------------------------------------------------
-- Table: bookings
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS bookings (
  id INT AUTO_INCREMENT PRIMARY KEY,
  customer_id INT NOT NULL,
  room_id INT NOT NULL,
  check_in_date DATETIME NOT NULL,
  check_out_date DATETIME NOT NULL,
  total_price DECIMAL(10, 2) DEFAULT 0,
  status ENUM('pending', 'confirmed', 'checked_in', 'checked_out', 'cancelled') DEFAULT 'pending',
  customer_type ENUM('OTA', 'Walk-in', 'Corporate') DEFAULT 'Walk-in',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  -- RESTRICT: Không cho xóa khách hàng nếu khách hàng có giao dịch đặt phòng
  CONSTRAINT fk_bookings_customers FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE RESTRICT ON UPDATE CASCADE,
  -- RESTRICT: Không cho xóa phòng nếu phòng đang có người đặt lịch
  CONSTRAINT fk_bookings_rooms FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE RESTRICT ON UPDATE CASCADE,
  INDEX idx_booking_dates (check_in_date, check_out_date),
  INDEX idx_booking_status (status)
) ENGINE=InnoDB;

-- --------------------------------------------------------
-- Table: booking_details
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS booking_details (
  id INT AUTO_INCREMENT PRIMARY KEY,
  booking_id INT NOT NULL,
  room_id INT NOT NULL,
  price_at_booking DECIMAL(10, 2) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  -- CASCADE: Xóa cha (booking) thì xóa con (chi tiết booking)
  CONSTRAINT fk_booking_details_bookings FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE ON UPDATE CASCADE,
  -- RESTRICT: Không cho phép xóa phòng dính với chi tiết booking
  CONSTRAINT fk_booking_details_rooms FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;

-- --------------------------------------------------------
-- Table: services
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS services (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL UNIQUE,
  description VARCHAR(255),
  price DECIMAL(10, 2) NOT NULL,
  status ENUM('active', 'inactive') DEFAULT 'active',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- --------------------------------------------------------
-- Table: service_usage
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS service_usage (
  id INT AUTO_INCREMENT PRIMARY KEY,
  booking_id INT NOT NULL,
  service_id INT NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  unit_price DECIMAL(10, 2) NOT NULL,
  total_price DECIMAL(10, 2) NOT NULL,
  usage_date DATETIME DEFAULT CURRENT_TIMESTAMP,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  -- CASCADE: Nếu hủy/xóa booking thì xóa luôn danh sách dịch vụ đã gọi
  CONSTRAINT fk_service_usage_bookings FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE ON UPDATE CASCADE,
  -- RESTRICT: Không xóa service vật lý nếu khách đang thuê/dùng chưa tính tiền
  CONSTRAINT fk_service_usage_services FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;

-- --------------------------------------------------------
-- Table: invoices
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS invoices (
  id INT AUTO_INCREMENT PRIMARY KEY,
  booking_id INT NOT NULL UNIQUE,
  total_room_fee DECIMAL(10, 2) NOT NULL DEFAULT 0,
  total_service_fee DECIMAL(10, 2) NOT NULL DEFAULT 0,
  discount DECIMAL(10, 2) NOT NULL DEFAULT 0,
  tax_amount DECIMAL(10, 2) NOT NULL DEFAULT 0,
  final_total DECIMAL(10, 2) NOT NULL,
  issue_date DATETIME DEFAULT CURRENT_TIMESTAMP,
  status ENUM('unpaid', 'paid', 'cancelled') DEFAULT 'unpaid',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  -- CASCADE: Booking bị xóa thì xóa invoice (nếu có thể tùy quy định, hoặc RESTRICT)
  CONSTRAINT fk_invoices_bookings FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

-- --------------------------------------------------------
-- Table: payments
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments (
  id INT AUTO_INCREMENT PRIMARY KEY,
  invoice_id INT NOT NULL,
  amount DECIMAL(10, 2) NOT NULL,
  payment_method ENUM('cash', 'credit_card', 'bank_transfer', 'e_wallet') NOT NULL,
  payment_date DATETIME DEFAULT CURRENT_TIMESTAMP,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  -- CASCADE: Hóa đơn bị hủy/xóa sẽ mất giao dịch con tương ứng
  CONSTRAINT fk_payments_invoices FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE ON UPDATE CASCADE,
  INDEX idx_payment_method (payment_method)
) ENGINE=InnoDB;

-- --------------------------------------------------------
-- Table: messages
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS messages (
  id INT AUTO_INCREMENT PRIMARY KEY,
  sender_id INT NOT NULL,
  receiver_id INT NOT NULL,
  content TEXT NOT NULL,
  is_read BOOLEAN DEFAULT FALSE,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_chat_participants (sender_id, receiver_id)
) ENGINE=InnoDB;

-- --------------------------------------------------------
-- Table: promotions
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS promotions (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(255),
  discount_type ENUM('percentage', 'fixed_amount') NOT NULL,
  discount_value DECIMAL(10, 2) NOT NULL,
  start_date DATETIME NOT NULL,
  end_date DATETIME NOT NULL,
  condition_type ENUM('none', 'room_type', 'min_stay', 'vip_only') DEFAULT 'none',
  condition_value VARCHAR(100),
  status ENUM('active', 'inactive') DEFAULT 'active',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- --------------------------------------------------------
-- Table: reviews
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS reviews (
  id INT AUTO_INCREMENT PRIMARY KEY,
  room_id INT NOT NULL,
  customer_id INT NOT NULL,
  rating INT NOT NULL CHECK(rating >= 1 AND rating <= 5),
  comment TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE,
  FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- --------------------------------------------------------
-- Table: loyalty_histories
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS loyalty_histories (
  id INT AUTO_INCREMENT PRIMARY KEY,
  customer_id INT NOT NULL,
  points INT NOT NULL,
  type ENUM('earn', 'redeem') NOT NULL,
  description VARCHAR(255),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- --------------------------------------------------------
-- VIEWS & TRIGGERS
-- --------------------------------------------------------

-- View: Báo cáo danh thu hàng tháng (Nhóm theo hóa đơn đã thanh toán)
CREATE OR REPLACE VIEW v_monthly_revenue AS
SELECT 
    DATE_FORMAT(issue_date, '%Y-%m') AS month,
    COUNT(id) AS total_invoices,
    SUM(total_room_fee) AS total_room_revenue,
    SUM(total_service_fee) AS total_service_revenue,
    SUM(final_total) AS gross_revenue
FROM invoices
WHERE status = 'paid'
GROUP BY DATE_FORMAT(issue_date, '%Y-%m')
ORDER BY month DESC;

-- Trigger: Cập nhật tổng tiền dịch vụ vào hóa đơn sau khi thêm service_usage mới
DROP TRIGGER IF EXISTS trg_after_insert_service_usage;

DELIMITER //
CREATE TRIGGER trg_after_insert_service_usage
AFTER INSERT ON service_usage
FOR EACH ROW
BEGIN
    UPDATE invoices
    SET total_service_fee = total_service_fee + NEW.total_price,
        final_total = final_total + NEW.total_price
    WHERE booking_id = NEW.booking_id;
END //
DELIMITER ;

-- --------------------------------------------------------
-- SEED DATA
-- --------------------------------------------------------

-- 1. roles
INSERT IGNORE INTO roles (name, description) VALUES 
('admin', 'Quản trị viên có toàn quyền truy cập'),
('staff', 'Nhân viên lễ tân'),
('customer', 'Khách hàng trú tại khách sạn');

-- 2. users (Mật khẩu mặc định cho tất cả là '123456')
INSERT IGNORE INTO users (username, password, role_id, full_name, email, phone, customer_id) VALUES 
('admin_main', '$2a$10$EUiC6sIZD/Un75n20QIKjO3r5xP2eVJ4AB0ZNr/E0guhBY5GyzbFG', 1, 'Administrator', 'admin@hotel.com', '0987654321', NULL),
('staff_01', '$2a$10$EUiC6sIZD/Un75n20QIKjO3r5xP2eVJ4AB0ZNr/E0guhBY5GyzbFG', 2, 'Nguyen Van A', 'nva@hotel.com', '0123456789', NULL),
('customer_test', '$2a$10$EUiC6sIZD/Un75n20QIKjO3r5xP2eVJ4AB0ZNr/E0guhBY5GyzbFG', 3, 'Khách hàng Test', 'test@gmail.com', '0900000000', 1);

-- 3. room_types
INSERT IGNORE INTO room_types (name, description, base_price, capacity, image_url, amenities) VALUES 
('Standard', 'Phòng tiêu chuẩn cơ bản, đầy đủ tiện nghi cho 2 người', 500000, 2, 'standard.jpg', 'Wifi, TV, Air Conditioning, Hot Water'),
('Deluxe', 'Phòng cao cấp view biển, không gian sang trọng', 1000000, 2, 'deluxe.jpg', 'Wifi, TV, Air Conditioning, Hot Water, Mini Bar, Ocean View'),
('Family', 'Phòng căn hộ rộng rãi cho cả gia đình', 1500000, 4, 'family.jpg', 'Wifi, TV, Air Conditioning, Hot Water, Mini Bar, Kitchen, Extra Bed');

-- 4. rooms
INSERT IGNORE INTO rooms (room_number, room_type_id, price, status) VALUES 
('101', 1, 500000, 'available'),
('102', 1, 500000, 'available'),
('103', 1, 500000, 'available'),
('104', 1, 500000, 'cleaning'),
('105', 1, 500000, 'available'),
('201', 2, 1000000, 'booked'),
('202', 2, 1000000, 'available'),
('203', 2, 1000000, 'occupied'),
('204', 2, 1000000, 'available'),
('205', 2, 1000000, 'maintenance'),
('301', 3, 1500000, 'occupied'),
('302', 3, 1500000, 'maintenance'),
('303', 3, 1500000, 'available'),
('304', 3, 1500000, 'available'),
('305', 3, 1500000, 'booked');

-- 5. customers
INSERT IGNORE INTO customers (full_name, identity_card, phone, email, address, is_vip, loyalty_points, total_loyalty_points, loyalty_level) VALUES 
('Nguyễn Văn A', '012345678912', '0901234567', 'nva@email.com', 'Hà Nội', TRUE, 1500, 1500, 'Gold'),
('Trần Thị B', '098765432109', '0987654321', 'ttb@email.com', 'Đà Nẵng', FALSE, 500, 500, 'Member'),
('Lê Hoàng C', '036985214701', '0912345678', 'lhc@email.com', 'TP.HCM', FALSE, 200, 200, 'Member'),
('Phạm Quang D', '079012345678', '0902345678', 'pqd@email.com', 'Cần Thơ', TRUE, 5000, 5000, 'Diamond'),
('Vũ Bích E', '040087654321', '0812345678', 'vbe@email.com', 'Hải Phòng', FALSE, 0, 0, 'Member'),
('Hoàng Tuấn F', '038012312312', '0931231234', 'htf@email.com', 'Bình Dương', FALSE, 100, 100, 'Member'),
('Ngô Thanh G', '046034534534', '0898765432', 'ntg@email.com', 'Đồng Nai', FALSE, 0, 0, 'Member'),
('Đinh Quỳnh H', '019056756756', '0981122334', 'dqh@email.com', 'Quảng Ninh', TRUE, 2200, 2200, 'Platinum'),
('Lý Tiểu I', '001098798798', '0979988776', 'lti@email.com', 'Kiên Giang', FALSE, 300, 300, 'Member'),
('Đỗ Hùng K', '054045645645', '0966655544', 'dhk@email.com', 'Khánh Hòa', FALSE, 0, 0, 'Member'),
('Bùi Diệu L', '027011122233', '0944433322', 'bdl@email.com', 'Vĩnh Phúc', FALSE, 800, 800, 'Silver'),
('Trương Minh M', '033099988877', '0922211100', 'tmm@email.com', 'Bắc Ninh', FALSE, 150, 150, 'Member'),
('Phan Tú N', '062077766655', '0911199988', 'ptn@email.com', 'Long An', FALSE, 0, 0, 'Member');

-- 6. services
INSERT IGNORE INTO services (name, description, price) VALUES 
('Giặt là', 'Dịch vụ giặt ủi lấy liền', 50000),
('Dọn phòng sớm', 'Dịch vụ dọn dẹp vệ sinh theo yêu cầu', 30000),
('Thuê xe', 'Thuê xe máy di chuyển 1 ngày', 150000);

-- 7. bookings
INSERT IGNORE INTO bookings (customer_id, room_id, check_in_date, check_out_date, total_price, status) VALUES 
(1, 1, '2026-03-01 14:00:00', '2026-03-05 12:00:00', 2000000, 'checked_out'),
(1, 2, '2026-04-10 14:00:00', '2026-04-15 12:00:00', 2500000, 'pending'),
(2, 3, '2026-03-15 14:00:00', '2026-03-17 12:00:00', 2000000, 'checked_out'),
(4, 4, '2026-03-20 14:00:00', '2026-03-22 12:00:00', 3000000, 'checked_out'),
(5, 5, '2026-03-25 14:00:00', '2026-03-28 12:00:00', 4500000, 'checked_out'),
(6, 1, '2026-04-01 14:00:00', '2026-04-03 12:00:00', 1000000, 'checked_out'),
(7, 2, '2026-04-05 14:00:00', '2026-04-07 12:00:00', 1000000, 'checked_in'),
(8, 3, '2026-04-12 14:00:00', '2026-04-14 12:00:00', 2000000, 'pending'),
(9, 4, '2026-04-20 14:00:00', '2026-04-25 12:00:00', 7500000, 'confirmed'),
(10, 5, '2026-03-10 14:00:00', '2026-03-12 12:00:00', 3000000, 'cancelled');

-- 8. invoices
INSERT IGNORE INTO invoices (booking_id, total_room_fee, final_total, status) VALUES 
(1, 2000000, 2000000, 'paid'),
(3, 2000000, 2000000, 'paid'),
(4, 3000000, 3000000, 'paid'),
(5, 4500000, 4500000, 'paid'),
(6, 1000000, 1000000, 'paid');

-- 9. payments
INSERT IGNORE INTO payments (invoice_id, amount, payment_method) VALUES 
(1, 2000000, 'cash'),
(2, 2000000, 'credit_card'),
(3, 3000000, 'bank_transfer'),
(4, 4500000, 'e_wallet'),
(5, 1000000, 'cash');

-- 10. messages
INSERT IGNORE INTO messages (sender_id, receiver_id, content, is_read) VALUES 
(4, 2, 'Chào lễ tân, cho tôi hỏi về dịch vụ giặt là?', 0),
(2, 4, 'Dạ chào anh, dịch vụ giặt là bên em hoạt động từ 7h sáng đến 10h tối ạ.', 1),
(4, 2, 'Cảm ơn bạn, tôi sẽ gửi đồ sau.', 0);

-- 11. promotions
INSERT IGNORE INTO promotions (name, description, discount_type, discount_value, start_date, end_date, condition_type, condition_value, status) VALUES 
('Mùa Hè Sôi Động', 'Giảm 10% cho tất cả các phòng', 'percentage', 10, '2026-05-01 00:00:00', '2026-08-31 23:59:59', 'none', '', 'active'),
('Giảm Giá VIP', 'Giảm 500k cho khách VIP', 'fixed_amount', 500000, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 'vip_only', '', 'active'),
('Ưu đãi Family', 'Giảm 15% khi thuê phòng Family tối thiểu 3 đêm', 'percentage', 15, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 'min_stay', '3', 'active');

-- 11. reviews
INSERT IGNORE INTO reviews (room_id, customer_id, rating, comment) VALUES 
(1, 1, 5, 'Phòng sạch sẽ, view đẹp, thái độ nhân viên tốt.'),
(2, 2, 4, 'Dịch vụ ổn nhưng check in hơi chậm.'),
(3, 3, 5, 'Rất hài lòng, sẽ quay lại lần sau.'),
(4, 4, 3, 'Điều hòa trong phòng hơi ồn, bù lại thức ăn ngon.'),
(5, 5, 5, 'Trải nghiệm tuyệt vời, đánh giá 5 sao cho chất lượng dịch vụ!');

-- 12. loyalty_histories
INSERT IGNORE INTO loyalty_histories (customer_id, points, type, description) VALUES 
(1, 1500, 'earn', 'Điểm thưởng từ booking ID 1'),
(2, 500, 'earn', 'Điểm thưởng từ booking ID 2'),
(3, 200, 'earn', 'Điểm thưởng từ dịch vụ dùng thêm'),
(4, 5000, 'earn', 'Điểm thưởng từ booking ID 4 (Khách hàng đặc biệt)'),
(8, 2200, 'earn', 'Tích điểm theo chương trình khuyến mãi đầu năm'),
(11, 800, 'earn', 'Khách hàng thân thiết check-in nhiều lần');
-- GENERATED BOOKINGS
INSERT INTO bookings (customer_id, room_id, check_in_date, check_out_date, total_price, status, customer_type) VALUES
(8, 3, '2025-05-20 04:00:00', '2025-05-25 12:00:00', 2500000, 'checked_out', 'Corporate'),
(8, 1, '2026-01-23 23:00:00', '2026-01-25 12:00:00', 1000000, 'confirmed', 'OTA'),
(8, 2, '2025-12-31 01:00:00', '2026-01-05 12:00:00', 2500000, 'checked_out', 'OTA'),
(1, 1, '2025-01-16 21:00:00', '2025-01-20 12:00:00', 2000000, 'confirmed', 'Walk-in'),
(2, 13, '2025-08-25 03:00:00', '2025-08-29 12:00:00', 6000000, 'checked_out', 'Walk-in'),
(9, 10, '2025-07-03 18:00:00', '2025-07-05 12:00:00', 2000000, 'checked_out', 'OTA'),
(3, 8, '2025-04-13 16:00:00', '2025-04-16 12:00:00', 3000000, 'checked_in', 'OTA'),
(10, 7, '2025-12-15 01:00:00', '2025-12-16 12:00:00', 1000000, 'confirmed', 'OTA'),
(7, 8, '2026-03-18 23:00:00', '2026-03-19 12:00:00', 1000000, 'checked_out', 'Walk-in'),
(4, 14, '2025-08-16 03:00:00', '2025-08-17 12:00:00', 1500000, 'checked_out', 'Corporate'),
(10, 7, '2025-06-02 17:00:00', '2025-06-04 12:00:00', 2000000, 'checked_out', 'Corporate'),
(7, 11, '2025-06-20 13:00:00', '2025-06-21 12:00:00', 1500000, 'checked_in', 'Walk-in'),
(11, 13, '2025-06-11 09:00:00', '2025-06-14 12:00:00', 4500000, 'checked_out', 'Walk-in'),
(13, 6, '2025-04-20 14:00:00', '2025-04-25 12:00:00', 5000000, 'checked_out', 'Walk-in'),
(2, 13, '2025-05-11 00:00:00', '2025-05-12 12:00:00', 1500000, 'checked_out', 'OTA'),
(13, 9, '2025-07-06 16:00:00', '2025-07-11 12:00:00', 5000000, 'checked_in', 'Walk-in'),
(13, 12, '2025-03-07 11:00:00', '2025-03-10 12:00:00', 4500000, 'checked_out', 'Corporate'),
(9, 1, '2026-03-26 09:00:00', '2026-03-29 12:00:00', 1500000, 'confirmed', 'Corporate'),
(7, 13, '2026-02-25 04:00:00', '2026-02-26 12:00:00', 1500000, 'checked_out', 'Corporate'),
(9, 3, '2025-01-12 23:00:00', '2025-01-14 12:00:00', 1000000, 'checked_out', 'Corporate'),
(12, 5, '2026-03-22 03:00:00', '2026-03-24 12:00:00', 1000000, 'checked_out', 'Walk-in'),
(3, 13, '2025-06-17 03:00:00', '2025-06-19 12:00:00', 3000000, 'checked_out', 'OTA'),
(1, 2, '2025-06-25 23:00:00', '2025-06-26 12:00:00', 500000, 'checked_out', 'OTA'),
(8, 13, '2026-01-03 11:00:00', '2026-01-04 12:00:00', 1500000, 'checked_out', 'Corporate'),
(11, 9, '2025-12-07 11:00:00', '2025-12-12 12:00:00', 5000000, 'checked_in', 'Walk-in'),
(7, 13, '2025-07-05 09:00:00', '2025-07-09 12:00:00', 6000000, 'checked_out', 'Walk-in'),
(13, 5, '2025-12-03 06:00:00', '2025-12-08 12:00:00', 2500000, 'checked_out', 'Walk-in'),
(6, 6, '2025-05-15 16:00:00', '2025-05-19 12:00:00', 4000000, 'checked_out', 'Corporate'),
(9, 3, '2026-05-30 00:00:00', '2026-06-02 12:00:00', 1500000, 'checked_out', 'OTA'),
(3, 13, '2025-11-12 04:00:00', '2025-11-13 12:00:00', 1500000, 'checked_out', 'Corporate'),
(7, 14, '2025-12-21 20:00:00', '2025-12-22 12:00:00', 1500000, 'checked_out', 'Walk-in'),
(7, 11, '2025-01-25 03:00:00', '2025-01-28 12:00:00', 4500000, 'checked_out', 'Corporate'),
(12, 15, '2025-02-18 15:00:00', '2025-02-22 12:00:00', 6000000, 'checked_out', 'Walk-in'),
(6, 6, '2025-09-22 14:00:00', '2025-09-24 12:00:00', 2000000, 'checked_in', 'Walk-in'),
(8, 12, '2026-05-07 07:00:00', '2026-05-09 12:00:00', 3000000, 'confirmed', 'Walk-in'),
(8, 8, '2025-12-18 13:00:00', '2025-12-20 12:00:00', 2000000, 'checked_out', 'Walk-in'),
(5, 2, '2025-05-10 14:00:00', '2025-05-12 12:00:00', 1000000, 'checked_out', 'Corporate'),
(10, 7, '2026-01-10 05:00:00', '2026-01-14 12:00:00', 4000000, 'checked_in', 'OTA'),
(12, 13, '2026-04-22 00:00:00', '2026-04-26 12:00:00', 6000000, 'checked_out', 'Corporate'),
(1, 8, '2025-10-11 15:00:00', '2025-10-13 12:00:00', 2000000, 'checked_in', 'Walk-in'),
(4, 12, '2025-12-01 21:00:00', '2025-12-03 12:00:00', 3000000, 'checked_out', 'Walk-in'),
(2, 3, '2025-11-27 02:00:00', '2025-11-30 12:00:00', 1500000, 'checked_out', 'OTA'),
(5, 2, '2026-04-15 20:00:00', '2026-04-19 12:00:00', 2000000, 'checked_out', 'Walk-in'),
(9, 10, '2025-08-30 18:00:00', '2025-09-02 12:00:00', 3000000, 'checked_out', 'Walk-in'),
(1, 6, '2026-01-30 10:00:00', '2026-02-04 12:00:00', 5000000, 'checked_out', 'OTA'),
(11, 4, '2026-01-11 04:00:00', '2026-01-12 12:00:00', 500000, 'checked_out', 'Walk-in'),
(7, 6, '2025-03-13 15:00:00', '2025-03-14 12:00:00', 1000000, 'checked_out', 'Walk-in'),
(5, 5, '2025-03-02 02:00:00', '2025-03-04 12:00:00', 1000000, 'checked_in', 'Corporate'),
(13, 9, '2025-01-20 21:00:00', '2025-01-25 12:00:00', 5000000, 'checked_out', 'Walk-in'),
(8, 12, '2025-07-24 04:00:00', '2025-07-27 12:00:00', 4500000, 'checked_out', 'Walk-in'),
(7, 15, '2026-01-25 07:00:00', '2026-01-26 12:00:00', 1500000, 'checked_out', 'Corporate'),
(10, 15, '2025-07-23 15:00:00', '2025-07-25 12:00:00', 3000000, 'checked_out', 'Walk-in'),
(4, 1, '2026-04-08 05:00:00', '2026-04-09 12:00:00', 500000, 'checked_in', 'OTA'),
(11, 13, '2025-06-29 15:00:00', '2025-07-04 12:00:00', 7500000, 'checked_in', 'Walk-in'),
(7, 1, '2026-02-07 20:00:00', '2026-02-08 12:00:00', 500000, 'confirmed', 'Walk-in'),
(5, 13, '2025-06-28 01:00:00', '2025-06-30 12:00:00', 3000000, 'confirmed', 'Walk-in'),
(12, 13, '2026-03-23 21:00:00', '2026-03-26 12:00:00', 4500000, 'checked_out', 'Walk-in'),
(12, 2, '2025-09-16 23:00:00', '2025-09-17 12:00:00', 500000, 'checked_out', 'Corporate'),
(1, 14, '2025-11-16 04:00:00', '2025-11-17 12:00:00', 1500000, 'checked_out', 'Walk-in'),
(7, 5, '2025-12-10 03:00:00', '2025-12-14 12:00:00', 2000000, 'checked_out', 'OTA'),
(13, 4, '2025-12-25 10:00:00', '2025-12-27 12:00:00', 1000000, 'checked_out', 'OTA'),
(12, 1, '2025-03-23 01:00:00', '2025-03-25 12:00:00', 1000000, 'checked_out', 'Walk-in'),
(5, 4, '2026-03-28 01:00:00', '2026-04-01 12:00:00', 2000000, 'checked_in', 'Walk-in'),
(11, 1, '2025-08-28 21:00:00', '2025-09-01 12:00:00', 2000000, 'checked_out', 'Corporate'),
(13, 15, '2026-01-28 05:00:00', '2026-02-02 12:00:00', 7500000, 'checked_out', 'OTA'),
(5, 12, '2025-11-21 18:00:00', '2025-11-26 12:00:00', 7500000, 'checked_in', 'Walk-in'),
(8, 5, '2026-03-21 20:00:00', '2026-03-26 12:00:00', 2500000, 'checked_in', 'Corporate'),
(7, 2, '2026-02-22 03:00:00', '2026-02-26 12:00:00', 2000000, 'checked_out', 'Corporate'),
(4, 8, '2025-01-19 21:00:00', '2025-01-21 12:00:00', 2000000, 'checked_in', 'Corporate'),
(4, 5, '2026-02-27 09:00:00', '2026-02-28 12:00:00', 500000, 'checked_out', 'Corporate'),
(7, 12, '2026-05-07 07:00:00', '2026-05-11 12:00:00', 6000000, 'confirmed', 'OTA'),
(3, 2, '2025-01-19 12:00:00', '2025-01-23 12:00:00', 2000000, 'checked_in', 'Corporate'),
(5, 15, '2026-02-25 06:00:00', '2026-02-27 12:00:00', 3000000, 'checked_out', 'Corporate'),
(2, 7, '2025-07-13 10:00:00', '2025-07-17 12:00:00', 4000000, 'checked_out', 'Walk-in'),
(3, 7, '2025-10-15 05:00:00', '2025-10-16 12:00:00', 1000000, 'checked_in', 'Corporate'),
(6, 3, '2025-02-05 20:00:00', '2025-02-08 12:00:00', 1500000, 'confirmed', 'Walk-in'),
(13, 3, '2025-08-23 12:00:00', '2025-08-27 12:00:00', 2000000, 'checked_out', 'Walk-in'),
(11, 12, '2025-11-07 17:00:00', '2025-11-11 12:00:00', 6000000, 'confirmed', 'Walk-in'),
(9, 10, '2025-08-29 05:00:00', '2025-08-31 12:00:00', 2000000, 'confirmed', 'Walk-in'),
(12, 13, '2025-08-04 19:00:00', '2025-08-08 12:00:00', 6000000, 'checked_out', 'Walk-in'),
(11, 14, '2026-05-29 17:00:00', '2026-05-31 12:00:00', 3000000, 'confirmed', 'Walk-in'),
(3, 12, '2025-12-26 03:00:00', '2025-12-28 12:00:00', 3000000, 'checked_out', 'Corporate'),
(3, 7, '2026-04-29 13:00:00', '2026-04-30 12:00:00', 1000000, 'checked_out', 'Walk-in'),
(11, 12, '2025-09-06 23:00:00', '2025-09-09 12:00:00', 4500000, 'checked_out', 'Walk-in'),
(8, 2, '2025-03-27 08:00:00', '2025-03-28 12:00:00', 500000, 'confirmed', 'Walk-in'),
(11, 6, '2025-02-01 07:00:00', '2025-02-06 12:00:00', 5000000, 'checked_out', 'Walk-in'),
(5, 11, '2025-02-22 03:00:00', '2025-02-23 12:00:00', 1500000, 'checked_in', 'OTA'),
(5, 6, '2025-10-16 02:00:00', '2025-10-21 12:00:00', 5000000, 'checked_out', 'Corporate'),
(3, 8, '2025-06-19 15:00:00', '2025-06-21 12:00:00', 2000000, 'checked_in', 'Walk-in'),
(2, 8, '2026-05-30 13:00:00', '2026-06-02 12:00:00', 3000000, 'checked_in', 'Walk-in'),
(13, 15, '2026-03-08 08:00:00', '2026-03-13 12:00:00', 7500000, 'checked_out', 'OTA'),
(13, 1, '2025-07-16 05:00:00', '2025-07-17 12:00:00', 500000, 'checked_out', 'Walk-in'),
(3, 7, '2025-09-22 14:00:00', '2025-09-26 12:00:00', 4000000, 'checked_out', 'Walk-in'),
(8, 6, '2026-03-24 06:00:00', '2026-03-29 12:00:00', 5000000, 'checked_out', 'Walk-in'),
(9, 7, '2025-06-05 17:00:00', '2025-06-08 12:00:00', 3000000, 'checked_out', 'OTA'),
(2, 14, '2025-11-19 11:00:00', '2025-11-22 12:00:00', 4500000, 'checked_in', 'Walk-in'),
(5, 15, '2025-03-28 21:00:00', '2025-03-29 12:00:00', 1500000, 'checked_out', 'Walk-in'),
(8, 13, '2025-02-10 12:00:00', '2025-02-11 12:00:00', 1500000, 'checked_out', 'OTA'),
(7, 6, '2025-08-11 09:00:00', '2025-08-16 12:00:00', 5000000, 'confirmed', 'OTA'),
(9, 15, '2026-01-04 04:00:00', '2026-01-09 12:00:00', 7500000, 'checked_in', 'Walk-in'),
(10, 12, '2025-09-09 12:00:00', '2025-09-12 12:00:00', 4500000, 'checked_out', 'Walk-in'),
(2, 8, '2025-05-14 10:00:00', '2025-05-15 12:00:00', 1000000, 'checked_out', 'OTA'),
(11, 8, '2025-08-09 18:00:00', '2025-08-13 12:00:00', 4000000, 'checked_out', 'Walk-in'),
(2, 5, '2025-02-16 08:00:00', '2025-02-19 12:00:00', 1500000, 'checked_in', 'Walk-in'),
(8, 3, '2025-06-27 07:00:00', '2025-07-02 12:00:00', 2500000, 'checked_out', 'Corporate'),
(1, 1, '2025-12-03 16:00:00', '2025-12-06 12:00:00', 1500000, 'checked_out', 'Walk-in'),
(13, 14, '2026-05-21 21:00:00', '2026-05-25 12:00:00', 6000000, 'checked_in', 'OTA'),
(3, 2, '2025-09-27 13:00:00', '2025-10-01 12:00:00', 2000000, 'checked_out', 'Corporate'),
(10, 10, '2026-02-13 04:00:00', '2026-02-18 12:00:00', 5000000, 'checked_in', 'Walk-in'),
(4, 3, '2025-10-17 14:00:00', '2025-10-20 12:00:00', 1500000, 'checked_out', 'Walk-in'),
(4, 12, '2025-08-19 09:00:00', '2025-08-20 12:00:00', 1500000, 'checked_out', 'Walk-in'),
(2, 9, '2026-02-15 16:00:00', '2026-02-19 12:00:00', 4000000, 'checked_out', 'Corporate'),
(3, 1, '2025-05-31 13:00:00', '2025-06-05 12:00:00', 2500000, 'confirmed', 'Walk-in'),
(11, 10, '2025-08-21 12:00:00', '2025-08-23 12:00:00', 2000000, 'confirmed', 'OTA'),
(6, 1, '2025-09-04 01:00:00', '2025-09-09 12:00:00', 2500000, 'checked_out', 'Corporate'),
(6, 4, '2025-02-17 14:00:00', '2025-02-21 12:00:00', 2000000, 'confirmed', 'OTA'),
(5, 10, '2026-02-27 19:00:00', '2026-03-01 12:00:00', 2000000, 'checked_out', 'Walk-in'),
(9, 9, '2025-09-23 12:00:00', '2025-09-27 12:00:00', 4000000, 'checked_out', 'Walk-in'),
(12, 2, '2025-01-25 09:00:00', '2025-01-27 12:00:00', 1000000, 'checked_out', 'Walk-in'),
(7, 10, '2025-03-25 06:00:00', '2025-03-26 12:00:00', 1000000, 'confirmed', 'Walk-in');

-- GENERATED INVOICES
INSERT INTO invoices (booking_id, total_room_fee, total_service_fee, final_total, status) VALUES
(1, 2500000, 200000, 2700000, 'paid'),
(3, 2500000, 50000, 2550000, 'paid'),
(5, 6000000, 200000, 6200000, 'paid'),
(6, 2000000, 100000, 2100000, 'paid'),
(9, 1000000, 200000, 1200000, 'paid'),
(10, 1500000, 50000, 1550000, 'paid'),
(11, 2000000, 0, 2000000, 'paid'),
(13, 4500000, 100000, 4600000, 'paid'),
(14, 5000000, 200000, 5200000, 'paid'),
(15, 1500000, 100000, 1600000, 'paid'),
(17, 4500000, 100000, 4600000, 'paid'),
(19, 1500000, 0, 1500000, 'paid'),
(20, 1000000, 200000, 1200000, 'paid'),
(21, 1000000, 150000, 1150000, 'paid'),
(22, 3000000, 200000, 3200000, 'paid'),
(23, 500000, 100000, 600000, 'paid'),
(24, 1500000, 50000, 1550000, 'paid'),
(26, 6000000, 150000, 6150000, 'paid'),
(27, 2500000, 150000, 2650000, 'paid'),
(28, 4000000, 50000, 4050000, 'paid'),
(29, 1500000, 200000, 1700000, 'paid'),
(30, 1500000, 100000, 1600000, 'paid'),
(31, 1500000, 150000, 1650000, 'paid'),
(32, 4500000, 150000, 4650000, 'paid'),
(33, 6000000, 100000, 6100000, 'paid'),
(36, 2000000, 100000, 2100000, 'paid'),
(37, 1000000, 0, 1000000, 'paid'),
(39, 6000000, 150000, 6150000, 'paid'),
(41, 3000000, 0, 3000000, 'paid'),
(42, 1500000, 150000, 1650000, 'paid'),
(43, 2000000, 50000, 2050000, 'paid'),
(44, 3000000, 50000, 3050000, 'paid'),
(45, 5000000, 150000, 5150000, 'paid'),
(46, 500000, 150000, 650000, 'paid'),
(47, 1000000, 50000, 1050000, 'paid'),
(49, 5000000, 50000, 5050000, 'paid'),
(50, 4500000, 0, 4500000, 'paid'),
(51, 1500000, 150000, 1650000, 'paid'),
(52, 3000000, 50000, 3050000, 'paid'),
(57, 4500000, 200000, 4700000, 'paid'),
(58, 500000, 100000, 600000, 'paid'),
(59, 1500000, 200000, 1700000, 'paid'),
(60, 2000000, 0, 2000000, 'paid'),
(61, 1000000, 200000, 1200000, 'paid'),
(62, 1000000, 100000, 1100000, 'paid'),
(64, 2000000, 150000, 2150000, 'paid'),
(65, 7500000, 100000, 7600000, 'paid'),
(68, 2000000, 100000, 2100000, 'paid'),
(70, 500000, 200000, 700000, 'paid'),
(73, 3000000, 0, 3000000, 'paid'),
(74, 4000000, 100000, 4100000, 'paid'),
(77, 2000000, 0, 2000000, 'paid'),
(80, 6000000, 0, 6000000, 'paid'),
(82, 3000000, 100000, 3100000, 'paid'),
(83, 1000000, 0, 1000000, 'paid'),
(84, 4500000, 200000, 4700000, 'paid'),
(86, 5000000, 200000, 5200000, 'paid'),
(88, 5000000, 150000, 5150000, 'paid'),
(91, 7500000, 200000, 7700000, 'paid'),
(92, 500000, 50000, 550000, 'paid'),
(93, 4000000, 0, 4000000, 'paid'),
(94, 5000000, 150000, 5150000, 'paid'),
(95, 3000000, 0, 3000000, 'paid'),
(97, 1500000, 200000, 1700000, 'paid'),
(98, 1500000, 200000, 1700000, 'paid'),
(101, 4500000, 150000, 4650000, 'paid'),
(102, 1000000, 200000, 1200000, 'paid'),
(103, 4000000, 50000, 4050000, 'paid'),
(105, 2500000, 50000, 2550000, 'paid'),
(106, 1500000, 0, 1500000, 'paid'),
(108, 2000000, 50000, 2050000, 'paid'),
(110, 1500000, 150000, 1650000, 'paid'),
(111, 1500000, 150000, 1650000, 'paid'),
(112, 4000000, 0, 4000000, 'paid'),
(115, 2500000, 200000, 2700000, 'paid'),
(117, 2000000, 200000, 2200000, 'paid'),
(118, 4000000, 200000, 4200000, 'paid'),
(119, 1000000, 200000, 1200000, 'paid');

-- GENERATED PAYMENTS
INSERT INTO payments (invoice_id, amount, payment_method) VALUES
(1, 2700000, 'cash'),
(2, 2550000, 'credit_card'),
(3, 6200000, 'cash'),
(4, 2100000, 'cash'),
(5, 1200000, 'cash'),
(6, 1550000, 'cash'),
(7, 2000000, 'cash'),
(8, 4600000, 'bank_transfer'),
(9, 5200000, 'bank_transfer'),
(10, 1600000, 'cash'),
(11, 4600000, 'bank_transfer'),
(12, 1500000, 'credit_card'),
(13, 1200000, 'cash'),
(14, 1150000, 'credit_card'),
(15, 3200000, 'bank_transfer'),
(16, 600000, 'credit_card'),
(17, 1550000, 'credit_card'),
(18, 6150000, 'cash'),
(19, 2650000, 'cash'),
(20, 4050000, 'cash'),
(21, 1700000, 'bank_transfer'),
(22, 1600000, 'cash'),
(23, 1650000, 'bank_transfer'),
(24, 4650000, 'bank_transfer'),
(25, 6100000, 'bank_transfer'),
(26, 2100000, 'bank_transfer'),
(27, 1000000, 'cash'),
(28, 6150000, 'bank_transfer'),
(29, 3000000, 'credit_card'),
(30, 1650000, 'cash'),
(31, 2050000, 'bank_transfer'),
(32, 3050000, 'bank_transfer'),
(33, 5150000, 'credit_card'),
(34, 650000, 'credit_card'),
(35, 1050000, 'bank_transfer'),
(36, 5050000, 'bank_transfer'),
(37, 4500000, 'bank_transfer'),
(38, 1650000, 'credit_card'),
(39, 3050000, 'bank_transfer'),
(40, 4700000, 'cash'),
(41, 600000, 'credit_card'),
(42, 1700000, 'cash'),
(43, 2000000, 'bank_transfer'),
(44, 1200000, 'cash'),
(45, 1100000, 'bank_transfer'),
(46, 2150000, 'bank_transfer'),
(47, 7600000, 'cash'),
(48, 2100000, 'cash'),
(49, 700000, 'bank_transfer'),
(50, 3000000, 'cash'),
(51, 4100000, 'credit_card'),
(52, 2000000, 'bank_transfer'),
(53, 6000000, 'cash'),
(54, 3100000, 'credit_card'),
(55, 1000000, 'cash'),
(56, 4700000, 'cash'),
(57, 5200000, 'bank_transfer'),
(58, 5150000, 'credit_card'),
(59, 7700000, 'cash'),
(60, 550000, 'credit_card'),
(61, 4000000, 'credit_card'),
(62, 5150000, 'credit_card'),
(63, 3000000, 'bank_transfer'),
(64, 1700000, 'credit_card'),
(65, 1700000, 'credit_card'),
(66, 4650000, 'cash'),
(67, 1200000, 'credit_card'),
(68, 4050000, 'credit_card'),
(69, 2550000, 'bank_transfer'),
(70, 1500000, 'bank_transfer'),
(71, 2050000, 'credit_card'),
(72, 1650000, 'bank_transfer'),
(73, 1650000, 'bank_transfer'),
(74, 4000000, 'bank_transfer'),
(75, 2700000, 'cash'),
(76, 2200000, 'bank_transfer'),
(77, 4200000, 'cash'),
(78, 1200000, 'bank_transfer');
