# TÍCH HỢP BÊN THỨ 3 & APIs

## 1. OTA (Đại lý du lịch trực tuyến: Booking.com, Agoda)
- **Giao thức:** REST JSON (Hiện đại) / HTNG (Dự phòng cho Legacy).
- **Tần suất Đồng bộ:** Đẩy thời gian thực qua Webhooks; Polling dự phòng mỗi 15 phút.
- **Kiểm tra Cân bằng Giá (Rate Parity):** Tự động kiểm tra trước khi đẩy update để tránh bán phá giá.
- **Xử lý Lỗi:** Hàng đợi tin nhắn chết (Dead Letter Queue - DLQ) cho các lần đồng bộ lỗi; thử lại với thuật toán exponential backoff.

## 2. CỔNG THANH TOÁN (Stripe / Cổng nội địa VN)
- **Phương thức Tích hợp:** API (Server-to-Server) cho token hóa + Hosted Redirect cho xác thực 3DS.
- **Tính lũy đẳng (Idempotency):** Bắt buộc. Ép buộc có header `Idempotency-Key` trên mọi `POST /payments`.
- **Webhook:** Xác nhận thanh toán bất đồng bộ (`payment_intent.succeeded`).
- **Xử lý Lỗi:** Nếu webhook lỗi, chạy cron job đối soát mỗi giờ 1 lần.

## 3. POS (Điểm Bán Hàng - F&B)
- **Tích hợp:** Direct REST API vào endpoint `BILL-02` (Đăng phí).
- **Chế độ Ngoại tuyến (Offline):** POS phải hỗ trợ Store & Forward. Nếu PMS sập (ví dụ: đang khóa lúc Night Audit), POS xếp hàng đợi cục bộ và đẩy lên khi nhận lại nhịp tim (heartbeat) 200 OK.
- **Đối soát (Reconciliation):** Đọc Z-read cuối ca đồng bộ tổng doanh thu so với các folio đã đăng trên PMS.

## 4. SMS / EMAIL (CARE-03)
- **Nhà cung cấp:** Twilio (SMS) / SendGrid (Email).
- **Quản lý Mẫu (Template):** Lưu trên PMS; AI chỉ kích hoạt template ID, không sinh văn bản thô.
- **Giới hạn tỷ lệ (Rate Limiting):** Tối đa 3 SMS mỗi khách mỗi ngày để tránh spam.
- **Xác thực & Định dạng:** Bearer Token, payload JSON. Thử lại tối đa 3 lần nếu gặp lỗi 5xx.
