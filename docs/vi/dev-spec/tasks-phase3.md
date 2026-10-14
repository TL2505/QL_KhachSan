# KẾ HOẠCH TRIỂN KHAI KỸ THUẬT & CƠ CẤU PHÂN RÃ CÔNG VIỆC (WBS) — GIAI ĐOẠN 3
**Dự án:** Hệ thống Quản trị Khách sạn 5★ & Nền tảng AI (`QL_KhachSan` — 200 Phòng, TP. Hồ Chí Minh, Việt Nam)  
**Quy mô đội ngũ:** 4 Kỹ sư (Dev A, B, C, D) | **Thời lượng:** 4 Tuần (Phase 3: Tuần 9–12)  
**Tiêu chuẩn kiến trúc:** Pure Java 17 (`com.sun.net.httpserver`), HikariCP 5.1.0 (`DatabaseConnection.java`), MariaDB 11, Java Swing + FlatLaf 3.4.1, Containerized AI Microservice (Python 3.11, FastAPI, PaddleOCR, InsightFace, Embedded Faiss, Prophet/ONNX).  
**Kỷ luật tuyệt đối:** 0 Spring Boot, 0 Hibernate, 0 Jakarta EE, 0 Lombok.

---

## I. TỔNG QUAN KIẾN TRÚC & RÀNG BUỘC PHẠM VI GIAI ĐOẠN 3

```
+---------------------------------------------------------------------------------------------------+
|                                  MÁY CHỦ VẬT LÝ (UBUNTU 22.04 LTS)                                |
|                                                                                                   |
|  [Máy trạm Nhân viên Desktop]                    [Máy quét Phần cứng / Webcam Tốc độ cao]         |
|  Java Swing + FlatLaf 3.4.1 (JDK 17)             Luồng Video trực tiếp (30 FPS, OpenCV/Webcam API)|
|  - Camera Viewfinder, Tự động điền OCR           - Căn chỉnh tỷ lệ chuẩn thẻ CCCD Việt Nam        |
|  - Huy hiệu So khớp Khuôn mặt (Ngưỡng 0.68)      - So sánh trực quan ảnh CCCD vs ảnh chân dung    |
|  - Thanh bên AI Copilot & Cảnh báo (320px)       - Biểu đồ trục kép JFreeChart dự báo 30 ngày     |
|  - Độ trễ EDT strictly < 16ms qua SwingWorker    - Bộ cài tự chạy độc lập (RAM Idle < 180MB)      |
|                             |                                                                     |
|                             | HTTP/1.1 + Bearer JWT + Tiêu đề Bảo mật OWASP Nghiêm ngặt           |
|                             v                                                                     |
|  +---------------------------------------------------------------------------------------------+  |
|  | DOCKER INTERNAL BRIDGE NETWORK: hotel_network (172.28.0.0/16)                               |  |
|  |                                                                                             |  |
|  |  +---------------------------------------------------------------------------------------+  |  |
|  |  | PMS Backend Core (app :8081)                                                          |  |  |
|  |  | - Native com.sun.net.httpserver (Executors.newFixedThreadPool(20, max 50 load))        |  |  |
|  |  | - SecurityHardeningFilter: HSTS, CSP, X-Content-Type-Options: nosniff, X-Frame: DENY   |  |  |
|  |  | - IpRateLimiter: Giới hạn Token Bucket trong RAM (60 req/phút cho IP vãng lai)        |  |  |
|  |  | - TokenBlacklistService: Thu hồi token trượt tức thì khi đăng xuất                    |  |  |
|  |  | - AiCheckinController: Proxy chuyển tiếp OCR/Face sang AI với timeout 3s              |  |  |
|  |  | - VirtualConciergeController: RAG Gateway & PromptGuardrailFilter (Chống Jailbreak)   |  |  |
|  |  | - YieldRecommendationController: Bộ đệm Hệ số Giá ML trong RAM (TTL 6 giờ)           |  |  |
|  |  +---------------------------+-------------------------------------------+---------------+  |  |
|  |                              |                                           |                  |  |
|  |       Kết nối HikariCP Pool  |            Loopback HTTP / mTLS           |                  |  |
|  |       (Pool: 20-50 conn)     |            (Cổng nội bộ :8082)            |                  |  |
|  |                              v                                           v                  |  |
|  |  +---------------------------------------+   +-------------------------------------------+  |  |
|  |  | MariaDB 11 Database Engine (db :3306) |   | AI & COMPUTER VISION MICROSERVICE         |  |  |
|  |  | - Cách ly vật lý, Không mở ra ngoài LAN |   | (ai-service :8082 - Python 3.11 FastAPI)  |  |  |
|  |  | - Mã hóa Kho bảo mật: AES-256-GCM (IV)|   | - Khống chế RAM tối đa: Strictly <= 1.5GB |  |  |
|  |  | - guest_identity_vault (PII thô / IV) |   | - PaddleOCR: Nhận diện 12 số CCCD         |  |  |
|  |  | - ai_chat_logs & ai_escalation_tickets|   | - InsightFace MobileFaceNet (Cosine: 0.68)|  |  |
|  |  | - v_daily_occupancy_history View      |   | - Faiss nhúng nội bộ (IndexFlatIP, < 15ms)|  |  |
|  |  | - Job Xóa/Ẩn danh hóa PII Hàng đêm    |   | - Prophet / ONNX Dự báo Công suất phòng   |  |  |
|  |  +---------------------------------------+   +-------------------------------------------+  |  |
|  +---------------------------------------------------------------------------------------------+  |
+---------------------------------------------------------------------------------------------------+
```

### 5 Ràng buộc Kiến trúc Cốt lõi của Phase 3
1. **Cô lập Microservice AI (Loopback & Resource Cap):** Toàn bộ thư viện nặng (PaddleOCR, InsightFace, Faiss, Prophet) chạy trong container Python 3.11 độc lập, giao tiếp với Java Core qua loopback HTTP nội bộ cổng 8082. Giới hạn RAM của container AI được ấn định tối đa $1.5\text{GB}$, tuyệt đối không gây tràn bộ nhớ host.
2. **Bảo mật Sinh trắc học & Dữ liệu Nhạy cảm (AES-256-GCM & DLP):** Toàn bộ số CCCD và dữ liệu ảnh quét được mã hóa AES-256-GCM kèm vector IV 96-bit độc bản trước khi lưu vào `guest_identity_vault`. Dữ liệu vector khuôn mặt và ảnh thô bị tự động hủy sau 30 ngày kể từ khi khách trả phòng; thông tin khách sau 3 năm được ẩn danh hóa mà không làm hỏng khóa ngoại `customer_id` trên sổ cái kế toán.
3. **Phòng vệ Trợ lý ảo RAG (Zero-Trust Prompt Guardrail):** Bộ lọc `PromptGuardrailFilter` trên Backend Java kiểm tra $100\%$ truy vấn của khách hàng. Mọi hành vi tiêm prompt (Jailbreak), trích xuất cấu hình hệ thống hoặc khai thác dữ liệu PII đều bị chặn đứng tại cổng vào với mã HTTP 400 Bad Request trước khi chạm tới LLM.
4. **Bảo toàn Hiệu năng Desktop EDT (< 16ms):** Mọi luồng stream webcam (30 FPS), tác vụ nhận diện OCR, gợi ý giá động và hiển thị biểu đồ doanh thu JFreeChart trên client Java Swing đều chạy qua `SwingWorker` hoặc luồng nền riêng biệt; Event Dispatch Thread duy trì tốc độ khung hình 60 FPS.
5. **Cơ chế Chịu lỗi & Suy thoái Mềm (Graceful Degradation):** Nếu container AI gặp sự cố hoặc phản hồi quá 3.000ms, Backend Java lập tức ngắt mạch, phản hồi HTTP 504 Gateway Timeout kèm chỉ dẫn nhân viên chuyển sang đối chiếu CCCD thủ công bằng mắt. Hệ thống Core PMS tiếp tục vận hành bình thường, không xảy ra tình trạng treo luồng.

---

## II. PHÂN RÃ CÔNG VIỆC CHI TIẾT TỪNG TUẦN (TUẦN 9 – 12)

---

### TUẦN 9: PHÂN HỆ THỊ GIÁC MÁY TÍNH & CHECK-IN SINH TRẮC HỌC (OCR & FACE VERIFICATION)

#### Dev A — Backend API & Hạ tầng Cốt lõi
* **TASK-A9.1: Controller Tích hợp Nhận diện Thẻ CCCD & So khớp Khuôn mặt**
  * **Module:** Cổng API Check-in AI
  * **Người thực hiện:** Dev A
  * **Phụ thuộc (DAG):** TASK-B9.1, TASK-D9.1
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/ai/AiCheckinController.java`, `src/quanlykhachsan/backend/ai/AiServiceClient.java`.
  * **Định nghĩa Hoàn thành (DoD):**
    - Cung cấp endpoint `POST /api/v1/ai/id-ocr` nhận Base64 ảnh thẻ căn cước (JPEG/PNG, $\le 5\text{MB}$); chuyển tiếp tới container AI trên `http://127.0.0.1:8082/ocr` với timeout $3.000\text{ms}$; trả về JSON: `{ "id_card": "079...", "full_name": "...", "dob": "...", "address": "..." }`.
    - Cung cấp endpoint `POST /api/v1/ai/face-verify` nhận `{ "id_photo": "...", "live_face": "..." }`; chuyển tiếp tới `http://127.0.0.1:8082/face-match`; trả về `{ "match": true/false, "confidence": 0.85, "recommendation": "ACCEPT" }`.
    - Hỗ trợ cờ môi trường `AI_SERVICE_MOCK=true` để phục vụ kiểm thử độc lập khi container AI chưa sẵn sàng.
    - Xử lý timeout/ngắt kết nối: Trả về HTTP 504 Gateway Timeout kèm message: `"AI service unavailable. Switch to manual Front Desk verification."`. Thời gian phản hồi OCR $\le 800\text{ms}$, Face Match $\le 400\text{ms}$.
  * **Phương thức xác minh:** Chạy kiểm thử tích hợp `mvn test -Dtest=AiCheckinControllerTest`. Giả lập AI service ngắt kết nối; assert mã trả về là 504 và backend Java không bị crash.

#### Dev B — Dữ liệu, Tài chính & Nghiệp vụ Cốt lõi
* **TASK-B9.1: DAO Kho Bảo mật & Mã hóa Sinh trắc học Danh tính Khách**
  * **Module:** Tầng Dữ liệu Sinh trắc học & Bảo mật
  * **Người thực hiện:** Dev B
  * **Phụ thuộc (DAG):** Không
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/customer/GuestIdentityVaultDao.java`, `src/quanlykhachsan/backend/utils/AesGcmEncryptionUtil.java`, `database/migrations/v3_1_biometric_vault.sql`.
  * **Định nghĩa Hoàn thành (DoD):**
    - Tạo bảng `guest_identity_vault`:
      ```sql
      CREATE TABLE guest_identity_vault (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          customer_id INT NOT NULL,
          encrypted_id_number VARBINARY(256) NOT NULL,
          encrypted_raw_payload VARBINARY(2048) NOT NULL,
          face_embedding_blob BLOB NULL,
          iv VARBINARY(16) NOT NULL,
          created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
          CONSTRAINT fk_vault_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE
      );
      ```
    - `AesGcmEncryptionUtil.java` thực hiện mã hóa AES-256-GCM với vector IV 96-bit ngẫu nhiên cho từng bản ghi.
    - Không lưu số CCCD dạng rõ (Plaintext) trong toàn bộ CSDL và nhật ký console.
    - Truy vấn giải mã chỉ khả dụng khi cung cấp khóa quản trị `AES_VAULT_KEY`.
  * **Phương thức xác minh:** Chạy unit test `mvn test -Dtest=GuestIdentityVaultDaoTest`. Kiểm tra trực tiếp bảng `guest_identity_vault` trong MariaDB; xác nhận dữ liệu là ciphertext nhị phân.

#### Dev C — Giao diện Desktop & Kênh Khách hàng
* **TASK-C9.1: Khung ngắm Camera & Hộp thoại Quét CCCD trên Swing**
  * **Module:** Giao diện Desktop Phần cứng & AI Viewfinder
  * **Người thực hiện:** Dev C
  * **Phụ thuộc (DAG):** TASK-D9.1
  * **Tệp mục tiêu:** `src/quanlykhachsan/frontend/view/AiCheckinDialog.java`, `src/quanlykhachsan/frontend/view/CameraPreviewPanel.java`.
  * **Định nghĩa Hoàn thành (DoD):**
    - Hộp thoại modal hiển thị khung ngắm camera trực tiếp 30 FPS sử dụng thư viện webcam/OpenCV nhẹ; độ trễ Event Dispatch Thread duy trì strictly $< 16\text{ms}$.
    - Vẽ khung căn chỉnh tỷ lệ chuẩn thẻ CCCD (tỷ lệ $85.6 \times 53.98\text{mm}$) với viền hỗ trợ định vị.
    - Nút "Chụp & Nhận diện CCCD" gửi ảnh bất đồng bộ qua `SwingWorker` tới `POST /api/v1/ai/id-ocr`, tự động điền các trường Họ tên, Số CCCD, Ngày sinh, Địa chỉ vào form khách trong $< 1.2\text{s}$.
    - Hiển thị đối chiếu 2 ảnh cạnh nhau (Ảnh chụp CCCD vs Ảnh chân dung camera) kèm huy hiệu độ tin cậy: Xanh lá ($\ge 0.68$, Khớp thành công) hoặc Đỏ ($< 0.68$, Yêu cầu đối chiếu mắt thường).
  * **Phương thức xác minh:** Chạy `AiCheckinDialog` với camera thật hoặc ảnh mẫu thử nghiệm. Đo kiểm FPS bằng Java VisualVM; xác nhận không có cảnh báo drop frame trên EDT.

#### Dev D — DevOps, Kiểm thử Tự động & Phân hệ AI
* **TASK-D9.1: Container Microservice Mô hình PaddleOCR & InsightFace**
  * **Module:** Hạ tầng AI Đóng gói Container
  * **Người thực hiện:** Dev D
  * **Phụ thuộc (DAG):** Không
  * **Tệp mục tiêu:** `ai-service/app.py`, `ai-service/ocr_engine.py`, `ai-service/face_engine.py`, `ai-service/config.py`, `ai-service/Dockerfile`, `ai-service/requirements.txt`.
  * **Định nghĩa Hoàn thành (DoD):**
    - Container Python 3.11 FastAPI độc lập chạy trên loopback `127.0.0.1:8082`.
    - Tích hợp PaddleOCR (mô hình tiếng Việt) trích xuất chính xác 12 số CCCD, họ tên tiếng Việt có dấu, ngày sinh với độ chính xác $\ge 96\%$ trên ảnh rõ nét.
    - Tích hợp InsightFace (MobileFaceNet ONNX runtime) trích xuất vector đặc trưng 512 chiều và tính khoảng cách Cosine Similarity.
    - Cấu hình biến môi trường động `AI_FACE_MATCH_THRESHOLD` với giá trị mặc định là 0.68 (cho phép tinh chỉnh trong dải $0.50 - 0.90$).
    - Giới hạn tài nguyên Docker: RAM tối đa $\le 1.5\text{GB}$, CPU $\le 2$ cores. Khởi động hoàn tất trong $< 15\text{s}$.
  * **Phương thức xác minh:** Chạy kiểm thử pytest `pytest ai-service/tests/test_vision.py`. Đánh giá 40 cặp ảnh mẫu; xác nhận RAM container không vượt quá 1.5GB bằng lệnh `docker stats`.

* **TASK-D9.2: Tập Dữ liệu CCCD Giả lập & Kiểm thử Độ chính xác Sinh trắc học**
  * **Module:** Kiểm thử Tự động Độ chính xác AI & Chống Giả mạo
  * **Người thực hiện:** Dev D
  * **Phụ thuộc (DAG):** TASK-D9.1
  * **Tệp mục tiêu:** `src/test/java/quanlykhachsan/ai/SyntheticCccdGenerator.java`, `src/test/java/quanlykhachsan/ai/FaceVerificationAccuracyTest.java`.
  * **Định nghĩa Hoàn thành (DoD):**
    - Sinh tập dữ liệu 100 ảnh CCCD giả lập với tên tiếng Việt ngẫu nhiên, số định danh hợp lệ và phông chữ chuẩn.
    - Kiểm thử tự động trên tập dữ liệu đối chứng: Tỷ lệ chấp nhận sai (FAR - False Acceptance Rate) đạt $< 0.01\%$; Tỷ lệ từ chối sai (FRR - False Rejection Rate) đạt $< 1.5\%$ tại ngưỡng cosine $0.68$.
    - Kịch bản kiểm thử Scenario 10 (AI Connection Timeout & Soft Degradation): Ngắt container AI; xác nhận Backend Java chuyển sang chế độ dự phòng an toàn.
  * **Phương thức xác minh:** Thực thi `mvn test -Dtest=FaceVerificationAccuracyTest`. 100% assertions đạt chuẩn.

---

### TUẦN 10: TRỢ LÝ ẢO CONCIERGE & HỖ TRỢ LỄ TÂN RAG

#### Dev A — Backend API & Hạ tầng Cốt lõi
* **TASK-A10.1: REST API Trợ lý Ảo Concierge & Bộ Ngắt mạch Chuyển tiếp**
  * **Module:** Cổng Gateway AI Virtual Concierge
  * **Người thực hiện:** Dev A
  * **Phụ thuộc (DAG):** TASK-B10.1, TASK-D10.1
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/ai/VirtualConciergeController.java`.
  * **Định nghĩa Hoàn thành (DoD):**
    - Cung cấp endpoint `POST /api/v1/ai/concierge/chat` tiếp nhận `{ "session_id": "...", "message": "..." }`; ủy quyền qua `PromptGuardrailFilter`; chuyển tiếp tới RAG pipeline trên `http://127.0.0.1:8082/rag/query`; trả về định dạng Markdown trong thời gian $< 1.2\text{s}$.
    - Cung cấp endpoint `POST /api/v1/ai/concierge/escalate` tiếp nhận yêu cầu hỗ trợ của khách; tạo ngay ticket hỗ trợ lễ tân trong CSDL; trả về mã `ticket_id` kèm HTTP 200 OK.
    - Áp dụng giới hạn tần suất (Rate Limiting): Tối đa 10 request/phút trên mỗi session token khách; vượt quá giới hạn trả về HTTP 429 Too Many Requests.
  * **Phương thức xác minh:** Chạy kiểm thử `mvn test -Dtest=VirtualConciergeControllerTest`. Gửi 15 request liên tục; xác nhận 5 request cuối nhận mã lỗi 429.

* **TASK-A10.2: Bộ lọc Phòng vệ An toàn Prompt Guardrail**
  * **Module:** Bộ lọc An toàn & Bảo mật AI
  * **Người thực hiện:** Dev A
  * **Phụ thuộc (DAG):** Không
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/ai/PromptGuardrailFilter.java`.
  * **Định nghĩa Hoàn thành (DoD):**
    - Lớp lọc chặn đứng các mẫu chuỗi độc hại: Prompt Injections ("ignore previous instructions", "system override", "bỏ qua chỉ dẫn"), SQL Injections (UNION SELECT, OR 1=1), và nỗ lực trích xuất PII ("hiển thị mật khẩu", "dump cccd").
    - Khi phát hiện vi phạm: Trả về ngay HTTP 400 Bad Request kèm mã lỗi RFC-7807 `{ "error_code": "PROMPT_INJECTION_DETECTED", "message": "Yêu cầu không hợp lệ do vi phạm chính sách bảo mật." }`.
    - Không chuyển tiếp bất kỳ chuỗi độc hại nào tới mô hình ngôn ngữ phía sau; ghi log cảnh báo an ninh bảo mật.
  * **Phương thức xác minh:** Chạy kiểm thử unit test `mvn test -Dtest=PromptGuardrailFilterTest` trên 100 mẫu payload tấn công độc hại.

#### Dev B — Dữ liệu, Tài chính & Nghiệp vụ Cốt lõi
* **TASK-B10.1: DAO Nhật ký Hội thoại AI & Phiếu Chuyển tiếp Lễ tân**
  * **Module:** Lưu trữ Hội thoại & Phiếu Hỗ trợ AI
  * **Người thực hiện:** Dev B
  * **Phụ thuộc (DAG):** Không
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/ai/AiInteractionDao.java`, `src/quanlykhachsan/backend/ai/EscalationTicketDao.java`, `database/migrations/v3_2_ai_interactions.sql`.
  * **Định nghĩa Hoàn thành (DoD):**
    - Tạo bảng `ai_chat_logs`:
      ```sql
      CREATE TABLE ai_chat_logs (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          session_id VARCHAR(64) NOT NULL,
          sender ENUM('GUEST', 'BOT', 'STAFF') NOT NULL,
          prompt TEXT NOT NULL,
          response TEXT NOT NULL,
          confidence_score DECIMAL(3,2),
          created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
          INDEX idx_session (session_id, created_at)
      );
      ```
    - Tạo bảng `ai_escalation_tickets`:
      ```sql
      CREATE TABLE ai_escalation_tickets (
          id INT AUTO_INCREMENT PRIMARY KEY,
          session_id VARCHAR(64) NOT NULL,
          room_id INT NULL,
          guest_name VARCHAR(100) NULL,
          issue_summary VARCHAR(255) NOT NULL,
          status ENUM('OPEN', 'ACKNOWLEDGED', 'RESOLVED') DEFAULT 'OPEN',
          assigned_staff_id INT NULL,
          created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
          resolved_at DATETIME NULL
      );
      ```
    - `AiInteractionDao` thực hiện ghi log bất đồng bộ qua `ExecutorService`, không làm nghẽn luồng xử lý chính.
  * **Phương thức xác minh:** Chạy `mvn test -Dtest=AiInteractionDaoTest`. Kiểm tra log chat và ticket được lưu trữ chính xác với correlation ID đầy đủ.

#### Dev C — Giao diện Desktop & Kênh Khách hàng
* **TASK-C10.1: Khung AI Copilot & Banner Cảnh báo Chuyển tiếp trên Swing**
  * **Module:** Giao diện Desktop AI Copilot
  * **Người thực hiện:** Dev C
  * **Phụ thuộc (DAG):** TASK-D10.1
  * **Tệp mục tiêu:** `src/quanlykhachsan/frontend/view/AiCopilotPanel.java`, `src/quanlykhachsan/frontend/view/EscalationAlertBanner.java`, `src/quanlykhachsan/frontend/view/StaffForm.java`.
  * **Định nghĩa Hoàn thành (DoD):**
    - Bổ sung thanh bên phụ (Sidebar) có thể thu gọn rộng 320px trên màn hình chính `StaffForm.java`.
    - Ô tìm kiếm nhanh cho phép nhân viên tra cứu quy chế khách sạn (SOP): "Quy định phụ thu trả phòng muộn?", trả về câu trả lời tóm tắt kèm trích dẫn văn bản trong $< 500\text{ms}$.
    - Banner cảnh báo chuyển màu Cam rực rỡ (`#F57C00`) nhấp nháy nhẹ và phát âm thanh cảnh báo khi có khách yêu cầu chuyển tiếp nhân viên (Escalation).
    - Nút "Tiếp nhận hỗ trợ" cho phép nhân viên mở ngay cửa sổ chat trực tiếp với khách phòng tương ứng; độ trễ EDT $< 16\text{ms}$.
  * **Phương thức xác minh:** Kích hoạt sự kiện escalation giả lập; kiểm tra banner cảnh báo hiển thị tức thì trên giao diện Swing mà không làm khựng chuột.

#### Dev D — DevOps, Kiểm thử Tự động & Phân hệ AI
* **TASK-D10.1: Kho Vector Nhúng Faiss & Quy trình RAG Quy chế Khách sạn**
  * **Module:** Tìm kiếm Vector & Truy xuất Tri thức
  * **Người thực hiện:** Dev D
  * **Phụ thuộc (DAG):** Không
  * **Tệp mục tiêu:** `ai-service/rag_engine.py`, `ai-service/knowledge_store/hotel_sops.index`, `ai-service/embed_sops.py`, `database/knowledge_base/hotel_sop_chunks.json`.
  * **Định nghĩa Hoàn thành (DoD):**
    - Thay thế hoàn toàn pgvector bằng thư viện Faiss `IndexFlatIP` (Cosine Similarity) nhúng trực tiếp trong container Python `ai-service/`, loại bỏ container PostgreSQL chạy ngầm và tiết kiệm 500MB RAM.
    - Phân đoạn toàn bộ quy chế khách sạn (giờ check-in/out, giá minibar, quy định bể bơi, menu nhà hàng) thành các khối 300 token với độ gối đầu 50 token.
    - Mã hóa ngữ nghĩa bằng mô hình `all-MiniLM-L6-v2` tạo vector 384 chiều; truy vấn Top-3 đoạn văn bản liên quan nhất hoàn tất trong $< 15\text{ms}$.
    - Tổng dung lượng RAM chiếm dụng của module vector search khống chế strictly $< 350\text{MB}$.
  * **Phương thức xác minh:** Chạy script đánh giá `python ai-service/evaluate_retrieval.py`. Đạt độ chính xác Top-3 Precision $\ge 92\%$ trên 50 câu hỏi thử nghiệm của khách.

* **TASK-D10.2: Bộ Kiểm thử An ninh Phòng thủ Tiêm Prompt Độc hại**
  * **Module:** Kiểm thử Tự động An ninh Mô hình Ngôn ngữ
  * **Người thực hiện:** Dev D
  * **Phụ thuộc (DAG):** TASK-A10.2, TASK-D10.1
  * **Tệp mục tiêu:** `src/test/java/quanlykhachsan/ai/PromptSecurityTest.java`, `src/test/resources/ai/jailbreak_payloads.txt`.
  * **Định nghĩa Hoàn thành (DoD):**
    - Xây dựng bộ dữ liệu 100 mẫu tấn công jailbreak tinh vi (Prompt Leaks, Roleplay attacks, SQL injections, Delimiter collision).
    - Tự động hóa kiểm thử qua JUnit 5: Đạt tỷ lệ ngăn chặn vi phạm chính xác $100\%$; 0 trường hợp rò rỉ system prompt, thông tin cấu hình hay dữ liệu PII của khách khác.
  * **Phương thức xác minh:** Thực thi `mvn test -Dtest=PromptSecurityTest`. Toàn bộ 100 đòn tấn công đều bị chặn đứng với mã HTTP 400.

---

### TUẦN 11: DỰ BÁO DOANH THU THÔNG MINH & TỐI ƯU CÔNG SUẤT PHÒNG

#### Dev A — Backend API & Hạ tầng Cốt lõi
* **TASK-A11.1: Endpoint Dự báo Doanh thu & Khuyến nghị Hệ số Giá Động**
  * **Module:** API Tối ưu hóa Doanh thu
  * **Người thực hiện:** Dev A
  * **Phụ thuộc (DAG):** TASK-B11.1, TASK-D11.1
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/report/YieldRecommendationController.java`, `src/quanlykhachsan/backend/ai/RevenueMlClient.java`.
  * **Định nghĩa Hoàn thành (DoD):**
    - Cung cấp endpoint `GET /api/v1/revenue/forecast?days=30` trả về mảng dự báo 30 ngày: `{ "date": "...", "predicted_occupancy": 82.5, "recommended_multiplier": 1.30, "confidence_interval": [78.0, 87.0] }`.
    - Cung cấp endpoint `POST /api/v1/revenue/apply-rates` nhận danh sách hệ số giá do Tổng quản lý phê duyệt; cập nhật nguyên tử vào bảng `pricing_rules` có kiểm tra khóa lạc quan.
    - Bộ đệm dữ liệu (In-memory Cache): Lưu kết quả dự báo trong 6 giờ; các request tiếp theo trả về ngay trong $< 5\text{ms}$.
    - Cơ chế dự phòng (Fallback): Nếu ML service offline, tự động chuyển về tính toán theo quy tắc công suất cơ bản (Rule-based) của Phase 2 mà không trả về lỗi 500.
  * **Phương thức xác minh:** Chạy kiểm thử `mvn test -Dtest=YieldRecommendationControllerTest`. Giả lập ML service ngoại tuyến; xác nhận API vẫn trả về hệ số giá cơ bản hợp lệ.

#### Dev B — Dữ liệu, Tài chính & Nghiệp vụ Cốt lõi
* **TASK-B11.1: Tổng hợp Dữ liệu Đặt phòng Lịch sử & View Tính năng Phân tích**
  * **Module:** Kỹ nghệ Dữ liệu & View Phân tích
  * **Người thực hiện:** Dev B
  * **Phụ thuộc (DAG):** Không
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/report/RevenueAggregatorDao.java`, `database/migrations/v3_3_revenue_views.sql`.
  * **Định nghĩa Hoàn thành (DoD):**
    - Tạo 2 View tối ưu hóa trong MariaDB: `v_daily_occupancy_history` (tổng hợp công suất theo ngày trong quá khứ) và `v_daily_revpar_history` (tổng hợp doanh thu trên phòng có sẵn).
    - Thiết lập Composite Index trên bảng bookings: `INDEX idx_revenue_dates (check_in_date, check_out_date, status)`.
    - Thời gian thực thi truy vấn tổng hợp trên 100.000 bản ghi lịch sử đạt strictly $< 80\text{ms}$.
    - Trích xuất dữ liệu ra định dạng chuẩn (CSV/JSON) sẵn sàng cung cấp cho mô hình Machine Learning.
  * **Phương thức xác minh:** Chạy benchmark test `mvn test -Dtest=RevenueAggregatorDaoBenchmarkTest`. Đo thời gian truy vấn trên CSDL có 100.000 dòng dữ liệu mẫu.

#### Dev C — Giao diện Desktop & Kênh Khách hàng
* **TASK-C11.1: Bảng Quản trị Doanh thu & Phê duyệt Giá Linh hoạt trên Swing**
  * **Module:** Giao diện Desktop Quản trị Doanh thu
  * **Người thực hiện:** Dev C
  * **Phụ thuộc (DAG):** TASK-D11.1
  * **Tệp mục tiêu:** `src/quanlykhachsan/frontend/view/RevenueForecastPanel.java`, `src/quanlykhachsan/frontend/view/YieldRecommendationTable.java`.
  * **Định nghĩa Hoàn thành (DoD):**
    - Bổ sung tab "Quản Trị Doanh Thu & Dự Báo (Yield)" trên màn hình quản lý.
    - Trực quan hóa công suất dự báo 30 ngày tới so với cùng kỳ năm trước bằng biểu đồ trục kép JFreeChart (FlatLaf Dark theme).
    - Bảng tương tác cho phép Tổng quản lý xem xét và chỉnh sửa hệ số giá khuyến nghị của AI cho từng hạng phòng trước khi duyệt.
    - Nút "Áp dụng toàn bộ khuyến nghị" hiển thị hộp thoại xác nhận tính toán trước mức tăng doanh thu kỳ vọng; thời gian render panel $< 30\text{ms}$, không gây đơ chuột.
  * **Phương thức xác minh:** Mở tab Quản trị doanh thu trong Swing; tương tác sửa hệ số trên bảng; kiểm tra biểu đồ và nút áp dụng hoạt động mượt mà.

#### Dev D — DevOps, Kiểm thử Tự động & Phân hệ AI
* **TASK-D11.1: Mô hình Dự báo Doanh thu Chuỗi Thời gian (Prophet / ONNX)**
  * **Module:** Huấn luyện & Triển khai Mô hình Học máy
  * **Người thực hiện:** Dev D
  * **Phụ thuộc (DAG):** Không (Sử dụng `mock_occupancy_history.csv` ngày đầu tuần)
  * **Tệp mục tiêu:** `ai-service/forecast_engine.py`, `ai-service/train_forecaster.py`, `tests/resources/ai/mock_occupancy_history.csv`, `ai-service/models/occupancy_prophet.onnx`.
  * **Định nghĩa Hoàn thành (DoD):**
    - Xây dựng mô hình chuỗi thời gian (Time-series) tính toán đầy đủ: Tính chu kỳ theo ngày trong tuần, các dịp nghỉ lễ/Tết tại Việt Nam, và mùa du lịch cao điểm.
    - Sai số phần trăm tuyệt đối trung bình (MAPE - Mean Absolute Percentage Error) đạt strictly $< 8.5\%$ trên tập dữ liệu kiểm thử độc lập (Holdout validation set).
    - Tối ưu hóa suy luận (Inference): Thời gian sinh dự báo cho chu kỳ 30 ngày hoàn tất trong $< 120\text{ms}$.
  * **Phương thức xác minh:** Chạy script đánh giá `python ai-service/evaluate_forecast.py`. Đo lường và in ra các chỉ số RMSE và MAPE đạt tiêu chuẩn.

* **TASK-D11.2: Kiểm thử Khóa Biên An toàn Giá & Chống Bán Vượt Phòng**
  * **Module:** Kiểm thử Tự động An toàn Tài chính
  * **Người thực hiện:** Dev D
  * **Phụ thuộc (DAG):** TASK-A11.1, TASK-D11.1
  * **Tệp mục tiêu:** `src/test/java/quanlykhachsan/ai/YieldSafetySanityTest.java`.
  * **Định nghĩa Hoàn thành (DoD):**
    - Kiểm chứng tuyệt đối: Hệ số giá động của AI không bao giờ được thấp hơn mức sàn ($0.70\times$ giá cơ bản) hoặc vượt quá mức trần ($2.50\times$ giá cơ bản).
    - Giả lập kịch bản nhu cầu đạt đỉnh 100% công suất: Xác nhận thuật toán tự động ngắt toàn bộ mã giảm giá và ngăn chặn triệt để tình trạng bán vượt phòng âm (Zero Overbooking).
  * **Phương thức xác minh:** Chạy `mvn test -Dtest=YieldSafetySanityTest`. 100% các ca kiểm thử biên giá và công suất đều vượt qua.

---

### TUẦN 12: GIA CỐ BẢO MẬT DOANH NGHIỆP, KIỂM THỬ TẢI & ĐÓNG GÓI PHÁT HÀNH

#### Dev A — Backend API & Hạ tầng Cốt lõi
* **TASK-A12.1: Gia cố Middleware Bảo mật & Bộ Giới hạn Tần suất IP**
  * **Module:** Tối ưu & Gia cố Cổng Gateway
  * **Người thực hiện:** Dev A
  * **Phụ thuộc (DAG):** Không
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/utils/SecurityHardeningFilter.java`, `src/quanlykhachsan/backend/utils/IpRateLimiter.java`, `src/quanlykhachsan/backend/auth/TokenBlacklistService.java`.
  * **Định nghĩa Hoàn thành (DoD):**
    - Bổ sung các header bảo mật chuẩn OWASP vào $100\%$ response HTTP:
      - `X-Content-Type-Options: nosniff`
      - `X-Frame-Options: DENY`
      - `Content-Security-Policy: default-src 'self'`
      - `Strict-Transport-Security: max-age=31536000; includeSubDomains`
    - Bộ giới hạn tốc độ IP (Token Bucket trong RAM): Khống chế tối đa 60 request/phút đối với các IP chưa xác thực; vi phạm trả về HTTP 429 Too Many Requests.
    - Dịch vụ thu hồi token tức thì (`TokenBlacklistService`): Khi đăng xuất, token JWT bị đưa ngay vào danh sách đen trượt trong bộ nhớ (Sliding window memory blacklist), vô hiệu hóa token ngay lập tức.
  * **Phương thức xác minh:** Chạy kiểm thử tích hợp `mvn test -Dtest=SecurityHardeningFilterTest`. Kiểm tra response headers qua cURL và bắn spam request để kích hoạt mã 429.

#### Dev B — Dữ liệu, Tài chính & Nghiệp vụ Cốt lõi
* **TASK-B12.1: Động cơ Xóa Dữ liệu Nhạy cảm CCCD & Ẩn danh hóa DLP**
  * **Module:** Tuân thủ Quyền Riêng tư & Lưu trữ DLP
  * **Người thực hiện:** Dev B
  * **Phụ thuộc (DAG):** Không
  * **Tệp mục tiêu:** `src/quanlykhachsan/backend/system/PiiAnonymizerJob.java`, `database/migrations/v3_4_audit_compliance.sql`.
  * **Định nghĩa Hoàn thành (DoD):**
    - Job chạy định kỳ hàng đêm (Nightly batch job): Tự động tìm và xóa sạch (gán NULL/zeroize) các vector khuôn mặt và ảnh thô CCCD trong `guest_identity_vault` đối với các lượt lưu trú đã trả phòng quá 30 ngày.
    - Quy trình ẩn danh hóa sau 3 năm: Ẩn danh hóa họ tên, số điện thoại của khách trong các booking đã tất toán quá thời hạn lưu trữ chứng từ tài chính.
    - Giữ nguyên toàn vẹn khóa ngoại `customer_id` và các bút toán sổ cái `folio_transactions` để phục vụ đối soát thuế VAT lịch sử; tạo vết băm mật mã (Cryptographic Audit Trail) chứng thực việc hủy dữ liệu hợp chuẩn.
  * **Phương thức xác minh:** Chạy unit test `mvn test -Dtest=PiiAnonymizerJobTest`. Xác nhận các cột dữ liệu sinh trắc học hết hạn bị gán NULL trong khi số dư và lịch sử giao dịch không bị thay đổi.

#### Dev C — Giao diện Desktop & Kênh Khách hàng
* **TASK-C12.1: Đóng gói Bản cài Độc lập Cho Desktop & Màn hình Khởi động**
  * **Module:** Đóng gói Ứng dụng & Phát hành Máy trạm
  * **Người thực hiện:** Dev C
  * **Phụ thuộc (DAG):** Toàn bộ các task Dev C trước đó
  * **Tệp mục tiêu:** `src/quanlykhachsan/frontend/view/SplashScreen.java`, `pom.xml` (Cấu hình Maven Shade & JPackage), `build_desktop_dist.bat`.
  * **Định nghĩa Hoàn thành (DoD):**
    - Màn hình khởi động (Splash Screen) hiển thị trong $< 300\text{ms}$ với thanh tiến trình tải mượt mà và nạp trước Look and Feel FlatLaf Dark theme.
    - Đóng gói bộ cài tự chạy độc lập (Self-contained distribution) cho Windows: Tích hợp sẵn JRE 17 rút gọn, người dùng không cần cài đặt Java trên máy trạm.
    - Đo kiểm mức chiếm dụng bộ nhớ của client Desktop: RAM ở chế độ nghỉ (Idle) strictly $< 180\text{MB}$; RAM ở chế độ cao điểm khi mở lưới 200 phòng và camera strictly $< 350\text{MB}$.
    - Thời gian khởi động lạnh (Cold start) từ lúc bấm icon đến khi mở form đăng nhập hoàn tất trong $< 2.0\text{s}$.
  * **Phương thức xác minh:** Chạy script `build_desktop_dist.bat`; kích hoạt ứng dụng trên máy trạm Windows sạch; đo thời gian khởi động và giám sát RAM qua Task Manager.

#### Dev D — DevOps, Kiểm thử Tự động & Phân hệ AI
* **TASK-D12.1: Kiểm thử Ứng suất Ngâm tải 200 CCU trong 2 Giờ (JMeter)**
  * **Module:** Xác thực Hiệu năng Quy mô Doanh nghiệp
  * **Người thực hiện:** Dev D
  * **Phụ thuộc (DAG):** TASK-A12.1, TASK-B12.1
  * **Tệp mục tiêu:** `tests/performance/200_ccu_hotel_soak_test.jmx`, `tests/performance/jmeter_benchmark_report.md`.
  * **Định nghĩa Hoàn thành (DoD):**
    - Duy trì liên tục 200 người dùng ảo đồng thời (200 CCU) bắn tải thực tế trong 2 giờ uninterrupted: 60% tra cứu phòng trống, 20% đặt phòng trực tuyến, 10% thanh toán gạch nợ VietQR, 10% truy vấn AI RAG/Check-in.
    - Thông lượng hệ thống đạt $\ge 350\text{ requests/second}$.
    - Độ trễ phân vị $95\%$ ($P_{95}$) strictly $< 120\text{ms}$; Tỷ lệ lỗi toàn hệ thống strictly $< 0.05\%$.
    - Hồ kết nối HikariCP (cấu hình 50 connections) hoạt động ổn định, 0 cảnh báo rò rỉ kết nối; khi ngừng tải, số active connections trở về đúng 0.
  * **Phương thức xác minh:** Thực thi kiểm thử tải bằng lệnh:
    `jmeter -n -t tests/performance/200_ccu_hotel_soak_test.jmx -l results.jtl -e -o reports/dashboard/`.

* **TASK-D12.2: Quét Lỗ hổng OWASP ZAP & Đóng gói Phát hành Golden Master**
  * **Module:** Kiểm toán An ninh & Triển khai Sản xuất
  * **Người thực hiện:** Dev D
  * **Phụ thuộc (DAG):** TASK-D12.1, TASK-C12.1
  * **Tệp mục tiêu:** `docs/security/owasp_zap_scan_report.md`, `docker-compose.prod.yml`, `deploy/production_deploy_checklist.sh`.
  * **Định nghĩa Hoàn thành (DoD):**
    - Quét bảo mật tự động qua OWASP ZAP Baseline Scanner: Đạt kết quả 0 lỗ hổng Critical và 0 lỗ hổng High.
    - Kiểm tra xâm nhập thủ công: Xác nhận miễn nhiễm hoàn toàn với SQL Injection, Broken Object Level Authorization (BOLA/IDOR), và Cross-Site Scripting (XSS).
    - Bộ tệp triển khai sản xuất hoàn chỉnh (`docker-compose.prod.yml`) gồm các container: Java Core Backend, MariaDB 11, AI Microservice, và Nginx Reverse Proxy.
    - Kích hoạt toàn bộ hệ sinh thái sản xuất thành công bằng 1 lệnh duy nhất `./deploy/production_deploy_checklist.sh` trong thời gian $< 90\text{s}$ ở trạng thái hoàn toàn khỏe mạnh (Healthy).
  * **Phương thức xác minh:** Chạy `./deploy/production_deploy_checklist.sh`; thực thi script smoke test đối soát trạng thái HTTP 200 trên tất cả các endpoint cốt lõi.

---

## III. BẢNG GIAO THỨC GIẢ LẬP NGÀY ĐẦU TUẦN (DAY-1 MOCK PROTOCOL)

Nhằm triệt tiêu hoàn toàn rủi ro nghẽn luồng phát triển giữa các tuần có phụ thuộc nối tiếp, quy tắc Day-1 Mock Protocol được áp dụng bắt buộc cho Phase 3:

| Tuần | Dịch vụ Upstream cần thiết | Bên cung cấp | Bên tiêu thụ | Bản hợp đồng / File Mock | Hạn chót (SLA) | Chiến lược Giải phóng (Decoupling) |
|---|---|:---:|:---:|---|:---:|---|
| **W9** | AI Vision Endpoints (`/ai/id-ocr`, `/ai/face-verify`) | **Dev D** | **Dev A, Dev C** | Cờ môi trường `AI_SERVICE_MOCK=true` & JSON payload mẫu trên cổng `:8089` | **W9 Ngày 1, 09:00** | Dev A và Dev C dựng UI Camera và API Gateway ngay trong sáng Thứ Hai, không cần chờ Dev D huấn luyện và đóng gói Docker AI. |
| **W10** | RAG SOP Knowledge (`/rag/query`) & Vector Store | **Dev D** | **Dev A, Dev C** | `mock-rag-response.json` & Mock RAG HTTP Stub trên cổng `:8089` | **W10 Ngày 1, 09:00** | Dev A xây dựng bộ lọc Prompt Guardrail và Dev C dựng Sidebar Copilot bằng dữ liệu giả lập; ghép nối container Faiss thật vào Ngày 4. |
| **W11** | Dữ liệu Lịch sử Đặt phòng & Mô hình ML Dự báo | **Dev D, Dev B** | **Dev A, Dev C** | File dataset mẫu `mock_occupancy_history.csv` & API Stub `:8089` | **W11 Ngày 1, 09:00** | Dev D bắt đầu huấn luyện model Prophet ngay từ file CSV; Dev A và C dựng giao diện biểu đồ JFreeChart với dữ liệu dự báo mẫu. |
| **W12** | Kịch bản Tải & Môi trường Kiểm thử Hardening | **Dev D** | **Dev A, Dev B, Dev C** | Pre-release Test Plan & JMeter Test Harness Draft | **W12 Ngày 1, 09:00** | Toàn đội ngũ rà soát checklist bảo mật và chuẩn bị bản build ứng dụng để bước vào đợt ngâm tải 200 CCU chính thức. |

---

## IV. BẢNG TIÊU CHÍ NGHIỆM THU MỐC GO/NO-GO CUỐI TUẦN 12 (PRODUCTION SIGN-OFF)

Dự án chỉ được phép xuất xưởng bản phát hành chính thức (Golden Master Release) khi thỏa mãn 100% các tiêu chí nghiệm thu khắt khe dưới đây:

### 1. Năng lực Thị giác Máy tính & Check-in Sinh trắc học
* [ ] **Nhận diện Thẻ CCCD:** Trích xuất chính xác 12 chữ số định danh, họ tên tiếng Việt có dấu, ngày sinh với độ trễ $\le 800\text{ms}$ và độ chính xác $\ge 96\%$.
* [ ] **So khớp Khuôn mặt Tự thích ứng:** Mô hình MobileFaceNet tại ngưỡng cosine $0.68$ đạt tỷ lệ $\text{FAR} < 0.01\%$ và $\text{FRR} < 1.5\%$.
* [ ] **Bảo vệ Dữ liệu Nhạy cảm (DLP):** Dữ liệu định danh được mã hóa AES-256-GCM; job định kỳ tự động hủy dữ liệu sinh trắc học sau 30 ngày post-checkout.
* [ ] **Chịu lỗi Mất kết nối AI (Scenario 10):** AI container offline không làm sập backend Java; hệ thống tự chuyển sang chế độ đối chiếu thủ công trong $< 3.0\text{s}$.

### 2. Trợ lý ảo RAG & An ninh Ngữ nghĩa
* [ ] **Tìm kiếm Ngữ nghĩa Siêu tốc:** Kho vector Faiss nhúng phản hồi truy vấn quy chế khách sạn trong $< 15\text{ms}$; dung lượng RAM container AI strictly $< 1.5\text{GB}$.
* [ ] **Phòng chống Tấn công Tiêm lệnh (Scenario 11):** Chặn đứng $100\%$ các đòn tấn công jailbreak, prompt injection và nỗ lực khai thác PII với mã lỗi HTTP 400.
* [ ] **Chuyển tiếp Lễ tân Kịp thời:** Gửi yêu cầu hỗ trợ tạo ticket tức thì và kích hoạt chuông/banner cảnh báo trên máy trạm lễ tân trong $< 16\text{ms}$ EDT.

### 3. Tối ưu hóa Doanh thu & Quản trị Giá động
* [ ] **Độ chính xác Dự báo Công suất:** Mô hình chuỗi thời gian Prophet đạt sai số $\text{MAPE} < 8.5\%$ trên tập dữ liệu kiểm thử độc lập.
* [ ] **Khóa An toàn Hệ số Giá:** Hệ số giá động của AI không bao giờ vượt ra ngoài biên an toàn [$0.70\times - 2.50\times$]; triệt tiêu hoàn toàn nguy cơ overbooking âm phòng.
* [ ] **Bộ đệm Dự báo Hiệu quả:** Cache kết quả dự báo trong 6 giờ; phục vụ các truy vấn lặp lại trong $< 5\text{ms}$.

### 4. Hiệu năng Chịu tải Cấp Doanh nghiệp & Ổn định Tài nguyên
* [ ] **Ngâm tải 200 CCU trong 2 giờ (Scenario 12):** Đạt thông lượng $\ge 350\text{ req/s}$; độ trễ $P_{95} < 120\text{ms}$; tỷ lệ lỗi giao dịch $< 0.05\%$.
* [ ] **Không Rò rỉ Kết nối HikariCP:** Hồ kết nối 50 connection duy trì ổn định trong 2 giờ ngâm tải, số active connections trở về đúng 0 sau khi xả tải.
* [ ] **Bảo vệ Gateway:** Lớp lọc bảo mật áp dụng đầy đủ OWASP headers; chặn spam IP tại ngưỡng 60 req/phút; thu hồi token đăng xuất tức thì.

### 5. An toàn Thông tin & Đóng gói Phân phối
* [ ] **Kiểm toán Lỗ hổng OWASP ZAP:** Đạt 0 lỗ hổng Critical và 0 lỗ hổng High.
* [ ] **Chất lượng Bản cài Desktop Client:** Khởi động lạnh $< 2.0\text{s}$; mức chiếm dụng RAM khi chạy nền $< 180\text{MB}$; không phụ thuộc vào JRE cài ngoài.
* [ ] **Triển khai Sản xuất 1 Lệnh:** Toàn bộ hệ sinh thái dịch vụ khởi động thành công ở trạng thái Healthy qua `./deploy/production_deploy_checklist.sh` trong $< 90\text{s}$.

---

## V. BIÊN BẢN KÝ DUYỆT NGHIỆM THU

| Vai trò Trách nhiệm | Họ tên / Chức danh | Chữ ký / Trạng thái | Ngày Ký duyệt |
|---|---|:---:|:---:|
| **Technical Lead** | Kiến trúc trưởng Hệ thống | `[ PHÊ DUYỆT - NỀN TẢNG WBS PHASE 3 ]` | `2026-10-14` |
| **QA & DevOps Lead** | Trưởng nhóm Kiểm thử (Dev D) | `[ XÁC NHẬN BỘ TEST HARNESS & 200 CCU GATE ]` | `2026-10-14` |
| **Core Backend Lead** | Kỹ sư Trưởng Backend (Dev A & B) | `[ CAM KẾT - WEEKS 9-12 BACKEND ]` | `2026-10-14` |
| **Frontend Lead** | Kỹ sư Trưởng Giao diện (Dev C) | `[ CAM KẾT - SWING & AI COPILOT ]` | `2026-10-14` |
