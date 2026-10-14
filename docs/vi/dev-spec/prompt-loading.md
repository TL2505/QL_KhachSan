# CHIẾN LƯỢC TẢI PROMPT HỖN HỢP (HYBRID)

## 1. TỔNG QUAN KIẾN TRÚC
AI Gateway sử dụng phương pháp kết hợp 3 tầng để quản lý giới hạn ngữ cảnh (context limit) và đảm bảo tuân thủ tuyệt đối các chính sách của khách sạn.

### Tầng 1: Tiêm tĩnh (Cốt lõi)
- **Nội dung:** `constraints.md` (11 quy tắc cứng) + Persona của Hệ thống (Khách/Nhân viên).
- **Thực thi:** Tiêm thẳng vào `system_prompt` của MỌI lượt gọi LLM.
- **Ngân sách:** ~500 tokens.
- **Mục đích:** Các quy tắc bất di bất dịch, không bao giờ được phép sót do lỗi truy xuất (retrieval).

### Tầng 2: RAG (Sinh văn bản tăng cường bằng truy xuất)
- **Nội dung:** `edge-cases.md`, `escalation-rules.md`, FAQ khách sạn, SOPs.
- **Thực thi:** Lưu trong `pgvector`. Các đoạn văn bản liên quan được truy xuất dựa trên độ tương đồng ngữ nghĩa của truy vấn người dùng.
- **Cơ chế:**
  - Mô hình nhúng (Embedding): `text-embedding-3-small` (OpenAI).
  - Lấy top: `Top-K = 3`.
  - Ngưỡng tương đồng: `0.75`.
- **Ngân sách:** ~1000 - 1500 tokens mỗi lần truy xuất.
- **Dự phòng (Fallback):** Nếu RAG trả về 0 kết quả (không có gì vượt ngưỡng), AI chỉ dựa vào Tiêm tĩnh và dùng câu trả lời dự phòng "Không có dữ liệu".

### Tầng 3: Thực thi qua Middleware (Vô hình với AI)
- **Nội dung:** `data-access.md` (Phân quyền RBAC), Cache TTLs, Ẩn PII (Dữ liệu cá nhân).
- **Thực thi:** Chạy trong middleware của FastAPI *trước khi* prompt đến LLM và *sau khi* LLM sinh câu trả lời.
- **Mục đích:** Bắt buộc ở mức mã nguồn (code-level). AI không thể "ảo giác" vượt qua tầng này vì nó không kiểm soát payload của API hay bước che dấu JSON (masking) cuối cùng.

## 2. ƯỚC TÍNH NGÂN SÁCH TOKEN (Mỗi Yêu cầu)
| Thành phần | Ước lượng Token | Tỷ lệ Ngữ cảnh |
| :--- | :--- | :--- |
| System Prompt tĩnh | ~500 | 12.5% |
| Lịch sử hội thoại (5 lượt gần nhất) | ~1000 | 25% |
| Ngữ cảnh RAG (Top-K) | ~1500 | 37.5% |
| Tin nhắn người dùng | ~100 | 2.5% |
| **Tổng Prompt Đầu vào** | **~3100 Tokens** | **< Tối ưu hóa dưới giới hạn 4K** |
