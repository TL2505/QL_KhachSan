# Kiểm tra workbench nhân viên buồng phòng

Ngày: 2026-10-29
Nhánh: `codex/housekeeping-role-portal`

## Phạm vi

Workbench buồng phòng được bổ sung checklist bắt buộc, báo cáo sự cố có thể đính kèm ảnh, cảnh báo cần kiểm tra lại, mức ưu tiên, lọc tầng/khu vực, nhật ký thao tác và số liệu công việc trong ngày. Migration `v4_14_housekeeping_workbench.sql` đã được áp dụng trên MariaDB QA cục bộ.

## Kiểm tra thủ công trên cổng 3000

- Đăng nhập bằng tài khoản QA buồng phòng đã được tạo trước đó; trang công việc tải được danh sách và thống kê.
- Tìm `QA-801` lọc đúng công việc; các thẻ `Tất cả bàn giao` và `Đã hoàn tất` hiển thị đúng nhóm.
- Mở chi tiết phòng QA-801 để xem checklist, mục sự cố và nhật ký thao tác. Nhật ký hiển thị các sự kiện nhận dọn và hoàn tất có người thực hiện và thời gian.
- Dữ liệu hiện có chỉ chứa một công việc đã hoàn tất, nên không có việc đang dọn để thử gửi sự cố hoặc thay đổi checklist qua thao tác trực tiếp.

## Lỗi phát hiện và xử lý

- Việc cũ được tính 6/6 nhưng checkbox hiển thị trống. Đổi trạng thái hoàn tất sang dấu kiểm tĩnh có nhãn truy cập, để không gây nhầm với mục có thể thao tác.
- API bỏ chọn checklist trước đây vẫn ghi `completed_at`. Sửa truy vấn để khi bỏ chọn, cả người xác nhận và thời điểm hoàn tất đều được xóa; nhờ đó nút hoàn thành tiếp tục bị khóa nếu còn mục bắt buộc chưa xong.
- Sau khi sửa dấu kiểm, phiên computer-use bị dừng trước bước tải lại trang. Trạng thái sau sửa được xác nhận bằng kiểm thử DOM, chưa chụp lại ảnh trình duyệt.

## Kiểm chứng tự động

- `npm test`: 157 kiểm thử đạt, 0 lỗi.
- `node --check` cho JavaScript workbench trong thư mục nguồn và bản public: đạt; hai bản trùng nội dung.
- `mvn -q -DskipTests compile`: đạt.
- `git diff --check`: đạt.

## Giới hạn

Chưa gửi báo cáo sự cố hoặc tải ảnh lên storage trong phiên QA này; chưa có công việc đang dọn để kiểm tra trực tiếp các thao tác checklist. Không gọi cổng thanh toán, không gửi email thật.
