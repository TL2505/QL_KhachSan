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

## Customer — các vấn đề còn tồn đọng (21/10/2026)

Phạm vi đợt sửa mới nhất: chỉ xử lý hộp thông báo bị che và theme của hộp thông báo. Các mục dưới đây **chưa sửa**, dựa trên ảnh người dùng gửi và kiểm tra mã; không coi các kiểm tra shell thành nghiệm thu toàn bộ nội dung customer.

| Mức | Vị trí | Vấn đề / bằng chứng | Hướng xử lý tiếp |
| --- | --- | --- | --- |
| P1 | Dashboard sáng — `#member-stats-wrapper` | Tiêu đề tiến độ gần như mất trên nền trắng; mô tả hạng và phần giải thích có độ tương phản thấp. `js/customer/dashboard.js` còn `text-white`, `text-slate-300` và nền tối bán trong suốt cố định. | Chuyển màu chữ/nền/viền sang token theme; kiểm tra tương phản cho cả bốn hạng. |
| P1 | Dashboard sáng — `#member-summary-stats` | Giá trị số đêm/lượt đặt phòng không nhìn rõ trong ảnh; `h4` còn `text-white` khi card đã đổi thành trắng. | Dùng màu nội dung theo theme cho giá trị và nhãn phụ; kiểm tra số 0 và số lớn. |
| P2 | Dashboard, Đặt phòng | Một số badge, icon, khối hướng dẫn và nút ở trang Đặt phòng còn màu amber cố định, chưa đồng bộ hoàn toàn với màu hạng. Giữ màu ngữ nghĩa thành công/cảnh báo khi phù hợp. | Rà soát component nội dung theo Member/Silver/Gold/VIP; tách màu thương hiệu trang trí của thẻ khỏi màu thao tác chính. |
| P2 | Đặt phòng và một số trang hồ sơ | Shell và nội dung có hai tiêu đề cùng nghĩa, ví dụ “Lịch sử đặt phòng” / “Lịch Sử Đặt Phòng” trong ảnh phản hồi trước. | Thống nhất một tiêu đề chính và phần mô tả, kiểm tra spacing từng tab. |
| P2 | Thẻ thành viên dashboard | Tên chủ thẻ bị cắt thành “Khách hàng Te...” ngay cả ở desktop trong ảnh mới. | Cân đối cột thông tin trên thẻ, cho phép xuống dòng hoặc cung cấp cách xem tên đầy đủ. |
| P2 | Đặt phòng — bảng rỗng | Ảnh phản hồi trước có thanh cuộn ngang ngay cả khi không có đơn; bố cục trạng thái rỗng bị bó trong khung bảng. | Kiểm tra `min-width` bảng và hiển thị trạng thái rỗng ngoài vùng cuộn; vẫn giữ cuộn ngang khi có dữ liệu trên màn hình hẹp. |
| P2 | Thông báo — khi API lỗi | Kiểm tra mã `loadNotifications()` cho thấy có fallback thông báo/voucher minh họa khi API không trả dữ liệu hợp lệ; có thể làm khách hiểu nhầm ưu đãi thật. Chưa xác nhận lỗi API trên backend thật. | Thay fallback bằng trạng thái lỗi/thử lại và phân biệt rõ dữ liệu rỗng; xử lý ở lượt sau, không đổi trong bản sửa lớp hiển thị này. |

Chưa nghiệm thu toàn bộ Dịch vụ & ưu đãi, Hồ sơ, Bảo mật và các modal với backend thật trong lượt này; cần rà soát sáng/tối, mobile và dữ liệu thực tế trước khi đóng audit customer.

## Kết quả và giới hạn kiểm tra

### Cập nhật sửa lỗi — 21/10/2026

- Sửa hộp thông báo customer: header có stacking context trên card/sidebar; dropdown dùng màu surface/text/muted theo theme và hạng, gồm tiêu đề, trạng thái rỗng, danh sách, voucher và nút. Khung co theo viewport, list cuộn độc lập; mobile neo theo header để không vượt cạnh trái. Không đổi endpoint hoặc thao tác đánh dấu đã đọc. Kiểm tra Chromium dùng bootstrap thật/API giả lập đạt 40 tổ hợp hạng × theme × viewport; hit-test phần đầu/cuối dropdown xác nhận nằm trên nội dung có stacking context, không vượt màn hình, đúng màu và cuộn được; kiểm tra trạng thái rỗng/đóng bằng click ngoài. 16 test auth/theme/portal đạt. Tài nguyên CDN không có trong môi trường kiểm tra; chưa nghiệm thu backend thật. CSS/module được tăng phiên bản `3.6.20`, root/public đồng bộ; ảnh `docs/verification/web-ui/customer-notifications-*.png`.

- Đồng bộ hiệu ứng tab customer với admin: dùng chung pseudo-element vạch 3px mở tới 58% chiều cao trong 180ms ease khi hover/chọn, thay vạch inset kín chiều cao bằng viền inset 1px và nền gradient 15%–4% theo màu hạng. Hover dùng nền màu nhấn 10%; tab đang chọn giữ gradient khi hover. Chromium xác nhận 8 tổ hợp 4 hạng × 2 theme, chuyển chọn và rời hover đều trả về đúng trạng thái. Tăng phiên bản stylesheet lên `3.6.19` và đồng bộ root/public.

- Sửa shell customer theo admin: bỏ CSS điều hướng cũ ép menu desktop nằm ngang; dùng đủ 5 tab, cùng kích thước sidebar/breakpoint và cơ chế thu gọn. Brand căn trái 16px (12px trên điện thoại); tài khoản/đăng xuất đặt cuối sidebar trên desktop và chỉ hiện một cụm trên header mobile. Bỏ chuyển tài khoản customer lên header gây lặp. Bảng màu theo hạng: Member xanh lam, Silver bạc, Gold vàng champagne, VIP tím; nút đặt phòng và tiến độ dùng cùng màu nhấn. Đồng bộ root/public và tăng phiên bản CSS/module để tránh cache cũ. `verify-customer-shell.cjs` chạy bootstrap/module thật với API giả lập, đạt 40 tổ hợp 4 hạng × 2 theme × 5 viewport (320–1340px), gồm kiểm tra thứ tự/kích thước menu, căn lề, đăng xuất duy nhất, thu gọn/mở rộng và không tràn ngang. 28 test auth/portal/theme/membership đạt; bộ Chromium hồi quy các trang staff/customer cũng đạt. CDN bị chặn trong môi trường kiểm tra nên ảnh không phản ánh font/icon/Tailwind CDN; chưa nghiệm thu với backend thật. Kết quả: `docs/verification/web-ui/customer-shell-results.json`.

- Đã sửa P1 theme staff: nền nội dung, thẻ phòng, bảng, nhãn trạng thái và trường trong modal dùng bảng màu sáng tương ứng với admin; chế độ tối được giữ nguyên. Các biến màu nội dung customer cũng được nối với theme đang chọn.
- Đã sửa P1 hạng thành viên theo `LoyaltyService.java`: Member từ 0, Silver từ 500, Gold từ 1.000, VIP từ 5.000 điểm. Chính sách staff, dashboard, loyalty và màu panel customer dùng chung `tier-helper.js`. Mốc 300/1.000 trong chính sách cũ không phản ánh backend, vì vậy không dùng làm chuẩn.
- Phân biệt điểm khả dụng và tổng điểm đã tích lũy: đổi voucher giảm số dư nhưng không làm giảm hạng hoặc tiến độ. Chuẩn hóa cả DTO Java (`pointsChange`, `type`, `description`, `createdAt`) và tên trường frontend cũ.
- Kiểm tra tự động: 66/66 test web UI đạt. Kiểm tra Chromium bằng dữ liệu giả lập đạt 16 tổ hợp staff/customer × sáng/tối × 1225/768/375/320 px, không tràn ngang trang. Ảnh và kết quả lưu trong `docs/verification/web-ui/`.
- Sửa thêm hai vị trí trong ảnh đối chiếu: nút thu gọn sidebar staff dùng chung kích thước/viền với admin; cụm tài khoản trên header staff ẩn ở desktop và hiện theo cùng breakpoint với admin. Chromium xác nhận 6 phép so sánh admin/staff tại 1225/768/375 px trong cả hai theme; 12 test auth/theme liên quan đạt.
- Đồng bộ trang Khách hàng staff theo ảnh đối chiếu tiếp theo: ba thẻ thống kê dùng bố cục admin; sửa nút làm mới, avatar tròn, nút xem chi tiết, tiêu đề/màu bảng và phân trang. Giữ nguyên thao tác dành cho staff. Kiểm tra Chromium đạt 8 tổ hợp theme/viewport với 13 hồ sơ giả lập và kiểm tra chuyển trang, tìm kiếm, làm mới; 11 test pagination/theme/portal liên quan đạt. Kết quả chi tiết: `docs/verification/web-ui/staff-customers-results.json`.
- Đồng bộ thêm Đặt phòng, Hóa đơn, Hồ sơ & bảo mật: dùng chung thẻ thống kê admin, bảng và nhãn trạng thái; bộ lọc co giãn theo viewport; trường hồ sơ căn theo cột; ngày giờ đặt phòng hiển thị gọn theo giờ Việt Nam và cột thao tác giữ cấu trúc ô bảng. Kiểm tra Chromium với dữ liệu giả lập đạt 30 tổ hợp 3 tab × 2 theme × 5 chiều rộng (320/375/768/1225/1440 px). Kết quả: `docs/verification/web-ui/staff-tabs-results.json`.
- Làm lại danh sách Nhận & trả phòng theo cấu trúc daisyUI card/card-body/card-actions, input, badge, button, loading và alert. Thẻ gọn, nút thanh toán tối thiểu 44px, bổ sung tìm phòng/khách/mã đặt phòng, phân trang, làm mới và trạng thái tải/rỗng/lỗi/thử lại. CSS chỉ nằm trong phạm vi checkout; phần hiển thị giá trị đặt phòng không được coi là số dư folio. Hộp thoại và luồng gửi thanh toán hiện có được giữ nguyên. Chromium đạt 10 tổ hợp theme/viewport 320–1600px và tương tác tìm kiếm, phân trang, mở modal, lỗi/thử lại, danh sách rỗng bằng dữ liệu giả lập; không gửi thanh toán. Kết quả: `docs/verification/web-ui/staff-checkout-results.json`; 11 test liên quan đạt.
- Thiết kế lại hộp thư staff: danh sách hội thoại có tìm kiếm, avatar, xem trước; khung chat daisyUI với tên khách, thời gian và ô soạn nhiều dòng; mobile có nút quay lại danh sách. Giữ endpoint inbox có `staffId`, history và send hiện có; hiển thị lỗi/thử lại, giữ bản nháp theo khách, chặn gửi trùng và bỏ kết quả trả về cho khách đã chuyển khỏi. Tự cập nhật dừng khi rời tab/ẩn ứng dụng/hết phiên. Chromium đạt 10 tổ hợp sáng/tối × 320/375/768/1225/1440px cùng kiểm tra gửi giả lập thành công/thất bại, bản nháp, tìm kiếm, chuyển khách và hết phiên; không gửi tin thật. Kết quả: `docs/verification/web-ui/staff-support-results.json`; 16 test auth/theme/portal liên quan đạt.
- Tinh chỉnh hộp thư theo ảnh phản hồi: bỏ tiêu đề shell trùng lặp riêng ở tab chat, bỏ nút Làm mới thường trực và để lỗi danh sách tự thử lại; giữ nút thử lại khi tải lịch sử lỗi. Khung chat dùng phần chiều cao/chiều rộng còn lại của layout thay vì giới hạn 740px, danh sách và tin nhắn cuộn riêng. Đo viewport từ 320×568, 375×480 đến 1225×1350; ô soạn luôn trong màn hình và khung lấp đầy vùng nội dung. Kết quả: `docs/verification/web-ui/staff-support-sizing.json`.
- Sửa lịch sử chat rỗng sau khi staff trả lời: inbox trả về tin nhắn mới nhất, nên `senderId` có thể là staff. Frontend xác định người đối thoại từ `receiverId` khi staff là người gửi; giữ nguyên tác giả tin nhắn và endpoint backend. Kiểm tra hồi quy với staff #2 trả lời khách #3; gộp các dòng trùng khách nếu có. Nút gửi đặt cạnh textarea; màn hình hẹp dùng nút biểu tượng có tên truy cập. Kiểm tra 3 test ánh xạ inbox và Chromium xác nhận lịch sử cũ + nút gửi cùng hàng trên 10 tổ hợp theme/viewport bằng dữ liệu giả lập.
- Kiểm tra trình duyệt sử dụng shell/CSS/module thật với API giả lập; chưa phải nghiệm thu đăng nhập và giao dịch với backend thật. Tài nguyên CDN được chặn trong lượt kiểm tra này, nên ảnh không phản ánh font/icon CDN.

- Staff ở desktop, giao diện tối: sidebar, topbar và vùng cuộn hiển thị ổn định; sidebar đang thu gọn theo trạng thái lưu trong phiên.
- Staff ở desktop, giao diện sáng: lỗi P1 thứ nhất tái hiện được. Sau kiểm tra, giao diện đã được khôi phục về tối.
- Chưa thể kiểm tra trực tiếp admin và customer trong cùng lượt vì tab đang đăng nhập bằng tài khoản staff. Do đó các kết luận so sánh với admin dựa trên quy tắc theme trong mã và giao diện admin đã có, không phải phiên đăng nhập admin mới.
- Chưa kiểm tra tablet/mobile vì viewport trình duyệt hiện tại không cung cấp điều khiển thay đổi kích thước.
