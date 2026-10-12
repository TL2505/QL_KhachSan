# KẾ HOẠCH TRIỂN KHAI KỸ THUẬT & CƠ CẤU PHÂN RÃ CÔNG VIỆC (WBS) — GIAI ĐOẠN 1
**Dự án:** Hệ thống Quản lý Khách sạn 5★ (QL_KhachSan_main — 200 Phòng, TP. Hồ Chí Minh)  
**Quy mô đội ngũ:** 4 Lập trình viên (Dev A, B, C, D) | **Thời lượng:** 4 Tuần (Phase 1: Core PMS & Desktop Integration)  
**Tiêu chuẩn kiến trúc:** Pure Java 17 (`com.sun.net.httpserver`), HikariCP 5.1.0 (`DatabaseConnection.java`), MariaDB 11 (Docker Network cô lập), Java Swing + FlatLaf 3.4.1.

---

## I. TỔNG QUAN KIẾN TRÚC & RÀNG BUỘC KỸ THUẬT

```
+---------------------------------------------------------------------------------------------------+
|                                  MÁY TRẠM VẬT LÝ (UBUNTU 22.04 LTS)                               |
|                                                                                                   |
|  [Staff Desktop Terminal]                                                                         |
|   Java Swing + FlatLaf (JDK 17) - StaffForm.java, RoomForm.java, BookingForm.java                 |
|   - Thiết kế tối ưu ca trực 8 tiếng của Lễ tân & Buồng phòng (Tốc độ > Hình thức)                |
|   - Bảng dữ liệu mật độ cao (24px/dòng), Sơ đồ phòng Ma trận 5×N (80×60px/ô)                      |
|   - java.net.http.HttpClient + SwingWorker (Tuyệt đối không block Event Dispatch Thread)          |
|           |                                                                                       |
|           | HTTP/1.1 (JSON) + Bearer JWT + Idempotency-Key (UUIDv4)                               |
|           v (Port 8081 nội bộ mạng khách sạn)                                                     |
|  +---------------------------------------------------------------------------------------------+  |
|  | DOCKER INTERNAL BRIDGE NETWORK: hotel_network (172.28.0.0/16)                               |  |
|  |                                                                                             |  |
|  |  +---------------------------------------+      +----------------------------------------+  |  |
|  |  | PMS Backend Core (app)                |      | MariaDB 11 (db)                        |  |  |
|  |  | - Native com.sun.net.httpserver        |      | - Cổng 3306 KHÔNG MỞ ra Host/LAN       |  |  |
|  |  | - Executors.newFixedThreadPool(20)    | JDBC | - Innodb Optimistic Lock (rooms.version|  |  |
|  |  | - HikariCP 5.1.0 (Pool tối đa 20 conn)|----->| - Bộ nhớ đệm InnoDB Buffer Pool        |  |  |
|  |  |   Timeout: 30s | Leak: 5000ms         |      | - Cách ly vật lý khỏi mạng bên ngoài   |  |  |
|  |  | - Đóng băng hệ thống (00:00 - 00:05)  |      +----------------------------------------+  |  |
|  |  +---------------------------------------+                                                   |  |
|  +---------------------------------------------------------------------------------------------+  |
+---------------------------------------------------------------------------------------------------+
```

---

## II. PHÂN RÃ CÔNG VIỆC CHI TIẾT TỪNG TUẦN (WEEKS 1 – 4)

---

### TUẦN 1: NỀN TẢNG HẠ TẦNG, CONNECTION POOL & XÁC THỰC BẢO MẬT

#### Dev A — Backend API & Infrastructure
* **TASK-A1.1: Khởi tạo HikariCP Connection Pool & Fixed Thread Pool**
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/utils/DatabaseConnection.java`, `src/quanlykhachsan/backend/Main.java`, `pom.xml`.
  * **Mô tả:** Tích hợp HikariCP 5.1.0, SLF4J (`slf4j-api`, `slf4j-simple`) và `jbcrypt` (0.4) vào `pom.xml`. Cài đặt `DatabaseConnection.java` theo mẫu Singleton: `maximumPoolSize=20`, `minimumIdle=5`, `idleTimeout=300000`, `connectionTimeout=30000` (30s), `leakDetectionThreshold=5000` (5s). Cấu hình `server.setExecutor(Executors.newFixedThreadPool(20))` trong `Main.java`.
  * **DoD:** Lấy kết nối trong $< 5\text{ms}$. Dưới 50 luồng đồng thời không có cảnh báo leak. Tự động thu hồi kết nối qua khối lệnh `try-with-resources`.
* **TASK-A1.2: Bộ định tuyến HTTP Router & Khớp biểu thức chính quy (Regex Path Matching)**
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/utils/Router.java`, `src/quanlykhachsan/backend/utils/RouteEntry.java`, `src/quanlykhachsan/backend/Main.java`.
  * **Mô tả:** Xây dựng Router độc lập xử lý trích xuất tham số động qua Regex (hỗ trợ `/api/v1/bookings/{id}`, `/api/v1/rooms/{id}`) và phân phối method `GET`, `POST`, `PUT`, `DELETE`.
  * **DoD:** Trích xuất chính xác tham số đường dẫn vào `Map<String, String>`. Trả về `404 Not Found` cho route lạ và `405 Method Not Allowed` cho sai method theo đúng JSON Envelope.
* **TASK-A1.3: Chuẩn hóa Envelope JSON & Xử lý ngoại lệ toàn cục**
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/utils/ApiResponseUtil.java`, `src/quanlykhachsan/backend/utils/GlobalExceptionHandler.java`.
  * **Mô tả:** Dùng Gson chuẩn hóa định dạng trả về: Thành công `{"data": {...}, "meta": {...}}`; Thất bại `{"error_code": "ERR_...", "message": "...", "timestamp": "..."}`. Bắt mọi uncaught exception đưa về HTTP `500`, không làm lộ stack trace.
  * **DoD:** Unit test kiểm thử đạt 100% cho các mã lỗi 400, 401, 403, 404, 409, 500 bám sát `docs/dev-spec/api-contracts.md`.
* **TASK-A1.4: Xác thực JWT & Login Controller**
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/auth/AuthController.java`, `src/quanlykhachsan/backend/utils/JwtFilter.java`, `src/quanlykhachsan/backend/utils/SecurityUtil.java`.
  * **Mô tả:** Cài đặt `POST /api/v1/auth/login`. Xác thực tài khoản nhân viên từ bảng `users`, kiểm tra mật khẩu qua `BCrypt`. Cấp JWT (JJWT, hạn 1 giờ) mang các claims: `userId`, `username`, `role`. `JwtFilter.java` kiểm tra header `Authorization: Bearer <token>`, trích xuất claims gán vào context request.
  * **DoD:** Đăng nhập đúng trả về HTTP `200 OK` kèm token; sai thông tin trả về HTTP `401`. Mọi request không có token hợp lệ tới API bảo vệ bị chặn ngay lập tức với mã 401.

#### Dev B — Core Business Logic & Financials
* **TASK-B1.1: Migration Cơ sở dữ liệu cho Versioning & Idempotency**
  * **Tệp mục tiêu:** `database/hotel.sql`, `src/quanlykhachsan/backend/utils/UpdateDbUtility.java`.
  * **Mô tả:** Bổ sung trường `version INT NOT NULL DEFAULT 0` vào bảng `rooms`. Tạo bảng `idempotency_keys (id VARCHAR(64) PRIMARY KEY, request_hash VARCHAR(64), response_body LONGTEXT, status_code INT, created_at DATETIME)`. Tạo bảng `folios`, `folio_transactions` và `audit_checkpoints`.
  * **DoD:** Kịch bản SQL thực thi trơn tru trên MariaDB 11, không lỗi khóa ngoại. Có sẵn chỉ mục trên `rooms(room_number, version)` và `idempotency_keys(created_at)`.
* **TASK-B1.2: Bộ lọc Idempotency & Ngăn chặn giao dịch trùng lặp**
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/utils/IdempotencyFilter.java`, `src/quanlykhachsan/backend/booking/IdempotencyService.java`.
  * **Mô tả:** Chặn bắt request `POST` gửi tới `/api/v1/bookings` và `/api/v1/folios/{id}/pay`. Bắt buộc kiểm tra header `Idempotency-Key` (UUIDv4). Nếu key đã tồn tại, trả về nguyên vẹn response body cũ kèm mã 200/201. Nếu key mới, khóa xử lý và lưu kết quả sau khi commit.
  * **DoD:** Gửi liên tiếp 2 request trùng lặp cùng `Idempotency-Key` chỉ tạo đúng 1 bản ghi trong CSDL; cả 2 đều nhận mã thành công giống nhau.
* **TASK-B1.3: Động cơ tính Thuế VAT (8%/10%) & Phí dịch vụ (5%) chuẩn xác**
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/booking/FinancialCalculator.java`, `src/quanlykhachsan/backend/booking/TaxConfig.java`.
  * **Mô tả:** Cài đặt toàn bộ phép tính bằng `BigDecimal` (`ROUND_HALF_UP`, scale 2). Xử lý chính xác VAT 8%/10%, phí dịch vụ 5%, phụ thu mà không làm trôi số thực.
  * **DoD:** 20 unit tests xác nhận: $\text{Tiền phòng} + \text{Phí dịch vụ} + \text{Thuế VAT} - \text{Giảm giá} == \text{Tổng thanh toán}$ với sai số bằng đúng 0.00.

#### Dev C — Desktop Frontend (Java Swing)
* **TASK-C1.1: Asynchronous HttpClient Core & Wrapper SwingWorker**
  * **Tệp mục tiêu:** `src/quanlykhachsan/frontend/api/ApiClient.java`, `src/quanlykhachsan/frontend/api/ApiCallback.java`.
  * **Mô tả:** Xây dựng module gọi API trung tâm bọc `java.net.http.HttpClient` (`connectTimeout=3000ms`, `requestTimeout=5000ms`). Tự động đính kèm `Authorization: Bearer <token>` và `Content-Type: application/json`. Đẩy kết quả về luồng đồ họa bằng `SwingWorker`.
  * **DoD:** Tuyệt đối không gọi I/O mạng trên Event Dispatch Thread (EDT). Khi giả lập trễ mạng 3 giây, giao diện vẫn phản hồi mượt mà, không đơ chuột.
* **TASK-C1.2: Bộ lắng nghe phím tắt phần cứng toàn cục (F1–F10)**
  * **Tệp mục tiêu:** `src/quanlykhachsan/frontend/utils/GlobalKeyDispatcher.java`, `src/quanlykhachsan/frontend/view/StaffForm.java`.
  * **Mô tả:** Đăng ký `KeyEventDispatcher` vào `KeyboardFocusManager` để bắt phím F1–F10 trên toàn ứng dụng: `F1` (Help), `F2` (Check-in), `F3` (Check-out), `F4` (Tìm kiếm), `F5` (Làm mới), `F8` (Mở Folio), `F9` (Đặt phòng mới).
  * **DoD:** Nhấn F2 mở hộp thoại Check-in bất kể vị trí con trỏ chuột/focus. Nhấn Esc đóng ngay modal hiện thời.
* **TASK-C1.3: Đồng bộ Theme Tokens & Chuẩn hóa FlatLaf**
  * **Tệp mục tiêu:** `src/quanlykhachsan/frontend/utils/UIThemeTokens.java`, `src/quanlykhachsan/frontend/view/StaffForm.java`.
  * **Mô tả:** Định nghĩa tập trung mã màu từ `ui-tokens.md`: Primary `#1B2A4A`, Success `#2E7D32`, Warning `#F57C00`, Error `#C62828`. Cấu hình FlatLaf: chiều cao hàng bảng `Table.rowHeight = 24`, bo góc `Component.arc = 4`.
  * **DoD:** Ứng dụng chạy mượt dưới `FlatLightLaf`. Bảng dữ liệu hiển thị đúng 24px/dòng, không còn mã màu hardcoded trong code.

#### Dev D — DevOps & QA / Testing
* **TASK-D1.1: Thiết lập mạng Docker Bridge cô lập & Hardening MariaDB**
  * **Tệp mục tiêu:** `docker-compose.yml`, `Dockerfile`.
  * **Mô tả:** Tạo mạng `hotel_network`. Khóa triệt để cổng 3306 và 5432 trên máy host. Chỉ public cổng 8081 (Backend) và 80 (Nginx). Bổ sung healthcheck MariaDB qua `mysqladmin ping -h localhost`.
  * **DoD:** Chạy `docker-compose up` sạch sẽ. Quét `nmap -p 3306 localhost` từ host báo `closed`/`filtered`. Backend kết nối DB qua DNS `db:3306`.
* **TASK-D1.2: Dựng Mock API Server phục vụ Dev C lập trình giao diện**
  * **Tệp mục tiêu:** `src/test/resources/mock-server/`, `src/test/java/quanlykhachsan/stubs/MockApiServer.java`.
  * **Mô tả:** Dựng máy chủ HTTP giả lập phục vụ 10 Core Endpoints trả về JSON mẫu bám sát `api-contracts.md` trong vòng 48 giờ đầu Tuần 1.
  * **DoD:** Dev C trỏ `ApiClient` về `http://localhost:8089` để làm giao diện Danh sách phòng, Đặt phòng mà không cần chờ Backend của Dev A.
* **TASK-D1.3: Cấu hình khung kiểm thử tích hợp JUnit 5**
  * **Tệp mục tiêu:** `pom.xml`, `src/test/java/quanlykhachsan/BaseIntegrationTest.java`.
  * **Mô tả:** Cấu hình Maven Surefire và Failsafe. Viết `BaseIntegrationTest` khởi tạo schema CSDL mẫu và tiện ích gửi HTTP request kiểm thử.
  * **DoD:** Chạy `mvn test` trên CI thành công 100% các test case cơ sở.

---

### TUẦN 2: LOGIC NGHIỆP VỤ LÕI, KHÓA LẠC QUAN & MA TRẬN PHÒNG SWING

#### Dev A — Backend API & Infrastructure
* **TASK-A2.1: Danh mục phòng & API tra cứu phòng trống thời gian thực**
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/room/RoomController.java`, `src/quanlykhachsan/backend/room/RoomDao.java`.
  * **Endpoints:** `GET /api/v1/rooms`, `GET /api/v1/rooms/availability`.
  * **Mô tả:** Trả về 200 phòng cùng trạng thái chuẩn (`vacant_clean`, `vacant_dirty`, `occupied_clean`, `occupied_dirty`, `out_of_order`, `reserved`) và số hiệu version. Hỗ trợ lọc theo ngày `?check_in=...&check_out=...`.
  * **DoD:** Thời gian xử lý truy vấn $< 15\text{ms}$ trên 200 phòng nhờ chỉ mục `idx_booking_dates` và `idx_room_status`.
* **TASK-A2.2: Quản lý khách hàng & Tìm kiếm nhanh**
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/customer/CustomerController.java`, `src/quanlykhachsan/backend/customer/CustomerDao.java`.
  * **Endpoints:** `POST /api/v1/customers`, `GET /api/v1/customers?search=...`.
  * **Mô tả:** Tiếp nhận thông tin định danh (CCCD/Passport, SĐT, Email). Ngăn trùng lặp giấy tờ tùy thân. Tìm kiếm nhanh theo chuỗi.
  * **DoD:** Trùng CCCD/Passport trả về `409 Conflict`. Tìm kiếm phản hồi trong $< 20\text{ms}$ qua chỉ mục `idx_customer_identity`.
* **TASK-A2.3: Vòng đời đặt phòng & Tích hợp Khóa lạc quan**
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/booking/BookingController.java`, `src/quanlykhachsan/backend/booking/BookingDao.java`.
  * **Endpoints:** `GET /api/v1/bookings`, `GET /api/v1/bookings/{id}`, `POST /api/v1/bookings`, `PUT /api/v1/bookings/{id}`, `POST /api/v1/bookings/{id}/cancel`.
  * **Mô tả:** Luồng tạo đặt phòng kèm chỉ định `room_id` và `room_version`. Kiểm tra trạng thái và chuyển phòng từ `vacant_clean` sang `reserved`.
  * **DoD:** Ngày đến $\ge$ ngày đi trả về `400 Bad Request`. Nếu phiên bản phòng bị thay đổi, ném ngoại lệ và trả về `409 Conflict`.

#### Dev B — Core Business Logic & Financials
* **TASK-B2.1: Động cơ Khóa lạc quan khi gán phòng (Optimistic Locking Engine)**
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/room/RoomAllocationService.java`.
  * **Mô tả:** Cài đặt logic giao dịch gán phòng với điều kiện version chặt chẽ:
    ```sql
    UPDATE rooms SET status = 'reserved', version = version + 1 
    WHERE id = ? AND version = ? AND status = 'vacant_clean';
    ```
    Nếu `rowsUpdated == 0`, tự động ném `OptimisticLockException` để Controller chuyển hóa thành HTTP `409 Conflict` kèm thông báo `"Phòng đã bị thay đổi trạng thái bởi một giao dịch khác"`.
  * **DoD:** Test 2 luồng đồng thời tranh chấp cùng phòng bảo đảm chính xác 1 luồng nhận `200/201` và 1 luồng nhận `409 Conflict`. Cột `version` tăng đúng 1.
* **TASK-B2.2: Sổ cái Folio & Xử lý thanh toán nguyên tử**
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/booking/FolioService.java`, `src/quanlykhachsan/backend/booking/FolioDao.java`.
  * **Endpoints:** `POST /api/v1/folios/{id}/pay`.
  * **Mô tả:** Khởi tạo Folio chính gắn với đặt phòng. Ghi nhận nợ (tiền phòng, dịch vụ) và có (tiền cọc, thanh toán). Xử lý thanh toán tuân thủ ACID (`setAutoCommit(false)` $\rightarrow$ tạo giao dịch $\rightarrow$ cập nhật số dư $\rightarrow$ `commit()`).
  * **DoD:** Số dư Folio sau thanh toán chính xác 100%. Lỗi giữa chừng tự động rollback, không để lại bản ghi rác.
* **TASK-B2.3: Động cơ phát hiện No-Show & Tự động phạt tiền phòng**
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/booking/NoShowEngine.java`.
  * **Mô tả:** Quét các đặt phòng `confirmed` nhưng khách không đến sau giờ quy định. Chuyển trạng thái sang `no_show`, tính phí phạt 1 đêm vào Folio và chuyển phòng về `vacant_dirty`.
  * **DoD:** Xử lý hàng loạt 50 đặt phòng quá hạn trong một phiên, ghi nhận đầy đủ lịch sử phạt tiền vào sổ cái.

#### Dev C — Desktop Frontend (Java Swing)
* **TASK-C2.1: Loại bỏ Mock Data & Nối API thật vào Form Swing**
  * **Tệp mục tiêu:** `src/quanlykhachsan/frontend/view/RoomForm.java`, `src/quanlykhachsan/frontend/view/BookingForm.java`.
  * **Mô tả:** Xóa toàn bộ dữ liệu mẫu hardcoded. Chuyển sang gọi `RoomAPI` và `BookingAPI` lấy dữ liệu thật từ Backend đổ vào `DefaultTableModel`.
  * **DoD:** 100% dữ liệu hiển thị trên Swing phản ánh chính xác dữ liệu trong MariaDB.
* **TASK-C2.2: Sơ đồ phòng Ma trận 5×N ô 80×60px chuẩn Token UI**
  * **Tệp mục tiêu:** `src/quanlykhachsan/frontend/view/RoomGridPanel.java`, `src/quanlykhachsan/frontend/view/RoomCellComponent.java`.
  * **Mô tả:** Hiển thị 200 phòng trên lưới cuộn gồm 5 cột. Kích thước mỗi ô 80×60px. Số phòng in đậm 12px, tên khách 12px (khi có khách). Màu nền ô tuân thủ `ui-tokens.md`: VC (`#4CAF50`), VD (`#FF9800`), OC (`#2196F3`), OD (`#FF5722`), OOO (`#F44336`), RESERVED (`#9C27B0`).
  * **DoD:** Lưới vẽ 200 phòng trong $< 50\text{ms}$. Click 1 lần cập nhật thanh chi tiết phòng bên phải (300px); double click mở Folio (`F8`).
* **TASK-C2.3: Bảng dữ liệu mật độ cao (Chiều cao dòng 24px)**
  * **Tệp mục tiêu:** `src/quanlykhachsan/frontend/utils/DenseTableRenderer.java`, `src/quanlykhachsan/frontend/view/BookingForm.java`.
  * **Mô tả:** Cài đặt `TableCellRenderer`: cố định dòng cao chính xác 24px; màu dòng xen kẽ (`#FFFFFF` và `#F5F5F5`); hiển thị chấm tròn màu trạng thái 8px đi kèm nhãn text.
  * **DoD:** Hiển thị tối thiểu 15 dòng mà không cần cuộn trên màn hình $1280\times 720\text{px}$. Sắp xếp cột phản hồi trong $< 10\text{ms}$.

#### Dev D — DevOps & QA / Testing
* **TASK-D2.1: Tự động hóa kiểm thử Kịch bản 5 (50 Luồng tranh chấp phòng qua `POST /bookings`)**
  * **Tệp mục tiêu:** `src/test/java/quanlykhachsan/concurrency/ConcurrentRoomAssignmentTest.java`.
  * **Mô tả:** Dùng `CountDownLatch` kích hoạt 50 luồng đồng thời gửi `POST /api/v1/bookings` tranh chấp cùng 1 phòng trống ở cùng 1 mili-giây.
  * **DoD:** Đúng 1 request nhận `200/201 OK`; 49 request nhận `409 Conflict`. Version phòng tăng đúng 1 lần; không bị double-booking.
* **TASK-D2.2: Kiểm thử ma trận phân quyền bảo mật (RBAC Verification)**
  * **Tệp mục tiêu:** `src/test/java/quanlykhachsan/security/RbacSecurityTest.java`.
  * **Mô tả:** Kiểm thử tự động trên toàn bộ API với các JWT: `FrontDesk`, `Housekeeping`, `F&B`, `Admin`.
  * **DoD:** Housekeeping gọi API thanh toán nhận `403 Forbidden`. Lễ tân gọi API quản lý tài khoản nhân viên nhận `403`. Admin truy cập được tất cả.
* **TASK-D2.3: Kiểm tra rò rỉ kết nối HikariCP (Leak Test)**
  * **Tệp mục tiêu:** `src/test/java/quanlykhachsan/database/HikariConnectionLeakTest.java`.
  * **Mô tả:** Bắn 200 request ngẫu nhiên liên tục; sau đó đọc thông số `HikariPoolMXBean.getActiveConnections()`.
  * **DoD:** Số kết nối active trở về 0 sau khi hoàn tất. Không có cảnh báo leak với ngưỡng 5000ms.

---

### TUẦN 3: ĐỘNG CƠ TÀI CHÍNH, ĐỊNH TUYẾN FOLIO, NIGHT AUDIT & XỬ LÝ NGOẠI LỆ

#### Dev A — Backend API & Infrastructure
* **TASK-A3.1: Middleware đóng băng hệ thống (System Freeze 00:00 – 00:05)**
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/utils/SystemStatusFilter.java`, `src/quanlykhachsan/backend/system/SystemStateHolder.java`.
  * **Mô tả:** Cài đặt cờ `volatile boolean isSystemFrozen`. Khi kích hoạt Night Audit, mọi request ghi (`POST`, `PUT`, `DELETE`) bị chặn đứng tại Filter và trả về HTTP `503 Service Unavailable` kèm header `Retry-After: 300`. Các truy vấn đọc (`GET`) vẫn hoạt động bình thường.
  * **DoD:** Request ghi trong giờ đóng băng nhận 503 trong $< 1\text{ms}$ mà không tiêu tốn kết nối JDBC từ HikariCP.
* **TASK-A3.2: Tiếp nhận giao dịch POS ngoại tuyến (Store-and-Forward Sync)**
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/booking/PosSyncController.java`, `src/quanlykhachsan/backend/booking/PosSyncDao.java`.
  * **Endpoints:** `POST /api/v1/pos/sync`.
  * **Mô tả:** Tiếp nhận lô giao dịch F&B/Spa phát sinh từ POS trong khung giờ bị khóa. Xác thực tính duy nhất của Idempotency Key; ghi nhận chi phí vào Folio dưới ngày kinh doanh mới.
  * **DoD:** Kịch bản 3: Hóa đơn phát sinh lúc 00:02 được đồng bộ lên lúc 00:10 được ghi nhận đúng vào ngày kế toán mới, không làm sai lệch ngày cũ đã chốt.
* **TASK-A3.3: Tối ưu bộ phân tích tham số Router & Header Processing**
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/utils/Router.java`, `src/quanlykhachsan/backend/utils/HttpExchangeHelper.java`.
  * **Mô tả:** Tối ưu hóa việc biên dịch sẵn Pattern Regex cho Router. Giải mã an toàn query string và header `Idempotency-Key`, `Authorization`.
  * **DoD:** Thời gian định tuyến trung bình mỗi request giảm xuống dưới 0.5ms.

#### Dev B — Core Business Logic & Financials
* **TASK-B3.1: Động cơ kiểm toán đêm Night Audit 7 bước theo Checkpoints**
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/system/NightAuditEngine.java`, `src/quanlykhachsan/backend/system/NightAuditStep.java`.
  * **Mô tả:** Chia nhỏ quy trình 7 bước thành các khối giao dịch độc lập (Chunking) kết hợp lưu trạng thái vào bảng `audit_checkpoints` để tránh phình Undo Log của MariaDB:
    * Bước 1: Khởi động Đóng băng (`SystemStateHolder.setFrozen(true)`).
    * Bước 2: Kết chuyển tiền phòng (xử lý từng khối 50 phòng/transaction, ghi nhận tiền phòng và thuế).
    * Bước 3: Xử lý No-Show (chuyển trạng thái và tính phạt).
    * Bước 4: Đồng bộ hóa bút toán phụ trợ (chốt số liệu dịch vụ và POS).
    * Bước 5: Cân đối số dư sổ cái ($\sum\text{Nợ} == \sum\text{Có} + \text{Công nợ phải thu}$).
    * Bước 6: Lập Snapshot Báo cáo Kinh doanh Ngày (DBR).
    * Bước 7: Đổi ngày kinh doanh & Mở khóa (`SystemStateHolder.setFrozen(false)`).
  * **DoD:** Hoàn tất trong $< 45\text{s}$ cho 200 phòng. Nếu lỗi ở bước nào, hệ thống ghi nhận checkpoint lỗi, cho phép chạy lại từ điểm dừng mà không tắc nghẽn pool kết nối.
* **TASK-B3.2: Tách & Định tuyến Hóa đơn Folio đa luồng (Scenario 1)**
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/booking/FolioRoutingEngine.java`.
  * **Endpoints:** `POST /api/v1/folios/{id}/split`, `PUT /api/v1/folios/{id}/routing`.
  * **Mô tả:** Hỗ trợ tách Folio cho khách đoàn: `FOLIO-MASTER` (chịu tiền phòng và thuế cố định); `FOLIO-GUEST` (chịu chi phí cá nhân: Mini-bar, Giặt là).
  * **DoD:** Dịch vụ phát sinh tự động ghi nhận nợ vào đúng `FOLIO-GUEST`. Đại diện đoàn thanh toán Master Folio không thể đóng các Folio cá nhân nếu còn dư nợ.
* **TASK-B3.3: Xử lý hạ hạng phòng khi phòng OOO lúc khách đến muộn (Scenario 2)**
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/booking/RoomDowngradeService.java`.
  * **Mô tả:** Xử lý khi phòng đã đặt gặp sự cố sang `out_of_order`. Cho phép quyền quản lý gán phòng hạng dưới, tự động tính lại giá phòng trên Folio về mức thấp hơn.
  * **DoD:** Cố tình gán phòng OOO lập tức bị chặn với mã 409; hạ hạng phòng thành công tự động cập nhật lại tổng tiền trên hóa đơn.

#### Dev C — Desktop Frontend (Java Swing)
* **TASK-C3.1: Hộp thoại Wizard Nhận/Trả phòng & Hiển thị Folio thời gian thực**
  * **Tệp mục tiêu:** `src/quanlykhachsan/frontend/view/BookingWizardDialog.java`, `src/quanlykhachsan/frontend/view/RoomActionDialog.java`, `src/quanlykhachsan/frontend/view/PaymentForm.java`.
  * **Mô tả:** Nối luồng Check-in (`F2`) và Check-out (`F3`) với API thật. Hiển thị số dư Folio được chia tách (Master vs Cá nhân) trực quan.
  * **DoD:** Bấm "Thanh toán & Trả phòng" gửi request kèm `Idempotency-Key` (UUIDv4). Nhận mã 200 tự động chuyển phòng sang `vacant_dirty` và vẽ lại sơ đồ.
* **TASK-C3.2: Banner cảnh báo Đóng băng hệ thống & Giả lập POS Store-and-Forward**
  * **Tệp mục tiêu:** `src/quanlykhachsan/frontend/view/SystemStatusBanner.java`, `src/quanlykhachsan/frontend/view/OfflinePosDialog.java`.
  * **Mô tả:** Khi nhận mã 503 trong giờ Night Audit, hiển thị banner hổ phách cảnh báo hệ thống đang khóa. Cung cấp hộp thoại lưu tạm hóa đơn cục bộ để gửi lại sau.
  * **DoD:** Vô hiệu hóa các nút chỉnh sửa dữ liệu trong giờ khóa. Các giao dịch lưu tạm tự động đẩy lên server khi hệ thống mở khóa.
* **TASK-C3.3: Thanh tìm kiếm nhanh & Bộ lọc tức thì (F4)**
  * **Tệp mục tiêu:** `src/quanlykhachsan/frontend/view/QuickFilterBar.java`, `src/quanlykhachsan/frontend/view/StaffForm.java`.
  * **Mô tả:** Nhấn `F4` đưa con trỏ vào ô tìm kiếm. Lọc tức thì theo Số phòng, Tên khách, Mã đặt phòng với cơ chế debounce 300ms.
  * **DoD:** Gõ "501" lọc sơ đồ phòng trong $< 10\text{ms}$. Xóa trắng ô tìm kiếm phục hồi nguyên vẹn lưới 200 phòng.

#### Dev D — DevOps & QA / Testing
* **TASK-D3.1: Tự động hóa kiểm thử Kịch bản 1 (Check-in đoàn phân tách Master/Guest Folio)**
  * **Tệp mục tiêu:** `src/test/java/quanlykhachsan/scenarios/GroupSplitCheckinTest.java`.
  * **Mô tả:** Kịch bản tự động: Tạo đoàn 20 phòng. Check-in riêng lẻ cho 5 phòng đến sớm. Xác nhận 5 phòng chuyển sang `occupied_clean`, 15 phòng còn lại giữ nguyên `reserved`. Chi phí dịch vụ trên 5 phòng vào đúng Guest Folio.
  * **DoD:** 100% assertions của Kịch bản 1 chạy đạt trong $< 3$ giây.
* **TASK-D3.2: Tự động hóa kiểm thử Kịch bản 3 (POS ngoại tuyến trong kỳ Night Audit)**
  * **Tệp mục tiêu:** `src/test/java/quanlykhachsan/scenarios/PosOfflineNightAuditTest.java`.
  * **Mô tả:** Giả lập POS gửi hóa đơn lúc 00:02 (lúc đóng băng), nhận mã 503 kèm `Retry-After: 300`. Giả lập gửi lại lúc 00:06, xác nhận giao dịch ghi nhận vào ngày mới.
  * **DoD:** Kiểm thử thành công cả 3 assertions quy định tại `docs/dev-spec/test-scenarios.md`.
* **TASK-D3.3: Tải nặng HikariCP & Kiểm tra rò rỉ (50 Luồng, 500 req/s)**
  * **Tệp mục tiêu:** `src/test/java/quanlykhachsan/performance/HikariStressTest.java`.
  * **Mô tả:** Bắn tải 500 req/s trong 5 phút vào API phòng trống và đặt phòng. Giám sát số kết nối mượn/trả qua JMX MBean.
  * **DoD:** Không phát sinh cảnh báo leak với ngưỡng `leakDetectionThreshold=5000ms`. Thời gian chờ mượn kết nối tối đa $< 20\text{ms}$.

---

### TUẦN 4: TÍCH HỢP TOÀN DIỆN, STRESS TEST, KIỂM TOÁN BẢO MẬT & ĐÓNG BĂNG HỆ THỐNG

#### Dev A — Backend API & Infrastructure
* **TASK-A4.1: Graceful Shutdown Hook & Đóng an toàn kết nối HikariCP**
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/Main.java`, `src/quanlykhachsan/backend/utils/DatabaseConnection.java`.
  * **Mô tả:** Đăng ký `Runtime.getRuntime().addShutdownHook()`. Khi nhận tín hiệu `SIGTERM`, cho phép request đang xử lý tối đa 10 giây để hoàn tất, sau đó ngắt `HttpServer` và đóng `HikariDataSource`.
  * **DoD:** Lệnh `docker stop` xuống container ghi nhận tiến trình tắt sạch, số lượng giao dịch bị ngắt quãng giữa chừng bằng 0.
* **TASK-A4.2: Tối ưu Gson TypeAdapters & Profiling bộ nhớ JVM**
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/utils/JsonUtil.java`, `src/quanlykhachsan/backend/utils/LocalDateTimeAdapter.java`.
  * **Mô tả:** Đăng ký TypeAdapter tùy biến cho `LocalDateTime` và `BigDecimal`. Dùng VisualVM theo dõi bộ nhớ heap, triệt tiêu rác bộ nhớ.
  * **DoD:** Xử lý JSON đạt trên 5,000 ops/giây/worker thread; Heap duy trì ổn định sau chu kỳ Full GC.
* **TASK-A4.3: Đóng băng đặc tả hợp đồng API OpenAPI 3.0**
  * **Tệp mục tiêu:** `docs/dev-spec/api-contracts.md`, `docs/dev-spec/openapi-p1.json`.
  * **Mô tả:** Xuất bản đặc tả OpenAPI 3.0 cho 10 Core Endpoints của Giai đoạn 1. Đảm bảo toàn bộ mã lỗi, DTO và header đồng nhất 100% với mã nguồn.
  * **DoD:** Kiểm tra tự động xác thực không có sai lệch giữa đặc tả và phản hồi thực tế từ hệ thống.

#### Dev B — Core Business Logic & Financials
* **TASK-B4.1: Động cơ tổng hợp Báo cáo Kinh doanh Ngày DBR (ADR & RevPAR)**
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/report/DbrReportService.java`, `src/quanlykhachsan/backend/report/ReportDao.java`.
  * **Mô tả:** Tổng hợp các chỉ số tài chính vào bảng `daily_business_reports`: Doanh thu tổng, Doanh thu phòng, Công suất phòng, ADR, RevPAR.
  * **DoD:** Công thức tính đúng chuẩn ngành khách sạn:
    $$\text{ADR} = \frac{\text{Doanh thu phòng}}{\text{Số phòng thực ở}}, \quad \text{RevPAR} = \frac{\text{Doanh thu phòng}}{\text{Tổng số phòng có sẵn}}$$
    Số liệu khớp từng xu so với sổ cái giao dịch.
* **TASK-B4.2: Quy trình Rollback & Khôi phục sự cố Night Audit theo Checkpoints**
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/system/NightAuditRollbackHandler.java`.
  * **Mô tả:** Giả lập sự cố mất kết nối CSDL đột ngột tại Bước 4 của Night Audit. Đảm bảo cơ chế phục hồi đọc bảng `audit_checkpoints` để hoàn tác phần dữ liệu dở dang và đưa hệ thống trở về trạng thái an toàn trước kiểm toán.
  * **DoD:** Đánh sập tiến trình giữa chừng: hệ thống tự động hoàn tác sạch sẽ, không bị tính trùng tiền phòng khi chạy lại.
* **TASK-B4.3: Kiểm thử biên tài chính (Split Bill, Hoàn tiền, Làm tròn)**
  * **Tệp mục tiêu:** `src/test/java/quanlykhachsan/financial/FinancialEdgeCaseTest.java`.
  * **Mô tả:** Kiểm thử thanh toán phức tạp: Chia 3 thẻ tín dụng, ghi có điều chỉnh giảm trừ, làm tròn tỷ giá ngoại tệ.
  * **DoD:** Số dư cuối kỳ trên Folio khi làm thủ tục check-out đạt mức cân bằng chính xác: $0.00$.

#### Dev C — Desktop Frontend (Java Swing)
* **TASK-C4.1: Kiểm toán hiệu năng Swing Event Dispatch Thread (0 EDT Lock > 16ms)**
  * **Tệp mục tiêu:** `src/quanlykhachsan/frontend/utils/EdtViolationDetector.java`, `src/quanlykhachsan/frontend/view/StaffForm.java`.
  * **Mô tả:** Tích hợp bộ đo thời gian thực thi trên luồng EDT. Cảnh báo bất kỳ tác vụ giao diện nào gây nghẽn khung hình quá 16ms (ngưỡng 60 FPS).
  * **DoD:** Thao tác liên tục (chuyển tab, cuộn sơ đồ 200 phòng, mở hộp thoại) không ghi nhận cảnh báo nghẽn EDT nào vượt quá 16ms.
* **TASK-C4.2: Hỗ trợ hiển thị đa màn hình cho Lễ tân (Tách cửa sổ Sơ đồ phòng)**
  * **Tệp mục tiêu:** `src/quanlykhachsan/frontend/view/DetachedRoomMapFrame.java`, `src/quanlykhachsan/frontend/view/StaffForm.java`.
  * **Mô tả:** Hỗ trợ tách Sơ đồ phòng ra cửa sổ `JFrame` độc lập trên Màn hình 2. Tự động ghi nhớ vị trí X, Y, kích thước và chỉ số màn hình vào tệp `.properties` khi thoát.
  * **DoD:** Mở ứng dụng trên máy tính 2 màn hình: cửa sổ làm việc chính ở Màn hình 1, Sơ đồ phòng tự động bung mở toàn màn hình tại Màn hình 2.
* **TASK-C4.3: Giả lập ca trực Lễ tân 2 giờ & Tinh chỉnh trải nghiệm phím tắt**
  * **Tệp mục tiêu:** Toàn bộ gói `src/quanlykhachsan/frontend/view/`.
  * **Mô tả:** Giả lập ca trực thực tế: Check-in 30 khách, check-out 20 khách, đổi phòng 3 khách, phát sinh 40 chi phí dịch vụ. Tinh chỉnh thứ tự phím Tab và vòng lấy nét.
  * **DoD:** Nhân viên hoàn tất quy trình Check-in trong $< 15$ giây hoàn toàn bằng bàn phím (`F2` $\rightarrow$ `Tab` $\rightarrow$ `F4` $\rightarrow$ `Enter`).

#### Dev D — DevOps & QA / Testing
* **TASK-D4.1: Quét lỗ hổng bảo mật OWASP & Đóng gói Docker Production**
  * **Tệp mục tiêu:** `pom.xml`, `.github/workflows/security.yml`.
  * **Mô tả:** Chạy OWASP Dependency-Check và quét image qua Trivy. Rà soát SQL Injection, cấu hình phân quyền và hardcoded secrets.
  * **DoD:** 0 lỗ hổng bảo mật mức Critical và High trong toàn bộ thư viện và container.
* **TASK-D4.2: Kiểm thử ngâm tải 50 người dùng đồng thời trong 30 phút (Soak Test)**
  * **Tệp mục tiêu:** `src/test/resources/load-test/phase1-soak-test.jmx`.
  * **Mô tả:** Giả lập 50 nhân viên lễ tân và buồng phòng thao tác liên tục trên 10 Core Endpoints trong 30 phút.
  * **DoD:**
    * P95 Response Time $< 30\text{ms}$; P99 Response Time $< 50\text{ms}$.
    * Tỷ lệ lỗi $= 0.00\%$ (ngoại trừ các mã lỗi 409 có chủ đích trong kịch bản tranh chấp).
    * CPU máy chủ $< 25\%$; bộ nhớ RAM không rò rỉ.
* **TASK-D4.3: Kịch bản triển khai tự động & Xác thực Healthcheck**
  * **Tệp mục tiêu:** `docker-compose.yml`, `scripts/deploy-phase1.sh`, `scripts/healthcheck.sh`.
  * **Mô tả:** Viết script khởi động toàn bộ hệ thống từ đầu trên máy chủ Ubuntu 22.04 LTS: tạo mạng, mount volume, nạp schema SQL, khởi động container và kiểm tra tính sẵn sàng.
  * **DoD:** Chạy `./deploy-phase1.sh` hoàn tất và toàn bộ container đạt trạng thái healthy trong $< 90$ giây.

---

## III. BẢNG MA TRẬN PHỤ THUỘC & BÀN GIAO CHÉO (CROSS-DEVELOPER DEPENDENCY MATRIX)

| Upstream Task | Chủ sở hữu | Downstream Task | Bên tiêu thụ | Thành phần bàn giao / Interface | Hạn chót (SLA) | Phương án dự phòng (Decoupling) |
|---|:---:|---|:---:|---|:---:|---|
| **TASK-A1.3 / A1.4** | **Dev A** | **TASK-C1.1** | **Dev C** | OpenAPI JSON Spec & DTO Auth/Booking | **W1 Ngày 2, 17:00** | Dev D cung cấp `MockApiServer` (TASK-D1.2) để Dev C code UI không phải chờ backend. |
| **TASK-B1.1** | **Dev B** | **TASK-A2.1 / A2.3** | **Dev A** | SQL Migration có `rooms.version` và `idempotency_keys` | **W1 Ngày 3, 12:00** | Dev A định nghĩa DTO và truy vấn trên schema stub cục bộ cho đến khi SQL được commit. |
| **TASK-B2.1** | **Dev B** | **TASK-A2.3** | **Dev A** | Service gán phòng ném `OptimisticLockException` | **W2 Ngày 2, 17:00** | Dev A định nghĩa Interface Java, ném ngoại lệ giả lập để hoàn thiện Controller. |
| **TASK-A2.1 / A2.3** | **Dev A** | **TASK-C2.1 / C2.2** | **Dev C** | API chạy thực tế tại `:8081/api/v1/rooms` và `/bookings` | **W2 Ngày 4, 12:00** | Dev C kiểm thử trên `MockApiServer`, chỉ đổi base URL sang `:8081` khi backend sẵn sàng. |
| **TASK-A3.1** | **Dev A** | **TASK-B3.1** | **Dev B** | Cờ `SystemStateHolder.isSystemFrozen` và Filter 503 | **W3 Ngày 2, 12:00** | Dev B dùng cờ `volatile boolean` cục bộ trong unit test trước khi tích hợp vào Filter. |
| **TASK-B3.1** | **Dev B** | **TASK-D3.2** | **Dev D** | API kích hoạt Night Audit thủ công & Event log hooks | **W3 Ngày 4, 12:00** | Dev D viết trước script test với mock response, sau đó trỏ sang endpoint thật. |
| **TASK-C2.2** | **Dev C** | **TASK-C4.2** | **Dev C** | `RoomGridPanel` sẵn sàng cho việc tách Frame | **W4 Ngày 1, 12:00** | Nhiệm vụ nội bộ của Dev C; khung cửa sổ được dựng độc lập với logic vẽ lưới phòng. |

---

## IV. BẢNG TIÊU CHÍ NGHIỆM THU MỐC GO/NO-GO CUỐI TUẦN 4

Hệ thống phải đạt **100% các tiêu chí dưới đây** trước khi được phép chuyển sang Phase 2 (AI Gateway & Guardrails). Bất kỳ một tiêu chí nào không đạt đều kích hoạt quyết định "NO-GO":

### 1. Tính toàn vẹn chức năng & 10 Core Endpoints
* [ ] **Hoàn thiện đủ 10 Core REST Endpoints:** `POST /auth/login` (cấp JWT hợp lệ), `GET /rooms`, `GET /rooms/availability`, `POST /customers`, `GET /bookings`, `GET /bookings/{id}`, `POST /bookings`, `PUT /bookings/{id}`, `POST /bookings/{id}/cancel`, `POST /folios/{id}/pay` vượt qua 100% các ca kiểm thử tích hợp.
* [ ] **Kịch bản 1 (Check-in đoàn phân tách Master/Guest Folio):** Cho phép 5 phòng đến sớm nhận phòng độc lập; tiền phòng đổ về `FOLIO-MASTER`, chi phí dịch vụ cá nhân đổ về `FOLIO-GUEST`.
* [ ] **Kịch bản 2 (Hạ hạng phòng do sự cố OOO):** Cấm check-in vào phòng OOO (báo mã 409); ghi nhận quyền can thiệp của quản lý gán phòng hạng dưới và tự động điều chỉnh giá trên Folio.
* [ ] **Kịch bản 3 (POS ngoại tuyến trong kỳ Night Audit):** Giao dịch phát sinh lúc 00:00–00:05 nhận mã `503 Retry-After: 300`. Giao dịch đồng bộ lại sau khi mở khóa được ghi nhận chuẩn xác vào ngày kinh doanh mới.

### 2. Tải đồng thời cao & Khóa lạc quan (Optimistic Locking)
* [ ] **Kịch bản 5 (Xung đột gán phòng qua `POST /bookings`):** 50 luồng đồng thời tranh chấp gán cùng 1 phòng: đúng 1 luồng nhận mã thành công `200/201`, 49 luồng còn lại nhận `409 Conflict`. Cột `version` của phòng tăng đúng 1 lần. Tuyệt đối không xảy ra đặt trùng.
* [ ] **Thực thi Idempotency:** Gửi lại request tạo đặt phòng hoặc thanh toán trùng lặp mang cùng `Idempotency-Key` (UUIDv4) trả về nguyên vẹn kết quả cũ từ cache/DB, không sinh thêm dòng dữ liệu mới.

### 3. Động cơ tài chính & Kiểm toán đêm Chunking
* [ ] **Night Audit 7 bước hoàn tất trong $< 45\text{s}$:** Hệ thống tự động đóng băng các request ghi trong 5 phút (00:00–00:05). Thực hiện chia nhỏ giao dịch theo khối phòng kết hợp ghi nhận `audit_checkpoints`.
* [ ] **Cân bằng sổ cái tuyệt đối:** $\sum\text{Bút toán Nợ} == \sum\text{Bút toán Có} + \text{Công nợ}$ với độ lệch bằng đúng $0.00$. Báo cáo DBR tổng hợp chuẩn xác các chỉ số ADR và RevPAR.
* [ ] **Phục hồi sau thảm họa (Disaster Recovery):** Cố tình ngắt kết nối giữa chừng ở Bước 4: hệ thống rollback an toàn dựa trên checkpoint, không làm sai lệch số liệu tài chính khi chạy lại.

### 4. Quản lý kết nối HikariCP & Hiệu năng
* [ ] **Kiểm soát rò rỉ kết nối HikariCP tuyệt đối:** Pool cố định 20 kết nối, `connectionTimeout=30000ms`, `leakDetectionThreshold=5000ms`. Chạy liên tục 10,000 request tải cao: số kết nối active quay về 0 khi kết thúc test, không phát sinh log cảnh báo rò rỉ giả.
* [ ] **Cam kết độ trễ (SLA):** P95 Response Time $< 30\text{ms}$, P99 Response Time $< 50\text{ms}$ trên toàn bộ các endpoint đọc dưới tải 50 luồng đồng thời.
* [ ] **Tính ổn định của Native HttpServer:** `com.sun.net.httpserver` phối hợp cùng `Executors.newFixedThreadPool(20)` duy trì ổn định mức tải 500 req/s mà không bị tràn bộ nhớ hay nghẽn luồng.

### 5. Trải nghiệm ứng dụng Desktop Swing FlatLaf
* [ ] **Loại bỏ 100% Mock Data:** Toàn bộ giao diện Desktop chạy trực tiếp trên API Backend thực tế.
* [ ] **Độ phản hồi của giao diện (0 EDT Lock > 16ms):** Toàn bộ tương tác mạng đều chạy qua `SwingWorker` hoặc `HttpClient` bất đồng bộ, bảo đảm tốc độ khung hình mượt mà 60 FPS.
* [ ] **Tuân thủ chặt chẽ Tokens UI:** Sơ đồ phòng hiển thị 200 phòng (5 cột × 40 hàng, ô $80\times 60\text{px}$) với các mã màu chuẩn xác tại `ui-tokens.md`. Bảng dữ liệu có chiều cao dòng chính xác $24\text{px}$.
* [ ] **Phím tắt phần cứng (F1–F10):** Hoạt động nhạy bén trên toàn hệ thống, không bị kẹt focus. Thao tác Check-in hoàn tất trong $< 15$ giây hoàn toàn bằng bàn phím.

### 6. An toàn hạ tầng & An ninh mạng
* [ ] **Cách ly mạng vật lý:** Cổng 3306 (MariaDB) và cổng 5432 (pgvector) hoàn toàn không bị lộ ra máy chủ host hoặc mạng LAN, chỉ có thể giao tiếp trong cụm mạng nội bộ Docker.
* [ ] **Quét mã độc & Lỗ hổng:** 0 lỗ hổng bảo mật mức độ Critical và High từ báo cáo quét OWASP Dependency-Check.
* [ ] **Khả năng triển khai:** Khởi động hệ thống mới từ script `./deploy-phase1.sh` đạt trạng thái healthy và sẵn sàng phục vụ trong $< 90$ giây.

---

## V. BIÊN BẢN KÝ DUYỆT NGHIỆM THU

| Chức danh | Chữ ký / Trạng thái | Ngày phê duyệt |
|---|:---:|:---:|
| **Technical Lead (Kiến trúc trưởng)** | `[ PHÊ DUYỆT THÔNG QUA BẢN HIỆU CHỈNH ]` | `2026-10-12` |
| **QA & DevOps Lead (Trưởng nhóm kiểm thử)** | `[ XÁC NHẬN BỘ TEST HARNESS ]` | `2026-10-12` |
| **Core PMS Backend Lead (Dev A & B)** | `[ CAM KẾT TIẾN ĐỘ WBS HIỆU CHỈNH ]` | `2026-10-12` |
| **Desktop Frontend Lead (Dev C)** | `[ XÁC NHẬN GIAO DIỆN KHÔNG PHỤ THUỘC ]` | `2026-10-12` |
