# Changelog
All notable changes to the **Aurelia Grand Palace 5★ Property Management System (PMS)** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [3.0.0] - 2026-10-17

### Added
- **Web Concierge & Voucher Wallet Phân Hệ 5:** Tạo mới mô-đun [concierge.js](file:///c:/NetBeansProjects/QL_KhachSan/web-ui/public/js/customer/concierge.js) tích hợp Ví Voucher hiển thị các mã ưu đãi độc quyền (`LUXURY2026`, `VIP5STAR`, `WELCOME50K`, `SPA2026`) với tính năng sao chép một chạm và biểu mẫu yêu cầu dịch vụ phòng trực tuyến 24/7 (Giặt ủi, Spa Hoàng Gia, Minibar, Dọn phòng, Đưa đón sân bay).
- **Express Check-in QR Pass & E-Folio Modal:** Tích hợp modal mã QR nhận phòng nhanh cho đơn đặt phòng xác nhận và modal Hóa đơn điện tử E-Folio chi tiết (tiền phòng, thuế VAT 8%, phí dịch vụ 5%, voucher khấu trừ) trực tiếp trong [bookings.js](file:///c:/NetBeansProjects/QL_KhachSan/web-ui/public/js/customer/bookings.js).
- **Auto-fill Thông Tin Khách Hàng:** Tự động nạp thông tin cá nhân (Họ tên, SĐT, Email, CCCD) vào Bước 2 form đặt phòng 3 bước trên [app.js](file:///c:/NetBeansProjects/QL_KhachSan/web-ui/js/app.js) khi khách hàng đã đăng nhập.
- **REST Endpoints Bổ Sung:** Đăng ký `/api/v1/auth/change-password`, `/api/v1/services` và `/api/v1/service-usage` trong [Main.java](file:///c:/NetBeansProjects/QL_KhachSan/src/quanlykhachsan/backend/Main.java).
- **Khóa Mạng Tĩnh Netplan:** Cấu hình tệp [deploy/01-netplan-static-ip.yaml](file:///c:/NetBeansProjects/QL_KhachSan/deploy/01-netplan-static-ip.yaml) ghim cứng IP Host-Only `192.168.56.102/24` cho máy ảo, đảm bảo kết nối cơ sở dữ liệu MariaDB luôn thông suốt.
- **Lớp Tương Thích DBConnection:** Bổ sung [DBConnection.java](file:///c:/NetBeansProjects/QL_KhachSan/src/quanlykhachsan/backend/utils/DBConnection.java) và [DBConnection.java](file:///c:/NetBeansProjects/QL_KhachSan/src/quanlykhachsan/database/DBConnection.java) tự động nạp cấu hình từ `config.properties` và trích xuất IP máy chủ từ `server.url`.
- **Thư Viện Giao Diện Desktop:** Bổ sung `flatlaf-extras` và `miglayout-swing` vào [pom.xml](file:///c:/NetBeansProjects/QL_KhachSan/pom.xml).
- **Tài Liệu Baseline & Nghiệm Thu:**
  - [DEVELOPER_PHASE_3_AUDIT_REPORT.md](file:///c:/NetBeansProjects/QL_KhachSan/docs/DEVELOPER_PHASE_3_AUDIT_REPORT.md)
  - [SYSTEM_GAP_ANALYSIS_AND_ROADMAP.md](file:///c:/NetBeansProjects/QL_KhachSan/docs/SYSTEM_GAP_ANALYSIS_AND_ROADMAP.md)
  - [PHASE_3_GO_NOGO_ACCEPTANCE.md](file:///c:/NetBeansProjects/QL_KhachSan/docs/PHASE_3_GO_NOGO_ACCEPTANCE.md)
  - [PHASE_4_AND_BEYOND_BASELINE.md](file:///c:/NetBeansProjects/QL_KhachSan/docs/PHASE_4_AND_BEYOND_BASELINE.md)

### Fixed
- **Triệt Tiêu Đóng Băng Nút Bấm UI (Button Freezing):** Bọc toàn bộ các sự kiện gửi yêu cầu (Đăng nhập, Đăng ký, Cập nhật thông tin `#profile-form`, Đổi mật khẩu `#password-form`, Gửi yêu cầu dịch vụ `#concierge-form`, Hủy đặt phòng) trong khối `try...catch...finally`, đảm bảo nút bấm luôn được mở khóa và phản hồi rõ ràng ngay cả khi gặp sự cố mạng hoặc lỗi máy chủ.
- **Sửa Lỗi Màn Hình Đen trong portal.html:** Cấu hình Tailwind CSS an toàn chống lỗi `Uncaught ReferenceError: tailwind is not defined` và nạp module script `portal-app.js` chuẩn xác.
- **Hủy Đặt Phòng Linh Hoạt & An Toàn:** Bổ sung các bí danh `PUT /api/v1/bookings/{id}/cancel` và `POST /api/v1/bookings/{id}/cancel` bên cạnh `DELETE /api/v1/bookings/{id}` trong [BookingController.java](file:///c:/NetBeansProjects/QL_KhachSan/src/quanlykhachsan/backend/booking/BookingController.java), kiểm soát chỉ cho phép hủy khi phòng ở trạng thái `pending`.
- **Đồng Bộ Nhị Phân Web UI:** Khắc phục triệt để sai lệch nhị phân giữa `web-ui/` và `web-ui/public/`, kiểm tra qua lệnh `fc /b` đạt kết quả 100% đồng nhất (Zero discrepancies).
- **Mở Port MariaDB Container:** Cập nhật [docker-compose.yml](file:///c:/NetBeansProjects/QL_KhachSan/docker-compose.yml) từ `expose: 3306` sang `ports: - "3306:3306"` cho phép máy trạm Desktop truy cập trực tiếp cơ sở dữ liệu trên máy ảo.

### Changed
- **Chuẩn Hóa Phản Hồi Lỗi Theo RFC 7807 (Problem Details):** Cập nhật [GlobalExceptionHandler.java](file:///c:/NetBeansProjects/QL_KhachSan/src/quanlykhachsan/backend/utils/GlobalExceptionHandler.java) và [ApiResponseUtil.java](file:///c:/NetBeansProjects/QL_KhachSan/src/quanlykhachsan/backend/utils/ApiResponseUtil.java) bổ sung đầy đủ các trường `type`, `title`, `status`, `detail`, `instance` theo chuẩn RFC 7807, đồng thời giữ nguyên các trường `error_code`, `message`, `timestamp` để tương thích ngược 100%.
- **Nâng Cấp Giao Diện Desktop FlatLaf Dark Luxury:** Chuẩn hóa toàn diện [UIThemeTokens.java](file:///c:/NetBeansProjects/QL_KhachSan/src/quanlykhachsan/frontend/utils/UIThemeTokens.java), [ThemeManager.java](file:///c:/NetBeansProjects/QL_KhachSan/src/quanlykhachsan/frontend/utils/ThemeManager.java), và [Quanlykhachsan.java](file:///c:/NetBeansProjects/QL_KhachSan/src/quanlykhachsan/Quanlykhachsan.java) với chiều cao dòng bảng `Table.rowHeight = 36`, bo góc `Button.arc = 12`, `Component.arc = 10`, `TextComponent.arc = 10`, `ProgressBar.arc = 10`, bảng màu Slate Dark (`#0F172A`), Panels (`#1E293B`), Metallic Gold (`#F59E0B`), Light Text (`#F8FAFC`).
- **Chống Lưu Đệm Tĩnh (Cache-Busting):** Thêm header `Cache-Control: no-cache, must-revalidate` trong [nginx.conf](file:///c:/NetBeansProjects/QL_KhachSan/nginx.conf) và [deploy/nginx.prod.conf](file:///c:/NetBeansProjects/QL_KhachSan/deploy/nginx.prod.conf), gắn tham số `?v=3.5.0` cho tất cả các thẻ `<link>` và `<script>`.
- **Bộ Kiểm Thử Web UI:** Mở rộng [portal.test.mjs](file:///c:/NetBeansProjects/QL_KhachSan/web-ui/tests/portal.test.mjs) đạt **37/37 tests PASS (100% Pass Rate, 0 failures)**.

### Security
- **Bảo Vệ Đổi Mật Khẩu & Xác Thực:** Yêu cầu xác thực Bearer JWT cho API đổi mật khẩu, kiểm tra mật khẩu hiện tại và áp dụng độ dài tối thiểu 6 ký tự.
- **Ngăn Chặn Rò Rỉ PII:** Tách biệt hoàn toàn luồng dữ liệu khách hàng và nhân viên nội bộ, hạn chế quyền truy cập dữ liệu quản trị tiền sảnh đối với tài khoản khách hàng.
- **Kế Hoạch Xoay Vòng Khóa & Rà Soát Lỗ Hổng:** Thiết lập quy trình định kỳ hàng tháng xoay vòng `JWT_SECRET`, quét bảo mật OWASP ZAP và kiểm tra an ninh mạng container.

---

## [2.0.0] - 2026-10-15
### Added
- Hệ thống thanh toán VietQR động chuẩn NAPAS 247.
- Cơ chế khóa sổ đêm (Night Audit freeze) và báo cáo doanh thu DBR.
- Khóa lạc quan (Optimistic Locking) trên bảng `rooms` với trường `version`.
- Hỗ trợ đăng nhập khách bằng mã OTP qua điện thoại (`/api/v1/auth/guest-otp`).

---

## [1.0.0] - 2026-10-09
### Added
- Khởi tạo kiến trúc nền tảng PMS trên Java Core HttpServer và MySQL/MariaDB.
- 5 endpoints cốt lõi cho quản lý phòng, đặt phòng và phân quyền cơ bản.
- Giao diện lễ tân ban đầu trên Java Swing.
