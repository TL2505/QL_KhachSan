# KẾ HOẠCH TRIỂN KHAI & PHÁT HÀNH

## 1. TÓM TẮT CÔNG NGHỆ
| Thành phần | Công nghệ | Mục đích |
| :--- | :--- | :--- |
| **Backend API** | Java 17 (plain HttpServer) | Logic nghiệp vụ cốt lõi, hàm PMS, Night Audit |
| **AI Gateway** | Python 3.11 + FastAPI | Guardrails, RAG, Điều phối LLM |
| **Cơ sở dữ liệu** | MariaDB 11 | Dữ liệu giao dịch, Đặt phòng, Hóa đơn (Folios) |
| **Vector DB** | PostgreSQL 16 + pgvector | Document embeddings cho RAG |
| **Triển khai** | Docker Compose | Quản lý container (6 containers) |

## 2. LỘ TRÌNH THEO GIAI ĐOẠN
### Giai đoạn 1: PMS Lõi & APIs (Tuần 1-4)
- **Phạm vi:** CRUD cho Phòng, Booking, Folio. Xác thực & RBAC.
- **Tiêu chí Go/No-Go:** 
  1. 100% API endpoints vượt qua unit tests.
  2. Xác thực (JWT) hạn chế đúng quyền hạn (roles).
  3. Xử lý thành công lỗi race condition khi đặt phòng đồng thời.

### Giai đoạn 2: AI Gateway & Guardrails (Tuần 5-8)
- **Phạm vi:** Dịch vụ FastAPI, tích hợp OpenAI, thực thi `constraints.md`.
- **Tiêu chí Go/No-Go:** 
  1. Tỷ lệ test chống prompt injection thành công > 99%.
  2. AI không bao giờ báo sai giá trong các bài test tự động.
  3. Độ trễ (Latency) < 2 giây cho mỗi phản hồi AI.

### Giai đoạn 3: Thử nghiệm AI & Tích hợp (Tuần 9-12)
- **Phạm vi:** Kết nối AI Gateway với API PMS. Test thử nghiệm Trợ lý Copilot cho nhân viên.
- **Tiêu chí Go/No-Go:**
  1. Nhân viên có thể lấy dữ liệu PMS qua ngôn ngữ tự nhiên.
  2. Luồng leo thang (Warm Transfer) định tuyến chuẩn xác tới Dashboard.
  3. Không để lộ PII trong 2 tuần test thử nghiệm nội bộ.

### Giai đoạn 4: Mở rộng & Ra mắt Khách hàng (Tuần 13+)
- **Phạm vi:** Ra mắt Chatbot cho khách, Webhooks, Đồng bộ OTA.
- **Lộ trình Mở rộng:** Chatbot Khách hàng → Nháp Booking → Yêu cầu Dịch vụ → Phân tích Doanh thu.

## 3. MA TRẬN RACI
| Công việc | Doanh nghiệp (Bạn) | Đội Dev | AI Tool/Gateway |
| :--- | :--- | :--- | :--- |
| Định nghĩa Chính sách (constraints) | **A/R** | C | I |
| Xây dựng PMS APIs | C | **A/R** | I |
| Triển khai Guardrails | I | **A/R** | C |
| Giám sát Vi phạm | **A** | R | **C** |
*(R=Chịu trách nhiệm thực thi, A=Người phê duyệt cuối cùng, C=Được tham vấn, I=Được thông báo)*

### Phạm vi Nền tảng theo Giai đoạn (Platform Scope per Phase)

| Giai đoạn | Nhân viên (Staff) | Khách hàng (Guest) | Quản trị (Admin) | Trọng tâm Dev C |
|---|---|---|---|---|
| 1-2 | Swing (sẵn có, chỉ tích hợp API) | Không có | Không có | Kết nối API → Swing. KHÔNG làm React. |
| 3 | Swing (giữ nguyên) | React web (đặt phòng, chat, đánh giá) | React dashboard | Xây dựng React web. |
| 4+ | React (di chuyển tùy chọn) | React + PWA | React | Chuyển đổi Swing → React. |

**Triết lý thiết kế:** Nền tảng = Nơi người dùng ngồi. Nhân viên ngồi bàn làm việc = Desktop. Khách dùng điện thoại = Web. Không thống nhất frontend cho đến Giai đoạn 4+.
