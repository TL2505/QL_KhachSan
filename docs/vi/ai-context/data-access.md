# KIỂM SOÁT TRUY CẬP DỮ LIỆU & PHÂN QUYỀN (RBAC)

## 1. VAI TRÒ KHÁCH HÀNG (Bên ngoài qua Chatbot/App)
- **ĐỌC:** Hồ sơ cá nhân, Đặt phòng của mình, Hóa đơn (folio) của mình, Tình trạng phòng công khai, Menu dịch vụ, Khuyến mãi đang áp dụng.
- **GHI:** Viết đánh giá của mình, Tạo nháp yêu cầu dịch vụ, Tạo nháp đặt phòng (cần qua luồng check-out để hoàn tất).
- **KHÔNG BAO GIỜ TRUY CẬP:** Thông tin PII của khách khác, P&L của nhân viên, HR, Giá nội bộ, Nhật ký hệ thống (audit logs).
- **THỰC THI:** Middleware từ chối các truy vấn nếu `guest_id != authenticated_token_id`.

## 2. PHẠM VI QUYỀN CỦA NHÂN VIÊN
- **Lễ tân (Front Desk):** Truy cập Đặt phòng, Folio, Hồ sơ, Trạng thái phòng (Đọc/Ghi). Chỉ đọc với Khuyến mãi. Không được truy cập F&B P&L.
- **Buồng phòng (Housekeeping):** Truy cập Trạng thái phòng, Danh sách công việc, Bảo trì (Đọc/Ghi). Không được truy cập Folio hoặc Giá phòng.
- **F&B / POS:** Truy cập Tên khách & Số phòng để tính phí. Không được truy cập toàn bộ hồ sơ CRM.
- **Marketing:** Truy cập Khuyến mãi (Đọc/Ghi), Đánh giá (Đọc tất cả).
- **IT / DevOps:** Truy cập log của AI Gateway, tình trạng hệ thống, tiến độ Night Audit, cấu hình Webhook (Đọc/Ghi). Không được truy cập PII của khách hoặc Folio.
- **Tuân thủ / QA (Compliance / QA):** Truy cập Audit Logs (Đọc), AI Interaction Logs (Đọc), Báo cáo vi phạm (Đọc). Không được chỉnh sửa bất kỳ dữ liệu vận hành nào.
- **Admin/GM:** Toàn quyền (Bao gồm ghi đè, Audit Logs, Night Audit).

## 3. QUYỀN TRUY CẬP KIỂM TOÁN ĐÊM (NIGHT AUDIT)
- **Kích hoạt:** Tự động (Cron lúc 00:00) hoặc thủ công bởi Night Auditor.
- **Hủy bỏ (Abort):** Chỉ Admin/GM (Yêu cầu xác thực 2 bước 2FA).
- **Xem:** Night Auditor, Admin, GM (Dashboard chỉ đọc).

## 4. RANH GIỚI CƠ SỞ DỮ LIỆU VẬT LÝ CHO AI GATEWAY (PHYSICAL DB BOUNDARY)
- Dịch vụ AI Gateway kết nối cơ sở dữ liệu thông qua tài khoản riêng biệt: `ai_reader`.
- **Danh sách bảng được phép truy cập (`GRANT SELECT`):**
  - `rooms`: Kiểm tra trạng thái và tiện ích phòng.
  - `room_types`: Lấy thông tin mô tả loại phòng.
  - `promotions`: Kiểm tra chính sách khuyến mãi đang kích hoạt.
  - `services` (hoặc `hotel_services`): Tra cứu dịch vụ phụ trợ (Spa, Restaurant, Laundry).
- **Ranh giới cấm tuyệt đối (Phòng thủ chiều sâu):**
  - Cơ sở dữ liệu MariaDB từ chối quyền truy cập của `ai_reader` vào các bảng PII và tài chính: `users`, `customers`, `bookings`, `booking_details`, `folios`, `payments`.
  - Mọi câu truy vấn cố tình trích xuất thông tin khách hàng từ AI Gateway sẽ bị MariaDB chặn ngay lập tức với mã lỗi `1142 (42000): SELECT command denied to user`.

## 5. ĐỘ MỚI DỮ LIỆU & CACHE TTL
- **Tình trạng & Trạng thái Phòng:** CHỈ THỜI GIAN THỰC (TTL = 0s) để tránh overbooking.
- **Giá phòng & Khuyến mãi:** TTL tối đa = 5 phút.
- **Hồ sơ khách hàng:** TTL = 1 giờ (Xóa cache khi khách check-in/out).

## 6. TRUY CẬP NHẬT KÝ KIỂM TOÁN (AUDIT LOG)
- Mọi tương tác của AI đều được lưu log (Timestamp, ID User, Hành động AI, Dữ liệu tham chiếu).
- Quyền truy cập bị giới hạn ở role Admin & Tuân thủ (Compliance).
- Thời gian lưu trữ: 6 tháng (tuân thủ GDPR/PDPA).

