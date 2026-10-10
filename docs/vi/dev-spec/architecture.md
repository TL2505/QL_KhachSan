# KIẾN TRÚC HỆ THỐNG & LUỒNG DỮ LIỆU — HỆ THỐNG PMS 5★

## 1. MA TRẬN LUỒNG DỮ LIỆU (12 DÒNG)
| Nguồn | Đích | Trình kích hoạt | Hành động |
| :--- | :--- | :--- | :--- |
| `BOOK-02` (Đặt phòng) | `ROOM-01` (Kho phòng) | Xác nhận đặt phòng | Trừ số lượng phòng trống theo ngày |
| `ROOM-01` (Kho phòng) | `BILL-02` (Hóa đơn) | Kiểm toán đêm (Night Audit) | Đăng phí tiền phòng hàng ngày vào Hóa đơn tổng |
| `SERV-01` (Dịch vụ) | `BILL-02` (Hóa đơn) | Khách gọi món/dịch vụ | Đăng phí phát sinh vào Hóa đơn khách |
| `BILL-02` (Hóa đơn) | `ROOM-02` (Xếp phòng) | Đã xác nhận thanh toán | Giải phóng phòng |
| `CRM-01` (Khách hàng) | `BOOK-02` (Đặt phòng) | Đặt phòng mới | Liên kết booking với hồ sơ khách hàng sẵn có |
| `PROMO-01` (Khuyến mãi) | `BOOK-02` (Đặt phòng) | Nhập mã Code | Tính toán lại tổng tiền trước khi xác nhận |
| `CRM-02` (Thành viên) | `BOOK-02` (Đặt phòng) | Đăng nhập VIP | Tự động áp dụng giá chiết khấu cho thành viên |
| `CARE-02` (Đánh giá) | `CRM-01` (Khách hàng) | Check-out | Cập nhật điểm cảm xúc của khách |
| `BILL-02` (Hóa đơn) | `ROOM-02` (Xếp phòng) | Thanh toán đủ | Kích hoạt check-out / giải phóng phòng |
| `ROOM-02` (Xếp phòng) | `BOOK-02` (Đặt phòng) | Trạng thái phòng OOO | Cảnh báo rủi ro Overbooking / Chặn đặt phòng |
| `SYS-01` (Xác thực) | `TẤT CẢ MODULE` | API Request | Xác thực JWT và quyền hạn RBAC |
| `NIGHT AUDIT` | `BILL-02`/`ROOM-02` | Cron lúc 00:00 | Chuyển ngày kinh doanh, đăng phí, đặt lại trạng thái |

## 2. QUY TRÌNH KIỂM TOÁN ĐÊM (NIGHT AUDIT)
**Lịch trình:** Hàng ngày lúc 00:00 theo giờ hệ thống.
**Quy tắc Khóa (Lock Rule):** PMS chuyển sang trạng thái "Đóng băng" từ 00:00 đến 00:05. Các tác vụ ghi API thủ công (nhân viên check-in, booking mới) trả về `503 Service Unavailable` kèm `Retry-After: 300`. Các tính toán tự động của hệ thống (đăng phí phòng, thuế, đối soát POS) vẫn chạy ngầm bên trong.
1. **Khóa Hệ thống:** Ngăn chặn ghi đồng thời thủ công (Đóng băng API).
2. **Xác minh Đến/Đi:** Gắn cờ khách Không đến (No-Show) và Khách ở quá hạn (Overstay).
3. **Phạt Không đến (No-Show):** Đăng phí no-show vào các booking đã được đảm bảo.
4. **Đăng Phí Tiền Phòng:** Áp dụng giá hàng ngày vào các hóa đơn đang hoạt động.
5. **Tính Thuế:** Đăng phí Thuế VAT và Thuế Thành phố (City Tax).
6. **Đăng Phí Dịch Vụ Định Kỳ:** Phí đỗ xe/ăn sáng hàng ngày.
7. **Đối soát POS:** Đồng bộ các giao dịch POS đang ở chế độ lưu-và-chuyển-tiếp.
8. **Thống kê & Báo cáo:** Cập nhật ADR, Công suất phòng (Occupancy), RevPAR và sinh báo cáo DBR.
9. **Chuyển Ngày:** Đẩy ngày kinh doanh từ Ngày N sang Ngày N+1.
10. **Mở khóa Hệ thống:** Khôi phục dịch vụ API.

## 3. CÁC TÌNH HUỐNG NGOẠI LỆ TRONG LUỒNG DỮ LIỆU
- **POS Ngoại tuyến:** POS phải xếp hàng giao dịch cục bộ trong thời gian Khóa 00:00.
- **Xung đột gán phòng:** Hai người đặt chung căn phòng cuối cùng sẽ được xử lý bằng Khóa Lạc quan (Optimistic Locking) trên `ROOM-01`.
- **Lũy đẳng (Idempotency):** Các webhook thanh toán tới `BILL-01` yêu cầu phải có `Idempotency-Key` để tránh thanh toán kép.
