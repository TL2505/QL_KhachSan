# Checklist triển khai chính sách loại hình lưu trú

Nguồn nghiệp vụ: [Tái cấu trúc nghiệp vụ đặt phòng và buồng phòng](tai-cau-truc-nghiep-vu-dat-phong-va-buong-phong.md).

## Đã triển khai trong nhánh công việc này

- [x] Khai báo `HOURLY`, `OVERNIGHT`, `DAILY` và buffer mặc định 30/60/120 phút.
- [x] Chuẩn hóa khung giờ; đặt theo giờ quá 6 tiếng được chuyển sang qua đêm khi bao phủ khung 22:00–06:00, nếu không thì chuyển sang ngày.
- [x] Thêm migration lưu loại hình, snapshot buffer, `room_release_at`, thời điểm nhận/trả thực tế và buffer cấu hình theo phòng.
- [x] Áp dụng khoảng giữ lịch có buffer trong availability, cấp phòng trong transaction và đường kiểm tra legacy.
- [x] Checkout cập nhật mốc sẵn sàng, tạo cleaning task theo loại hình và đặt `due_at` bằng check-in kế tiếp.
- [x] Check-in kiểm tra thời điểm, trạng thái sạch, buffer và task dọn phòng.
- [x] Thêm cột đơn giá giờ/qua đêm và API báo giá theo các mức đã cấu hình.
- [x] Cập nhật UI tìm phòng, chọn loại hình, báo giá và tạo booking; mã ưu đãi chỉ áp dụng cho `DAILY`.
- [x] Chuyển insert booking legacy qua kiểm tra cấp phòng chung; khóa đường cập nhật lịch/trạng thái trực tiếp.
- [x] Đăng ký API danh sách cleaning task cho nhân viên buồng phòng.
- [x] Thêm API/màn hình quản trị cấu hình ba đơn giá theo hạng phòng; chỉ role `admin` được đọc/lưu.
- [x] Thêm hàng đợi cleaning task có thứ tự ưu tiên, cờ quá hạn và thao tác nhận task; không nhận dọn trước khi phòng được giải phóng.
- [x] Tính phụ thu nhận sớm theo mỗi giờ bắt đầu. Khi trả muộn, miễn 30 phút đầu, sau đó mỗi giờ bắt đầu tính một phụ thu; quá 6 giờ tính thêm một lần bằng một giờ phụ thu. Dùng reference ổn định để tác vụ nền và checkout không ghi trùng.
- [x] Đơn Web thanh toán tại quầy còn dư nợ sau checkout được giữ đang lưu trú; sau 30 phút quá hạn ghi dấu quá hạn và tạo một thông báo trong trung tâm thông báo nhân viên, kèm khách, phòng, số điện thoại, dư nợ và lối tắt thu tiền/trả phòng.
- [x] Khi HOURLY lưu trú thực tế vượt 6 giờ, tính lại chênh lệch giá theo gói OVERNIGHT/DAILY, áp buffer của gói cuối và yêu cầu cấu hình giá đầy đủ.
- [x] Checkout lưu khoản đã thu; đóng booking/tạo cleaning task khi các khoản cơ sở đã thanh toán. Phụ thu do thời gian thực tế còn dư nợ được giữ trên folio và có thể thu tiếp sau checkout.
- [x] Bổ sung test cho khung giờ/chuyển gói và assert các cột/bảng rental policy trên cả schema cũ lẫn schema khởi tạo mới.
- [ ] Chạy migration integration test trên MariaDB 11 và rà DB acceptance trên môi trường thử nghiệm cô lập.

> Trạng thái xác minh: Maven compile, `RentalStayWindowTest`, `HousekeepingControllerAuthorizationTest` và `RentalPolicyAdminControllerSecurityTest` đạt. `MariaDbMigrationIntegrationTest` có assert cho cả schema cũ và mới nhưng bị skip trong phiên này vì không có `HOTEL_MARIADB_TEST_URL`/user và Docker CLI không sẵn có.

## Quy tắc đang dùng khi hiện thực

- Đặt theo ngày giữ khung 14:00–12:00; qua đêm phải bao phủ 22:00–06:00 và cho phép vào sớm từ 18:00 hoặc trả muộn đến 12:00 để tính phụ thu giờ.
- Booking theo giờ tính trọn block 2 giờ đầu; phần lẻ sau đó làm tròn lên giờ kế tiếp.
- `hourly_base_price` lưu tổng giá của block 2 giờ đầu; `hourly_extra_hour_price` là giá cho mỗi giờ phụ thu.
- Khi đặt theo giờ vượt 6 giờ, hệ thống chuyển gói trước khi kiểm tra lịch/giá. UI phải hiển thị gói đã chuyển và giá sau chuyển trước khi xác nhận.
- Đơn giá HOURLY/OVERNIGHT phải được cấu hình riêng; không suy diễn từ đơn giá theo ngày.
- Nếu các cột đơn giá theo giờ/qua đêm còn `NULL`, API báo giá và tạo booking từ chối loại hình đó cho đến khi quản trị viên nhập giá.
- `room_release_at` là cột STORED được tính từ `COALESCE(check_out_actual, check_out_date) + buffer_minutes`; thao tác checkout cập nhật giờ thực tế để database tự cập nhật mốc giải phóng, đồng thời các insert cũ vẫn có giá trị hợp lệ.
- Nhận phòng thực tế sớm hơn giờ đã đặt: mỗi giờ bắt đầu tính `hourly_extra_hour_price`; trả phòng sớm không tự sinh hoàn tiền. Trả muộn được miễn 30 phút đầu; sau đó mỗi giờ bắt đầu tính `hourly_extra_hour_price`, và một lần phụ thu bằng một giờ khi quá 6 giờ. Phụ thu nhận sớm ghi các thành phần `room_rate`, `service_charge`, `vat`; phụ thu quá giờ ghi vào folio bằng reference ổn định để tác vụ nền và checkout chỉ ghi phần chênh lệch mới.
- Web đặt thanh toán tại quầy còn dư nợ ở giờ check-out thì ở lại trạng thái đang lưu trú. Sau 30 phút chưa thanh toán, hệ thống đánh dấu quá hạn và phát một sự kiện bền vững trong trung tâm thông báo nhân viên để lễ tân liên hệ và thu tiền; trả phòng chỉ hoàn tất sau khi nhân viên xác nhận thu.
- HOURLY vượt 6 giờ khi checkout: gói cuối được xác định từ giờ thực tế; quote gói mới được so với các thành phần giá ban đầu, chỉ thu phần dương (không tự hoàn chênh lệch giảm), rồi snapshot `rental_type` và buffer được cập nhật khi checkout commit.
- Nếu phụ thu mới làm folio còn dư nợ sau khoản thu hiện tại, API trả `checkout_required_payment` cùng số dư; checkout vẫn được ghi nhận khi các khoản cơ sở đã tất toán, task được tạo và folio còn mở để thu tiếp. Invoice chỉ hoàn tất khi toàn bộ balance bằng 0.
- Hàng đợi dọn phòng ưu tiên task có `expected_finish_at > due_at`, sau đó task đang thực hiện và các task theo `due_at`; task trễ cũng được đánh dấu `overdue`. Task chỉ chuyển `pending` sang `in_progress` sau khi buffer phòng kết thúc.
