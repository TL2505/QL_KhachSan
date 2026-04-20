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


-- SEED DATA FROM PRODUCTION

INSERT INTO `roles` (`id`, `name`, `description`, `created_at`, `updated_at`) VALUES
(1, 'admin', 'Quản trị viên có toàn quyền truy cập', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(2, 'staff', 'Nhân viên lễ tân', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(3, 'customer', 'Khách hàng trú tại khách sạn', '2026-04-20 04:03:26', '2026-04-20 04:03:26');


INSERT INTO `users` (`id`, `username`, `password`, `role_id`, `status`, `full_name`, `email`, `phone`, `customer_id`, `created_at`, `updated_at`) VALUES
(1, 'admin_main', '$2a$10$EUiC6sIZD/Un75n20QIKjO3r5xP2eVJ4AB0ZNr/E0guhBY5GyzbFG', 1, 'active', 'Administrator', 'admin@hotel.com', '0987654321', NULL, '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(2, 'staff_01', '$2a$10$EUiC6sIZD/Un75n20QIKjO3r5xP2eVJ4AB0ZNr/E0guhBY5GyzbFG', 2, 'active', 'Nguyen Van A', 'nva@hotel.com', '0123456789', NULL, '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(3, 'customer_test', '$2a$10$EUiC6sIZD/Un75n20QIKjO3r5xP2eVJ4AB0ZNr/E0guhBY5GyzbFG', 3, 'active', 'Khách hàng Test', 'test@gmail.com', '0900000000', 1, '2026-04-20 04:03:26', '2026-04-20 04:03:26');


INSERT INTO `room_types` (`id`, `name`, `description`, `base_price`, `capacity`, `image_url`, `amenities`, `created_at`, `updated_at`) VALUES
(1, 'Standard', 'Phòng tiêu chuẩn cơ bản, đầy đủ tiện nghi cho 2 người', 500000.00, 2, 'standard.jpg', 'Wifi, TV, Air Conditioning, Hot Water', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(2, 'Deluxe', 'Phòng cao cấp view biển, không gian sang trọng', 1000000.00, 2, 'deluxe.jpg', 'Wifi, TV, Air Conditioning, Hot Water, Mini Bar, Ocean View', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(3, 'Family', 'Phòng căn hộ rộng rãi cho cả gia đình', 1500000.00, 4, 'family.jpg', 'Wifi, TV, Air Conditioning, Hot Water, Mini Bar, Kitchen, Extra Bed', '2026-04-20 04:03:26', '2026-04-20 04:03:26');


INSERT INTO `rooms` (`id`, `room_number`, `room_type_id`, `price`, `status`, `created_at`, `updated_at`) VALUES
(1, '101', 1, 500000.00, 'cleaning', '2026-04-20 04:03:26', '2026-04-20 04:40:20'),
(2, '102', 1, 500000.00, 'available', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(3, '103', 1, 500000.00, 'available', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(4, '104', 1, 500000.00, 'cleaning', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(5, '105', 1, 500000.00, 'available', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(6, '201', 2, 1000000.00, 'booked', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(7, '202', 2, 1000000.00, 'available', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(8, '203', 2, 1000000.00, 'occupied', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(9, '204', 2, 1000000.00, 'available', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(10, '205', 2, 1000000.00, 'maintenance', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(11, '301', 3, 1500000.00, 'occupied', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(12, '302', 3, 1500000.00, 'maintenance', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(13, '303', 3, 1500000.00, 'available', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(14, '304', 3, 1500000.00, 'available', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(15, '305', 3, 1500000.00, 'booked', '2026-04-20 04:03:26', '2026-04-20 04:03:26');


INSERT INTO `customers` (`id`, `full_name`, `identity_card`, `phone`, `email`, `address`, `is_vip`, `loyalty_points`, `total_loyalty_points`, `loyalty_level`, `created_at`, `updated_at`) VALUES
(1, 'Nguyễn Văn A', '012345678912', '0901234567', 'nva@email.com', 'Hà Nội', 1, 1500, 1500, 'Gold', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(2, 'Trần Thị B', '098765432109', '0987654321', 'ttb@email.com', 'Đà Nẵng', 0, 500, 500, 'Member', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(3, 'Lê Hoàng C', '036985214701', '0912345678', 'lhc@email.com', 'TP.HCM', 0, 200, 200, 'Member', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(4, 'Phạm Quang D', '079012345678', '0902345678', 'pqd@email.com', 'Cần Thơ', 1, 5500, 5500, 'VIP', '2026-04-20 04:03:26', '2026-04-20 04:40:20'),
(5, 'Vũ Bích E', '040087654321', '0812345678', 'vbe@email.com', 'Hải Phòng', 0, 0, 0, 'Member', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(6, 'Hoàng Tuấn F', '038012312312', '0931231234', 'htf@email.com', 'Bình Dương', 0, 100, 100, 'Member', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(7, 'Ngô Thanh G', '046034534534', '0898765432', 'ntg@email.com', 'Đồng Nai', 0, 0, 0, 'Member', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(8, 'Đinh Quỳnh H', '019056756756', '0981122334', 'dqh@email.com', 'Quảng Ninh', 1, 2200, 2200, 'Platinum', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(9, 'Lý Tiểu I', '001098798798', '0979988776', 'lti@email.com', 'Kiên Giang', 0, 300, 300, 'Member', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(10, 'Đỗ Hùng K', '054045645645', '0966655544', 'dhk@email.com', 'Khánh Hòa', 0, 0, 0, 'Member', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(11, 'Bùi Diệu L', '027011122233', '0944433322', 'bdl@email.com', 'Vĩnh Phúc', 0, 800, 800, 'Silver', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(12, 'Trương Minh M', '033099988877', '0922211100', 'tmm@email.com', 'Bắc Ninh', 0, 150, 150, 'Member', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(13, 'Phan Tú N', '062077766655', '0911199988', 'ptn@email.com', 'Long An', 0, 0, 0, 'Member', '2026-04-20 04:03:26', '2026-04-20 04:03:26');


INSERT INTO `services` (`id`, `name`, `description`, `price`, `status`, `created_at`, `updated_at`) VALUES
(1, 'Giặt là', 'Dịch vụ giặt ủi lấy liền', 50000.00, 'active', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(2, 'Dọn phòng sớm', 'Dịch vụ dọn dẹp vệ sinh theo yêu cầu', 30000.00, 'active', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(3, 'Thuê xe', 'Thuê xe máy di chuyển 1 ngày', 150000.00, 'active', '2026-04-20 04:03:26', '2026-04-20 04:03:26');


INSERT INTO `bookings` (`id`, `customer_id`, `room_id`, `check_in_date`, `check_out_date`, `total_price`, `status`, `customer_type`, `created_at`, `updated_at`) VALUES
(1, 1, 1, '2026-03-01 14:00:00', '2026-03-05 12:00:00', 2000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(2, 1, 2, '2026-04-10 14:00:00', '2026-04-15 12:00:00', 2500000.00, 'pending', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(3, 2, 3, '2026-03-15 14:00:00', '2026-03-17 12:00:00', 2000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(4, 4, 4, '2026-03-20 14:00:00', '2026-03-22 12:00:00', 3000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(5, 5, 5, '2026-03-25 14:00:00', '2026-03-28 12:00:00', 4500000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(6, 6, 1, '2026-04-01 14:00:00', '2026-04-03 12:00:00', 1000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(7, 7, 2, '2026-04-05 14:00:00', '2026-04-07 12:00:00', 1000000.00, 'checked_in', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(8, 8, 3, '2026-04-12 14:00:00', '2026-04-14 12:00:00', 2000000.00, 'pending', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(9, 9, 4, '2026-04-20 14:00:00', '2026-04-25 12:00:00', 7500000.00, 'confirmed', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(10, 10, 5, '2026-03-10 14:00:00', '2026-03-12 12:00:00', 3000000.00, 'cancelled', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(11, 8, 3, '2025-05-20 04:00:00', '2025-05-25 12:00:00', 2500000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(12, 8, 1, '2026-01-23 23:00:00', '2026-01-25 12:00:00', 1000000.00, 'confirmed', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(13, 8, 2, '2025-12-31 01:00:00', '2026-01-05 12:00:00', 2500000.00, 'checked_out', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(14, 1, 1, '2025-01-16 21:00:00', '2025-01-20 12:00:00', 2000000.00, 'confirmed', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(15, 2, 13, '2025-08-25 03:00:00', '2025-08-29 12:00:00', 6000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(16, 9, 10, '2025-07-03 18:00:00', '2025-07-05 12:00:00', 2000000.00, 'checked_out', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(17, 3, 8, '2025-04-13 16:00:00', '2025-04-16 12:00:00', 3000000.00, 'checked_in', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(18, 10, 7, '2025-12-15 01:00:00', '2025-12-16 12:00:00', 1000000.00, 'confirmed', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(19, 7, 8, '2026-03-18 23:00:00', '2026-03-19 12:00:00', 1000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(20, 4, 14, '2025-08-16 03:00:00', '2025-08-17 12:00:00', 1500000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(21, 10, 7, '2025-06-02 17:00:00', '2025-06-04 12:00:00', 2000000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(22, 7, 11, '2025-06-20 13:00:00', '2025-06-21 12:00:00', 1500000.00, 'checked_in', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(23, 11, 13, '2025-06-11 09:00:00', '2025-06-14 12:00:00', 4500000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(24, 13, 6, '2025-04-20 14:00:00', '2025-04-25 12:00:00', 5000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(25, 2, 13, '2025-05-11 00:00:00', '2025-05-12 12:00:00', 1500000.00, 'checked_out', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(26, 13, 9, '2025-07-06 16:00:00', '2025-07-11 12:00:00', 5000000.00, 'checked_in', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(27, 13, 12, '2025-03-07 11:00:00', '2025-03-10 12:00:00', 4500000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(28, 9, 1, '2026-03-26 09:00:00', '2026-03-29 12:00:00', 1500000.00, 'confirmed', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(29, 7, 13, '2026-02-25 04:00:00', '2026-02-26 12:00:00', 1500000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(30, 9, 3, '2025-01-12 23:00:00', '2025-01-14 12:00:00', 1000000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(31, 12, 5, '2026-03-22 03:00:00', '2026-03-24 12:00:00', 1000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(32, 3, 13, '2025-06-17 03:00:00', '2025-06-19 12:00:00', 3000000.00, 'checked_out', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(33, 1, 2, '2025-06-25 23:00:00', '2025-06-26 12:00:00', 500000.00, 'checked_out', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(34, 8, 13, '2026-01-03 11:00:00', '2026-01-04 12:00:00', 1500000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(35, 11, 9, '2025-12-07 11:00:00', '2025-12-12 12:00:00', 5000000.00, 'checked_in', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(36, 7, 13, '2025-07-05 09:00:00', '2025-07-09 12:00:00', 6000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(37, 13, 5, '2025-12-03 06:00:00', '2025-12-08 12:00:00', 2500000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(38, 6, 6, '2025-05-15 16:00:00', '2025-05-19 12:00:00', 4000000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(39, 9, 3, '2026-05-30 00:00:00', '2026-06-02 12:00:00', 1500000.00, 'checked_out', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(40, 3, 13, '2025-11-12 04:00:00', '2025-11-13 12:00:00', 1500000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(41, 7, 14, '2025-12-21 20:00:00', '2025-12-22 12:00:00', 1500000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(42, 7, 11, '2025-01-25 03:00:00', '2025-01-28 12:00:00', 4500000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(43, 12, 15, '2025-02-18 15:00:00', '2025-02-22 12:00:00', 6000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(44, 6, 6, '2025-09-22 14:00:00', '2025-09-24 12:00:00', 2000000.00, 'checked_in', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(45, 8, 12, '2026-05-07 07:00:00', '2026-05-09 12:00:00', 3000000.00, 'confirmed', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(46, 8, 8, '2025-12-18 13:00:00', '2025-12-20 12:00:00', 2000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(47, 5, 2, '2025-05-10 14:00:00', '2025-05-12 12:00:00', 1000000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(48, 10, 7, '2026-01-10 05:00:00', '2026-01-14 12:00:00', 4000000.00, 'checked_in', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(49, 12, 13, '2026-04-22 00:00:00', '2026-04-26 12:00:00', 6000000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(50, 1, 8, '2025-10-11 15:00:00', '2025-10-13 12:00:00', 2000000.00, 'checked_in', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(51, 4, 12, '2025-12-01 21:00:00', '2025-12-03 12:00:00', 3000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(52, 2, 3, '2025-11-27 02:00:00', '2025-11-30 12:00:00', 1500000.00, 'checked_out', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(53, 5, 2, '2026-04-15 20:00:00', '2026-04-19 12:00:00', 2000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(54, 9, 10, '2025-08-30 18:00:00', '2025-09-02 12:00:00', 3000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(55, 1, 6, '2026-01-30 10:00:00', '2026-02-04 12:00:00', 5000000.00, 'checked_out', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(56, 11, 4, '2026-01-11 04:00:00', '2026-01-12 12:00:00', 500000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(57, 7, 6, '2025-03-13 15:00:00', '2025-03-14 12:00:00', 1000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(58, 5, 5, '2025-03-02 02:00:00', '2025-03-04 12:00:00', 1000000.00, 'checked_in', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(59, 13, 9, '2025-01-20 21:00:00', '2025-01-25 12:00:00', 5000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(60, 8, 12, '2025-07-24 04:00:00', '2025-07-27 12:00:00', 4500000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(61, 7, 15, '2026-01-25 07:00:00', '2026-01-26 12:00:00', 1500000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(62, 10, 15, '2025-07-23 15:00:00', '2025-07-25 12:00:00', 3000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(63, 4, 1, '2026-04-08 05:00:00', '2026-04-09 12:00:00', 500000.00, 'paid', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:40:20'),
(64, 11, 13, '2025-06-29 15:00:00', '2025-07-04 12:00:00', 7500000.00, 'checked_in', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(65, 7, 1, '2026-02-07 20:00:00', '2026-02-08 12:00:00', 500000.00, 'confirmed', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(66, 5, 13, '2025-06-28 01:00:00', '2025-06-30 12:00:00', 3000000.00, 'confirmed', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(67, 12, 13, '2026-03-23 21:00:00', '2026-03-26 12:00:00', 4500000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(68, 12, 2, '2025-09-16 23:00:00', '2025-09-17 12:00:00', 500000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(69, 1, 14, '2025-11-16 04:00:00', '2025-11-17 12:00:00', 1500000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(70, 7, 5, '2025-12-10 03:00:00', '2025-12-14 12:00:00', 2000000.00, 'checked_out', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(71, 13, 4, '2025-12-25 10:00:00', '2025-12-27 12:00:00', 1000000.00, 'checked_out', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(72, 12, 1, '2025-03-23 01:00:00', '2025-03-25 12:00:00', 1000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(73, 5, 4, '2026-03-28 01:00:00', '2026-04-01 12:00:00', 2000000.00, 'checked_in', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(74, 11, 1, '2025-08-28 21:00:00', '2025-09-01 12:00:00', 2000000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(75, 13, 15, '2026-01-28 05:00:00', '2026-02-02 12:00:00', 7500000.00, 'checked_out', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(76, 5, 12, '2025-11-21 18:00:00', '2025-11-26 12:00:00', 7500000.00, 'checked_in', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(77, 8, 5, '2026-03-21 20:00:00', '2026-03-26 12:00:00', 2500000.00, 'checked_in', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(78, 7, 2, '2026-02-22 03:00:00', '2026-02-26 12:00:00', 2000000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(79, 4, 8, '2025-01-19 21:00:00', '2025-01-21 12:00:00', 2000000.00, 'checked_in', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(80, 4, 5, '2026-02-27 09:00:00', '2026-02-28 12:00:00', 500000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(81, 7, 12, '2026-05-07 07:00:00', '2026-05-11 12:00:00', 6000000.00, 'confirmed', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(82, 3, 2, '2025-01-19 12:00:00', '2025-01-23 12:00:00', 2000000.00, 'checked_in', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(83, 5, 15, '2026-02-25 06:00:00', '2026-02-27 12:00:00', 3000000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(84, 2, 7, '2025-07-13 10:00:00', '2025-07-17 12:00:00', 4000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(85, 3, 7, '2025-10-15 05:00:00', '2025-10-16 12:00:00', 1000000.00, 'checked_in', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(86, 6, 3, '2025-02-05 20:00:00', '2025-02-08 12:00:00', 1500000.00, 'confirmed', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(87, 13, 3, '2025-08-23 12:00:00', '2025-08-27 12:00:00', 2000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(88, 11, 12, '2025-11-07 17:00:00', '2025-11-11 12:00:00', 6000000.00, 'confirmed', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(89, 9, 10, '2025-08-29 05:00:00', '2025-08-31 12:00:00', 2000000.00, 'confirmed', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(90, 12, 13, '2025-08-04 19:00:00', '2025-08-08 12:00:00', 6000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(91, 11, 14, '2026-05-29 17:00:00', '2026-05-31 12:00:00', 3000000.00, 'confirmed', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(92, 3, 12, '2025-12-26 03:00:00', '2025-12-28 12:00:00', 3000000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(93, 3, 7, '2026-04-29 13:00:00', '2026-04-30 12:00:00', 1000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(94, 11, 12, '2025-09-06 23:00:00', '2025-09-09 12:00:00', 4500000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(95, 8, 2, '2025-03-27 08:00:00', '2025-03-28 12:00:00', 500000.00, 'confirmed', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(96, 11, 6, '2025-02-01 07:00:00', '2025-02-06 12:00:00', 5000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(97, 5, 11, '2025-02-22 03:00:00', '2025-02-23 12:00:00', 1500000.00, 'checked_in', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(98, 5, 6, '2025-10-16 02:00:00', '2025-10-21 12:00:00', 5000000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(99, 3, 8, '2025-06-19 15:00:00', '2025-06-21 12:00:00', 2000000.00, 'checked_in', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(100, 2, 8, '2026-05-30 13:00:00', '2026-06-02 12:00:00', 3000000.00, 'checked_in', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(101, 13, 15, '2026-03-08 08:00:00', '2026-03-13 12:00:00', 7500000.00, 'checked_out', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(102, 13, 1, '2025-07-16 05:00:00', '2025-07-17 12:00:00', 500000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(103, 3, 7, '2025-09-22 14:00:00', '2025-09-26 12:00:00', 4000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(104, 8, 6, '2026-03-24 06:00:00', '2026-03-29 12:00:00', 5000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(105, 9, 7, '2025-06-05 17:00:00', '2025-06-08 12:00:00', 3000000.00, 'checked_out', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(106, 2, 14, '2025-11-19 11:00:00', '2025-11-22 12:00:00', 4500000.00, 'checked_in', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(107, 5, 15, '2025-03-28 21:00:00', '2025-03-29 12:00:00', 1500000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(108, 8, 13, '2025-02-10 12:00:00', '2025-02-11 12:00:00', 1500000.00, 'checked_out', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(109, 7, 6, '2025-08-11 09:00:00', '2025-08-16 12:00:00', 5000000.00, 'confirmed', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(110, 9, 15, '2026-01-04 04:00:00', '2026-01-09 12:00:00', 7500000.00, 'checked_in', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(111, 10, 12, '2025-09-09 12:00:00', '2025-09-12 12:00:00', 4500000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(112, 2, 8, '2025-05-14 10:00:00', '2025-05-15 12:00:00', 1000000.00, 'checked_out', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(113, 11, 8, '2025-08-09 18:00:00', '2025-08-13 12:00:00', 4000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(114, 2, 5, '2025-02-16 08:00:00', '2025-02-19 12:00:00', 1500000.00, 'checked_in', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(115, 8, 3, '2025-06-27 07:00:00', '2025-07-02 12:00:00', 2500000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(116, 1, 1, '2025-12-03 16:00:00', '2025-12-06 12:00:00', 1500000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(117, 13, 14, '2026-05-21 21:00:00', '2026-05-25 12:00:00', 6000000.00, 'checked_in', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(118, 3, 2, '2025-09-27 13:00:00', '2025-10-01 12:00:00', 2000000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(119, 10, 10, '2026-02-13 04:00:00', '2026-02-18 12:00:00', 5000000.00, 'checked_in', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(120, 4, 3, '2025-10-17 14:00:00', '2025-10-20 12:00:00', 1500000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(121, 4, 12, '2025-08-19 09:00:00', '2025-08-20 12:00:00', 1500000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(122, 2, 9, '2026-02-15 16:00:00', '2026-02-19 12:00:00', 4000000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(123, 3, 1, '2025-05-31 13:00:00', '2025-06-05 12:00:00', 2500000.00, 'confirmed', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(124, 11, 10, '2025-08-21 12:00:00', '2025-08-23 12:00:00', 2000000.00, 'confirmed', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(125, 6, 1, '2025-09-04 01:00:00', '2025-09-09 12:00:00', 2500000.00, 'checked_out', 'Corporate', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(126, 6, 4, '2025-02-17 14:00:00', '2025-02-21 12:00:00', 2000000.00, 'confirmed', 'OTA', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(127, 5, 10, '2026-02-27 19:00:00', '2026-03-01 12:00:00', 2000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(128, 9, 9, '2025-09-23 12:00:00', '2025-09-27 12:00:00', 4000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(129, 12, 2, '2025-01-25 09:00:00', '2025-01-27 12:00:00', 1000000.00, 'checked_out', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(130, 7, 10, '2025-03-25 06:00:00', '2025-03-26 12:00:00', 1000000.00, 'confirmed', 'Walk-in', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(131, 1, 1, '2026-04-20 00:00:00', '2026-04-21 00:00:00', 500000.00, 'pending', 'Walk-in', '2026-04-20 04:39:52', '2026-04-20 04:39:52');


INSERT INTO `invoices` (`id`, `booking_id`, `total_room_fee`, `total_service_fee`, `discount`, `tax_amount`, `final_total`, `issue_date`, `status`, `created_at`, `updated_at`) VALUES
(1, 1, 2000000.00, 0.00, 0.00, 0.00, 2000000.00, '2026-04-20 04:03:26', 'paid', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(2, 3, 2000000.00, 0.00, 0.00, 0.00, 2000000.00, '2026-04-20 04:03:26', 'paid', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(3, 4, 3000000.00, 0.00, 0.00, 0.00, 3000000.00, '2026-04-20 04:03:26', 'paid', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(4, 5, 4500000.00, 0.00, 0.00, 0.00, 4500000.00, '2026-04-20 04:03:26', 'paid', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(5, 6, 1000000.00, 0.00, 0.00, 0.00, 1000000.00, '2026-04-20 04:03:26', 'paid', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(84, 63, 500000.00, 0.00, 500000.00, 0.00, 0.00, '2026-04-20 04:40:20', 'paid', '2026-04-20 04:40:20', '2026-04-20 04:40:20');


INSERT INTO `payments` (`id`, `invoice_id`, `amount`, `payment_method`, `payment_date`, `created_at`, `updated_at`) VALUES
(1, 1, 2000000.00, 'cash', '2026-04-20 04:03:26', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(2, 2, 2000000.00, 'credit_card', '2026-04-20 04:03:26', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(3, 3, 3000000.00, 'bank_transfer', '2026-04-20 04:03:26', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(4, 4, 4500000.00, 'e_wallet', '2026-04-20 04:03:26', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(5, 5, 1000000.00, 'cash', '2026-04-20 04:03:26', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(84, 84, 500000.00, 'cash', '2026-04-20 04:40:20', '2026-04-20 04:40:20', '2026-04-20 04:40:20');


INSERT INTO `messages` (`id`, `sender_id`, `receiver_id`, `content`, `is_read`, `created_at`) VALUES
(4, 1, NULL, 'tôi muốn đặt phòng', 0, '2026-04-20 04:38:57');


INSERT INTO `promotions` (`id`, `name`, `description`, `discount_type`, `discount_value`, `start_date`, `end_date`, `condition_type`, `condition_value`, `status`, `created_at`, `updated_at`) VALUES
(1, 'Mùa Hè Sôi Động', 'Giảm 10% cho tất cả các phòng', 'percentage', 10.00, '2026-05-01 00:00:00', '2026-08-31 23:59:59', 'none', '', 'active', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(2, 'Giảm Giá VIP', 'Giảm 500k cho khách VIP', 'fixed_amount', 500000.00, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 'vip_only', '', 'active', '2026-04-20 04:03:26', '2026-04-20 04:03:26'),
(3, 'Ưu đãi Family', 'Giảm 15% khi thuê phòng Family tối thiểu 3 đêm', 'percentage', 15.00, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 'min_stay', '3', 'active', '2026-04-20 04:03:26', '2026-04-20 04:03:26');


INSERT INTO `reviews` (`id`, `room_id`, `customer_id`, `rating`, `comment`, `created_at`) VALUES
(1, 1, 1, 5, 'Phòng sạch sẽ, view đẹp, thái độ nhân viên tốt.', '2026-04-20 04:03:26'),
(2, 2, 2, 4, 'Dịch vụ ổn nhưng check in hơi chậm.', '2026-04-20 04:03:26'),
(3, 3, 3, 5, 'Rất hài lòng, sẽ quay lại lần sau.', '2026-04-20 04:03:26'),
(4, 4, 4, 3, 'Điều hòa trong phòng hơi ồn, bù lại thức ăn ngon.', '2026-04-20 04:03:26'),
(5, 5, 5, 5, 'Trải nghiệm tuyệt vời, đánh giá 5 sao cho chất lượng dịch vụ!', '2026-04-20 04:03:26');


INSERT INTO `loyalty_histories` (`id`, `customer_id`, `points`, `type`, `description`, `created_at`) VALUES
(1, 1, 1500, 'earn', 'Điểm thưởng từ booking ID 1', '2026-04-20 04:03:26'),
(2, 2, 500, 'earn', 'Điểm thưởng từ booking ID 2', '2026-04-20 04:03:26'),
(3, 3, 200, 'earn', 'Điểm thưởng từ dịch vụ dùng thêm', '2026-04-20 04:03:26'),
(4, 4, 5000, 'earn', 'Điểm thưởng từ booking ID 4 (Khách hàng đặc biệt)', '2026-04-20 04:03:26'),
(5, 8, 2200, 'earn', 'Tích điểm theo chương trình khuyến mãi đầu năm', '2026-04-20 04:03:26'),
(6, 11, 800, 'earn', 'Khách hàng thân thiết check-in nhiều lần', '2026-04-20 04:03:26');


