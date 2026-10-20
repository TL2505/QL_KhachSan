# Biên bản kiểm thử giao diện thủ công

Ngày kiểm tra: 21/10/2026  
Ứng dụng: `http://localhost:3000/portal.html?view=loyalty`  
Vai trò đang đăng nhập: Staff (`Nguyen Van A`)  
Viewport: 1225 × 1168 px  
Thiết bị/mode: desktop, giao diện tối; đã chuyển tạm sang sáng rồi khôi phục về tối.

## Lỗi đã xác nhận

### P1 — Giao diện sáng của staff vẫn để vùng nội dung tối

**Các bước tái hiện:** mở trang Thành viên bằng tài khoản staff, đổi giao diện từ tối sang sáng.

**Thực tế:** topbar và sidebar chuyển sang nền sáng, nhưng nền vùng nội dung vẫn xanh đen; tiêu đề “Thành viên” và “Chính Sách Hệ Thành Viên” cũng tối, khó đọc. Ba panel sáng nằm trên nền tối tạo cảm giác hai chủ đề bị ghép lại.

**Mong đợi:** staff dùng cùng cách chuyển sáng/tối và độ tương phản với admin.

**Dấu hiệu trong mã:** [portal-admin.css](portal-admin.css#L1231) đặt nền cố định `#090e18` cho `.staff-portal .portal-content`; kiểm tra giao diện sáng cho thấy quy tắc này không đổi theo theme.

### P1 — Ngưỡng hạng customer không thống nhất giữa chính sách và phân màu

Trang Thành viên hiển thị Gold từ **300 điểm**, VIP từ **1.000 điểm**, Silver là mặc định. Trong khi [tier-helper.js](js/customer/tier-helper.js#L5) và logic tính màu tại [view-customer.js](js/view-customer.js#L44) phân loại Silver từ 500 điểm, Gold từ 1.000 điểm và VIP từ 5.000 điểm. [loyalty.js](js/customer/loyalty.js#L87) lại dùng ngưỡng thứ ba: Gold 300 và VIP 1.000.

**Tác động:** khách hàng từ 300–499 điểm có thể thấy Gold theo chính sách nhưng nhận màu Member; từ 1.000–4.999 điểm có thể thấy VIP theo chính sách nhưng nhận màu Gold. Vì vậy màu panel không đảm bảo phản ánh hạng đang hiển thị.

**Mong đợi:** một bộ ngưỡng hạng duy nhất được dùng cho chính sách, dashboard, tiến độ loyalty và màu panel.

## Kết quả và giới hạn kiểm tra

- Staff ở desktop, giao diện tối: sidebar, topbar và vùng cuộn hiển thị ổn định; sidebar đang thu gọn theo trạng thái lưu trong phiên.
- Staff ở desktop, giao diện sáng: lỗi P1 thứ nhất tái hiện được. Sau kiểm tra, giao diện đã được khôi phục về tối.
- Chưa thể kiểm tra trực tiếp admin và customer trong cùng lượt vì tab đang đăng nhập bằng tài khoản staff. Do đó các kết luận so sánh với admin dựa trên quy tắc theme trong mã và giao diện admin đã có, không phải phiên đăng nhập admin mới.
- Chưa kiểm tra tablet/mobile vì viewport trình duyệt hiện tại không cung cấp điều khiển thay đổi kích thước.
