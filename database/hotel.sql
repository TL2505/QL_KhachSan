-- CREATE DATABASE
CREATE DATABASE IF NOT EXISTS hotel_management_system
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE hotel_management_system;

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
  status ENUM('available', 'booked', 'occupied', 'maintenance', 'dirty') DEFAULT 'available',
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
-- VIEWS & TRIGGERS
-- --------------------------------------------------------

-- View: Báo cáo danh thu hàng tháng (Nhóm theo hóa đơn đã thanh toán)
CREATE OR REPLACE VIEW view_monthly_revenue AS
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
INSERT INTO roles (name, description) VALUES 
('admin', 'Quản trị viên có toàn quyền truy cập'),
('staff', 'Nhân viên lễ tân');

-- 2. users
INSERT INTO users (username, password, role_id) VALUES 
('admin_main', 'hashed_pass_123', 1),
('staff_01', 'hashed_pass_456', 2),
('staff_02', 'hashed_pass_789', 2);

-- 3. room_types
INSERT INTO room_types (name, description, base_price, capacity) VALUES 
('Standard', 'Phòng tiêu chuẩn cơ bản 2 người', 500000, 2),
('Deluxe', 'Phòng cao cấp view biển hướng nắng', 1000000, 2),
('Family', 'Phòng căn hộ gia đình diện tích rộng', 1500000, 4);

-- 4. rooms
INSERT INTO rooms (room_number, room_type_id, price, status) VALUES 
('101', 1, 500000, 'available'),
('102', 1, 500000, 'available'),
('201', 2, 1000000, 'booked'),
('301', 3, 1500000, 'occupied'),
('302', 3, 1500000, 'maintenance');

-- 5. customers
INSERT INTO customers (full_name, identity_card, phone, email, address) VALUES 
('Nguyễn Văn A', '012345678912', '0901234567', 'nva@email.com', 'Hà Nội'),
('Trần Thị B', '098765432109', '0987654321', 'ttb@email.com', 'Đà Nẵng'),
('Lê Hoàng C', '036985214701', '0912345678', 'lhc@email.com', 'TP.HCM');

-- 6. services
INSERT INTO services (name, description, price) VALUES 
('Giặt là', 'Dịch vụ giặt ủi lấy liền', 50000),
('Dọn phòng sớm', 'Dịch vụ dọn dẹp vệ sinh theo yêu cầu', 30000),
('Thuê xe', 'Thuê xe máy di chuyển 1 ngày', 150000);

-- 7. bookings (Phải khớp với trạng thái rooms ở trên)
-- Phòng 201 (ID 3) đang 'booked' -> Trần Thị B (ID 2) đặt trước
INSERT INTO bookings (customer_id, room_id, check_in_date, check_out_date, total_price, status) VALUES 
(2, 3, '2026-04-10 14:00:00', '2026-04-15 12:00:00', 5000000, 'confirmed');

-- Phòng 301 (ID 4) đang 'occupied' -> Nguyễn Văn A (ID 1) đang ở
INSERT INTO bookings (customer_id, room_id, check_in_date, check_out_date, total_price, status) VALUES 
(1, 4, '2026-03-27 10:00:00', '2026-03-30 12:00:00', 4500000, 'checked_in');
