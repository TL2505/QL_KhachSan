# BIÊN BẢN GHI NHẬN QUYẾT ĐỊNH KIẾN TRÚC (ADR) — GIAI ĐOẠN 2 & 3
**Dự án:** Hệ thống Quản trị Khách sạn 5★ & Nền tảng AI (`QL_KhachSan` — 200 Phòng)  
**Tiêu chuẩn cơ sở:** Pure Java 17 (`com.sun.net.httpserver`), HikariCP 5.1.0, MariaDB 11 (InnoDB), Java Swing + FlatLaf 3.4.1, Containerized AI Microservice (Python 3.11, FastAPI, PaddleOCR, InsightFace, Faiss, Prophet).  
**Kỷ luật tuyệt đối:** 0 Spring Boot, 0 Hibernate, 0 Jakarta EE, 0 Lombok.

---

## BẢNG DANH MỤC CÁC QUYẾT ĐỊNH KIẾN TRÚC

| Mã ADR | Tiêu đề Quyết định Kiến trúc | Trạng thái | Ngày Ký duyệt |
|---|---|:---:|:---:|
| **ADR-05** | Xử lý Tiền kiểm CORS & Hỗ trợ Tiêu đề `Idempotency-Key` trên Java thuần | `ĐÃ CHẤP THUẬN` | `2026-10-14` |
| **ADR-06** | Bất biến Số dư Kế toán Folio Không Âm & Hạch toán Tiền thừa `credit_adjustment` | `ĐÃ CHẤP THUẬN` | `2026-10-14` |
| **ADR-07** | Ngưỡng Tương đồng Cosine Tự thích ứng FaceNet (0.68) trong So khớp Sinh trắc học | `ĐÃ CHẤP THUẬN` | `2026-10-14` |
| **ADR-08** | Kho Vector Faiss Nhúng Cục bộ cho RAG Quy chế (Thay thế pgvector) | `ĐÃ CHẤP THUẬN` | `2026-10-14` |
| **ADR-09** | Giao thức Giả lập Ngày Đầu tuần (Day-1 Mock Protocol) Giải phóng Tiến độ 4 Kỹ sư | `ĐÃ CHẤP THUẬN` | `2026-10-14` |

---

## ADR-05: Xử lý Tiền kiểm CORS & Hỗ trợ Tiêu đề `Idempotency-Key` trên Java thuần

### 1. Ngữ cảnh & Bài toán Kỹ thuật
Trong Giai đoạn 2, cổng đặt phòng công cộng (Web Booking Engine phát triển bằng HTML5/JS/CSS thuần, 0 NPM) được đưa vào vận hành để phục vụ khách hàng trực tuyến tra cứu và đặt phòng từ trình duyệt máy tính và điện thoại. Do ứng dụng Web chạy trên origin trình duyệt (ví dụ: `http://localhost:8081` hoặc tên miền phụ) và có gửi kèm các tiêu đề HTTP tùy biến (`Idempotency-Key`, `X-Signature`, `Authorization`), các trình duyệt hiện đại sẽ tự động gửi yêu cầu tiền kiểm preflight `OPTIONS` trước khi bắn các mutation request (`POST /api/v1/bookings/public`).

Với ràng buộc kiến trúc **Pure Java 17** (`com.sun.net.httpserver`), hệ thống không sử dụng các framework như Spring MVC hay Jakarta servlet filter. Mặc định, `HttpServer` so khớp đường dẫn chính xác và điều hướng method trực tiếp tới handler. Nếu một yêu cầu `OPTIONS` gửi tới một đường dẫn chỉ cấu hình `POST`, máy chủ sẽ trả về `405 Method Not Allowed`, hoặc nếu đi qua `JwtFilter`, nó sẽ từ chối với mã lỗi `401 Unauthorized`, làm tê liệt hoàn toàn luồng đặt phòng của khách hàng trên Web.

### 2. Quyết định Kiến trúc
1. Xây dựng bộ lọc `CorsFilter` kế thừa `com.sun.net.httpserver.Filter` và đặt ở **vị trí ngoài cùng (Outermost filter)** trong chuỗi xử lý HTTP pipeline.
2. `CorsFilter` chủ động đánh chặn toàn bộ các yêu cầu HTTP `OPTIONS` và phản hồi lập tức với mã `HTTP/1.1 204 No Content` kèm theo đầy đủ các header tiền kiểm:
   - `Access-Control-Allow-Origin: *` (hoặc tên miền sản xuất được cấp phép)
   - `Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS`
   - `Access-Control-Allow-Headers: Content-Type, Authorization, Idempotency-Key, X-Signature, X-Requested-With`
   - `Access-Control-Max-Age: 86400` (Bộ nhớ đệm preflight 24 giờ)
3. Đối với tất cả các HTTP method khác (`GET`, `POST`, `PUT`, `DELETE`), `CorsFilter` tự động tiêm tiêu đề `Access-Control-Allow-Origin: *` vào response trước khi chuyển giao tiếp cho các bộ lọc phía sau.
4. Bổ sung mảng danh sách trắng tập trung `PUBLIC_PATHS` trong `JwtFilter`:
   ```java
   private static final String[] PUBLIC_PATHS = {
       "/api/v1/auth/login",
       "/api/v1/auth/guest-otp",
       "/api/v1/rooms/availability",
       "/api/v1/pricing/quote",
       "/api/v1/bookings/public",
       "/booking"
   };
   ```
   Mọi yêu cầu khớp với danh sách này sẽ được bỏ qua khâu kiểm tra token JWT.

### 3. Hệ quả & Đánh đổi
* **Tích cực:**
  - Khách hàng trên trình duyệt web có thể gửi request kèm khóa khử trùng lặp `Idempotency-Key` mượt mà, không gặp lỗi CORS.
  - Hoàn toàn độc lập với framework ngoài (0 Spring Boot, 0 Jakarta EE); vận hành gọn nhẹ trên Java SE 17 thuần.
  - Phản hồi tiền kiểm hoàn tất trong $< 0.2\text{ms}$ mà không tiêu tốn kết nối CSDL từ pool HikariCP.
* **Tiêu cực / Đánh đổi:**
  - Danh sách `PUBLIC_PATHS` phải được quản lý và rà soát nghiêm ngặt để tránh vô tình để lộ các endpoint quản trị nội bộ của khách sạn.

### 4. Trạng thái
`ĐÃ CHẤP THUẬN (ACCEPTED)`

---

## ADR-06: Bất biến Số dư Kế toán Folio Không Âm & Hạch toán Tiền thừa `credit_adjustment`

### 1. Ngữ cảnh & Bài toán Kỹ thuật
Trong hệ thống quản trị khách sạn 5 sao, hóa đơn tổng hợp (Folio) đại diện cho sổ cái theo dõi công nợ phải thu của khách hàng. Trong quá trình thanh toán trả phòng hoặc chuyển khoản VietQR / Napas 247, khách hàng thường có thói quen làm tròn số tiền chuyển khoản lớn hơn số tiền ghi trên hóa đơn (ví dụ: hóa đơn 4.500.000 VNĐ nhưng chuyển khoản chẵn 5.000.000 VNĐ).

Nếu hệ thống xử lý một cách ngây thơ bằng cách lấy số dư trừ đi số tiền nhận được, số dư sẽ trở thành `balance = -500.000,00 VNĐ`. Việc để số dư Folio bị âm dẫn đến các rủi ro kế toán nghiêm trọng:
- Vi phạm nguyên lý Kế toán Kép (Double-Entry Bookkeeping), trong đó khoản phải thu không thể là một tài sản âm.
- Khi kiểm toán thuế theo Thông tư 78/2021/TT-BTC của Bộ Tài chính, số dư âm làm sai lệch tổng doanh thu chịu thuế và gây lỗi khi phát hành hóa đơn điện tử VAT.
- Gây mâu thuẫn trong việc xác định liệu khách đã hoàn tất nghĩa vụ thanh toán hay khách sạn đang nợ tiền mặt của khách.

### 2. Quyết định Kiến trúc
1. **Bất biến CSDL:** Thiết lập ràng buộc kiểm tra toàn vẹn trên bảng `folios` trong MariaDB InnoDB:
   ```sql
   ALTER TABLE folios ADD CONSTRAINT chk_folio_balance_non_negative CHECK (balance >= 0.00);
   ```
2. **Chuẩn hóa ID Parity:** Tách biệt rõ ràng 2 trường định danh trên mọi bảng Folio:
   - `id`: `BIGINT AUTO_INCREMENT PRIMARY KEY` (phục vụ liên kết khóa ngoại và tối ưu chỉ mục nội bộ CSDL).
   - `code`: `VARCHAR(64) NOT NULL UNIQUE` (mã nghiệp vụ công khai, ví dụ: `FOLIO-MASTER-105`, `FOLIO-GUEST-105`) dùng làm nội dung chuyển khoản ngân hàng và in trên hóa đơn.
3. **Thuật toán Quyết toán Tiền thừa Nguyên tử:** Trong `FolioPaymentService.java`, thực thi giao dịch thanh toán trong khối nguyên tử (`setAutoCommit(false)`):
   - Khóa dòng dữ liệu: `SELECT balance, status FROM folios WHERE id = ? FOR UPDATE`.
   - Nếu số tiền thanh toán $P \le \text{số dư hiện tại } B$:
     - Chèn bản ghi `folio_transactions` với `charge_type = 'payment'`, `amount = P`.
     - Cập nhật `folios SET balance = balance - P`.
   - Nếu số tiền thanh toán $P > \text{số dư hiện tại } B$ (Thanh toán dư tiền):
     - Tính tiền thừa: `excess = P - B`.
     - Chèn giao dịch 1: `charge_type = 'payment'`, `amount = B`.
     - Chèn giao dịch 2: `charge_type = 'credit_adjustment'`, `amount = excess`, `description = 'Excess overpayment credit'`.
     - Cập nhật `folios SET balance = 0.00, status = 'settled'`.
4. Đảm bảo số dư Folio **luôn luôn không âm ($\ge 0.00$) trong mọi tình huống**.

### 3. Hệ quả & Đánh đổi
* **Tích cực:**
  - Bảo đảm 100% tính toàn vẹn toán học và pháp lý của sổ cái kế toán khách sạn.
  - Hoàn toàn tương thích với quy chuẩn xuất hóa đơn điện tử VAT theo Thông tư 78.
  - Khoản tiền dư được theo dõi minh bạch dưới dạng công nợ phải trả / tiền đặt cọc của khách thay vì làm hỏng số dư Folio.
* **Tiêu cực / Đánh đổi:**
  - Khi có thanh toán thừa, hệ thống phải thực hiện ghi 2 bản ghi giao dịch thay vì 1, làm tăng thời gian xử lý khoảng $\sim 1\text{ms}$.

### 4. Trạng thái
`ĐÃ CHẤP THUẬN (ACCEPTED)`

---

## ADR-07: Ngưỡng Tương đồng Cosine Tự thích ứng FaceNet (0.68) trong So khớp Sinh trắc học

### 1. Ngữ cảnh & Bài toán Kỹ thuật
Trong Giai đoạn 3 (Tuần 9), hệ thống tích hợp tính năng Check-in Nhanh bằng Thị giác máy tính. Khi khách đến quầy, camera máy trạm chụp chân dung trực tiếp và so sánh với ảnh thẻ căn cước (CCCD) được trích xuất qua PaddleOCR.

Bài toán so khớp khuôn mặt 1:1 đòi hỏi so sánh hai vector đặc trưng 512 chiều. Trong môi trường thực tế của khách sạn 5 sao:
- Ngưỡng khoảng cách Euclidean cố định hoạt động kém hiệu quả do sự thay đổi của ánh sáng sảnh lễ tân, góc chụp camera và sự chênh lệch tuổi tác giữa ảnh chụp trên thẻ CCCD với khuôn mặt hiện tại.
- Nếu đặt ngưỡng quá khắt khe, Tỷ lệ Từ chối Sai (FRR - False Rejection Rate) sẽ tăng cao, gây phiền hà cho khách VIP khi liên tục bị bắt đối chiếu lại.
- Nếu đặt ngưỡng quá lỏng lẻo, Tỷ lệ Chấp nhận Sai (FAR - False Acceptance Rate) sẽ tăng, dẫn tới rủi ro giả mạo danh tính nhận phòng.

### 2. Quyết định Kiến trúc
1. Ứng dụng mô hình **InsightFace MobileFaceNet** đóng gói trong container Python microservice (`ai-service` cổng 8082), thực thi suy luận qua runtime ONNX trên CPU.
2. Chuẩn hóa độ đo bằng khoảng cách góc **Cosine Similarity** giữa hai vector chuẩn hóa $L_2$ $\mathbf{u}$ và $\mathbf{v}$:
   $$\text{Cosine Similarity}(\mathbf{u}, \mathbf{v}) = \frac{\mathbf{u} \cdot \mathbf{v}}{\|\mathbf{u}\|_2 \|\mathbf{v}\|_2}$$
3. Thiết lập **Ngưỡng Thích ứng Cơ sở là $\theta = 0.68$**:
   - $\text{Độ tương đồng} \ge 0.68$: Khớp thành công (`decision: "ACCEPT"`). Giao diện Desktop hiển thị huy hiệu Xanh lá.
   - $\text{Độ tương đồng} < 0.68$: Chưa đủ độ tin cậy (`decision: "MANUAL_REVIEW"`). Giao diện hiển thị cảnh báo Cam yêu cầu lễ tân đối chiếu thủ công bằng mắt.
4. Cho phép cấu hình ngưỡng động qua biến môi trường `AI_FACE_MATCH_THRESHOLD` (cho phép tinh chỉnh trong khoảng $0.50 - 0.90$) giúp Trưởng bộ phận An ninh có thể điều chỉnh độ nhạy cho các tầng VIP hoặc kiosk tự phục vụ.

### 3. Hệ quả & Đánh đổi
* **Tích cực:**
  - Kiểm thử thực tế trên 100 cặp danh tính giả lập đạt $\text{FAR} < 0.01\%$ và $\text{FRR} < 1.5\%$ tại ngưỡng $\theta = 0.68$.
  - Trọng lượng mô hình MobileFaceNet ONNX dưới 50MB; thời gian suy luận trên CPU chỉ mất $< 30\text{ms}$.
  - Bộ nhớ RAM tiêu thụ cực thấp, giúp container AI hoạt động thoải mái trong trần ngân sách 1.5GB RAM.
  - Cơ chế suy thoái mềm: Khi độ tin cậy thấp, quy trình check-in không bị gián đoạn mà chuyển sang kiểm tra thủ công.
* **Tiêu cực / Đánh đổi:**
  - Trường hợp khách đeo khẩu trang kín hoặc kính râm tối màu cần được lễ tân hướng dẫn tháo bỏ trước khi chụp ảnh.

### 4. Trạng thái
`ĐÃ CHẤP THUẬN (ACCEPTED)`

---

## ADR-08: Kho Vector Faiss Nhúng Cục bộ cho RAG Quy chế (Thay thế pgvector)

### 1. Ngữ cảnh & Bài toán Kỹ thuật
Bản phác thảo ban đầu của Giai đoạn 3 từng cân nhắc sử dụng tiện ích mở rộng `pgvector` trên PostgreSQL 16 để lưu trữ vector đặc trưng của các văn bản quy chế khách sạn (SOP) phục vụ Trợ lý ảo Concierge.

Tuy nhiên, quá trình đo lường tài nguyên thực tế cho thấy các bất cập nghiêm trọng:
- Bổ sung một container PostgreSQL chạy song song với MariaDB 11, Backend Java và Container AI sẽ làm tiêu tốn thêm 400MB–600MB RAM của máy chủ.
- Dẫn đến tình trạng vận hành 2 hệ quản trị CSDL cùng lúc (MariaDB cho nghiệp vụ khách sạn + PostgreSQL cho vector RAG), làm tăng gấp đôi độ phức tạp của kịch bản backup, cấu hình connection pool và đồng bộ dữ liệu.
- Vi phạm trực tiếp giới hạn **trần bộ nhớ 1.5GB RAM** của phân hệ AI.

### 2. Quyết định Kiến trúc
1. **Loại bỏ hoàn toàn PostgreSQL và pgvector khỏi toàn bộ dự án.**
2. Sử dụng thư viện **Embedded Faiss** (`faiss-cpu`) tích hợp trực tiếp bên trong tiến trình Python 3.11 của `ai-service`.
3. Kiến trúc Lưu trữ & Truy xuất Tri thức RAG:
   - Toàn bộ văn bản quy chế khách sạn (giờ check-in/out, giá minibar, quy định hồ bơi/gym, chính sách hủy) được phân đoạn thành các khối 300 token với độ gối đầu 50 token.
   - Vector hóa ngữ nghĩa (384 chiều) ngoại tuyến thông qua mô hình siêu nhẹ `sentence-transformers/all-MiniLM-L6-v2`.
   - Vector được nạp vào chỉ mục trong RAM kiểu `IndexFlatIP` (Tích vô hướng trên vector chuẩn hóa, tương đương toán học với Cosine Similarity).
   - Chỉ mục được lưu trữ thành tệp nhị phân nén (`hotel_sops.index`, dung lượng $< 10\text{MB}$) và tải trực tiếp vào bộ nhớ RAM khi container khởi động.
4. Cam kết SLA truy xuất: Tìm kiếm Top-3 đoạn văn bản liên quan nhất hoàn tất trong $< 15\text{ms}$ trên CPU.

### 3. Hệ quả & Đánh đổi
* **Tích cực:**
  - Tiết kiệm hơn 500MB RAM, giữ mức chiếm dụng RAM của container AI ở trạng thái nghỉ dưới 350MB và tải đỉnh strictly $\le 1.5\text{GB}$.
  - Triệt tiêu hoàn toàn độ trễ mạng giữa Python và CSDL vector bên ngoài; tìm kiếm vector diễn ra trực tiếp qua mã C++ nhúng trong RAM.
  - Không tốn công bảo trì thêm một hệ quản trị CSDL; cập nhật quy chế chỉ cần chạy lại script `embed_sops.py`.
* **Tiêu cực / Đánh đổi:**
  - Việc cập nhật quy chế động theo thời gian thực cần kích hoạt lệnh nạp lại tệp chỉ mục trong RAM. Do quy chế khách sạn ít khi thay đổi liên tục từng phút, đánh đổi này hoàn toàn tối ưu.

### 4. Trạng thái
`ĐÃ CHẤP THUẬN (ACCEPTED)`

---

## ADR-09: Giao thức Giả lập Ngày Đầu tuần (Day-1 Mock Protocol) Giải phóng Tiến độ 4 Kỹ sư

### 1. Ngữ cảnh & Bài toán Kỹ thuật
Dự án được phân bổ cho 4 kỹ sư chuyên biệt (Dev A: Backend API, Dev B: Dữ liệu/Tài chính, Dev C: Desktop/Web UI, Dev D: DevOps/QA/AI). Trong các mô hình phát triển phần mềm tuần tự truyền thống, Dev C (Giao diện) và Dev D (Kiểm thử tự động) thường rơi vào trạng thái "ngồi chờ" trong 2–3 ngày đầu tuần để đợi Dev A và Dev B viết xong lược đồ CSDL, DAO và Controller.

Điều này tạo ra sự dồn ứ công việc vào cuối tuần, dẫn đến việc kiểm thử vội vã, phát hiện lỗi muộn và làm trễ các mốc nghiệm thu vào chiều Thứ Sáu.

### 2. Quyết định Kiến trúc
1. Chính thức hóa và bắt buộc áp dụng **Giao thức Giả lập Ngày Đầu tuần (Day-1 Mock Protocol)**:
   - Trước **09:00 sáng Thứ Hai** hàng tuần, Dev D có nhiệm vụ triển khai toàn bộ các endpoint giả lập trên `MockApiServer` (cổng 8089) bám sát 100% bản đặc tả hợp đồng JSON trong `docs/dev-spec/api-contracts.md`.
   - Trong phân hệ AI, Dev A và Dev D duy trì cờ môi trường `AI_SERVICE_MOCK=true` để giả lập dữ liệu ảnh OCR và phản hồi RAG trong bộ nhớ.
2. **Quy trình Phát triển Song song Tuyệt đối (Thứ Hai – Thứ Tư):**
   - **Dev C (Giao diện):** Cấu hình client (`ApiClient` trên Swing và `fetch()` trên Web) trỏ trực tiếp sang `http://localhost:8089` (hoặc bật `AI_SERVICE_MOCK=true`). Tập trung 100% thời gian dựng layout, phím tắt, camera viewfinder và tối ưu hiệu năng luồng EDT (<16ms).
   - **Dev D (Kiểm thử QA):** Viết sẵn kịch bản kiểm thử đồng thời JUnit 5 và kịch bản tải JMeter dựa trên hợp đồng giao tiếp.
   - **Dev A & Dev B (Backend & Nghiệp vụ):** Cài đặt CSDL MariaDB, DAO, Controller và thuật toán tài chính trên cổng 8081 mà không bị áp lực phụ thuộc từ giao diện.
3. **Mốc Ghép nối Thống nhất vào Thứ Năm:**
   - Sáng Thứ Năm, Dev C chỉ cần đổi địa chỉ base URL từ cổng `:8089` sang `:8081`. Do cả hai bên đều tuân thủ nghiêm ngặt hợp đồng API từ đầu tuần, quá trình ghép nối tích hợp toàn diện hoàn tất trong vòng dưới 2 giờ.

### 3. Hệ quả & Đánh đổi
* **Tích cực:**
  - Triệt tiêu 100% thời gian chết (Idle time) của cả 4 lập trình viên.
  - Các sai lệch hoặc điểm bất hợp lý trong thiết kế API được phát hiện và chỉnh sửa ngay sáng Thứ Hai thay vì chiều Thứ Sáu.
  - Tạo điều kiện thuận lợi để chạy ngâm tải 200 CCU và kiểm thử hồi quy toàn diện vào ngày Thứ Sáu mà không bị xáo trộn mã nguồn.
* **Tiêu cực / Đánh đổi:**
  - Yêu cầu kỷ luật cao trong việc duy trì và cập nhật các tệp dữ liệu giả lập mẫu trong `src/test/resources/mock-server/` mỗi khi có thay đổi hợp đồng API.

### 4. Trạng thái
`ĐÃ CHẤP THUẬN (ACCEPTED)`
