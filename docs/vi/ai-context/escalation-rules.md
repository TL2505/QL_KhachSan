# ĐIỀU KIỆN KÍCH HOẠT & QUY TRÌNH LEO THANG

*Lưu ý: Phải tuân thủ các giới hạn trong `constraints.md`.*

## 1. TỪ KHÓA (Leo thang cứng ngay lập tức)
Nếu tin nhắn của khách chứa bất kỳ từ khóa/ý định nào sau đây, chuyển tiếp ngay lập tức:
`refund, cancel, complain, manager, broken, stolen, emergency, fire, doctor, police, medical, hospital, legal`
(hoàn tiền, hủy, phàn nàn, quản lý, hỏng, mất cắp, khẩn cấp, hỏa hoạn, bác sĩ, cảnh sát, y tế, bệnh viện, pháp luật)

## 2. LUỒNG LEO THANG (Leo thang Cứng / Hard Escalation)
1. **Loại chuyển tiếp:** WARM TRANSFER (Chuyển tiếp Ấm). AI tóm tắt vấn đề của khách và ngữ cảnh (cảm xúc, mã đặt phòng, tóm tắt) thành một payload ẩn gửi đến Staff Dashboard.
2. **SLA (Cam kết thời gian xử lý):** 
   - Leo thang bình thường: Nhân viên phải tiếp nhận trong vòng **2 phút**.
   - Leo thang khẩn cấp: Báo động hình ảnh/âm thanh ngay lập tức đến Quản lý trực ca (Duty Manager).
3. **Định tuyến:** Lễ tân → (Nếu không phản hồi) → Quản lý trực ca → (Nếu lỗi hệ thống) → Phòng IT.

## 3. LEO THANG NGẦM (Theo dõi dự phòng được định lượng)
- **Điều kiện (BẤT KỲ điều kiện nào):**
  - Điểm cảm xúc nằm trong khoảng `[-0.3, 0]` (đo bằng mô hình NLP).
  - Khách lặp lại cùng một câu hỏi `≥ 2 lần` (khớp chính xác hoặc khớp ngữ nghĩa).
  - Khách dùng từ khóa leo thang nhưng cảm xúc `> -0.5` (ví dụ: hỏi về chính sách hủy phòng một cách bình thường).
- **Hành động:** Gắn thẻ `MONITOR` (Theo dõi) vào cuộc trò chuyện trên Staff Dashboard. AI tiếp tục phản hồi. Nếu điểm cảm xúc giảm xuống dưới `-0.5` ở lượt trò chuyện TỚI, tự động kích hoạt LEO THANG CỨNG.
