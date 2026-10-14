# KẾ HOẠCH ỨNG PHÓ SỰ CỐ AI

## 1. PHÂN LOẠI SỰ CỐ
- **Tier 1 (Nghiêm trọng):** AI làm lộ dữ liệu PII, báo sai giá gây thiệt hại tài chính, hoặc tạo ra nội dung phản cảm.
- **Tier 2 (Cao):** AI cung cấp sai thông tin chính sách (ví dụ: hủy phòng), AI Gateway bị sập.
- **Tier 3 (Trung bình):** Khối lượng câu trả lời dự phòng (fallback) tăng vọt do mạng nội bộ chậm.

## 2. QUY TRÌNH ỨNG PHÓ (Tier 1 & 2)
1. **Nút Ngắt khẩn cấp (Kill Switch):** IT ngay lập tức vô hiệu hóa AI đối mặt với khách qua Admin Dashboard. Giao diện chat chuyển sang chế độ "Chỉ nhân viên trực tiếp".
2. **Ngăn chặn:** Xác định các khách hàng bị ảnh hưởng. Xuất file log trò chuyện.
3. **Xin lỗi & Khắc phục:** Quản lý trực ca lập tức liên hệ với khách bị ảnh hưởng.
4. **Phân tích Nguyên nhân Cốt lõi (RCA):** Đội Dev phân tích prompt, log tìm kiếm RAG, và kết quả sinh ra của LLM.
5. **Vá lỗi & Khôi phục:** Triển khai bản fix cho `constraints.md` hoặc Middleware. QA test lại chính xác prompt gây lỗi. Bật lại AI.

## 3. MẪU XIN LỖI (Dành cho Nhân viên)
*Lưu ý: Các mẫu này CHỈ DÀNH CHO NHÂN VIÊN. AI KHÔNG ĐƯỢC PHÉP tự sinh ra các hứa hẹn đền bù theo Quy tắc 2 trong constraints.md.*

**Nếu AI báo sai giá:**
*"Kính chào [Tên], chúng tôi thành thật xin lỗi. Trợ lý ảo của chúng tôi đã cung cấp mức giá không chính xác do lỗi đồng bộ hệ thống. Mức giá chính xác là [Giá]. Để thể hiện thiện chí, chúng tôi sẽ áp dụng [Giảm giá/Dịch vụ miễn phí]."*

**Nếu AI không kích hoạt leo thang khẩn cấp kịp thời:**
*"Kính chào [Tên], chúng tôi vô cùng xin lỗi vì hệ thống tự động đã không kết nối quý khách với đội ngũ nhân viên kịp thời. Chúng tôi đang rà soát lại vấn đề này ngay lập tức. Hiện tại tôi có thể hỗ trợ quý khách điều gì ạ?"*
