# KẾ HOẠCH TRIỂN KHAI KỸ THUẬT & CƠ CẤU PHÂN RÃ CÔNG VIỆC (WBS) — GIAI ĐOẠN 2
**Dự án:** Hệ thống Quản trị Khách sạn 5★ & Nền tảng AI (`QL_KhachSan` — 200 Phòng, TP. Hồ Chí Minh, Việt Nam)  
**Quy mô đội ngũ:** 4 Kỹ sư (Dev A, B, C, D) | **Thời lượng:** 4 Tuần (Phase 2: Tuần 5–8)  
**Tiêu chuẩn kiến trúc:** Pure Java 17 (`com.sun.net.httpserver`), HikariCP 5.1.0 (`DatabaseConnection.java`), MariaDB 11 (Mạng Docker cô lập), Java Swing + FlatLaf 3.4.1, Pure Web Booking Engine (Vanilla HTML5/JS/CSS thuần, 0 NPM).

---

## I. TỔNG QUAN KIẾN TRÚC & RÀNG BUỘC PHẠM VI GIAI ĐOẠN 2

```
+---------------------------------------------------------------------------------------------------+
|                                  MÁY CHỦ VẬT LÝ (UBUNTU 22.04 LTS)                                |
|                                                                                                   |
|  [Máy trạm Nhân viên Desktop]                 [Cổng Web Đặt phòng Khách hàng (Mobile/Desktop)]    |
|   Java Swing + FlatLaf 3.4.1 (JDK 17)          HTML5 / ES6+ / CSS3 thuần túy (Không dùng NPM)     |
|   - Sơ đồ phòng 5xN (80x60px), Bảng 24px       - Booking Engine tương thích, Xác thực OTP Khách   |
|   - Wizard Khách vãng lai, Hộp thoại VietQR    - Hiển thị VietQR động, Báo giá Voucher thời gian  |
|   - Độ trễ EDT < 16ms qua SwingWorker          - Tải trực tiếp tài nguyên tĩnh từ Java cổng :8081 |
|           |                                           |                                           |
|           | HTTP/1.1 + Bearer JWT + Idempotency-Key   | HTTP/1.1 + CORS Preflight (OPTIONS 204)   |
|           v                                           v                                           |
|  +---------------------------------------------------------------------------------------------+  |
|  | DOCKER INTERNAL BRIDGE NETWORK: hotel_network (172.28.0.0/16)                               |  |
|  |                                                                                             |  |
|  |  +---------------------------------------------------------------------------------------+  |  |
|  |  | PMS Backend Core (app :8081)                                                          |  |  |
|  |  | - Native com.sun.net.httpserver (Executors.newFixedThreadPool(20))                     |  |  |
|  |  | - StaticFileHandler: Phục vụ web-ui/public/ tại /booking/* và / kèm MIME type          |  |  |
|  |  | - CorsFilter: Tiền kiểm OPTIONS 204 (Access-Control-Allow-Headers: Idempotency-Key...) |  |  |
|  |  | - JwtFilter: Danh sách trắng PUBLIC_PATHS (/auth/*, /rooms/availability, /pricing...)  |  |  |
|  |  | - Động cơ VietQR: EMVCo Napas 247 Dynamic QR + Thuật toán kiểm tra CRC16-CCITT Kermit   |  |  |
|  |  | - Bank Webhook Controller: Xác thực chữ ký điện tử HMAC-SHA256 (X-Signature)           |  |  |
|  |  | - Máy trạng thái Buồng phòng & Chặn quyền RBAC (Cấm Lễ tân đổi bẩn -> sạch)           |  |  |
|  |  | - Động cơ Giá linh hoạt: Hệ số BAR theo công suất phòng (<50%: 1.0x -> >85%: 1.5x)    |  |  |
|  |  | - Xuất Hóa đơn VAT Điện tử: iTextPDF chuẩn Thông tư 78/2021/TT-BTC nhúng font Unicode |  |  |
|  |  +-------------------------------------------+-------------------------------------------+  |  |
|  |                                              | Kết nối HikariCP JDBC Pool                   |  |
|  |                                              v (Pool: 20 kết nối, 50 khi tải cao)           |  |
|  |  +---------------------------------------------------------------------------------------+  |  |
|  |  | MariaDB 11 Database Engine (db :3306 - Cách ly vật lý, KHÔNG mở cổng ra Host/LAN)     |  |  |
|  |  | - Khóa lạc quan Optimistic Locking trên rooms.version qua lệnh nguyên tử CAS           |  |  |
|  |  | - Chuẩn hóa Folio ID: id (BIGINT PK) phân tách với code (VARCHAR UNIQUE, FOLIO-...)   |  |  |
|  |  | - Bất biến Số dư Kế toán: CHECK (balance >= 0.00); tiền thừa -> credit_adjustment     |  |  |
|  |  | - Khử trùng lặp Webhook Ngân hàng: Bảng bank_webhook_logs (bank_trans_id UNIQUE)       |  |  |
|  |  +---------------------------------------------------------------------------------------+  |  |
|  +---------------------------------------------------------------------------------------------+  |
+---------------------------------------------------------------------------------------------------+
```

---

## II. PHÂN RÃ CÔNG VIỆC CHI TIẾT TỪNG TUẦN (TUẦN 5 – 8)

---

### TUẦN 5: CỔNG ĐẶT PHÒNG KHÁCH HÀNG, ĐỘNG CƠ BOOKING TRỰC TUYẾN & ĐỒNG THỜI VÃNG LAI

#### Dev A — Backend API & Hạ tầng Cốt lõi
* **TASK-A5.1: Xử lý Tệp Tĩnh (StaticFileHandler) cho Web Booking Engine**
  * **Module:** Hạ tầng HTTP & Phục vụ Tài nguyên Tĩnh
  * **Người thực hiện:** Dev A
  * **Phụ thuộc (DAG):** Không
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/utils/StaticFileHandler.java`, `src/quanlykhachsan/backend/Main.java`.
  * **Định nghĩa Hoàn thành (DoD):** Cài đặt `HttpHandler` xử lý định tuyến cho các yêu cầu tại `/booking/*` và tài nguyên tĩnh trong thư mục `web-ui/public/`. Phân giải chính xác các kiểu MIME chuẩn (`.html` -> `text/html; charset=UTF-8`, `.css` -> `text/css`, `.js` -> `application/javascript`, `.png` -> `image/png`, `.svg` -> `image/svg+xml`). Chặn đứng tấn công duyệt đường dẫn (Path Traversal, `..` trả về `403 Forbidden`). Xử lý tài nguyên không tồn tại trả về `404 Not Found`.
  * **Phương thức xác minh:** `curl -I http://localhost:8081/booking/index.html` trả về `HTTP/1.1 200 OK` với `Content-Type: text/html; charset=UTF-8`. `curl -I http://localhost:8081/booking/../../etc/passwd` trả về `HTTP/1.1 403 Forbidden`.

* **TASK-A5.2: Cấu hình Tiền kiểm CORS & Bộ lọc Danh sách Trắng Whitelist**
  * **Module:** Bộ lọc Bảo mật & Gateway
  * **Người thực hiện:** Dev A
  * **Phụ thuộc (DAG):** Không
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/utils/CorsFilter.java`, `src/quanlykhachsan/backend/utils/JwtFilter.java`.
  * **Định nghĩa Hoàn thành (DoD):** `CorsFilter` đánh chặn yêu cầu `OPTIONS`, lập tức phản hồi HTTP `204 No Content` với các tiêu đề: `Access-Control-Allow-Origin: *`, `Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS`, `Access-Control-Allow-Headers: Content-Type, Authorization, Idempotency-Key, X-Signature`. `JwtFilter` bổ sung danh sách trắng `PUBLIC_PATHS = {"/api/v1/auth/login", "/api/v1/auth/guest-otp", "/api/v1/rooms/availability", "/api/v1/pricing/quote", "/api/v1/bookings/public", "/booking"}` cho phép khách chưa đăng nhập truy cập mà không bị lỗi 401.
  * **Phương thức xác minh:** `curl -X OPTIONS http://localhost:8081/api/v1/bookings/public -H "Access-Control-Request-Headers: Idempotency-Key" -I` trả về `204 No Content`. `curl http://localhost:8081/api/v1/rooms/availability` chạy thành công không cần token JWT.

* **TASK-A5.3: Controller Phát hành & Xác thực Mã OTP Khách hàng**
  * **Module:** Xác thực Người dùng Khách
  * **Người thực hiện:** Dev A
  * **Phụ thuộc (DAG):** TASK-B5.1
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/auth/GuestAuthController.java`, `src/quanlykhachsan/backend/auth/GuestAuthService.java`.
  * **Định nghĩa Hoàn thành (DoD):** Mở 2 endpoint `POST /api/v1/auth/guest-otp/request` và `POST /api/v1/auth/guest-otp/verify`. Kiểm tra định dạng số điện thoại/email; tạo mã số OTP 6 chữ số ngẫu nhiên an toàn mật mã học với hạn sống 5 phút (TTL). Quá trình xác minh đối soát mã trong CSDL, cấp token JJWT dành riêng cho khách (`role: "guest"`, hạn 24 giờ) chứa `phone` và `customer_id`.
  * **Phương thức xác minh:** Chạy kiểm thử tự động `mvn test -Dtest=GuestAuthIntegrationTest`. Xác thực mã hết hạn trả về `400 Bad Request`, mã hợp lệ trả về `200 OK` kèm chuỗi JJWT.

* **TASK-A5.4: Controller Tiếp nhận Đặt phòng Công cộng Trực tuyến**
  * **Module:** API Động cơ Đặt phòng
  * **Người thực hiện:** Dev A
  * **Phụ thuộc (DAG):** TASK-A5.2, TASK-B5.2
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/booking/PublicBookingController.java`.
  * **Định nghĩa Hoàn thành (DoD):** Mở endpoint `POST /api/v1/bookings/public`. Bắt buộc có header `Idempotency-Key` (UUIDv4). Xác thực JWT khách hoặc dữ liệu liên hệ trong body. Ủy quyền gán phòng cho `RoomAllocationService` kèm `room_version` kỳ vọng. Khi thành công trả về `201 Created` kèm mã đặt phòng, số phòng gán và ID Folio khởi tạo. Nếu xung đột phiên bản trả về `409 Conflict`.
  * **Phương thức xác minh:** `curl -X POST http://localhost:8081/api/v1/bookings/public -H "Idempotency-Key: $(uuidgen)" -H "Content-Type: application/json" -d '{"customer_name":"Nguyen Van A","phone":"0901234567","room_id":101,"room_version":0,"check_in_date":"2026-11-01 14:00:00","check_out_date":"2026-11-03 12:00:00"}'` nhận mã `201 Created`.

#### Dev B — Dữ liệu, Tài chính & Nghiệp vụ Cốt lõi
* **TASK-B5.1: Nâng cấp Lược đồ CSDL cho OTP Khách & Kênh Đặt phòng Web**
  * **Module:** CSDL & Migration
  * **Người thực hiện:** Dev B
  * **Phụ thuộc (DAG):** Không
  * **Tệp mục tiêu:** `database/migrations/v2_0_week5_public_portal.sql`, `src/quanlykhachsan/backend/utils/UpdateDbUtility.java`.
  * **Định nghĩa Hoàn thành (DoD):** Tạo bảng `guest_otps` (`id BIGINT AUTO_INCREMENT PRIMARY KEY, phone VARCHAR(20) NOT NULL, email VARCHAR(100), otp_code VARCHAR(6) NOT NULL, expires_at DATETIME NOT NULL, consumed_at DATETIME NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, INDEX idx_otp_phone_code (phone, otp_code, expires_at))`. Mở rộng kiểu ENUM `bookings.customer_type` thêm giá trị `'Web'`. Thêm script chạy migration vào `UpdateDbUtility.java`.
  * **Phương thức xác minh:** Chạy `mvn test -Dtest=DatabaseMigrationTest`. Kiểm tra MariaDB; bảng `guest_otps` tồn tại và lệnh `SHOW COLUMNS FROM bookings LIKE 'customer_type'` có chứa giá trị `'Web'`.

* **TASK-B5.2: Cơ chế Phân bổ Phòng Đa kênh & Chống Xung đột Đồng thời**
  * **Module:** Nghiệp vụ Tồn kho Phòng
  * **Người thực hiện:** Dev B
  * **Phụ thuộc (DAG):** TASK-B5.1
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/room/RoomAllocationService.java`.
  * **Định nghĩa Hoàn thành (DoD):** Cài đặt phương thức cập nhật nguyên tử theo mẫu so-và-đổi (CAS):
    ```sql
    UPDATE rooms SET status = 'reserved', version = version + 1 
    WHERE id = ? AND version = ? AND status = 'vacant_clean';
    ```
    Nếu `rowsAffected == 0`, truy vấn lại trạng thái phòng: Nếu trạng thái không còn là `vacant_clean`, ném lỗi `RoomUnavailableException` (HTTP 409); nếu phiên bản bị lệch, ném lỗi `OptimisticLockException` (HTTP 409). Bảo đảm an toàn luồng tuyệt đối khi Web và Lễ tân tranh chấp cùng một phòng.
  * **Phương thức xác minh:** Chạy unit test `mvn test -Dtest=RoomAllocationServiceTest`. Kiểm tra lệnh CAS tăng phiên bản phòng đúng 1 đơn vị và từ chối phiên bản cũ.

* **TASK-B5.3: Tự động Thu nạp Hồ sơ Khách & Khử Trùng lặp CRM**
  * **Module:** Nghiệp vụ Quản trị Khách hàng CRM
  * **Người thực hiện:** Dev B
  * **Phụ thuộc (DAG):** TASK-B5.1
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/customer/GuestIngestionService.java`, `src/quanlykhachsan/backend/customer/CustomerDao.java`.
  * **Định nghĩa Hoàn thành (DoD):** Tiếp nhận danh tính khách từ đơn đặt phòng Web. Kiểm tra số điện thoại chuẩn hóa (`090...` hoặc `+8490...`) trong bảng `customers`. Nếu đã tồn tại, tự động gán `customer_id` cũ; nếu mới, tạo bản ghi với `loyalty_level='Member'` và `total_loyalty_points=0`. Khóa giao dịch tránh tạo trùng lặp bản ghi khách hàng khi có nhiều yêu cầu đặt phòng đồng thời cùng số điện thoại.
  * **Phương thức xác minh:** Chạy `mvn test -Dtest=GuestIngestionDeduplicationTest`. Bắn 10 yêu cầu đặt phòng đồng thời với cùng 1 số điện thoại; xác nhận chỉ có duy nhất 1 bản ghi khách hàng được tạo trong bảng `customers`.

#### Dev C — Giao diện Desktop & Web Đặt phòng
* **TASK-C5.1: Xây dựng Khung Nền tảng Web Booking (HTML5/JS/CSS thuần, 0 NPM)**
  * **Module:** Giao diện Đặt phòng Trực tuyến Web
  * **Người thực hiện:** Dev C
  * **Phụ thuộc (DAG):** Không (Phát triển độc lập qua Mock API TASK-D5.1)
  * **Tệp mục tiêu:** `web-ui/public/index.html`, `web-ui/public/css/booking.css`, `web-ui/public/js/booking-core.js`.
  * **Định nghĩa Hoàn thành (DoD):** Phát triển 100% bằng JavaScript ES6+, HTML5 và CSS3 thuần (không cài NPM, không cần build tool). Giao diện đáp ứng mượt mà (từ màn hình 375px mobile đến 1920px desktop). Bộ chọn ngày nhận/trả phòng, bộ chọn hạng phòng, xem trước giá phòng thời gian thực. Giao tiếp với `/api/v1/rooms/availability` qua `fetch()` nội tại của trình duyệt.
  * **Phương thức xác minh:** Mở `http://localhost:8081/booking/index.html` trên trình duyệt Chrome và chế độ giả lập điện thoại di động (375x667). 0 lỗi console; hiển thị danh sách phòng dưới 200ms.

* **TASK-C5.2: Hộp thoại Nhập OTP Khách & Luồng Hoàn tất Đặt phòng Web**
  * **Module:** Giao diện Đặt phòng Trực tuyến Web
  * **Người thực hiện:** Dev C
  * **Phụ thuộc (DAG):** TASK-C5.1, TASK-D5.1
  * **Tệp mục tiêu:** `web-ui/public/js/guest-otp.js`, `web-ui/public/js/booking-api.js`.
  * **Định nghĩa Hoàn thành (DoD):** Hộp thoại modal nhập OTP: 6 ô số tự động nhảy con trỏ khi nhập và lùi khi bấm xóa, đồng hồ đếm ngược 60 giây gửi lại mã. Tự động sinh khóa `Idempotency-Key` chuẩn UUIDv4 lưu trong `sessionStorage`. Gửi đơn đặt phòng tới `POST /api/v1/bookings/public`, lưu trữ token JWT trong `sessionStorage` sau khi xác thực thành công.
  * **Phương thức xác minh:** Kiểm thử trên Chrome DevTools: Nhập số điện thoại `0901234567`, nhập mã mock `123456`, bấm xác nhận; kiểm tra tab Network xác nhận header `Idempotency-Key: <uuid>` được gắn kèm trong request gửi đi.

* **TASK-C5.3: Wizard Check-in Khách Vãng lai Nhanh trên Desktop Swing**
  * **Module:** Giao diện Desktop Lễ tân
  * **Người thực hiện:** Dev C
  * **Phụ thuộc (DAG):** Không
  * **Tệp mục tiêu:** `src/quanlykhachsan/frontend/view/WalkInWizardDialog.java`, `src/quanlykhachsan/frontend/view/StaffForm.java`.
  * **Định nghĩa Hoàn thành (DoD):** Phím tắt `Ctrl+W` mở cửa sổ Check-in khách vãng lai (Walk-in Wizard). Tự động lọc danh sách phòng sạch trống (`vacant_clean`). Thu thập thông tin Họ tên, CCCD/Hộ chiếu, Số điện thoại. Thực hiện tạo đơn đặt phòng và gán nhận phòng trong một thao tác duy nhất qua `SwingWorker` (độ trễ EDT strictly < 16ms). Lập tức đổi màu ô phòng trên sơ đồ sang Xanh dương (`occupied_clean`).
  * **Phương thức xác minh:** Chạy `MainUI.java`. Bấm `Ctrl+W`, điền form, chọn phòng và bấm xác nhận. Ô phòng 101 đổi từ màu Xanh lá (`#4CAF50`) sang Xanh dương (`#2196F3`) trong thời gian dưới 200ms.

#### Dev D — DevOps, Kiểm thử Tự động & Phân hệ AI
* **TASK-D5.1: Mở rộng Mock API Server cho các Endpoint Tuần 5**
  * **Module:** Test Harness & Máy chủ Giả lập
  * **Người thực hiện:** Dev D
  * **Phụ thuộc (DAG):** Không
  * **Tệp mục tiêu:** `src/test/java/quanlykhachsan/stubs/MockApiServer.java`, `src/test/resources/mock-server/mock-guest-otp.json`, `src/test/resources/mock-server/mock-public-booking.json`.
  * **Định nghĩa Hoàn thành (DoD):** Nâng cấp máy chủ Mock API (cổng 8089) trước 12:00 ngày đầu tuần. Giả lập: `POST /api/v1/auth/guest-otp/request` (trả về 200 `{status: "OTP_SENT"}`), `POST /api/v1/auth/guest-otp/verify` (trả về 200 `{token: "mock-guest-jwt"}`), `POST /api/v1/bookings/public` (trả về 201 `{booking_id: 999, room_number: "101"}`).
  * **Phương thức xác minh:** `curl -X POST http://localhost:8089/api/v1/auth/guest-otp/verify -H "Content-Type: application/json" -d '{"phone":"0901234567","otp":"123456"}'` trả về đúng định dạng JSON Envelope.

* **TASK-D5.2: Tự động hóa Kiểm thử Đồng thời: Kịch bản 9 (Web vs Vãng lai Tranh chấp Phòng)**
  * **Module:** Kiểm thử Tự động Xung đột Tải
  * **Người thực hiện:** Dev D
  * **Phụ thuộc (DAG):** TASK-A5.4, TASK-B5.2
  * **Tệp mục tiêu:** `src/test/java/quanlykhachsan/scenarios/Scenario9WebVsWalkInRaceTest.java`.
  * **Định nghĩa Hoàn thành (DoD):** Kiểm thử JUnit 5 sử dụng `CountDownLatch(20)`: 10 luồng giả lập Web Booking và 10 luồng giả lập Lễ tân vãng lai bắn đồng thời yêu cầu đặt phòng vào đúng 1 phòng trống duy nhất còn lại (Phòng 205, phiên bản 0) tại cùng một phần nghìn giây.
  * **Phương thức xác minh:** Thực thi `mvn test -Dtest=Scenario9WebVsWalkInRaceTest`. Khẳng định: Đúng 1 luồng nhận mã `200/201`; 19 luồng còn lại nhận `409 Conflict`. Cột `version` của phòng 205 trong MariaDB tăng đúng 1 lần. Chỉ có duy nhất 1 booking được ghi nhận.

* **TASK-D5.3: Tự động hóa Kiểm thử Tiền kiểm CORS & Phục vụ Tài nguyên Tĩnh**
  * **Module:** Kiểm thử Tự động An ninh Web
  * **Người thực hiện:** Dev D
  * **Phụ thuộc (DAG):** TASK-A5.1, TASK-A5.2
  * **Tệp mục tiêu:** `src/test/java/quanlykhachsan/security/CorsAndStaticServingTest.java`.
  * **Định nghĩa Hoàn thành (DoD):** Bộ kiểm thử tự động xác minh:
    1. `OPTIONS /api/v1/bookings/public` trả về 204 kèm `Access-Control-Allow-Headers` có chứa `Idempotency-Key`.
    2. `GET /booking/index.html` trả về 200 kèm nội dung HTML.
    3. Thử nghiệm Path Traversal `GET /booking/../pom.xml` bị chặn với mã 403 Forbidden.
  * **Phương thức xác minh:** Chạy `mvn test -Dtest=CorsAndStaticServingTest`. Toàn bộ 5 assertions đều đạt trong < 1500ms.

---

### TUẦN 6: CỔNG THANH TOÁN VIETQR ĐỘNG & ĐỐI SOÁT BẤT BIẾN SỔ CÁI

#### Dev A — Backend API & Hạ tầng Cốt lõi
* **TASK-A6.1: Controller Tạo Mã Thanh toán VietQR Động**
  * **Module:** Cổng Thanh toán Tài chính
  * **Người thực hiện:** Dev A
  * **Phụ thuộc (DAG):** TASK-B6.2
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/booking/VietQrController.java`.
  * **Định nghĩa Hoàn thành (DoD):** Cung cấp `GET /api/v1/folios/{id}/vietqr`. Tìm Folio theo `id` (hoặc `code`); xác nhận Folio đang mở; lấy số dư cần thanh toán. Gọi `VietQrGenerator` để tạo chuỗi dữ liệu chuẩn EMVCo Napas 247 và ảnh mã QR Base64 (300x300px). Trả về JSON envelope chứa: `qr_code_string`, `qr_image_base64`, `amount` và `reference_code`.
  * **Phương thức xác minh:** `curl http://localhost:8081/api/v1/folios/1/vietqr -H "Authorization: Bearer <token>"` trả về mã 200 kèm chuỗi `qr_code_string` bắt đầu bằng `00020101021238` và dữ liệu ảnh Base64 hợp lệ.

* **TASK-A6.2: Controller Tiếp nhận Webhook Ngân hàng & Xác thực Chữ ký HMAC**
  * **Module:** Cổng Webhook Ngân hàng
  * **Người thực hiện:** Dev A
  * **Phụ thuộc (DAG):** TASK-B6.1, TASK-B6.3
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/booking/BankWebhookController.java`, `src/quanlykhachsan/backend/utils/HmacSignatureUtil.java`.
  * **Định nghĩa Hoàn thành (DoD):** Cung cấp endpoint `POST /api/v1/webhooks/vietqr`. Đọc trực tiếp raw body HTTP. Kiểm tra tiêu đề `X-Signature: hex(HMAC_SHA256(bank_secret, raw_body))`. Nếu chữ ký sai hoặc thiếu, lập tức trả về `401 Unauthorized` mà không truy cập CSDL. Trích xuất payload: `bank_trans_id`, `amount`, `reference_code`, `trans_time`. Chuyển tiếp sang `FolioPaymentService`.
  * **Phương thức xác minh:** Chạy `mvn test -Dtest=BankWebhookControllerTest`. Payload bị sửa chữ ký trả về 401; chữ ký đúng trả về 200 `{ "status": "SETTLED" }`.

* **TASK-A6.3: Endpoint Thăm dò Trạng thái Thanh toán (Fast Polling)**
  * **Module:** API Đối soát Thanh toán
  * **Người thực hiện:** Dev A
  * **Phụ thuộc (DAG):** TASK-B6.1
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/booking/PaymentPollController.java`.
  * **Định nghĩa Hoàn thành (DoD):** Cung cấp `GET /api/v1/folios/{id}/payment-status`. Truy vấn số dư và trạng thái Folio qua chỉ mục khóa chính (<5ms). Trả về `{ "folio_id": 1, "folio_code": "FOLIO-MASTER-101", "balance": 0.00, "status": "settled", "last_payment_at": "..." }`.
  * **Phương thức xác minh:** `curl http://localhost:8081/api/v1/folios/1/payment-status -H "Authorization: Bearer <token>"` trả về JSON phản ánh chính xác trạng thái Folio.

#### Dev B — Dữ liệu, Tài chính & Nghiệp vụ Cốt lõi
* **TASK-B6.1: Nâng cấp Lược đồ: ID Parity của Folio & Ràng buộc Số dư Không Âm**
  * **Module:** Lược đồ Sổ cái Kế toán
  * **Người thực hiện:** Dev B
  * **Phụ thuộc (DAG):** Không
  * **Tệp mục tiêu:** `database/migrations/v2_0_week6_vietqr_accounting.sql`, `src/quanlykhachsan/backend/utils/UpdateDbUtility.java`.
  * **Định nghĩa Hoàn thành (DoD):**
    1. Chuẩn hóa ID Parity: Bổ sung cột `code VARCHAR(64) NOT NULL UNIQUE` vào bảng `folios` (ví dụ: `FOLIO-MASTER-101`, `FOLIO-GUEST-101`), giữ nguyên `id BIGINT AUTO_INCREMENT PRIMARY KEY`.
    2. An toàn Số dư: Thêm ràng buộc `CHECK (balance >= 0.00)` trên MariaDB 11.
    3. Nhật ký Webhook: Tạo bảng `bank_webhook_logs` (`id BIGINT AUTO_INCREMENT PRIMARY KEY, bank_trans_id VARCHAR(100) NOT NULL UNIQUE, folio_id INT NOT NULL, amount DECIMAL(12,2) NOT NULL, signature VARCHAR(128) NOT NULL, status ENUM('PROCESSED', 'REJECTED', 'DUPLICATE') NOT NULL, raw_payload LONGTEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)`.
  * **Phương thức xác minh:** Chạy migration; thử lệnh `INSERT INTO folios (booking_id, folio_type, balance, code) VALUES (1, 'master', -100.00, 'TEST-NEG')`. Xác nhận MariaDB từ chối với lỗi vi phạm ràng buộc CHECK.

* **TASK-B6.2: Thuật toán Dữ liệu EMVCo Napas 247 & Động cơ CRC16-CCITT**
  * **Module:** Tính toán Chuẩn Thanh toán QR
  * **Người thực hiện:** Dev B
  * **Phụ thuộc (DAG):** Không
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/booking/VietQrGenerator.java`, `src/quanlykhachsan/backend/utils/Crc16Ccitt.java`.
  * **Định nghĩa Hoàn thành (DoD):** Xây dựng trình tạo chuỗi QR chuẩn EMVCo bằng Java thuần:
    - Thẻ `00`: Payload Format Indicator ("01")
    - Thẻ `01`: Point of Initiation Method ("12" - Mã QR động)
    - Thẻ `38`: Thông tin Tài khoản Thụ hưởng (Napas GUID `A000000727`, Mã định danh Ngân hàng `970422` [MBBank], Số tài khoản)
    - Thẻ `53`: Đơn vị tiền tệ ("704" - VND)
    - Thẻ `54`: Số tiền giao dịch (Định dạng chính xác không trôi số thập phân)
    - Thẻ `58`: Quốc gia ("VN")
    - Thẻ `62`: Dữ liệu bổ sung (Thẻ con `08`: Mã tham chiếu Folio)
    - Thẻ `63`: Tính checksum CRC16-CCITT Kermit (đa thức `0x1021`, giá trị khởi tạo `0xFFFF`, viết hoa 4 ký tự hex).
  * **Phương thức xác minh:** Chạy unit test `mvn test -Dtest=VietQrGeneratorTest` đối chiếu chuỗi sinh ra với mã kiểm tra tiêu chuẩn của Napas.

* **TASK-B6.3: Động cơ Quyết toán Idempotent & Chống Âm Tiền Sổ cái**
  * **Module:** Động cơ Hạch toán Folio
  * **Người thực hiện:** Dev B
  * **Phụ thuộc (DAG):** TASK-B6.1
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/booking/FolioPaymentService.java`.
  * **Định nghĩa Hoàn thành (DoD):** Xử lý thanh toán trong giao dịch nguyên tử MariaDB (`setAutoCommit(false)`):
    1. Khóa dòng bản ghi: `SELECT balance, status FROM folios WHERE id = ? FOR UPDATE`.
    2. Kiểm tra `bank_trans_id` trong `bank_webhook_logs`: Nếu đã có, trả về ngay trạng thái quyết toán đã lưu (Idempotency hit).
    3. Xử lý trường hợp chuyển dư tiền: Nếu số tiền thanh toán `P > số dư hiện tại B`:
       - Ghi nhận bút toán thanh toán: `charge_type = 'payment'`, `amount = B`.
       - Ghi nhận bút toán điều chỉnh thừa: `charge_type = 'credit_adjustment'`, `amount = (P - B)`, `description = 'Excess overpayment credit'`.
       - Đặt số dư `balance = 0.00` và trạng thái `status = 'settled'` (Số dư TUYỆT ĐỐI không âm).
    4. Commit giao dịch và ghi nhận nhật ký vào `bank_webhook_logs`.
  * **Phương thức xác minh:** Thực thi `mvn test -Dtest=FolioPaymentServiceTest`. Kiểm tra 3 trường hợp: Đúng số tiền, thiếu tiền và thanh toán dư 150%; xác nhận `balance >= 0.00` trong 100% trường hợp.

#### Dev C — Giao diện Desktop & Web Đặt phòng
* **TASK-C6.1: Hộp thoại Hiển thị VietQR Động Kèm Đồng hồ Đếm ngược trên Swing**
  * **Module:** Giao diện Thanh toán Desktop
  * **Người thực hiện:** Dev C
  * **Phụ thuộc (DAG):** TASK-D6.1
  * **Tệp mục tiêu:** `src/quanlykhachsan/frontend/view/VietQrPaymentDialog.java`, `src/quanlykhachsan/frontend/view/PaymentForm.java`.
  * **Định nghĩa Hoàn thành (DoD):** Màn hình thanh toán Check-out bổ sung nút "Thanh toán VietQR Napas 247". Bật modal lấy và vẽ ảnh QR 300x300px, hiển thị Ngân hàng, Người thụ hưởng, Số tiền VND và Mã giao dịch. Kèm thanh tiến trình đếm ngược 5 phút. Tự động thăm dò `/api/v1/folios/{id}/payment-status` mỗi 2.000ms qua `SwingWorker` không gây đơ giao diện.
  * **Phương thức xác minh:** Mở hộp thoại VietQR trong Swing. Mã QR hiển thị rõ nét; đồng hồ 5 phút đếm lùi đều đặn mỗi giây và không có cảnh báo giật lag trên EDT.

* **TASK-C6.2: Âm thanh & Thông báo Hoàn tất Quyết toán Tức thì**
  * **Module:** Âm thanh & Thông báo Desktop
  * **Người thực hiện:** Dev C
  * **Phụ thuộc (DAG):** TASK-C6.1
  * **Tệp mục tiêu:** `src/quanlykhachsan/frontend/utils/SoundNotifier.java`, `src/quanlykhachsan/frontend/view/VietQrPaymentDialog.java`.
  * **Định nghĩa Hoàn thành (DoD):** Khi thăm dò phát hiện trạng thái `settled`:
    1. Phát âm thanh chuông xác nhận sang trọng bằng thư viện `javax.sound.sampled`.
    2. Hiển thị thông báo biểu tượng dấu tích xanh lớn: `"Thanh toán thành công! Sổ cái đã cân bằng."`.
    3. Tự động đóng modal sau 1.500ms và cập nhật trạng thái phòng sang `vacant_dirty`.
  * **Phương thức xác minh:** Kích hoạt webhook giả lập; giao diện Swing hiển thị dấu tích xanh và phát chuông báo trong thời gian < 50ms.

* **TASK-C6.3: Màn hình Thanh toán VietQR trên Web Booking Engine**
  * **Module:** Giao diện Đặt phòng Trực tuyến Web
  * **Người thực hiện:** Dev C
  * **Phụ thuộc (DAG):** TASK-D6.1
  * **Tệp mục tiêu:** `web-ui/public/js/payment-qr.js`, `web-ui/public/css/payment.css`.
  * **Định nghĩa Hoàn thành (DoD):** Bước thanh toán tương thích trên Web. Hiển thị mã VietQR động; cung cấp nút "Tải ảnh QR" và "Sao chép số tài khoản". Thăm dò trạng thái qua `fetch()` mỗi 2.5 giây. Khi phát hiện hoàn tất thanh toán, tự động chuyển hướng đến màn hình hóa đơn xác nhận đặt phòng.
  * **Phương thức xác minh:** Hoàn tất đặt phòng trên trình duyệt. Màn hình hiển thị mã QR. Bắn webhook thành công; trình duyệt tự động chuyển trang hóa đơn trong vòng 3 giây.

#### Dev D — DevOps, Kiểm thử Tự động & Phân hệ AI
* **TASK-D6.1: Trình Giả lập Bắn Webhook Ngân hàng trên Mock Server**
  * **Module:** Test Harness & Stubs
  * **Người thực hiện:** Dev D
  * **Phụ thuộc (DAG):** Không
  * **Tệp mục tiêu:** `src/test/java/quanlykhachsan/stubs/BankWebhookSimulator.java`.
  * **Định nghĩa Hoàn thành (DoD):** Triển khai trình giả lập webhook trên cổng 8089 trước 12:00 ngày đầu tuần. Có khả năng tạo các payload có chữ ký HMAC-SHA256 chuẩn hoặc cố tình sai lệch, thiết lập độ trễ mạng và giả lập trùng lặp mã giao dịch.
  * **Phương thức xác minh:** `curl http://localhost:8089/mock/fire-webhook?bank_trans_id=TXN-123&amount=1500000` bắn thành công POST mang chữ ký hợp lệ sang backend.

* **TASK-D6.2: Tự động hóa Kiểm thử: Kịch bản 7 (Phòng thủ Bão Webhook Trùng lặp)**
  * **Module:** Kiểm thử Tự động Ứng suất & Đồng thời
  * **Người thực hiện:** Dev D
  * **Phụ thuộc (DAG):** TASK-A6.2, TASK-B6.3
  * **Tệp mục tiêu:** `src/test/java/quanlykhachsan/scenarios/Scenario7DuplicateWebhookTest.java`.
  * **Định nghĩa Hoàn thành (DoD):** Kiểm thử JUnit 5 kích hoạt 50 luồng bắn đồng thời cùng một `bank_trans_id` ("TXN-DUP-50") cho cùng 1 `folio_id` qua `CountDownLatch(50)`.
  * **Phương thức xác minh:** Chạy `mvn test -Dtest=Scenario7DuplicateWebhookTest`. Khẳng định:
    1. Đúng 1 yêu cầu thực thi ghi sổ và trừ số dư.
    2. 49 yêu cầu nhận phản hồi HTTP 200 xác nhận đã nhận (Idempotent acknowledgement).
    3. Bảng `bank_webhook_logs` ghi nhận đúng 1 dòng `PROCESSED` và 49 dòng `DUPLICATE`.
    4. Tiền trên Folio chỉ được gạch nợ đúng 1 lần.

* **TASK-D6.3: Tự động hóa Kiểm thử Thanh toán Dư & Số dư Không Âm**
  * **Module:** Kiểm thử Tự động Nghiệp vụ Tài chính
  * **Người thực hiện:** Dev D
  * **Phụ thuộc (DAG):** TASK-B6.1, TASK-B6.3
  * **Tệp mục tiêu:** `src/test/java/quanlykhachsan/financial/OverpaymentCreditAdjustmentTest.java`.
  * **Định nghĩa Hoàn thành (DoD):** Tự động tạo Folio có số dư 1.000.000 VND và nhận webhook thanh toán 1.500.000 VND.
  * **Phương thức xác minh:** Chạy `mvn test -Dtest=OverpaymentCreditAdjustmentTest`. Xác nhận:
    1. Số dư Folio cuối cùng là `0.00` (KHÔNG PHẢI là -500.000).
    2. Tạo ra 2 giao dịch: `charge_type='payment'` 1.000.000 VND và `charge_type='credit_adjustment'` 500.000 VND.
    3. Ràng buộc MariaDB CHECK không bao giờ bị kích hoạt lỗi.

---

### TUẦN 7: QUẢN LÝ BUỒNG PHÒNG THỜI GIAN THỰC, BẢO TRÌ & TỰ ĐỘNG HÓA MINIBAR

#### Dev A — Backend API & Hạ tầng Cốt lõi
* **TASK-A7.1: Controller Quy trình Buồng phòng & Cập nhật Trạng thái**
  * **Module:** API Tác nghiệp Buồng phòng
  * **Người thực hiện:** Dev A
  * **Phụ thuộc (DAG):** TASK-B7.2
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/room/HousekeepingController.java`.
  * **Định nghĩa Hoàn thành (DoD):** Mở endpoint `PUT /api/v1/rooms/{id}/housekeeping`. Yêu cầu Bearer JWT. Kiểm tra RBAC: chỉ cho phép role `housekeeping` hoặc `admin`. Từ chối tài khoản có role `front_desk` hoặc `f&b` với mã HTTP `403 Forbidden`. Cập nhật trạng thái phòng (`vacant_dirty` -> `vacant_clean`) và lưu nhật ký mã nhân viên, thời lượng dọn và ghi chú kiểm phòng.
  * **Phương thức xác minh:** Chạy `mvn test -Dtest=HousekeepingControllerSecurityTest`. Token Lễ tân trả về 403; token Buồng phòng trả về 200 OK.

* **TASK-A7.2: Controller Kiểm kê Tiêu thụ Minibar & Ghi nợ Chi phí Phát sinh**
  * **Module:** API F&B và Dịch vụ Phòng
  * **Người thực hiện:** Dev A
  * **Phụ thuộc (DAG):** TASK-B7.3
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/room/MinibarController.java`.
  * **Định nghĩa Hoàn thành (DoD):** Mở endpoint `POST /api/v1/rooms/{id}/minibar`. Bắt buộc có header `Idempotency-Key`. Tiếp nhận mảng vật phẩm tiêu thụ: `[{ "item_id": 1, "quantity": 2 }]`. Xác nhận phòng đang có khách ở (`occupied`); chuyển giao cho `MinibarPostingService` trừ tồn kho vật lý và ghi nợ trực tiếp vào Folio dịch vụ phụ (`FOLIO-GUEST-xxx`).
  * **Phương thức xác minh:** `curl -X POST http://localhost:8081/api/v1/rooms/101/minibar -H "Idempotency-Key: $(uuidgen)" -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d '{"items":[{"item_id":1,"quantity":2}]}'` trả về 201 kèm ID giao dịch dịch vụ vừa tạo.

* **TASK-A7.3: Controller Khóa Phòng Bảo trì OOO/OOS & Ghi đè Quyền Quản lý**
  * **Module:** API Quản trị Bảo trì Phòng
  * **Người thực hiện:** Dev A
  * **Phụ thuộc (DAG):** TASK-B7.2
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/room/MaintenanceController.java`.
  * **Định nghĩa Hoàn thành (DoD):** Mở `PUT /api/v1/rooms/{id}/maintenance`. Cho phép chuyển phòng sang trạng thái hỏng hóc `out_of_order` (OOO) hoặc ngừng hoạt động `out_of_service` (OOS). Nếu phòng đã có khách đặt trước trong khoảng thời gian sửa chữa, bắt buộc phải có `manager_pin` trong header hoặc payload; nếu thiếu lập tức từ chối với `409 Conflict`.
  * **Phương thức xác minh:** `curl -X PUT http://localhost:8081/api/v1/rooms/101/maintenance -H "Authorization: Bearer <token>" -d '{"status":"out_of_order","reason":"AC Leak"}'` trả về 409 nếu không có PIN, và 200 khi có PIN Quản lý hợp lệ.

#### Dev B — Dữ liệu, Tài chính & Nghiệp vụ Cốt lõi
* **TASK-B7.1: Nâng cấp Lược đồ: Danh mục Minibar, Nhật ký Phòng & Theo dõi OOO**
  * **Module:** CSDL Buồng phòng & Tồn kho
  * **Người thực hiện:** Dev B
  * **Phụ thuộc (DAG):** Không
  * **Tệp mục tiêu:** `database/migrations/v2_0_week7_housekeeping_minibar.sql`, `src/quanlykhachsan/backend/utils/UpdateDbUtility.java`.
  * **Định nghĩa Hoàn thành (DoD):**
    1. Tạo bảng `minibar_items` (`id INT AUTO_INCREMENT PRIMARY KEY, room_id INT NOT NULL, name VARCHAR(100) NOT NULL, unit_price DECIMAL(10,2) NOT NULL, standard_stock INT NOT NULL, current_stock INT NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, CONSTRAINT fk_minibar_rooms FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE)`.
    2. Tạo bảng `room_status_audits` (`id BIGINT AUTO_INCREMENT PRIMARY KEY, room_id INT NOT NULL, from_status VARCHAR(30) NOT NULL, to_status VARCHAR(30) NOT NULL, updated_by_user_id INT NOT NULL, reason VARCHAR(255), created_at DATETIME DEFAULT CURRENT_TIMESTAMP)`.
  * **Phương thức xác minh:** Chạy migration; xác nhận bảng được tạo đầy đủ và ràng buộc khóa ngoại hoạt động chuẩn xác trong MariaDB.

* **TASK-B7.2: Máy Trạng thái Vòng đời Phòng (State Machine) & Kiểm soát RBAC**
  * **Module:** Nghiệp vụ Trạng thái Phòng
  * **Người thực hiện:** Dev B
  * **Phụ thuộc (DAG):** TASK-B7.1
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/room/RoomStateMachine.java`, `src/quanlykhachsan/backend/room/RoomStateTransitionException.java`.
  * **Định nghĩa Hoàn thành (DoD):** Kiểm soát nghiêm ngặt các bước chuyển trạng thái chuẩn:
    - Lễ tân (Front Desk): `vacant_clean` -> `occupied_clean` (Nhận phòng), `occupied_*` -> `vacant_dirty` (Trả phòng).
    - Buồng phòng (Housekeeping): `vacant_dirty` -> `vacant_clean`, `occupied_dirty` -> `occupied_clean`.
    - Bảo trì (Maintenance): Mọi trạng thái -> `out_of_order`, `out_of_order` -> `vacant_clean`.
    - CHẶN VI PHẠM: Cấm Lễ tân đổi `vacant_dirty` -> `vacant_clean`. Cấm chuyển trực tiếp `occupied_*` -> `vacant_clean` (không được bỏ qua khâu dọn phòng).
  * **Phương thức xác minh:** Chạy `mvn test -Dtest=RoomStateMachineTest`. 25 ca kiểm thử bao quát toàn bộ các trường hợp chuyển trạng thái hợp lệ và bất hợp pháp.

* **TASK-B7.3: Động cơ Tự động Trừ Kho Minibar & Ghi nợ Chi phí vào Folio Phụ**
  * **Module:** Động cơ Quản lý Kho & Bút toán Folio
  * **Người thực hiện:** Dev B
  * **Phụ thuộc (DAG):** TASK-B7.1
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/room/MinibarPostingService.java`.
  * **Định nghĩa Hoàn thành (DoD):** Xử lý giao dịch nguyên tử:
    1. Trừ số lượng tồn `minibar_items.current_stock` theo danh sách tiêu thụ. (Báo lỗi nếu số lượng yêu cầu lớn hơn tồn thực tế).
    2. Xác định đơn đặt phòng đang hoạt động của phòng đó.
    3. Tìm hoặc tự động tạo `FOLIO-GUEST-xxx` (Folio dịch vụ phụ).
    4. Thêm bản ghi `folio_transactions` (`transaction_type = 'debit'`, `charge_type = 'minibar'`, `amount = quantity * unit_price`, `business_date = current_hotel_date`).
    5. Cộng dồn số tiền vào `balance` của Folio phụ.
  * **Phương thức xác minh:** Chạy `mvn test -Dtest=MinibarPostingServiceTest`. Tồn kho bị trừ chính xác và số dư Folio phụ tăng đúng tổng giá trị hàng tiêu thụ.

#### Dev C — Giao diện Desktop & Web Đặt phòng
* **TASK-C7.1: Bảng Thao tác Nhanh Buồng phòng trên Desktop Swing**
  * **Module:** Giao diện Buồng phòng Desktop
  * **Người thực hiện:** Dev C
  * **Phụ thuộc (DAG):** TASK-D7.1
  * **Tệp mục tiêu:** `src/quanlykhachsan/frontend/view/HousekeepingPanel.java`, `src/quanlykhachsan/frontend/view/StaffForm.java`.
  * **Định nghĩa Hoàn thành (DoD):** Tab chuyên dụng dành riêng cho bộ phận Buồng phòng trên Swing. Hiển thị lưới phòng kèm huy hiệu trực quan. Cung cấp nút thao tác 1 chạm "Đã dọn phòng xong" cho các phòng bẩn. Bộ lọc nhanh: "Phòng bẩn cần dọn". Cập nhật trạng thái qua `SwingWorker` trong thời gian < 50ms, không làm đơ giao diện.
  * **Phương thức xác minh:** Mở tab Buồng phòng trong Swing. Lọc các phòng bẩn; bấm "Đã dọn phòng xong" trên phòng 302; màu phòng chuyển từ Cam sang Xanh lá trong vòng dưới 100ms.

* **TASK-C7.2: Hộp thoại Kiểm kê & Chốt Hóa đơn Minibar trên Swing**
  * **Module:** Giao diện Minibar Desktop
  * **Người thực hiện:** Dev C
  * **Phụ thuộc (DAG):** TASK-D7.1
  * **Tệp mục tiêu:** `src/quanlykhachsan/frontend/view/MinibarChecklistDialog.java`, `src/quanlykhachsan/frontend/view/RoomActionDialog.java`.
  * **Định nghĩa Hoàn thành (DoD):** Màn hình Check-out và kiểm tra phòng bổ sung nút "Kiểm kho Minibar". Bật hộp thoại danh sách vật phẩm (Bia Heineken, Nước suối Evian, Snack khoai tây, Socola) kèm nút tăng/giảm (+/-). Tính tổng tiền VND tức thì. Nút "Ghi nợ vào Folio" gửi request có header `Idempotency-Key` (UUIDv4) và đóng modal.
  * **Phương thức xác minh:** Mở hộp thoại Minibar; tăng Heineken lên 2 lon, 1 gói snack. Tổng tiền hiển thị `120.000 VNĐ`. Bấm chốt; xác nhận request gửi thành công và số dư Folio được cập nhật ngay lập tức.

* **TASK-C7.3: Hộp thoại Đưa Phòng vào Bảo trì (OOO) Kèm Mã PIN Quản lý**
  * **Module:** Giao diện Bảo trì Desktop
  * **Người thực hiện:** Dev C
  * **Phụ thuộc (DAG):** TASK-D7.1
  * **Tệp mục tiêu:** `src/quanlykhachsan/frontend/view/MaintenanceDialog.java`.
  * **Định nghĩa Hoàn thành (DoD):** Menu chuột phải trên phòng cho phép chọn "Bảo trì / Sửa chữa (OOO)". Yêu cầu nhập lý do (Rò rỉ nước, Lỗi điều hòa, Hỏng nội thất) và ngày dự kiến xong. Nếu phòng đã có khách đặt trước trong tương lai, hiển thị hộp thoại cảnh báo màu cam yêu cầu nhập Mã PIN Xác nhận của Quản lý ca trực.
  * **Phương thức xác minh:** Thử chuyển phòng đã được đặt sang OOO trong Swing; kiểm tra hộp thoại nhập PIN xuất hiện; nhập đúng PIN chuyển màu phòng sang Đỏ (`#F44336`).

#### Dev D — DevOps, Kiểm thử Tự động & Phân hệ AI
* **TASK-D7.1: Mở rộng Mock API Server cho Buồng phòng & Minibar**
  * **Module:** Test Harness & Stubs
  * **Người thực hiện:** Dev D
  * **Phụ thuộc (DAG):** Không
  * **Tệp mục tiêu:** `src/test/java/quanlykhachsan/stubs/HousekeepingMockStubs.java`.
  * **Định nghĩa Hoàn thành (DoD):** Triển khai các endpoint giả lập trên cổng 8089 trước 12:00 ngày đầu tuần cho `PUT /api/v1/rooms/{id}/housekeeping`, `POST /api/v1/rooms/{id}/minibar` và `PUT /api/v1/rooms/{id}/maintenance` phục vụ Dev C làm giao diện.
  * **Phương thức xác minh:** `curl -X PUT http://localhost:8089/api/v1/rooms/101/housekeeping -d '{"status":"vacant_clean"}'` trả về 200 OK.

* **TASK-D7.2: Tự động hóa Kiểm thử: Kịch bản 8 (Máy Trạng thái Buồng phòng & Chặn Quyền RBAC)**
  * **Module:** Kiểm thử Tự động An ninh & Trạng thái
  * **Người thực hiện:** Dev D
  * **Phụ thuộc (DAG):** TASK-A7.1, TASK-B7.2
  * **Tệp mục tiêu:** `src/test/java/quanlykhachsan/scenarios/Scenario8HousekeepingRbacTest.java`.
  * **Định nghĩa Hoàn thành (DoD):** Bộ kiểm thử tự động kiểm chứng:
    1. Token Lễ tân gọi `PUT /rooms/101/housekeeping` chuyển `vacant_clean` bị chặn với mã HTTP `403 Forbidden`.
    2. Token Buồng phòng cập nhật thành công từ `vacant_dirty` sang `vacant_clean` nhận mã HTTP `200 OK`.
    3. Mọi tài khoản cố tình chuyển trực tiếp từ `occupied_clean` sang `vacant_clean` đều bị chặn với mã lỗi HTTP `400/409 Conflict`.
  * **Phương thức xác minh:** Thực thi `mvn test -Dtest=Scenario8HousekeepingRbacTest`. Đạt 100% assertions trong thời gian < 2.000ms.

* **TASK-D7.3: Kiểm thử Tự động Ứng suất Ghi nợ Minibar Đồng thời**
  * **Module:** Kiểm thử Tự động Đồng thời Tài chính
  * **Người thực hiện:** Dev D
  * **Phụ thuộc (DAG):** TASK-A7.2, TASK-B7.3
  * **Tệp mục tiêu:** `src/test/java/quanlykhachsan/booking/MinibarConcurrentPostingTest.java`.
  * **Định nghĩa Hoàn thành (DoD):** Khởi chạy 20 luồng đồng thời ghi nợ chi phí minibar trên 20 phòng khác nhau. Đảm bảo mỗi Folio ghi nhận chính xác chi phí, kho hàng bị trừ chuẩn xác và không gây rò rỉ kết nối HikariCP.
  * **Phương thức xác minh:** Thực thi `mvn test -Dtest=MinibarConcurrentPostingTest`. Kiểm tra chỉ số pool; số kết nối active quay về đúng 0 sau khi hoàn tất.

---

### TUẦN 8: ĐỘNG CƠ GIÁ LINH HOẠT, VÍ KHUYẾN MÃI, HÓA ĐƠN VAT & BÁO CÁO DBR

#### Dev A — Backend API & Hạ tầng Cốt lõi
* **TASK-A8.1: Controller Báo giá Phòng Linh hoạt Thời gian thực**
  * **Module:** API Động cơ Giá phòng
  * **Người thực hiện:** Dev A
  * **Phụ thuộc (DAG):** TASK-B8.1
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/pricing/PricingController.java`.
  * **Định nghĩa Hoàn thành (DoD):** Mở `GET /api/v1/pricing/quote?room_type_id=1&check_in=2026-11-01&check_out=2026-11-03`. Endpoint công khai nằm trong danh sách trắng `JwtFilter`. Gọi `DynamicPricingEngine` tính toán giá BAR linh hoạt theo tỷ lệ công suất phòng dự kiến trong khoảng thời gian lưu trú. Phản hồi dưới 10ms kèm chi tiết giá gốc, hệ số tăng giá, bóc tách thuế và tổng tiền dự kiến.
  * **Phương thức xác minh:** `curl "http://localhost:8081/api/v1/pricing/quote?room_type_id=1&check_in=2026-11-01&check_out=2026-11-03"` trả về 200 kèm báo giá linh hoạt chính xác.

* **TASK-A8.2: Controller Kiểm tra & Xác thực Mã Giảm giá Voucher**
  * **Module:** API Khuyến mãi & Marketing
  * **Người thực hiện:** Dev A
  * **Phụ thuộc (DAG):** TASK-B8.2
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/promotion/PromotionController.java`.
  * **Định nghĩa Hoàn thành (DoD):** Mở `POST /api/v1/promotions/validate`. Tiếp nhận mã voucher, giá trị đơn đặt phòng và số điện thoại khách. Kiểm tra tính hợp lệ, hạn sử dụng, giới hạn lượt dùng trên từng khách và điều kiện chi tiêu tối thiểu. Trả về số tiền được khấu trừ và tổng tiền sau giảm giá.
  * **Phương thức xác minh:** `curl -X POST http://localhost:8081/api/v1/promotions/validate -H "Content-Type: application/json" -d '{"code":"SUMMER50","total_amount":3000000,"phone":"0901234567"}'` trả về 200 kèm số tiền giảm giá.

* **TASK-A8.3: Controller Xuất Tệp Hóa đơn Điện tử VAT PDF Chuẩn Thông tư 78**
  * **Module:** API Hóa đơn Điện tử
  * **Người thực hiện:** Dev A
  * **Phụ thuộc (DAG):** TASK-B8.3
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/invoice/InvoicePdfController.java`.
  * **Định nghĩa Hoàn thành (DoD):** Mở `GET /api/v1/invoices/{id}/pdf`. Lấy dữ liệu hóa đơn đã quyết toán; gọi `VatInvoicePdfGenerator` tạo luồng byte PDF chuẩn Thông tư 78/2021/TT-BTC. Đặt đúng các header HTTP: `Content-Type: application/pdf`, `Content-Disposition: inline; filename=HD-2026-{id}.pdf`. Truyền tải luồng nhị phân tốc độ cao, không rò rỉ bộ nhớ.
  * **Phương thức xác minh:** `curl -s http://localhost:8081/api/v1/invoices/1/pdf -H "Authorization: Bearer <token>" -o test_invoice.pdf`. Mở tệp; tài liệu hiển thị nguyên vẹn trên Adobe Acrobat hoặc trình đọc PDF.

* **TASK-A8.4: Endpoint Tổng hợp Báo cáo Kinh doanh Ngày (DBR)**
  * **Module:** API Báo cáo Tài chính
  * **Người thực hiện:** Dev A
  * **Phụ thuộc (DAG):** TASK-B8.4
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/report/FinancialReportController.java`.
  * **Định nghĩa Hoàn thành (DoD):** Mở `GET /api/v1/reports/financial/dbr?date=2026-10-14`. Yêu cầu quyền `admin` hoặc `front_desk`. Tổng hợp các chỉ số tài chính trong ngày: Tổng Doanh thu, Doanh thu Phòng, Doanh thu F&B/Minibar, Phí Dịch vụ (5%), Thuế VAT (8%/10%), Tổng phòng sẵn sàng, Số phòng có khách, ADR và RevPAR.
  * **Phương thức xác minh:** `curl "http://localhost:8081/api/v1/reports/financial/dbr?date=2026-10-14" -H "Authorization: Bearer <token>"` trả về 200 với các chỉ số chuẩn của báo cáo DBR.

#### Dev B — Dữ liệu, Tài chính & Nghiệp vụ Cốt lõi
* **TASK-B8.1: Thuật toán Bậc thang Giá Động & Ma trận Công suất Phòng**
  * **Module:** Nghiệp vụ Tối ưu Doanh thu
  * **Người thực hiện:** Dev B
  * **Phụ thuộc (DAG):** Không
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/pricing/DynamicPricingEngine.java`, `src/quanlykhachsan/backend/pricing/PricingTier.java`.
  * **Định nghĩa Hoàn thành (DoD):** Cài đặt công thức tính công suất chuẩn ngành khách sạn:
    $$\text{Tỷ lệ Công suất} = \frac{\text{Số phòng đang ở} + \text{Số phòng đặt đã xác nhận}}{\text{Tổng phòng kinh doanh (200 - OOO)}}$$
    Áp dụng hệ số bậc thang giá:
    - Bậc 1 (Công suất < 50%): Hệ số $1.00\times$ (Giá cơ bản BAR)
    - Bậc 2 (Công suất 50% – 69.9%): Hệ số $1.15\times$
    - Bậc 3 (Công suất 70% – 84.9%): Hệ số $1.30\times$
    - Bậc 4 (Công suất $\ge 85\%$): Hệ số $1.50\times$ (Tăng giá giờ cao điểm)
    Tính toán bằng `BigDecimal` kèm quy tắc làm tròn chính xác (`ROUND_HALF_UP`).
  * **Phương thức xác minh:** Chạy `mvn test -Dtest=DynamicPricingEngineTest`. 15 ca kiểm thử kiểm tra hệ số tương ứng trên mọi dải công suất.

* **TASK-B8.2: Động cơ Ví Khuyến mãi & Quy tắc Chống Cộng dồn Voucher**
  * **Module:** Nghiệp vụ Khuyến mãi
  * **Người thực hiện:** Dev B
  * **Phụ thuộc (DAG):** Không
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/promotion/VoucherService.java`.
  * **Định nghĩa Hoàn thành (DoD):** Thực thi các quy tắc khuyến mãi:
    1. Giới hạn mức giảm giá tối đa (ví dụ: Giảm 20% nhưng không vượt quá 500.000 VNĐ).
    2. Mỗi đơn đặt phòng / Folio chỉ được áp dụng đúng 1 voucher (Chống cộng dồn).
    3. Giảm giá được trừ vào tiền phòng trước thuế, sau đó mới tính 5% phí phục vụ và 8%/10% VAT.
    4. Ghi nhận giao dịch sử dụng mã giảm giá nguyên tử vào bảng `promotions`.
  * **Phương thức xác minh:** Chạy `mvn test -Dtest=VoucherServiceTest`. Kiểm tra việc chặn trần giảm giá và thứ tự tính thuế chuẩn xác.

* **TASK-B8.3: Trình Tạo Hóa đơn Điện tử iTextPDF Chuẩn Thông tư 78/2021/TT-BTC**
  * **Module:** Động cơ Tạo Tệp PDF Hóa đơn
  * **Người thực hiện:** Dev B
  * **Phụ thuộc (DAG):** Không
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/invoice/VatInvoicePdfGenerator.java`, `pom.xml`.
  * **Định nghĩa Hoàn thành (DoD):** Tích hợp iText 7 (`com.itextpdf:itext7-core:7.2.5`) vào `pom.xml`. Nhúng font chữ TrueType tiếng Việt (`DejaVuSans.ttf`). Thiết kế mẫu hóa đơn giá trị gia tăng điện tử chuẩn quy định Thông tư 78:
    - Tên Đơn vị bán hàng, Địa chỉ, Mã số thuế (MST: 0312345678).
    - Tên Khách hàng, Tên Đơn vị mua hàng, Mã số thuế người mua.
    - Bảng chi tiết dịch vụ: Tiền phòng, Minibar, Giặt là.
    - Bóc tách thuế suất: Phí phục vụ (5%), Thuế GTGT (8% hoặc 10%), Tổng tiền thanh toán bằng số và bằng chữ.
    - Mã QR tra cứu hóa đơn điện tử liên kết tới cổng xác thực.
    - 100% không bị lỗi font tiếng Việt, không bị vỡ khung bảng.
  * **Phương thức xác minh:** Chạy `mvn test -Dtest=VatInvoicePdfGeneratorTest`. Xuất tệp PDF; đọc nội dung qua Apache PDFBox, xác nhận đầy đủ dấu tiếng Việt: "Hóa đơn giá trị gia tăng (tiền tệ: VNĐ)".

* **TASK-B8.4: Tổng hợp Dữ liệu Tài chính DBR & Cân đối Sổ cái Bất biến**
  * **Module:** Kế toán Tổng hợp & Báo cáo DBR
  * **Người thực hiện:** Dev B
  * **Phụ thuộc (DAG):** Không
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/report/DbrLedgerAggregator.java`, `src/quanlykhachsan/backend/report/DbrSnapshot.java`.
  * **Định nghĩa Hoàn thành (DoD):** Tổng hợp toàn bộ các dòng giao dịch từ `folio_transactions` của ngày kinh doanh đã đóng sổ. Tính toán:
    $$\text{ADR} = \frac{\text{Doanh thu Phòng}}{\text{Số phòng có khách}}, \quad \text{RevPAR} = \frac{\text{Doanh thu Phòng}}{\text{Tổng phòng kinh doanh}}$$
    Bảo đảm cân đối sổ cái tới từng xu:
    $$\sum\text{Bút toán Nợ} == \sum\text{Bút toán Có} + \text{Số dư Công nợ phải thu}$$
    Lưu trữ bản chụp dữ liệu hàng ngày vào bảng `daily_business_reports`.
  * **Phương thức xác minh:** Chạy `mvn test -Dtest=DbrLedgerAggregatorTest`. Độ lệch giữa báo cáo và giao dịch thực tế bằng đúng 0.00 VNĐ.

#### Dev C — Giao diện Desktop & Web Đặt phòng
* **TASK-C8.1: Tích hợp Xem & In Hóa đơn VAT PDF trên Desktop Swing**
  * **Module:** Giao diện Xuất Hóa đơn Desktop
  * **Người thực hiện:** Dev C
  * **Phụ thuộc (DAG):** TASK-D8.1
  * **Tệp mục tiêu:** `src/quanlykhachsan/frontend/view/InvoiceViewerDialog.java`, `src/quanlykhachsan/frontend/view/StaffForm.java`.
  * **Định nghĩa Hoàn thành (DoD):** Hộp thoại thanh toán bổ sung nút "Xuất Hóa Đơn Điện Tử (VAT)". Tải dữ liệu PDF từ backend qua `SwingWorker`; lưu tệp `.pdf` tạm; tự động mở trình đọc PDF mặc định của hệ điều hành qua `java.awt.Desktop.getDesktop().open(file)` trong thời gian < 500ms.
  * **Phương thức xác minh:** Bấm "Xuất Hóa Đơn Điện Tử" trên giao diện Swing. Trình đọc PDF của Windows bật lên hiển thị hóa đơn đầy đủ thông tin khách và dịch vụ.

* **TASK-C8.2: Hiển thị Giá Động & Khung Nhập Mã Khuyến mãi trên Web Booking**
  * **Module:** Giao diện Đặt phòng Trực tuyến Web
  * **Người thực hiện:** Dev C
  * **Phụ thuộc (DAG):** TASK-D8.1
  * **Tệp mục tiêu:** `web-ui/public/js/pricing-quote.js`, `web-ui/public/js/voucher.js`.
  * **Định nghĩa Hoàn thành (DoD):** Bảng tóm tắt đặt phòng hiển thị giá phòng linh hoạt kèm nhãn công suất thực tế ("Giá ưu đãi 1.15x"). Khung nhập "Mã giảm giá / Voucher" kèm nút "Áp dụng". Hiệu ứng xoay chờ khi kiểm tra; hiển thị nhãn xanh giảm giá khi thành công hoặc thông báo đỏ khi mã sai.
  * **Phương thức xác minh:** Nhập voucher `SUMMER50` trên web; tổng tiền cập nhật tức thì với hiệu ứng gạch ngang giá cũ.

* **TASK-C8.3: Bảng Điều khiển Báo cáo Doanh thu DBR trên Desktop Swing**
  * **Module:** Giao diện Báo cáo Phân tích Desktop
  * **Người thực hiện:** Dev C
  * **Phụ thuộc (DAG):** TASK-D8.1
  * **Tệp mục tiêu:** `src/quanlykhachsan/frontend/view/DbrReportPanel.java`.
  * **Định nghĩa Hoàn thành (DoD):** Thêm menu "Báo Cáo Doanh Thu (DBR)" trong Swing. Hiển thị các khối chỉ số KPI: Tỷ lệ lấp đầy (%), Doanh thu Phòng, Tổng Doanh thu, ADR, RevPAR. Bảng chi tiết doanh thu theo từng bộ phận (Phòng, Minibar, Dịch vụ). Nút "Xuất Excel / CSV" trích xuất dữ liệu báo cáo.
  * **Phương thức xác minh:** Mở tab báo cáo DBR; chọn ngày; các khối KPI và bảng biểu hiển thị dữ liệu đầy đủ trong vòng 300ms.

#### Dev D — DevOps, Kiểm thử Tự động & Phân hệ AI
* **TASK-D8.1: Mở rộng Mock API Server cho Giá động, Voucher & Báo cáo DBR**
  * **Module:** Test Harness & Stubs
  * **Người thực hiện:** Dev D
  * **Phụ thuộc (DAG):** Không
  * **Tệp mục tiêu:** `src/test/java/quanlykhachsan/stubs/PricingAndReportMockStubs.java`.
  * **Định nghĩa Hoàn thành (DoD):** Triển khai các endpoint giả lập trên cổng 8089 trước 12:00 ngày đầu tuần cho `/api/v1/pricing/quote`, `/api/v1/promotions/validate` và `/api/v1/reports/financial/dbr` để Dev C làm việc không bị gián đoạn.
  * **Phương thức xác minh:** `curl http://localhost:8089/api/v1/pricing/quote?room_type_id=1` trả về báo giá giả lập.

* **TASK-D8.2: Kiểm thử Tự động Tải Cao Endpoint Báo giá Linh hoạt (50 CCU)**
  * **Module:** Kiểm thử Tự động Hiệu năng
  * **Người thực hiện:** Dev D
  * **Phụ thuộc (DAG):** TASK-A8.1, TASK-B8.1
  * **Tệp mục tiêu:** `src/test/java/quanlykhachsan/performance/DynamicPricingLoadTest.java`.
  * **Định nghĩa Hoàn thành (DoD):** Kích hoạt 50 luồng đồng thời liên tục truy vấn báo giá phòng qua 10.000 requests. Đo kiểm việc sử dụng kết nối HikariCP và độ trễ phản hồi.
  * **Phương thức xác minh:** Chạy `mvn test -Dtest=DynamicPricingLoadTest`. Độ trễ phân vị P95 < 15ms, P99 < 30ms, 0 lỗi rò rỉ kết nối, 0 timeout.

* **TASK-D8.3: Kiểm thử Tuân thủ Hóa đơn PDF Thông tư 78 & Ký tự Tiếng Việt**
  * **Module:** Kiểm thử Tự động Tuân thủ Pháp lý
  * **Người thực hiện:** Dev D
  * **Phụ thuộc (DAG):** TASK-A8.3, TASK-B8.3
  * **Tệp mục tiêu:** `src/test/java/quanlykhachsan/invoice/Circular78PdfInvoiceTest.java`.
  * **Định nghĩa Hoàn thành (DoD):** Tự động tạo hóa đơn mẫu chứa đầy đủ các ký tự tiếng Việt phức tạp ("Nguyễn Văn Đạt", "Phòng Hạng Sang Hướng Biển", "Tiền dịch vụ minibar và đồ uống"). Xuất PDF, phân tích nội dung bằng Apache PDFBox, khẳng định độ toàn vẹn ký tự đạt 100% (không có ký tự `?` hoặc lỗi ô vuông).
  * **Phương thức xác minh:** Thực thi `mvn test -Dtest=Circular78PdfInvoiceTest`. Toàn bộ assertions đạt 100%.

* **TASK-D8.4: Bộ Kiểm thử Hồi quy Toàn diện Phase 2 & Chốt Cổng Nghiệm thu Tuần 8**
  * **Module:** Bộ Kiểm thử Hồi quy Tổng thể
  * **Người thực hiện:** Dev D
  * **Phụ thuộc (DAG):** Toàn bộ các Task Tuần 5–8
  * **Tệp mục tiêu:** `src/test/java/quanlykhachsan/Phase2RegressionSuite.java`.
  * **Định nghĩa Hoàn thành (DoD):** Trình chạy kiểm thử tự động thực thi:
    - 47 bài kiểm thử nền tảng của Phase 1.
    - Kịch bản 7 (Phòng thủ Bão Webhook Trùng lặp).
    - Kịch bản 8 (Máy trạng thái Buồng phòng & Chặn quyền RBAC).
    - Kịch bản 9 (Xung đột Web vs Khách vãng lai).
    - Kiểm tra chuỗi VietQR EMVCo CRC16.
    - Kiểm tra số dư Folio không âm & bút toán điều chỉnh tiền thừa.
    - Kiểm tra hóa đơn PDF chuẩn Thông tư 78.
    - Đối soát cân bằng sổ cái báo cáo DBR.
  * **Phương thức xác minh:** Chạy `mvn clean test -Dtest=Phase2RegressionSuite`. 100% bài kiểm thử đều PASS (0 thất bại, 0 lỗi).

---

## III. BẢNG GIAO THỨC GIẢ LẬP NGÀY ĐẦU TUẦN (DAY-1 MOCK PROTOCOL)

Nhằm triệt tiêu hoàn toàn rủi ro tắc nghẽn phụ thuộc giữa các nhà phát triển, quy chuẩn Day-1 Mock Protocol được bắt buộc áp dụng cho Phase 2:

| Tuần | Dịch vụ Upstream cần thiết | Bên cung cấp | Bên tiêu thụ | Bản hợp đồng / File Mock | Hạn chót (SLA) | Chiến lược Giải phóng (Decoupling) |
|---|---|:---:|:---:|---|:---:|---|
| **W5** | OTP Khách (`/auth/guest-otp/*`) & Đặt phòng Web (`/bookings/public`) | **Dev A / Dev B** | **Dev C (Web UI)** | `mock-guest-otp.json`, `mock-public-booking.json` trên cổng 8089 | **W5 Ngày 1, 12:00** | Dev D dựng stubs trên `MockApiServer` (cổng 8089). Dev C lập trình giao diện Web bằng JS thuần kết nối tới `:8089`, chuyển sang `:8081` vào Thứ Năm. |
| **W6** | Dữ liệu VietQR (`/folios/{id}/vietqr`) & Webhook (`/webhooks/vietqr`) | **Dev A / Dev B** | **Dev C (Swing/Web) & Dev D (QA)** | Dữ liệu chuỗi EMVCo & ảnh Base64 giả lập trên `:8089` | **W6 Ngày 1, 12:00** | Dev D cung cấp `BankWebhookSimulator` trên `:8089`. Dev C tích hợp đồng hồ đếm ngược và thăm dò trạng thái qua dữ liệu mẫu. |
| **W7** | Trạng thái Buồng phòng (`/rooms/{id}/housekeeping`) & Minibar (`/rooms/{id}/minibar`) | **Dev A / Dev B** | **Dev C (Swing Staff)** | `HousekeepingMockStubs.java` trên cổng `:8089` | **W7 Ngày 1, 12:00** | Dev C xây dựng bảng thao tác buồng phòng và modal kiểm kho minibar qua API giả lập; ghép nối API thật vào Ngày 4. |
| **W8** | Báo giá phòng (`/pricing/quote`) & Báo cáo DBR (`/reports/financial/dbr`) | **Dev A / Dev B** | **Dev C (Swing/Web) & Dev D (QA)** | `PricingAndReportMockStubs.java` trên cổng `:8089` | **W8 Ngày 1, 12:00** | Dev C hiển thị giao diện biểu đồ DBR và áp dụng voucher trên Web qua dữ liệu stub; Dev D viết script tải JMeter trước. |

---

## IV. BẢNG TIÊU CHÍ NGHIỆM THU MỐC GO/NO-GO CUỐI TUẦN 8

Hệ thống phải đạt **100% các tiêu chí dưới đây** trước khi Phase 2 được nghiệm thu và cho phép bắt đầu Phase 3 (Nền tảng AI & Thị giác Máy tính):

### 1. Tính toàn vẹn chức năng & Endpoint Mới
* [ ] **Động cơ Đặt phòng Web:** Khách hàng vãng lai có thể tra cứu phòng trống, nhận mã OTP qua điện thoại và đặt phòng thành công trên Web mà không bị lỗi 401.
* [ ] **Tiền kiểm CORS:** `CorsFilter` phản hồi HTTP `204 No Content` cho các yêu cầu tiền kiểm `OPTIONS` chứa header `Idempotency-Key` và `X-Signature`.
* [ ] **Phục vụ Tệp tĩnh:** `StaticFileHandler` phân phối tài nguyên Web Booking HTML/JS/CSS trực tiếp trên cổng 8081 với đúng MIME type và miễn nhiễm 100% với Path Traversal.

### 2. Cổng Thanh toán VietQR & Bất biến Số dư Kế toán
* [ ] **Tuân thủ Chuẩn EMVCo Napas 247:** Chuỗi VietQR sinh ra đúng cấu trúc thẻ dữ liệu EMVCo và tính toán chính xác mã kiểm tra CRC16-CCITT Kermit.
* [ ] **Kịch bản 7 (Phòng thủ Bão Webhook Trùng lặp):** 50 webhook đồng thời mang cùng một `bank_trans_id` chỉ thực hiện ghi sổ đúng 1 lần duy nhất; 49 request còn lại nhận phản hồi idempotent.
* [ ] **Số dư Folio Không Âm:** `folios.balance` luôn luôn $\ge 0.00$. Thanh toán thừa được ghi nhận thành giao dịch điều chỉnh `charge_type = 'credit_adjustment'`, không bao giờ vi phạm ràng buộc CHECK CSDL.
* [ ] **Chuẩn hóa ID Parity:** Phân tách rõ ràng giữa `id` (BIGINT PK) và `code` (VARCHAR UNIQUE, ví dụ: `FOLIO-MASTER-101`, `FOLIO-GUEST-101`).

### 3. Máy Trạng thái Buồng phòng & Tự động hóa Minibar
* [ ] **Kịch bản 8 (Máy Trạng thái Buồng phòng & RBAC):** Lễ tân bị chặn đứng (HTTP 403) khi cố tình đánh dấu phòng bẩn thành phòng sạch. Chặn việc chuyển trực tiếp từ phòng đang ở sang phòng sạch (HTTP 400/409).
* [ ] **Tự động Ghi nợ Minibar:** Gửi danh sách đồ uống minibar tự động trừ tồn kho và ghi nợ tức thì vào Folio dịch vụ phụ `FOLIO-GUEST-xxx`.
* [ ] **Khóa Bảo trì OOO:** Chuyển phòng sang bảo trì OOO bắt buộc phải có Mã PIN của Quản lý nếu phòng đã có khách đặt trước.

### 4. Động cơ Giá Linh hoạt, Hóa đơn Thông tư 78 & Báo cáo DBR
* [ ] **Bậc thang Giá Linh hoạt:** Giá BAR tự động nhân hệ số theo 4 dải công suất (<50%: 1.0x, 50-70%: 1.15x, 70-85%: 1.3x, $\ge 85\%$: 1.5x) mà không bị trôi số thập phân.
* [ ] **Hóa đơn Điện tử Chuẩn Thông tư 78:** Hóa đơn VAT xuất dạng PDF hiển thị chuẩn tiếng Việt Unicode (0 lỗi font) và tính toán chính xác 5% phí dịch vụ cùng thuế VAT 8%/10%.
* [ ] **Cân đối Sổ cái Báo cáo DBR:** Báo cáo DBR cân bằng tuyệt đối giữa Nợ, Có và Công nợ phải thu với độ lệch đúng 0.00 VNĐ.

### 5. Hiệu năng, Xung đột Tải & Độ nhạy Giao diện EDT
* [ ] **Kịch bản 9 (Xung đột Web vs Khách vãng lai):** 10 đơn Web và 10 đơn Lễ tân cùng tranh chấp 1 phòng trống duy nhất chỉ sinh ra đúng 1 đơn thành công và 19 mã lỗi 409 Conflict.
* [ ] **Độ trễ Giao diện Swing (EDT):** Toàn bộ thao tác (Wizard vãng lai, Hộp thoại VietQR, Kiểm kho minibar) duy trì độ trễ $< 16\text{ms}$ trên luồng EDT (đảm bảo 60 FPS).
* [ ] **Độ ổn định Hồ kết nối HikariCP:** 50 kết nối đồng thời dưới tải cao không gây rò rỉ kết nối; số kết nối active trở về đúng 0 sau khi hoàn tất kiểm thử.

---

## V. BIÊN BẢN KÝ DUYỆT NGHIỆM THU

| Vai trò Trách nhiệm | Họ tên / Chức danh | Chữ ký / Trạng thái | Ngày Ký duyệt |
|---|---|:---:|:---:|
| **Technical Lead** | Kiến trúc trưởng Hệ thống | `[ PHÊ DUYỆT - NỀN TẢNG WBS PHASE 2 ]` | `2026-10-14` |
| **QA & DevOps Lead** | Trưởng nhóm Kiểm thử (Dev D) | `[ XÁC NHẬN BỘ TEST HARNESS & GATES ]` | `2026-10-14` |
| **Core Backend Lead** | Kỹ sư Trưởng Backend (Dev A & B) | `[ CAM KẾT - WEEKS 5-8 BACKEND ]` | `2026-10-14` |
| **Frontend Lead** | Kỹ sư Trưởng Giao diện (Dev C) | `[ CAM KẾT - SWING & WEB BOOKING ]` | `2026-10-14` |
