# GIÁM SÁT AI & CÁC CHỈ SỐ ĐO LƯỜNG (KPI)

## 1. CÁC CHỈ SỐ CHÍNH (KPIs)
- **Tỉ lệ Xử lý Tự động (Deflection Rate):** Mục tiêu `> 60%` (Các cuộc trò chuyện được giải quyết mà không cần nhân viên).
- **Tỉ lệ Vi phạm (Violation Rate):** Mục tiêu `< 0.1%` (Các trường hợp AI vi phạm `constraints.md`).
- **Tuân thủ SLA Leo thang:** Mục tiêu `> 95%` (Nhân viên tiếp nhận chuyển tiếp ấm trong vòng 2 phút).
- **Thời gian Xử lý Trung bình (AHT):** Mục tiêu tốc độ phản hồi của AI `< 2 giây`.

## 2. GIÁM SÁT LIÊN TỤC
- **Theo dõi Cảm xúc (Sentiment):** Báo cáo hàng ngày về cảm xúc trung bình của khách khi dùng AI.
- **Từ khóa Kích hoạt:** Phân tích hàng tuần về các lý do hàng đầu gây ra Leo Thang Cứng để tối ưu FAQ hoặc quy trình SOP.
- **Kiểm tra Ảo giác (Hallucination Checks):** Hàng tuần, role Tuân thủ/QA (Compliance/QA) lấy ngẫu nhiên 100 log của AI để kiểm tra việc tuân thủ quy tắc dữ liệu.

## 3. NGƯỠNG BÁO ĐỘNG (Slack / Email)
- **NGHIÊM TRỌNG (CRITICAL):** Tỉ lệ vi phạm vượt `0.5%` trong 1 giờ → Báo động IT & GM.
- **CAO (HIGH):** Tuân thủ SLA leo thang giảm xuống dưới `80%` (Nhân viên bỏ lơ chat) → Báo động Trưởng bộ phận Tiền sảnh (FOM).
- **TRUNG BÌNH (MEDIUM):** Fallback (Câu trả lời dự phòng) bị gọi `> 10 lần` trong một giờ (Có thể do lỗi API PMS) → Báo động IT.
