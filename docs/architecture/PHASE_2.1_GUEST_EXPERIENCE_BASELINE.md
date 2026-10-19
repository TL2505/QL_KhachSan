# ĐƯỜNG CƠ SỞ ĐẶC TẢ KIẾN TRÚC HỆ THỐNG - GIAI ĐOẠN 2.1
## AURELIA GRAND PALACE 5★ PMS (PROPERTY MANAGEMENT SYSTEM)
### Định danh Giai đoạn: `PHASE_2.1 - Guest Journey, In-Stay Services, Lifecycle Notifications & Unified Reputation Engine`

- **Tài liệu:** Bản Đặc tả Kiến trúc Chi tiết & Tiêu chuẩn Vận hành Kỹ thuật (Production Baseline Specification)
- **Đường dẫn tệp:** `docs/architecture/PHASE_2.1_GUEST_EXPERIENCE_BASELINE.md`
- **Phiên bản:** `2.1.0-PROD-BASELINE`
- **Ngày ban hành:** 18/10/2026
- **Chủ biên:** Lead Solution Architect & Principal Security Engineer
- **Phê duyệt bởi:** Technical Steering Committee & Chief Infrastructure Auditor
- **Trạng thái:** `APPROVED & LOCKED FOR PRODUCTION IMPLEMENTATION`
- **Chính sách an toàn:** `Zero-Regression Enforcement`, `OWASP Top 10 Compliance`, `Dual-Environment Parity (Docker VM 192.168.56.102 & Localhost Dev)`

---

## MỤC LỤC CHI TIẾT
1. [Tổng Quan Điều Hành & Ma Trận Phân Bổ Trách Nhiệm (Executive Summary & Scope Matrix)](#1-tổng-quan-điều-hành--ma-trận-phân-bổ-trách-nhiệm-executive-summary--scope-matrix)
2. [Sơ Đồ Tuần Tự Toàn Diện Toàn Hệ Thống (End-to-End System Sequence Diagram)](#2-sơ-đồ-tuần-tự-toàn-diện-toàn-hệ-thống-end-to-end-system-sequence-diagram)
3. [Đặc Tả Nâng Cấp Cơ Sở Dữ Liệu An Toàn (Database Migration Specification - v3_2_phase2_1_schema.sql)](#3-đặc-tả-nâng-cấp-cơ-sở-dữ-liệu-an-toàn-database-migration-specification---v3_2_phase2_1_schemasql)
4. [Đặc Tả Hợp Đồng REST API Chi Tiết (REST API Contracts Specification)](#4-đặc-tả-hợp-đồng-rest-api-chi-tiết-rest-api-contracts-specification)
5. [Đặc Tả Kỹ Thuật 5 Trụ Cột Vận Hành Cốt Lõi (5 Operational Pillars Deep-Dive)](#5-đặc-tả-kỹ-thuật-5-trụ-cột-vận-hành-cốt-lõi-5-operational-pillars-deep-dive)
   - [Trụ cột 1: Tinh gọn Điều hướng Trang chủ & Tái thiết Kế Cổng Thành Viên](#trụ-cột-1-tinh-gọn-điều-hướng-trang-chủ--tái-thiết-kế-cổng-thành-viên)
   - [Trụ cột 2: Hệ thống Tự phục vụ Trong kỳ nghỉ (In-Stay Self-Service)](#trụ-cột-2-hệ-thống-tự-phục-vụ-trong-kỳ-nghỉ-in-stay-self-service)
   - [Trụ cột 3: Động cơ Thông báo Vòng đời & Hệ thống Voucher Đa dịch vụ](#trụ-cột-3-động-cơ-thông-báo-vòng-đời--hệ-thống-voucher-đa-dịch-vụ)
   - [Trụ cột 4: Hệ thống Đánh giá Toàn diện 5 Sao (All-in-One Stay Reputation)](#trụ-cột-4-hệ-thống-đánh-giá-toàn-diện-5-sao-all-in-one-stay-reputation)
   - [Trụ cột 5: Ràng buộc Kỹ thuật & An toàn Dữ liệu Bắt buộc (Production Security & Concurrency)](#trụ-cột-5-ràng-buộc-kỹ-thuật--an-toàn-dữ-liệu-bắt-buộc-production-security--concurrency)
6. [Kế Hoạch Thi Công 4 Chặng (4 Execution Milestones)](#6-kế-hoạch-thi-công-4-chặng-4-execution-milestones)
7. [Kế Hoạch Hoàn Tác & Dự Phòng Thảm Họa (Rollback & Safety Plan)](#7-kế-hoạch-hoàn-tác--dự-phòng-thảm-họa-rollback--safety-plan)

---

## 1. TỔNG QUAN ĐIỀU HÀNH & MA TRẬN PHÂN BỔ TRÁCH NHIỆM (EXECUTIVE SUMMARY & SCOPE MATRIX)

### 1.1. Bối cảnh & Mục tiêu Chiến lược
Hệ thống Quản lý Khách sạn 5 sao **Aurelia Grand Palace (PMS)** đã vận hành ổn định các phân hệ lõi: Sơ đồ phòng thời gian thực, quản lý đặt phòng, xuất hóa đơn VAT, thanh toán tự động qua VietQR và bảo mật danh tính khách hàng bằng Vault mã hóa AES-256-GCM.

Bước vào giai đoạn **PHASE_2.1**, hệ thống mở rộng phạm vi từ "vận hành nghiệp vụ tiền sảnh" sang **"Trải nghiệm vòng đời khách hàng toàn diện (Full Guest Experience Lifecycle)"**. Mục tiêu sống còn là kết nối liền mạch giữa:
1. **Trải nghiệm Số của Khách hàng (Web-UI):** Khách tự do khám phá phòng, nhận ưu đãi chào mừng, tự gọi món ăn/spa/dịch vụ phòng trong thời gian lưu trú và để lại đánh giá xác thực nhận điểm thưởng.
2. **Nghiệp vụ Vận hành Tiền sảnh & Buồng phòng (Desktop PMS - Java Swing):** Lễ tân, F&B và Housekeeping tiếp nhận tức thời các yêu cầu của khách đang ở trong phòng mà không cần điện thoại bàn, đồng bộ hạch toán tức thời vào Folio phòng, theo dõi và phản hồi đánh giá khách hàng ngay tại giao diện quản trị.
3. **Độ ổn định Hạ tầng & Không hồi quy (Zero-Regression Policy):** Mọi mở rộng cơ sở dữ liệu và API tuyệt đối không được làm gián đoạn hay phá vỡ các luồng nghiệp vụ hiện hữu của Desktop Client Swing và Docker Compose VM (`192.168.56.102:8081` & MariaDB `3306`).

### 1.2. Ma Trận Phân Bổ Trách Nhiệm Đa Tầng (Architectural Scope Matrix)

| Phân hệ / Tầng Kiến trúc | Công nghệ & Môi trường | Phạm vi Trách nhiệm & Nghiệp vụ Mới trong Phase 2.1 |
| :--- | :--- | :--- |
| **Web Guest Landing**<br>*(Port 3000 / Nginx)* | HTML5, Vanilla JS (`app.js`, `script.js`), Tailwind CSS | • Loại bỏ hoàn toàn nút "Đăng ký" khỏi Header.<br>• Nút "Đăng nhập" điều hướng trực tiếp sang `portal.html`.<br>• Quản lý phiên khách hàng qua `customer_token`: hiển thị `Xin chào, [Tên]`, nút `[Cổng thành viên]` và nút `[Đăng xuất]`.<br>• Nhúng Widget Đánh giá Danh tiếng Toàn diện: Điểm 5 sao trung bình, breakdown 4 tiêu chí, bình luận xác thực kèm phản hồi chính thức của Ban Giám Đốc. |
| **Member Service Portal**<br>*(Port 3000 / `portal.html`)* | HTML5, DaisyUI 5, Tailwind CSS, Lucide Icons, `portal-app.js`, `concierge.js`, `bookings.js` | • Thiết kế giao diện Hoàng gia: Dark Navy Blue (`#0b0f17`, `#131b2e`), Royal Gold Shield (`#d4af37`), tab dạng viên nang (pill-shaped tabs).<br>• Sửa dứt điểm lỗi click tab "Tạo tài khoản / Đăng ký" không ăn (toggle active tab, chuyển đổi form mượt mà không văng lỗi null reference).<br>• Nút tròn góc trên bên phải gắn icon "X" (Close) để quay lại trang chủ `index.html`.<br>• Mô-đun **In-Stay Self-Service**: Đặt F&B, Spa, Dọn phòng/Đồ dùng cho khách đang có trạng thái `CHECKED_IN`.<br>• Trung tâm Thông báo Chuông (Notification Bell Center) hiển thị thông báo vòng đời và voucher khuyến mãi.<br>• Form Đánh giá Toàn diện 5 sao (Review-to-Reward) mở khóa sau khi Check-out kèm phần thưởng 200 điểm thưởng. |
| **Java Core Backend**<br>*(Port 8081 / Native HttpServer)* | Java 17 LTS, Native `HttpServer`, HikariCP Pool, JJWT, Gson, BCrypt | • Bổ sung Controller: `NotificationController`, `ServiceOrderController`.<br>• Nâng cấp `ReviewController` hỗ trợ đánh giá 4 thành phần, chống review ảo (chỉ cho phép khi booking `checked_out`), kiểm tra IDOR, tự động thưởng điểm loyalty transactional.<br>• Nâng cấp `VoucherValidationController` & `VoucherService`: thẩm định voucher đa dịch vụ (Scope: `ROOM`, `FNB`, `SPA`, `ALL`), hạn dùng theo kỳ nghỉ (TTL Check-in đến Check-out).<br>• Đăng ký endpoint công khai `GET /api/v1/reviews/public` cho Landing page.<br>• API quản lý trạng thái đơn dịch vụ cho Desktop PMS. |
| **Desktop PMS Client**<br>*(Java Swing / FlatLaf Dark Luxury)* | Java Swing, FlatLaf Theme, Background `SwingWorker`, `ReviewManagementForm`, `MainUI` | • Bổ sung Background Worker thăm dò (Polling ngầm 10-15s) để nhận tín hiệu đơn dịch vụ phòng mới kèm chuông báo động nhẹ cho Lễ tân.<br>• Tích hợp màn hình xem chi tiết và phản hồi công khai đánh giá của khách hàng (`management_response`) trực tiếp từ Desktop UI.<br>• Hiển thị các khoản phí dịch vụ tự phục vụ phát sinh trực tiếp vào hóa đơn Folio phòng trên `InvoiceForm` và `PaymentForm`. |
| **MariaDB Database**<br>*(Port 3306 / `hotel_prod_db`)* | MariaDB 11.2 Jammy, InnoDB Engine, ACID Transactions | • Bảng mới `customer_notifications` (thông báo vòng đời, voucher chào mừng, tri ân).<br>• Bảng mới `service_orders` (đơn đặt món, đặt lịch spa, yêu cầu buồng phòng).<br>• Nâng cấp bảng `promotions` (thêm `applicable_scope`, `special_occasion`, `valid_after_checkout_days`).<br>• Nâng cấp bảng `reviews` (thêm `booking_id`, `rating_room`, `rating_fnb`, `rating_service`, `rating_cleanliness`, `rating_overall`, `management_response`, `reward_granted`).<br>• Toàn bộ DDL tuân thủ `IF NOT EXISTS` và `ADD COLUMN IF NOT EXISTS`. |

---

## 2. SƠ ĐỒ TUẦN TỰ TOÀN DIỆN TOÀN HỆ THỐNG (END-TO-END SYSTEM SEQUENCE DIAGRAM)

Quy trình khép kín từ lúc khách đặt phòng đến khi trả phòng và để lại phản hồi danh tiếng:

```text
+--------------+      +--------------+      +-------------------+      +----------------+      +------------------+      +-----------------+
| GUEST / WEB  |      | NGINX / WEB  |      | BACKEND HTTPSERVER|      | MARIADB 11.2   |      | FOLIO / BILLING  |      | DESKTOP PMS APP |
| (User Browser|      | (Port 3000)  |      | (Port 8081)       |      | (Port 3306)    |      | (Accounting)     |      | (Staff / Admin) |
+-------+------+      +-------+------+      +---------+---------+      +-------+--------+      +--------+---------+      +--------+--------+
        |                     |                       |                        |                        |                         |
[ GIAI ĐOẠN 1: ĐẶT PHÒNG THÀNH CÔNG & KÍCH HOẠT VOUCHER CHÀO MỪNG ]            |                        |                         |
        | --- 1. POST /api/v1/bookings/public ------> |                        |                        |                         |
        |                     |                       | --- Ghi nhận Booking ->| (status='confirmed')   |                         |
        |                     |                       | <--- Booking ID -------+                        |                         |
        |                     |                       |                                                 |                         |
        |                     |                       | === TRIGGER MILESTONE 1: BOOKING CONFIRMED === |                         |
        |                     |                       | --- Sinh Voucher In-Stay (vd: AURELIA_DINE15) ->|                         |
        |                     |                       | --- INSERT INTO customer_notifications -------->|                         |
        | <--- 201 Created (Kèm Booking Code & Info) -|                        |                        |                         |
        |                     |                       |                        |                        |                         |
[ GIAI ĐOẠN 2: CHECK-IN & KÍCH HOẠT TỰ PHỤC VỤ TRONG PHÒNG (IN-STAY SELF-SERVICE) ]                    |                         |
        |                                             |                        |                        |                         |
        | --- Lễ tân làm thủ tục Check-in trên Desktop PMS ---------------------------------------------------------------------->|
        |                                             |                        |                        |    (status='checked_in')|
        |                                             | <--- PUT /api/v1/bookings/{id}/checkin -----------------------------------|
        |                                             | --- Cập nhật status='checked_in' -------------->|                         |
        |                                             | --- Tạo Folio Master & Folio Incidental ------->|                         |
        |                                             |                                                 |                         |
        | --- 2. Khách vào Portal -> Chọn F&B/Spa --->|                                                 |                         |
        | --- 3. POST /api/v1/promotions/validate --->|                                                 |                         |
        |      (Áp voucher AURELIA_DINE15, scope=FNB) | --- Kiểm tra hạn dùng, scope=FNB, in-house ---->|                         |
        | <--- 200 OK (Chiết khấu hợp lệ) ------------|                                                 |                         |
        |                                             |                                                 |                         |
        | --- 4. POST /api/v1/service-orders -------->|                                                 |                         |
        |      (Body: booking_id, items, voucher)     | --- Bắt đầu ACID Transaction ------------------>|                         |
        |                                             | --- Kiểm tra IDOR & trạng thái checked_in ----->|                         |
        |                                             | --- INSERT INTO service_orders (PENDING) ------>|                         |
        |                                             | --- Ghi nhận nợ vào Folio (Folio Debit Transaction) --------------------> |
        |                                             | --- Commit Transaction ------------------------>|                         |
        | <--- 201 Created (Đơn dịch vụ đã tiếp nhận)-|                                                 |                         |
        |                                             |                                                 |                         |
        |                                             | <=== 5. Polling định kỳ (10s) GET /api/v1/service-orders/active ========= |
        |                                             | ===> Trả về đơn dịch vụ phòng mới ======================================> |
        |                                             |                                                 |    (Phát chuông & Pop-up|
        |                                             |                                                 |     Lễ tân phân công bar|
        |                                             |                                                 |     hoặc buồng phòng)   |
        |                                             |                                                 |                         |
[ GIAI ĐOẠN 3: CHECK-OUT, XUẤT HÓA ĐƠN & VOUCHER TRI ÂN KHÁCH HÀNG ]                                    |                         |
        |                                             |                                                 |                         |
        | --- Khách hoàn tất thanh toán & Check-out tại quầy -------------------------------------------------------------------->|
        |                                             | <--- POST /api/v1/folios/{id}/settle & check-out -------------------------|
        |                                             | --- Cập nhật status='checked_out' ------------->|                         |
        |                                             | --- Đóng Folio -------------------------------->|                         |
        |                                             |                                                 |                         |
        |                                             | === TRIGGER MILESTONE 2: CHECK-OUT COMPLETED == |                         |
        |                                             | --- Sinh Voucher Tri Ân (vd: MEMORIAL_ROOM25) ->|                         |
        |                                             | --- Gửi thông báo Cảm ơn & Mời đánh giá -------->|                         |
        |                                             |                                                 |                         |
[ GIAI ĐOẠN 4: REVIEW-TO-REWARD & ĐỒNG BỘ DANH TIẾNG TRÊN DASHBOARD ]                                   |                         |
        |                                             |                                                 |                         |
        | --- 6. Khách mở Portal -> Form Đánh Giá --->|                                                 |                         |
        | --- 7. POST /api/v1/reviews ---------------->|                                                 |                         |
        |      (Điểm 4 tiêu chí + Overall + Comment)  | --- Bắt đầu ACID Transaction ------------------>|                         |
        |                                             | --- Kiểm tra: booking.status=='checked_out' --->|                         |
        |                                             | --- Kiểm tra: thời gian <= 14 ngày sau c/out ->|                         |
        |                                             | --- Kiểm tra: chưa review (uq_booking_review) ->|                         |
        |                                             | --- INSERT INTO reviews ----------------------->|                         |
        |                                             | --- CỘNG NGAY 200 ĐIỂM THƯỞNG HỘI VIÊN --------->|                         |
        |                                             |     (UPDATE customers SET loyalty_points += 200)|                         |
        |                                             |     (INSERT INTO loyalty_histories)             |                         |
        |                                             | --- Commit Transaction ------------------------>|                         |
        | <--- 201 Created (Thành công + 200 điểm) ---|                                                 |                         |
        |                                             |                                                 |                         |
        |                                             | <=== 8. Quản lý xem danh sách đánh giá tại Desktop PMS ================== |
        |                                             | ===> GET /api/v1/reviews ===============================================> |
        |                                             | <=== 9. Gõ phản hồi chính thức: PUT /api/v1/reviews/{id}/reply =========== |
        |                                             | --- UPDATE reviews SET management_response ---->|                         |
        |                                             |                                                 |                         |
        | --- 10. Khách vãng lai xem Landing Page ---->|                                                 |                         |
        | --- 11. GET /api/v1/reviews/public --------->|                                                 |                         |
        | <--- 200 OK: Trả về điểm trung bình resort,  |                                                 |                         |
        |      tỷ lệ % 4 tiêu chí & các review xác thực kèm lời phản hồi của Quản lý --------------------|                         |
+-------+------+      +-------+------+      +---------+---------+      +-------+--------+      +--------+---------+      +--------+--------+
```

---

## 3. ĐẶC TẢ NÂNG CẤP CƠ SỞ DỮ LIỆU AN TOÀN (DATABASE MIGRATION SPECIFICATION - `v3_2_phase2_1_schema.sql`)

Tệp script nâng cấp: `database/migrations/v3_2_phase2_1_schema.sql`.
Toàn bộ câu lệnh được thiết kế với chính sách **Zero-Downtime & Zero-Regression**:
- Sử dụng cú pháp an toàn `CREATE TABLE IF NOT EXISTS` và `ADD COLUMN IF NOT EXISTS`.
- Khóa ngoại với hành vi bảo vệ (`ON DELETE RESTRICT` hoặc `ON DELETE CASCADE` phù hợp).
- Khởi tạo chỉ mục hiệu năng cao cho các trường tìm kiếm thường xuyên (`customer_id`, `booking_id`, `status`, `created_at`).

```sql
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
    service_id INT NULL, -- NULL nếu là yêu cầu buồng phòng tùy biến (gối lông vũ, bàn ủi)
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
```

---

## 4. ĐẶC TẢ HỢP ĐỒNG REST API CHI TIẾT (REST API CONTRACTS SPECIFICATION)

### 4.1. Phân Hệ Thông Báo Vòng Đời (Lifecycle Notifications)

#### 4.1.1. `GET /api/v1/notifications`
- **Mục đích:** Khách hàng đã đăng nhập tải danh sách thông báo cá nhân (voucher được tặng, nhắc nhở dịch vụ, hóa đơn điện tử) kèm số lượng tin chưa đọc.
- **Quyền hạn (RBAC):** Bắt buộc `Authorization: Bearer <customer_token>` (Role: `CUSTOMER`, `STAFF`, `ADMIN`).
- **Query Parameters:** `?unread_only=false&limit=20`
- **Response Headers:** `Content-Type: application/json; charset=utf-8`
- **Response (200 OK):**
```json
{
  "success": true,
  "unread_count": 2,
  "data": [
    {
      "id": 108,
      "booking_id": 42,
      "notification_type": "checkin_welcome",
      "title": "Chào Mừng Quý Khách Đến Với Aurelia Grand Palace!",
      "message": "Phòng 802 của Quý khách đã sẵn sàng. Trân trọng gửi tặng Quý khách mã ưu đãi ẩm thực AURELIA_DINE15 giảm 15% cho mọi dịch vụ F&B tại phòng trong suốt kỳ lưu trú.",
      "voucher_code": "AURELIA_DINE15",
      "action_url": "portal.html?view=concierge&category=FNB",
      "is_read": false,
      "created_at": "2026-10-18T14:30:00Z"
    },
    {
      "id": 95,
      "booking_id": 38,
      "notification_type": "checkout_gratitude",
      "title": "Cảm Ơn Quý Khách Đã Lựa Chọn Aurelia Grand Palace",
      "message": "Hóa đơn điện tử của kỳ nghỉ #38 đã được cập nhật. Kính tặng Quý khách mã tri ân MEMORIAL_ROOM25 giảm 25% cho kỳ nghỉ kế tiếp. Kính mời Quý khách để lại đánh giá để nhận ngay 200 điểm thưởng hội viên!",
      "voucher_code": "MEMORIAL_ROOM25",
      "action_url": "portal.html?view=bookings&action=review&booking_id=38",
      "is_read": false,
      "created_at": "2026-10-17T11:00:00Z"
    }
  ]
}
```

#### 4.1.2. `PUT /api/v1/notifications/{id}/read`
- **Mục đích:** Đánh dấu một thông báo cụ thể là đã đọc.
- **Quyền hạn:** Kiểm tra IDOR (`notification.customer_id == jwt.customer_id`).
- **Response (200 OK):**
```json
{
  "success": true,
  "message": "Đã đánh dấu thông báo là đã đọc",
  "data": {
    "id": 108,
    "is_read": true,
    "read_at": "2026-10-18T20:30:00Z"
  }
}
```

---

### 4.2. Phân Hệ Đặt Dịch Vụ Tại Phòng (In-Stay Service Orders)

#### 4.2.1. `POST /api/v1/service-orders`
- **Mục đích:** Khách đang ở trong phòng (`checked_in`) đặt đồ ăn, đồ uống bar, hẹn giờ spa hoặc yêu cầu buồng phòng.
- **Quyền hạn:** `Authorization: Bearer <customer_token>`.
- **Ràng buộc nghiệp vụ bắt buộc:**
  1. `booking_id` phải thuộc về `jwt.customer_id`.
  2. Trạng thái của booking phải là `checked_in`.
  3. Nếu có `voucher_code`: Thẩm định voucher có `applicable_scope` phù hợp với `category` và còn hiệu lực.
  4. Hạch toán tự động: Tạo giao dịch ghi nợ (`debit`) vào Folio đang mở của booking.
- **Request Body (JSON):**
```json
{
  "booking_id": 42,
  "category": "FNB",
  "service_id": 102,
  "item_name": "Bít Tết Bò Wagyu A5 Dùng Tại Phòng",
  "quantity": 2,
  "unit_price": 1250000.00,
  "voucher_code": "AURELIA_DINE15",
  "special_instructions": "Chín vừa (Medium Rare), không cho tỏi, phục vụ lúc 19:30 tối nay",
  "scheduled_time": "2026-10-18T19:30:00"
}
```
- **Response (201 Created):**
```json
{
  "success": true,
  "message": "Yêu cầu dịch vụ phòng đã được gửi thành công đến bộ phận F&B và Lễ tân!",
  "data": {
    "order_id": 305,
    "booking_id": 42,
    "room_number": "802",
    "category": "FNB",
    "item_name": "Bít Tết Bò Wagyu A5 Dùng Tại Phòng",
    "quantity": 2,
    "unit_price": 1250000.00,
    "subtotal": 2500000.00,
    "discount_amount": 375000.00,
    "final_price": 2125000.00,
    "order_status": "PENDING",
    "folio_transaction_id": 892,
    "created_at": "2026-10-18T18:45:12Z"
  }
}
```
- **Mã lỗi chuẩn hóa:**
  - `400 Bad Request`: Thiếu thông tin bắt buộc, số lượng âm, hoặc voucher không đúng danh mục F&B.
  - `403 Forbidden`: `booking_id` không thuộc về tài khoản này (IDOR Detected) hoặc booking chưa Check-in (`status != 'checked_in'`).
  - `404 Not Found`: Không tìm thấy phòng hoặc Folio phòng tương ứng.

#### 4.2.2. `GET /api/v1/service-orders/active`
- **Mục đích:** Desktop PMS Background Worker gọi định kỳ (Polling 10-15s) để hiển thị thông báo khẩn cấp cho Lễ tân khi có khách gọi dịch vụ.
- **Quyền hạn:** `STAFF`, `ADMIN` (Lễ tân, F&B Manager, Housekeeping Supervisor).
- **Response (200 OK):**
```json
{
  "success": true,
  "pending_count": 1,
  "data": [
    {
      "order_id": 305,
      "room_number": "802",
      "customer_name": "Trần Hải Long",
      "category": "FNB",
      "item_name": "Bít Tết Bò Wagyu A5 Dùng Tại Phòng",
      "quantity": 2,
      "final_price": 2125000.00,
      "special_instructions": "Chín vừa (Medium Rare), không cho tỏi, phục vụ lúc 19:30 tối nay",
      "order_status": "PENDING",
      "created_at": "2026-10-18T18:45:12Z"
    }
  ]
}
```

---

### 4.3. Phân Hệ Thẩm Định Voucher Đa Dịch Vụ (Multi-Service Voucher Validation)

#### 4.3.1. `POST /api/v1/promotions/validate`
- **Mục đích:** Kiểm tra tính hợp lệ và tính toán số tiền giảm giá chính xác theo từng nhóm chi phí (Tiền phòng riêng biệt với F&B và Spa).
- **Quyền hạn:** Cho phép xem trước công khai hoặc kèm JWT.
- **Request Body (JSON):**
```json
{
  "voucher_code": "AURELIA_DINE15",
  "booking_id": 42,
  "category": "FNB",
  "raw_amount": 2500000.00
}
```
- **Response (200 OK):**
```json
{
  "success": true,
  "message": "Voucher hợp lệ",
  "data": {
    "promotion_id": 4,
    "code": "AURELIA_DINE15",
    "applicable_scope": "FNB",
    "discount_type": "percentage",
    "discount_value": 15.00,
    "original_amount": 2500000.00,
    "discount_amount": 375000.00,
    "net_amount": 2125000.00,
    "expiry_date": "2026-12-31T23:59:59Z"
  }
}
```
- **Lỗi từ chối áp dụng (400 Bad Request):**
```json
{
  "success": false,
  "error_code": "PROMO_SCOPE_MISMATCH",
  "message": "Voucher AURELIA_DINE15 chỉ áp dụng cho dịch vụ Ẩm thực & Đồ uống (FNB), không áp dụng cho Tiền phòng."
}
```

---

### 4.4. Phân Hệ Đánh Giá Kỳ Nghỉ Toàn Diện & Phản Hồi Quản Trị (Reputation Engine)

#### 4.4.1. `POST /api/v1/reviews`
- **Mục đích:** Khách hàng gửi biểu mẫu đánh giá toàn diện sau khi đã hoàn tất trả phòng (`checked_out`). Tự động cộng ngay 200 điểm thưởng hội viên.
- **Quyền hạn:** `Authorization: Bearer <customer_token>`.
- **Ràng buộc:**
  - `booking_id` phải có `status == 'checked_out'`.
  - Không vượt quá 14 ngày kể từ ngày `check_out_date`.
  - Chưa từng gửi đánh giá cho `booking_id` này (`uq_booking_review`).
- **Request Body (JSON):**
```json
{
  "booking_id": 38,
  "rating_room": 5,
  "rating_fnb": 4,
  "rating_service": 5,
  "rating_cleanliness": 5,
  "rating_overall": 4.8,
  "comment": "Kỳ nghỉ vô cùng tuyệt vời! Nhân viên tiền sảnh thân thiện, món bò Wagyu phục vụ tại phòng chuẩn 5 sao. Tôi sẽ quay lại vào dịp giáng sinh tới."
}
```
- **Response (201 Created):**
```json
{
  "success": true,
  "message": "Cảm ơn Quý khách đã gửi đánh giá! Quý khách đã được cộng ngay 200 điểm thưởng hội viên vào tài khoản.",
  "data": {
    "review_id": 78,
    "booking_id": 38,
    "room_number": "802",
    "rating_overall": 4.8,
    "reward_points": 200,
    "new_loyalty_points_balance": 1450,
    "created_at": "2026-10-18T20:15:00Z"
  }
}
```

#### 4.4.2. `GET /api/v1/reviews/public`
- **Mục đích:** API công khai (Public Endpoint, skip JWT filter) phục vụ Landing Page `index.html` hiển thị chỉ số danh tiếng thực tế và các nhận xét đã có phản hồi từ Quản lý.
- **Query Parameters:** `?page=1&limit=6`
- **Response (200 OK):**
```json
{
  "success": true,
  "summary": {
    "total_reviews": 128,
    "overall_average": 4.9,
    "room_average": 4.9,
    "fnb_average": 4.8,
    "service_average": 5.0,
    "cleanliness_average": 4.9,
    "recommendation_percentage": 98.5
  },
  "reviews": [
    {
      "id": 78,
      "customer_display_name": "Trần H*** L***",
      "room_type_name": "Executive Ocean Suite",
      "rating_overall": 4.8,
      "rating_room": 5,
      "rating_fnb": 4,
      "rating_service": 5,
      "rating_cleanliness": 5,
      "comment": "Kỳ nghỉ vô cùng tuyệt vời! Nhân viên tiền sảnh thân thiện, món bò Wagyu phục vụ tại phòng chuẩn 5 sao.",
      "created_at": "2026-10-18T20:15:00Z",
      "management_response": {
        "responder_title": "Tổng Quản Lý Khu Nghỉ Dưỡng (General Manager)",
        "content": "Chân thành cảm ơn Ông Long và gia đình đã dành thời gian chia sẻ trải nghiệm quý báu. Đội ngũ ẩm thực rất vui khi Ông hài lòng với món Bò Wagyu. Khách sạn rất mong được đón tiếp Ông trong dịp Giáng Sinh sắp tới!",
        "responded_at": "2026-10-18T21:00:00Z"
      }
    }
  ]
}
```

#### 4.4.3. `PUT /api/v1/reviews/{id}/reply`
- **Mục đích:** Quản lý khách sạn (Admin hoặc Front Desk Manager) gõ phản hồi công khai cho nhận xét của khách từ Desktop PMS hoặc Web Portal.
- **Quyền hạn:** `STAFF`, `ADMIN`.
- **Request Body (JSON):**
```json
{
  "management_response": "Chân thành cảm ơn Ông Long và gia đình đã dành thời gian chia sẻ trải nghiệm quý báu. Đội ngũ ẩm thực rất vui khi Ông hài lòng với món Bò Wagyu. Khách sạn rất mong được đón tiếp Ông trong dịp Giáng Sinh sắp tới!"
}
```
- **Response (200 OK):**
```json
{
  "success": true,
  "message": "Phản hồi của Ban Quản Lý đã được xuất bản công khai!",
  "data": {
    "review_id": 78,
    "management_response": "Chân thành cảm ơn Ông Long...",
    "responded_by": "Nguyễn Hoàng Nam (Admin)",
    "responded_at": "2026-10-18T21:00:00Z"
  }
}
```

---

## 5. ĐẶC TẢ KỸ THUẬT 5 TRỤ CỘT VẬN HÀNH CỐT LÕI (5 OPERATIONAL PILLARS DEEP-DIVE)

```text
+----------------------------------------------------------------------------------------------------+
|                                    AURELIA 5-PILLAR ARCHITECTURE                                   |
+----------------------------------------------------------------------------------------------------+
|  [PILLAR 1: PORTAL REDESIGN]   <--->  [PILLAR 2: IN-STAY SERVICES]   <--->  [PILLAR 3: NOTIFICATIONS] |
|  - Hide Register on Index             - In-House Eligibility Check          - Confirmed -> Dine Voucher   |
|  - Login redirects to Portal          - F&B, Spa, Housekeeping Catalog      - Checkout -> Retention Promo |
|  - Royal Gold Emblem & Pill Tabs      - Real-time Swing Desktop Polling     - Multi-Service Scope Filter  |
+----------------------------------------------------------------------------------------------------+
                                      |
                                      v
+----------------------------------------------------------------------------------------------------+
|  [PILLAR 4: 5★ REPUTATION ENGINE]                         [PILLAR 5: SECURITY & PRODUCTION RIGOR]  |
|  - Verified Stays Only (Checked-Out <= 14d)               - IDOR Protection across all APIs        |
|  - 4 Component Ratings + Overall (1.0-5.0)                - Atomic DB Transaction for Points       |
|  - Review-to-Reward (Auto +200 Points)                    - Non-blocking SwingWorker EDT Calls     |
|  - Public Widget & Management Reply Modal                 - Dual-Env (Docker VM & Localhost) Parity|
+----------------------------------------------------------------------------------------------------+
```

---

### TRỤ CỘT 1: TINH GỌN ĐIỀU HƯỚNG TRANG CHỦ & TÁI THIẾT KẾ CỔNG THÀNH VIÊN

#### 1.1. Trang Chủ Công Cộng (`web-ui/index.html` & `app.js`):
1. **Loại bỏ nút Đăng ký:**
   - Xóa bỏ hoặc ẩn hoàn toàn phần tử `#header-register-btn` trong `#header-auth-container`.
   - Toàn bộ lưu lượng khách muốn đăng ký tài khoản mới sẽ được dẫn qua trang tập trung `portal.html` nhằm đảm bảo trải nghiệm thương hiệu đồng nhất và tập trung bảo mật CSRF/Rate-limiting.
2. **Cập nhật nút Đăng nhập:**
   - Thay đổi hành vi click của `#header-login-btn`: Thay vì mở popup modal trên trang chủ, thực hiện điều hướng mượt mà trực tiếp sang cổng dịch vụ:
     ```javascript
     window.location.href = 'portal.html';
     ```
3. **Quản lý trạng thái phiên đăng nhập trên Header:**
   - Khi trang chủ tải xong, hàm `syncHeaderAuthState()` kiểm tra token đăng nhập:
     ```javascript
     const token = localStorage.getItem('customer_token') || localStorage.getItem('auth_token');
     const user = JSON.parse(localStorage.getItem('auth_user') || '{}');
     if (token && user.fullName) {
       // Ẩn cụm nút Login/Register
       document.getElementById('header-auth-container').innerHTML = `
         <div class="flex items-center gap-2.5 text-xs">
           <span class="text-amber-200/90 font-medium">Xin chào, <strong class="text-white">${escapeHtml(user.fullName)}</strong></span>
           <a href="portal.html" class="px-3 py-1.5 rounded-lg bg-amber-500/20 text-amber-300 border border-amber-400/30 hover:bg-amber-500/30 font-semibold transition-all">Cổng Thành Viên</a>
           <button id="btn-logout-header" onclick="handleCustomerLogout()" class="text-slate-400 hover:text-rose-400 transition-colors p-1" title="Đăng xuất"><i data-lucide="log-out" class="w-4 h-4"></i></button>
         </div>
       `;
       if (window.lucide) window.lucide.createIcons();
     }
     ```

#### 1.2. Cổng Quản Lý Thành Viên (`web-ui/portal.html` & `portal-app.js`):
1. **Thẩm mỹ Thiết kế 5 Sao (Dark Navy Blue + Royal Gold Shield Emblem):**
   - Nền chủ đạo: Obsidian Dark Slate (`#0b0f17`, `#131b2e`), kết hợp viền ánh vàng Champagne (`#d4af37`, `#aa820a`).
   - Huy hiệu biểu tượng: Chiếc khiên Hoàng gia kết hợp Vương miện vàng (Royal Gold Shield & Crown Emblem) đại diện cho sự bảo an và đẳng cấp thượng lưu.
   - Thanh Tab dạng viên nang (Pill-shaped Tabs): Bo tròn góc `rounded-full`, hiệu ứng chuyển động trượt mượt mà (slide indicator) khi người dùng chuyển giữa các tab nghiệp vụ.
2. **Khắc phục triệt để lỗi Tab "Tạo tài khoản / Đăng ký" không click được:**
   - Nguyên nhân gốc rễ: Đoạn mã cũ cố gắng gọi các hàm xử lý trước khi DOM render hoặc có xung đột giữa DaisyUI `tabs-border` với các sự kiện click tùy biến, dẫn đến lỗi `Uncaught TypeError: Cannot read properties of null`.
   - Giải pháp chuẩn kiến trúc: Bắt sự kiện trực tiếp trên hai nút tab `#show-login` và `#show-register` thông qua Event Delegation an toàn, chuyển đổi hiển thị giữa `#login-form` và `#register-form`, đồng thời toggle các lớp CSS `.active`, `.tab-active` và cập nhật thuộc tính trợ năng `aria-selected`:
     ```javascript
     function setupAuthTabs() {
       const loginBtn = document.getElementById('show-login');
       const regBtn = document.getElementById('show-register');
       const loginForm = document.getElementById('login-form');
       const regForm = document.getElementById('register-form');
       if (!loginBtn || !regBtn || !loginForm || !regForm) return;

       loginBtn.onclick = (e) => {
         e.preventDefault();
         loginBtn.classList.add('tab-active', 'active');
         regBtn.classList.remove('tab-active', 'active');
         loginForm.hidden = false;
         regForm.hidden = true;
       };
       regBtn.onclick = (e) => {
         e.preventDefault();
         regBtn.classList.add('tab-active', 'active');
         loginBtn.classList.remove('tab-active', 'active');
         regForm.hidden = false;
         loginForm.hidden = true;
       };
     }
     ```
3. **Nút Tròn Góc Trên Bên Phải (Close / Quay về Trang chủ):**
   - Đặt một nút tròn thanh lịch tại góc phải thanh Topbar:
     ```html
     <a href="index.html" class="w-10 h-10 rounded-full border border-amber-500/30 bg-slate-900/80 hover:bg-amber-500/20 text-amber-300 flex items-center justify-center transition-all shadow-lg hover:scale-105" title="Đóng & Về trang chủ" aria-label="Quay lại trang chủ">
       <i data-lucide="x" class="w-5 h-5"></i>
     </a>
     ```

---

### TRỤ CỘT 2: HỆ THỐNG TỰ PHỤC VỤ TRONG KỲ NGHỈ (IN-STAY SELF-SERVICE)

#### 2.1. Điều Kiện Tiên Quyết (Eligibility Check)
- Chức năng In-Stay Self-Service chỉ xuất hiện hoặc mở khóa khi khách hàng có đơn đặt phòng hợp lệ với trạng thái chính xác: `status == 'checked_in'`.
- Khách vãng lai chưa đặt phòng hoặc đã trả phòng (`checked_out`) sẽ thấy thông báo: *"Dịch vụ phòng trực tuyến chỉ áp dụng cho Quý khách đang lưu trú tại khách sạn."* kèm nút tra cứu đặt phòng hoặc đặt phòng mới.

#### 2.2. Danh Mục Dịch Vụ Phục Vụ Tại Phòng
1. **Ẩm thực & Thức uống tại phòng (In-Room Dining - F&B):**
   - Bữa sáng chuẩn lục địa hoặc bữa sáng cung đình Huế.
   - Thăn bò Wagyu A5 áp chảo, mì Ý hải sản tôm hùm, súp yến sào.
   - Cocktail Aurelia Royal Sunset, Vang đỏ Bordeaux, Champagne ướp lạnh.
2. **Chăm sóc Sức khỏe & Spa (Spa & Wellness Appointments):**
   - Massage Thụy Điển 60/90 phút.
   - Liệu trình tắm thảo dược hoàng cung.
   - Chọn khung giờ hẹn phục vụ (Time-slot selector từ 08:00 đến 22:00).
3. **Dịch vụ Buồng phòng & Đồ dùng bổ sung (Housekeeping Amenities - Miễn phí/Có phí):**
   - Đổi thêm gối lông vũ siêu êm ái, chăn phụ.
   - Mượn bàn ủi hơi nước, máy sấy tóc cao cấp, bộ chuyển đổi ổ cắm điện du lịch.
   - Yêu cầu dọn phòng khẩn cấp (Express Cleaning).

#### 2.3. Hạch Toán Nợ Tự Động Vào Folio Phòng (Folio Billing Integration)
- Khi khách nhấn "Xác nhận Đặt dịch vụ", hệ thống thực hiện trong một Transaction cơ sở dữ liệu:
  1. Kiểm tra mã giảm giá (nếu có) và trừ chiết khấu trực tiếp vào đơn giá.
  2. Tạo bản ghi mới trong bảng `service_orders` với trạng thái `PENDING`.
  3. Tìm Folio đang mở (`status == 'open'`) gắn với `booking_id`.
  4. Tạo một bản ghi kép trong `folio_transactions`:
     - `folio_id`: ID của Folio phòng.
     - `transaction_type`: `'debit'` (ghi nợ chi phí).
     - `charge_type`: `'service_charge'` hoặc `'fnb_charge'`.
     - `description`: Tên món/dịch vụ (VD: `[Room Service] Bít Tết Bò Wagyu A5 x2 (Phòng 802)`).
     - `amount`: Số tiền thanh toán cuối cùng sau giảm giá (`final_price`).
     - `business_date`: Ngày kinh doanh hiện hành của khách sạn.
  5. Cập nhật số dư nợ của Folio: `balance = balance + final_price`.
- Toàn bộ giao dịch này được phản ánh ngay lập tức trên màn hình Quản lý Hóa đơn và Thanh toán (`InvoiceForm.java`, `PaymentForm.java`) của nhân viên lễ tân Desktop PMS.

#### 2.4. Cơ Chế Báo Động Tức Thời Cho Desktop PMS (Real-time Staff Alerting)
- Trên ứng dụng Desktop Java Swing, tạo một tiến trình chạy ngầm `ServiceOrderAlertWorker` (kế thừa `SwingWorker` hoặc `ScheduledExecutorService`) với chu kỳ thăm dò mỗi 10-15 giây gọi `GET /api/v1/service-orders/active`.
- Khi phát hiện đơn dịch vụ có `order_status == 'PENDING'`:
  - Kích hoạt chuông thông báo (sound alert) `Toolkit.getDefaultToolkit().beep()`.
  - Hiển thị Toast notification hoặc Badge đỏ nhấp nháy trên thanh công cụ của Lễ tân.
  - Cho phép Lễ tân bấm một nút "Tiếp nhận & Chuyển Bếp/Spa" để đổi trạng thái đơn sang `ACCEPTED`.

---

### TRỤ CỘT 3: ĐỘNG CƠ THÔNG BÁO VÒNG ĐỜI & HỆ THỐNG VOUCHER ĐA DỊCH VỤ

#### 3.1. Hai Mốc Kích Hoạt Tự Động (Lifecycle Triggers)

```text
[ VÒNG ĐỜI KHÁCH HÀNG (GUEST LIFECYCLE) ]

          (1) ĐẶT PHÒNG THÀNH CÔNG (Confirmed)
                        |
                        v
          [ TỰ ĐỘNG SINH VOUCHER CHÀO MỪNG ]
          - Mã: AURELIA_DINE15 hoặc WELCOME_SPA20
          - Scope: FNB / SPA (Giảm 15% - 20%)
          - Hạn dùng: Ngày Check-in -> 23:59 Ngày Check-out
                        |
                        v
          (2) KHÁCH CHECK-IN & SỬ DỤNG DỊCH VỤ
          - Áp dụng voucher khi gọi món / đặt spa
                        |
                        v
          (3) KHÁCH THANH TOÁN & CHECK-OUT (Completed)
                        |
                        v
          [ TỰ ĐỘNG SINH VOUCHER TRI ÂN & E-FOLIO ]
          - Mã: MEMORIAL_ROOM25
          - Scope: ROOM (Giảm 25% lần sau / Sinh nhật)
          - Đính kèm link đánh giá nhận 200 điểm thưởng
```

1. **Mốc 1: Đặt phòng Xác nhận (Booking Confirmed):**
   - Khi giao dịch đặt phòng được lưu thành công vào cơ sở dữ liệu (`status = 'confirmed'`), trigger backend tự động chèn một bản ghi vào bảng `customer_notifications`:
     - **Tiêu đề:** *"Chào mừng Quý khách đến với Aurelia Grand Palace!"*
     - **Nội dung:** *"Kính gửi Quý khách, đơn đặt phòng đã được xác nhận. Chúng tôi trân trọng gửi tặng Quý khách mã ưu đãi Ẩm thực & Bar AURELIA_DINE15 (giảm 15%) áp dụng cho mọi dịch vụ ăn uống tại phòng trong suốt kỳ nghỉ."*
     - **Voucher đính kèm:** `AURELIA_DINE15` (hoặc `WELCOME_SPA20`).
2. **Mốc 2: Hoàn tất Trả phòng (Check-out Completed):**
   - Khi nhân viên lễ tân bấm hoàn tất Check-out trên Desktop PMS (`status = 'checked_out'`), trigger backend lập tức kích hoạt:
     - Chèn thông báo cảm ơn vào `customer_notifications`.
     - Cung cấp mã giữ chân khách hàng (Guest Retention Loyalty Voucher): `MEMORIAL_ROOM25` (giảm 25% giá phòng cho lần lưu trú tiếp theo, có giá trị trong vòng 1 năm hoặc nhân dịp sinh nhật/kỷ niệm ngày cưới).
     - Gửi kèm đường dẫn mở khóa biểu mẫu Đánh giá Kỳ nghỉ để nhận ngay 200 điểm thưởng hội viên.

#### 3.2. Cấu Trúc Bảng `promotions` Mở Rộng & Cơ Chế Thẩm Định Voucher
- Các trường mở rộng bắt buộc:
  - `applicable_scope`: Quy định phạm vi giảm giá hợp lệ (`ROOM`: chỉ giảm tiền phòng; `FNB`: chỉ giảm đồ ăn/thức uống; `SPA`: chỉ giảm dịch vụ spa; `ALL`: giảm toàn bộ hóa đơn tổng).
  - `special_occasion`: Phân loại sự kiện tri ân (`WELCOME`, `RETENTION`, `BIRTHDAY`, `ANNIVERSARY`).
  - `valid_after_checkout_days`: Số ngày có hiệu lực sau khi trả phòng (dành riêng cho voucher giữ chân).
- **Thuật toán thẩm định tại `VoucherService.java`:**
  - Nếu `applicable_scope == 'FNB'`, kiểm tra danh mục dịch vụ trong request có đúng là `FNB` hay không. Nếu khách áp dụng mã này vào tiền phòng hoặc dịch vụ đưa đón, hệ thống sẽ từ chối với thông báo lỗi rõ ràng: `PROMO_SCOPE_MISMATCH`.
  - Tách bạch cấu trúc tính thuế VAT 8% và phí phục vụ 5%: Chiết khấu voucher chỉ áp dụng trên giá gốc trước thuế của hạng mục dịch vụ hợp lệ.

---

### TRỤ CỘT 4: HỆ THỐNG ĐÁNH GIÁ TOÀN DIỆN 5 SAO (ALL-IN-ONE STAY REPUTATION)

#### 4.1. Ràng Buộc Đánh Giá Xác Thực (Verified Stay Policy)
- **Tuyệt đối chống đánh giá ảo (Zero Fake Reviews):** Chỉ những khách hàng thực sự đã lưu trú và hoàn tất thủ tục thanh toán trả phòng (`bookings.status == 'checked_out'`) mới có quyền gửi biểu mẫu đánh giá.
- **Ràng buộc duy nhất (Unique Stay Constraint):** Mỗi `booking_id` chỉ được gắn duy nhất với một bài đánh giá. Cơ sở dữ liệu ép buộc điều này bằng chỉ mục duy nhất `UNIQUE KEY uq_booking_review (booking_id)`.
- **Cửa sổ gửi đánh giá (Time Window):** Quyền gửi đánh giá chỉ mở trong vòng **14 ngày** kể từ thời điểm `check_out_date`. Quá thời hạn này, hệ thống đóng quyền gửi và hiển thị thông báo: *"Thời hạn gửi phản hồi cho kỳ nghỉ này đã kết thúc."*

#### 4.2. Bộ Tiêu Chí Đánh Giá 4 Thành Phần & Điểm Tổng Thể
Thay vì chỉ chấm điểm sao chung chung, hệ thống cung cấp giao diện chấm điểm 5 sao trực quan cho từng khía cạnh:
1. **Phòng nghỉ (`rating_room`: 1 - 5 sao):** Chất lượng đệm giường, độ cách âm, view cảnh quan ban công, trang thiết bị tiện nghi trong phòng.
2. **Ẩm thực & Thức uống (`rating_fnb`: 1 - 5 sao):** Bữa sáng buffet, thực đơn gọi món tại phòng (In-room dining), đồ uống tại Sky Bar.
3. **Dịch vụ & Thái độ Phục vụ (`rating_service`: 1 - 5 sao):** Tác phong chuyên nghiệp của lễ tân, quản gia, phục vụ bàn và kỹ thuật viên spa.
4. **Vệ sinh & Không gian (`rating_cleanliness`: 1 - 5 sao):** Độ sạch sẽ tinh tươm của khăn ga gối, phòng tắm, hương thơm tinh dầu và cảnh quan sảnh.
5. **Điểm Tổng Thể (`rating_overall`: 1.0 - 5.0):** Điểm đánh giá đại diện, cho phép số thập phân (ví dụ: 4.8), kèm theo ô văn bản đóng góp ý kiến chi tiết (`comment`).

#### 4.3. Cơ Chế Thưởng Điểm Tức Thời (Review-to-Reward Engine)
- Ngay khi bản ghi đánh giá được thêm thành công vào bảng `reviews`, Backend thực hiện một giao dịch ACID khép kín:
  1. Cập nhật điểm thưởng của khách: `UPDATE customers SET loyalty_points = loyalty_points + 200, total_loyalty_points = total_loyalty_points + 200 WHERE id = ?`.
  2. Tự động kiểm tra thăng hạng thẻ (nếu điểm tích lũy chạm mốc Silver, Gold hoặc VIP).
  3. Ghi nhật ký vào bảng `loyalty_histories`: `type = 'earn'`, `points = 200`, `description = 'Thưởng hoàn tất đánh giá kỳ nghỉ #[booking_id]'`.
  4. Đánh dấu cờ `reward_granted = TRUE` trên bản ghi `reviews`.
- Trả về thông báo chúc mừng cho khách hàng trên giao diện Web: *"Cảm ơn Quý khách! 200 điểm thưởng hội viên đã được cộng vào ví của Quý khách."*

#### 4.4. Công Khai Danh Tiếng & Quản Trị Phản Hồi (Reputation Dashboard)
1. **Widget Danh Tiếng trên Trang Chủ (`index.html`):**
   - Hiển thị thanh điểm trung bình toàn resort (ví dụ: `4.9 / 5.0 Xuất sắc` dựa trên 128+ đánh giá thực tế).
   - Biểu đồ cột ngang (Progress bars) trực quan cho 4 hạng mục: Phòng (4.9), Ẩm thực (4.8), Dịch vụ (5.0), Vệ sinh (4.9).
   - Danh sách đánh giá xác thực có gắn huy hiệu `[Khách hàng Đã Lưu Trú - Verified Guest]` và hiển thị trang trọng khung **"Phản hồi chính thức từ Ban Quản Lý Khách Sạn"**.
2. **Màn Hình Quản Lý Đánh Giá Trên Desktop PMS (`ReviewManagementForm.java`):**
   - Hiển thị bảng danh sách đánh giá kèm đầy đủ điểm 4 tiêu chí.
   - Thêm nút **"Phản hồi Đánh giá (Reply)"**: Mở hộp thoại `ManagementReplyDialog` cho phép Quản lý nhập nội dung phản hồi chính thức của khách sạn.
   - Khi lưu, gọi API `PUT /api/v1/reviews/{id}/reply`, cập nhật trường `management_response` và tự động hiển thị ra ngoài website.

---

### TRỤ CỘT 5: RÀNG BUỘC KỸ THUẬT & AN TOÀN DỮ LIỆU BẮT BUỘC (PRODUCTION SECURITY & CONCURRENCY)

#### 5.1. Chống Vượt Quyền & Thao Túng Tham Số (IDOR Protection)
Mọi API liên quan đến dữ liệu khách hàng (`/api/v1/notifications`, `/api/v1/service-orders`, `/api/v1/reviews`) phải tuân thủ nghiêm ngặt quy tắc đối soát quyền sở hữu:
- Không bao giờ tin tưởng `customer_id` do client gửi lên trong Request Body.
- Trích xuất `jwt_customer_id` trực tiếp từ JWT Access Token đã qua xác thực của `JwtFilter`.
- Thực hiện truy vấn kiểm tra chéo trước khi thực thi:
  ```sql
  SELECT customer_id, status FROM bookings WHERE id = ?;
  ```
  Nếu `booking.customer_id != jwt_customer_id` (trừ khi người thực hiện có quyền `admin` hoặc `staff`), hệ thống lập tức ngắt kết nối và trả về HTTP `403 Forbidden` kèm ghi log cảnh báo an ninh `SECURITY_ALERT_IDOR_VIOLATION`.

#### 5.2. Chống Gian Lận Điểm Thưởng & Kiểm Soát Đồng Thời (Race Condition & Concurrency)
- **Ngăn chặn click đúp nhận nhiều lần điểm:**
  Bằng cách đặt `UNIQUE KEY uq_booking_review (booking_id)`, MariaDB sẽ trả về mã lỗi `1062 Duplicate entry` nếu có 2 luồng cố gắng gửi đánh giá cho cùng một mã booking.
- Toàn bộ thao tác ghi nhận đánh giá và cộng điểm hội viên phải nằm trong cùng một Transaction:
  ```java
  connection.setAutoCommit(false);
  try {
      // 1. Insert review
      // 2. Update loyalty_points
      // 3. Insert loyalty_histories
      connection.commit();
  } catch (Exception ex) {
      connection.rollback();
      throw ex;
  } finally {
      connection.setAutoCommit(true);
  }
  ```

#### 5.3. Quy Định Về Hạn Dùng (TTL - Time To Live)
1. **Voucher Chào mừng In-Stay:** Bắt đầu có hiệu lực từ `check_in_date` và hết hạn đúng `23:59:59` của ngày `check_out_date`. Không thể tái sử dụng sau khi đã rời khách sạn.
2. **Quyền Gửi Đánh giá Kỳ nghỉ:** Mở ngay sau khi `status == 'checked_out'` và đóng lại sau đúng **14 ngày**.
3. **Voucher Tri ân Lưu trú:** Có hiệu lực tối đa 365 ngày kể từ ngày trả phòng, chỉ áp dụng cho lần đặt phòng kế tiếp.

#### 5.4. An Toàn Luồng Giao Diện Desktop (Swing Concurrency & EDT Safety)
- Toàn bộ các cuộc gọi mạng HTTP từ ứng dụng Desktop Swing (`ReviewAPI`, `ServiceOrderAPI`) bắt buộc phải bọc trong `SwingWorker` hoặc `CompletableFuture`.
- Tuyệt đối không thực hiện `HttpURLConnection.connect()` hoặc đọc phản hồi trực tiếp trên Event Dispatch Thread (EDT) để ngăn ngừa hiện tượng đơ giật giao diện (UI Freezing).

---

## 6. KẾ HOẠCH THI CÔNG 4 CHẶNG (4 EXECUTION MILESTONES)

```text
+---------------------------------------------------------------------------------------------------------+
|                                    LỘ TRÌNH TRIỂN KHAI 4 CHẶNG (PHASE 2.1)                              |
+---------------------------------------------------------------------------------------------------------+
| CHẶNG 1: DATABASE & MIGRATION |  - Áp dụng file v3_2_phase2_1_schema.sql vào MariaDB 3306              |
|                               |  - Nạp Seed Data Voucher (AURELIA_DINE15, MEMORIAL_ROOM25) & Menu F&B   |
+-------------------------------+-------------------------------------------------------------------------+
| CHẶNG 2: BACKEND CONTROLLERS  |  - Bổ sung ServiceOrderController, NotificationController               |
|                               |  - Nâng cấp ReviewController (4 tiêu chí + auto 200 điểm loyalty)       |
|                               |  - Nâng cấp VoucherValidationController (thẩm định scope FNB/SPA/ROOM)  |
|                               |  - Đăng ký các endpoints mới vào Main.java                             |
+-------------------------------+-------------------------------------------------------------------------+
| CHẶNG 3: WEB-UI & PORTAL      |  - Tinh gọn Header index.html (ẩn Đăng ký, Login dẫn sang Portal)      |
|                               |  - Thiết kế lại portal.html (Dark Navy Blue + Royal Gold Shield Emblem) |
|                               |  - Fix triệt để click tab Đăng ký, nút tròn đóng X quay lại trang chủ   |
|                               |  - Xây dựng form đặt dịch vụ phòng In-Stay & form Review-to-Reward      |
|                               |  - Xây dựng Widget Đánh giá Danh tiếng & Phản hồi Quản lý trên index    |
+-------------------------------+-------------------------------------------------------------------------+
| CHẶNG 4: DESKTOP PMS & AUDIT  |  - Nâng cấp ReviewManagementForm trên Desktop: Xem chi tiết & Gõ Reply  |
|                               |  - Bổ sung Background Worker polling đơn dịch vụ phòng mới cho Lễ tân   |
|                               |  - Kiểm thử hồi quy toàn diện 9/9 Desktop tests & 37/37 Web tests       |
+---------------------------------------------------------------------------------------------------------+
```

### Chặng 1: Nâng Cấp Schema Dữ Liệu & Seed Data (Database Migration)
- **Mục tiêu:** Tạo bảng mới `customer_notifications`, `service_orders`, cập nhật cột cho `promotions`, `reviews`.
- **Thực thi:**
  - Chạy tệp script `database/migrations/v3_2_phase2_1_schema.sql` vào máy ảo MariaDB `192.168.56.102:3306`.
  - Nạp các mã ưu đãi chuẩn mực: `AURELIA_DINE15`, `WELCOME_SPA20`, `MEMORIAL_ROOM25`.
  - Nạp 7 món ăn và dịch vụ phòng mẫu chuẩn 5 sao.
- **Tiêu chí nghiệm thu:** Cấu trúc bảng mở rộng không làm ảnh hưởng đến các dữ liệu booking, folio cũ.

### Chặng 2: Hiện Thực Backend Handlers, Business Rules & Hạch Toán Folio
- **Mục tiêu:** Xây dựng logic nghiệp vụ Java tại backend native `HttpServer`.
- **Thực thi:**
  - Viết `NotificationController.java`: `GET /api/v1/notifications`, `PUT /api/v1/notifications/{id}/read`.
  - Viết `ServiceOrderController.java`: `POST /api/v1/service-orders`, `GET /api/v1/service-orders/active`, `PUT /api/v1/service-orders/{id}/status`.
  - Tích hợp ghi nợ tự động vào `FolioService`: tự động sinh bản ghi `folio_transactions` với `charge_type = 'service_charge'`.
  - Cập nhật `ReviewController.java`: Mở endpoint công khai `GET /api/v1/reviews/public`, kiểm soát điều kiện đã check-out, chống review trùng lặp và cộng 200 điểm thưởng transactional.
  - Cập nhật `VoucherService.java` & `VoucherValidationController.java`: Lọc voucher theo `applicable_scope` (`ROOM`, `FNB`, `SPA`, `ALL`).
  - Đăng ký toàn bộ route mới vào `src/quanlykhachsan/backend/Main.java`.
- **Tiêu chí nghiệm thu:** Chạy unit test API đạt mã HTTP 200/201 đúng đặc tả, kiểm tra IDOR trả về đúng 403 Forbidden.

### Chặng 3: Nâng Cấp Giao Diện Web UI (`index.html` & `portal.html`)
- **Mục tiêu:** Hiện thực trải nghiệm người dùng đẳng cấp, giải quyết dứt điểm các lỗi điều hướng và tương tác tab.
- **Thực thi:**
  - Sửa `web-ui/index.html` & `app.js`: Ẩn nút "Đăng ký" trên Header; sửa nút "Đăng nhập" chuyển hướng sang `portal.html`; đồng bộ trạng thái đăng nhập hiển thị tên khách và nút Cổng thành viên.
  - Tái thiết kế `web-ui/portal.html` & `portal-app.js`: Tông màu Dark Navy Blue + Huy hiệu Vương miện Khiên Hoàng gia; sửa lỗi click chuyển đổi tab Đăng nhập / Đăng ký; gắn nút tròn "X" đóng portal về trang chủ.
  - Phát triển giao diện In-Stay Concierge: Bảng chọn món ăn, đồ uống, đặt lịch spa và dịch vụ phòng.
  - Phát triển biểu mẫu Review-to-Reward: Đánh giá 4 tiêu chí bằng sao vàng, gửi nhận xét và nhận ngay 200 điểm.
  - Xây dựng Widget Đánh giá Danh tiếng trên `index.html`: Thanh điểm trung bình 4 tiêu chí và danh sách nhận xét có phản hồi của Quản lý.
  - Đồng bộ 100% mã nguồn giữa `web-ui/` và `web-ui/public/`.
- **Tiêu chí nghiệm thu:** Kiểm tra giao diện trên trình duyệt không còn lỗi console, chuyển đổi form đăng ký mượt mà, đặt dịch vụ và gửi đánh giá thành công.

### Chặng 4: Tích Hợp Quản Lý Desktop PMS & Kiểm Thử Hồi Quy Toàn Diện
- **Mục tiêu:** Cung cấp công cụ quản trị cho Lễ tân / Quản lý và đảm bảo an toàn tuyệt đối cho toàn bộ hệ thống.
- **Thực thi:**
  - Nâng cấp `src/quanlykhachsan/frontend/view/staff/ReviewManagementForm.java`: Bổ sung cột điểm 4 tiêu chí và nút "Phản hồi công khai" (`management_response`).
  - Xây dựng luồng Polling ngầm `ServiceOrderAlertWorker` trên Desktop Swing để lễ tân nhận thông báo khi có đơn phòng mới.
  - Chạy bộ kiểm thử tự động toàn diện:
    - Desktop Swing Tests: `mvn test -Dtest="DesktopServerConnectionAndThemeTest,DesktopLoginAuthAndClockSkewTest"`.
    - Web UI Tests: `node --test web-ui/tests/*.test.mjs`.
- **Tiêu chí nghiệm thu:** Toàn bộ test suite đạt tỷ lệ pass 100%, không phát sinh lỗi hồi quy (zero regressions).

---

## 7. KẾ HOẠCH HOÀN TÁC & DỰ PHÒNG THẢM HỌA (ROLLBACK & SAFETY PLAN)

Trong trường hợp phát sinh sự cố nghiêm trọng trong quá trình triển khai Phase 2.1, quy trình hoàn tác khẩn cấp sẽ được kích hoạt theo các bước sau:

### 7.1. Kịch Bản Hoàn Tác Cơ Sở Dữ Liệu (`v3_2_phase2_1_rollback.sql`)
```sql
USE hotel_prod_db;

-- 1. Xóa các bảng mới tạo (chỉ ảnh hưởng phân hệ mới, không ảnh hưởng dữ liệu đặt phòng cũ)
DROP TABLE IF EXISTS service_orders;
DROP TABLE IF EXISTS customer_notifications;

-- 2. Hủy các cột mở rộng trên bảng reviews (nếu cần thiết hoàn nguyên)
-- Lưu ý: Dữ liệu review cũ của khách vẫn được bảo toàn nguyên vẹn
ALTER TABLE reviews
    DROP FOREIGN KEY IF EXISTS fk_reviews_booking,
    DROP FOREIGN KEY IF EXISTS fk_reviews_responder,
    DROP INDEX IF EXISTS uq_booking_review,
    DROP COLUMN IF EXISTS booking_id,
    DROP COLUMN IF EXISTS rating_room,
    DROP COLUMN IF EXISTS rating_fnb,
    DROP COLUMN IF EXISTS rating_service,
    DROP COLUMN IF EXISTS rating_cleanliness,
    DROP COLUMN IF EXISTS rating_overall,
    DROP COLUMN IF EXISTS management_response,
    DROP COLUMN IF EXISTS responded_by_user_id,
    DROP COLUMN IF EXISTS responded_at,
    DROP COLUMN IF EXISTS reward_granted,
    DROP COLUMN IF EXISTS reward_points;

-- 3. Hủy các cột mở rộng trên bảng promotions
ALTER TABLE promotions
    DROP INDEX IF EXISTS uq_promo_code,
    DROP COLUMN IF EXISTS applicable_scope,
    DROP COLUMN IF EXISTS special_occasion,
    DROP COLUMN IF EXISTS valid_after_checkout_days;
```

### 7.2. Cờ Bật Tắt Tính Năng (Feature Flagging Strategy)
- Trên Backend Java: Bố trí cấu hình `app.feature.phase2_1.enabled=true` trong `config.properties`.
- Nếu cờ này chuyển sang `false`:
  - `ServiceOrderController` và `NotificationController` sẽ trả về `503 Service Temporarily Suspended`.
  - `ReviewController` tự động quay về chế độ tương thích cũ (đánh giá phòng theo mã phòng đơn giản).
  - Desktop Swing tắt tiến trình Polling thông báo đơn dịch vụ phòng.

### 7.3. Tiêu Chí Nghiệm Thu Đóng Pha (Go/No-Go Acceptance Criteria)
1. **Kiểm thử Kết nối Máy ảo & Localhost:** Cả hai môi trường (`192.168.56.102` và `localhost:8081`) đều vượt qua các bài test xác thực, không gặp lỗi `ConnectException` hay `HTTP 401`.
2. **Khách hàng Trải nghiệm Liền mạch:** Khách đặt phòng -> Nhận voucher chào mừng -> Gọi món tại phòng thành công -> Trả phòng -> Nhận voucher tri ân -> Đánh giá 5 sao nhận 200 điểm thưởng hiển thị ngay trên Portal.
3. **Quản trị PMS Desktop Chủ động:** Lễ tân nhận tín hiệu đơn dịch vụ phòng tức thì, quản lý phản hồi được đánh giá và xem doanh thu dịch vụ phòng đã hạch toán vào Folio.
4. **Không Thất Thoát Dữ Liệu:** 100% giao dịch Folio, hóa đơn VAT và điểm thưởng hội viên khớp nối tuyệt đối với số liệu kế toán.

---
*Tài liệu này là Cơ sở Kiến trúc Chính thức (Baseline Specification) có hiệu lực bắt buộc áp dụng cho toàn bộ đội ngũ Kỹ sư Phần mềm, Chuyên viên An toàn Thông tin và Kiểm thử viên của dự án Aurelia Grand Palace PMS.*
