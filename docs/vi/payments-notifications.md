# Thanh toán và thông báo

## Kiến trúc dùng chung

Tiền mặt, POS, webhook ngân hàng cũ và payOS ghi vào `payment_reconciliations`; sổ tiền gốc vẫn là `folio_transactions`. Mỗi giao dịch lưu kênh, phương thức, chiều tiền, mã bên ngoài, số tiền nhận, số tiền áp dụng, tiền dư, kết quả và thời điểm. Khóa duy nhất là `(provider, transaction_id)`. Bảng `payment_webhook_deliveries` lưu từng lần nhận và cả trường hợp trùng hoặc xung đột. Migration `v4_2_payments_notifications.sql` bổ sung bảng/cột và nhập các biên nhận cũ; không ghi lại tiền vào sổ, không thay khóa định danh.

`PaymentGateway` là giao diện tạo link QR; `PaymentLinkService` quản lý tham chiếu, hạn thanh toán và cache trong DB. `PayOsGateway`/`PayOsWebhookController` là adapter đầu tiên. Thêm cổng mới bằng adapter kiểm chứng dữ liệu riêng, sau đó chuyển sự kiện đã xác thực thành `PaymentReconciliationService.VerifiedPayment`. Không gửi `VerifiedPayment` từ trình duyệt và không thêm một sổ tiền riêng cho mỗi cổng. Đăng ký adapter tại factory trong `PaymentLinkService` và route webhook riêng. Mỗi nhà cung cấp phải có kiểm thử chữ ký, phát lại, sai tài khoản, số tiền và thời điểm theo tài liệu của họ.

`provider`, `payment_method`, `direction` là chuỗi để mở rộng. `booking_refunds` lưu riêng yêu cầu hoàn tiền và trạng thái xử lý; biên nhận thu tiền trong `payment_reconciliations` và sổ `folio_transactions` không bị xóa/sửa. Mock gateway đánh dấu hoàn tiền thành công để kiểm thử. Với PayOS, ứng dụng chỉ đóng được link QR chưa thanh toán; việc hoàn tiền giao dịch đã nhận cần kênh chi/hoàn tiền riêng hoặc xử lý qua bộ phận hỗ trợ. Không hiển thị trạng thái hoàn tiền thành công khi chưa có xác nhận từ kênh chi.

## Chuẩn bị payOS

1. Tạo tài khoản tại [my.payos.vn](https://my.payos.vn), xác minh cá nhân/doanh nghiệp, liên kết tài khoản ngân hàng và tạo kênh thanh toán theo [hướng dẫn API chính thức](https://payos.vn/docs/api/).
2. Lấy **Client ID, API Key, Checksum Key** trong kênh đó. Điền các biến sau vào `.env` trên máy chạy backend, không gửi key vào chat hoặc commit:

```dotenv
PAYMENT_QR_PROVIDER=payos
PAYOS_CLIENT_ID=
PAYOS_API_KEY=
PAYOS_CHECKSUM_KEY=
PAYOS_BANK_BIN=
PAYOS_ACCOUNT_NUMBER=
PAYOS_ACCOUNT_NAME=
PAYOS_RETURN_URL=https://your-domain.example/portal.html?view=bookings
PAYOS_CANCEL_URL=https://your-domain.example/portal.html?view=bookings
NOTIFICATION_EMAIL_ENABLED=false
```

3. Tên thụ hưởng, BIN và số tài khoản phải khớp dữ liệu trả về có chữ ký của kênh. Sao chép tên chính xác từ nhà cung cấp; không dùng tài khoản mẫu. Checksum Key của payOS độc lập với `AES_VAULT_KEY` và `HMAC_IDENTITY_KEY`; giữ nguyên hai khóa định danh đã có.
4. Đưa backend sau reverse proxy HTTPS công khai, chuyển tiếp `/api/v1/webhooks/payos` đến localhost:8081. Đăng ký URL đó trong kênh payOS. Return/cancel URL chỉ phục vụ điều hướng, không xác nhận tiền. Không mở công khai cổng DB.
5. Khởi động bằng `start-backend.bat`. Trong mục **Giao dịch & thông báo**, tra cứu lịch sử; API `/payment-operations/configuration` cho nhân viên biết cấu hình có đủ trường hay chưa. Đủ trường chưa có nghĩa credential/ngân hàng đã được xác minh.
6. Khi đã có kênh, dùng tài khoản thử được chỉ định và một booking thử: quét QR, kiểm tra đủ tiền tự chuyển booking thành confirmed, rồi thử gửi lại webhook và thanh toán thiếu. Không tự POST trạng thái PAID từ client.

Khi chạy backend bằng `docker-compose.yml`, Compose chuyển tiếp các biến `PAYOS_*` và `PAYMENT_QR_PROVIDER` từ `.env` vào container; giá trị PayOS trống vẫn cho phép các chức năng khác chạy nhưng QR chưa sẵn sàng. `docker-compose.prod.yml` nạp chúng từ tệp `PMS_BACKEND_ENV_FILE`. Trong cả hai trường hợp webhook vẫn cần URL HTTPS công khai trỏ qua reverse proxy.

Hiện chủ dự án chưa có thông tin kênh/ngân hàng nên các giá trị được để trống. Backend và đăng nhập vẫn chạy; yêu cầu QR trả lỗi chưa sẵn sàng. Chưa có bằng chứng nhận tiền thật.

## Đối soát và trạng thái

Webhook payOS kiểm tra HMAC-SHA256 bằng [quy tắc chữ ký chính thức](https://payos.vn/docs/tich-hop-webhook/kiem-tra-du-lieu-voi-signature/) trên dữ liệu đã sắp xếp. Chỉ sau đó mới tra đơn đã lưu và kiểm tra mã link, nội dung chính xác, tài khoản thụ hưởng, VND, mã thành công và giờ giao dịch. Không tin cờ success ngoài dữ liệu được ký.

| Trạng thái chung | Ý nghĩa |
| --- | --- |
| WAITING | Chưa có tiền được ghi nhận; mã QR hoặc ảnh chuyển khoản chưa phải xác nhận |
| PARTIAL | Có tiền được áp dụng nhưng vẫn còn dư nợ |
| PAID | Dư nợ bằng 0 và có biên nhận thanh toán trong sổ |
| RECONCILIATION_ERROR | Có giao dịch cần xử lý: sai nội dung/tài khoản/link, chưa xác nhận, quá hạn hoặc booking đã hủy/kết thúc |

Phần còn thiếu được trả bằng `remaining_amount`. Khi tất cả folio đã đủ tiền, booking pending tự được xác nhận trong cùng transaction. Thanh toán dư: chỉ áp dụng đủ dư nợ, phần dư ghi `credit_adjustment` riêng để xử lý công nợ/hoàn tiền. Không tính tiền dư là doanh thu phòng.

Webhook trùng trả lại kết quả đã lưu và không thu tiền/thông báo lại, kể cả sau trả phòng. Cùng mã giao dịch nhưng khác dữ liệu được lưu lần nhận `CONFLICT`. Giao dịch đến sau khi booking bị hủy/no-show/trả phòng không mở lại booking. QR web có hạn khách thanh toán 60 phút; hệ thống giữ thêm tối đa 2 phút nội bộ để nhận webhook đã phát sinh đúng hạn, rồi tự hủy booking chưa trả và giải phóng phòng. Khách có thể mở lại đúng link còn hạn từ lịch sử. Khi khách tự hủy trước check-in, link QR đang mở phải được nhà cung cấp đóng trước khi booking bị hủy. Giao dịch ngoài hạn cần đối soát; webhook đến chậm nhưng giờ chuyển nằm trong hạn có thể được áp dụng nếu booking còn hợp lệ. Giao dịch chưa tìm thấy đơn lưu audit với folio trống để nhân viên tra cứu.

Lỗi nghiệp vụ đã lưu trả HTTP 200 để dừng gửi lặp; 200 không đồng nghĩa PAID. Chữ ký sai trả 401, payload sai trả 400/413, chưa cấu hình hoặc DB tạm lỗi trả 503 để nhà cung cấp có thể gửi lại. Khi mất callback, kiểm tra cấu hình webhook và yêu cầu nhà cung cấp gửi lại sự kiện gốc. Hiện không có job tự xác nhận dựa riêng trên tổng tiền từ API tra cứu.

Link có mã order lưu trước khi gọi cổng, cùng folio/mức tiền chỉ tái dùng link còn hạn. Nếu tạo link thành công ở cổng nhưng mất phản hồi, tiếp tục dùng cùng order khi thử lại. Nếu API tra cứu không trả đủ dữ liệu QR/thụ hưởng có chữ ký, hệ thống dừng an toàn, không tự tạo QR thay thế hoặc ghi tiền; kiểm tra đơn trên payOS trước khi tạo lại sau hết hạn. Cần kiểm chứng thêm ca này với kênh thực.

## Thông báo và email

Các sự kiện booking_created, booking_confirmed, payment_paid/payment_partial/payment_error, checkin_welcome, checkout_gratitude, booking_cancelled tạo thông báo trong ứng dụng và một email trong `notification_outbox`, cùng transaction nghiệp vụ. Nhắc nhận phòng 24 giờ trước giờ dự kiến; nhắc trả phòng khi còn 10% thời lượng lưu trú. Worker quét mỗi 30 giây và giữ khóa sự kiện theo booking + lịch hẹn để không gửi lặp. Chỉ booking `Web` được tự động nhận/trả phòng: check-in đúng giờ nhận phòng nếu phòng sẵn sàng; check-out đúng giờ kết thúc, chuyển phòng thành bẩn và tạo cleaning task. Booking tại quầy vẫn do nhân viên thao tác. Review sau khi checked_out hiện đã cộng 200 điểm trong transaction tạo đánh giá.

Email dùng `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_APP_PASSWORD`, `SMTP_FROM` hiện có. Hỗ trợ TLS trực tiếp cổng 465 hoặc STARTTLS 587, kiểm tra hostname/chứng chỉ. Dùng địa chỉ email của hồ sơ khách. Thiếu email được ghi SKIPPED/MISSING_EMAIL. Chỉ bật `NOTIFICATION_EMAIL_ENABLED=true` sau khi kiểm tra SMTP và địa chỉ khách; mặc định false vẫn tạo thông báo trong ứng dụng và giữ hàng đợi.

Lỗi SMTP xác định có thể thử lại: tối đa 5 lần, chờ 30/60/120/240 giây giữa các lần. Lỗi 5xx dừng FAILED. SENT nghĩa SMTP đã nhận thư, chưa xác nhận khách đã đọc hoặc hộp thư không lọc spam. Message-ID ổn định cho cùng email. Không thể bảo đảm exactly-once với SMTP: mất kết nối sau DATA hoặc worker chết lúc đang gửi chuyển UNKNOWN, không gửi lại mù; nhân viên kiểm tra log nhà cung cấp trước khi xử lý. Mục lịch sử hiển thị từng lần gửi và mã lỗi không chứa mật khẩu.

## Máy dev khác và database được chia sẻ

Database được gửi phải đi kèm AES/HMAC gốc qua kênh secret riêng. Không đổi khóa để sửa lỗi giải mã. Mỗi máy tự đặt DB_HOST/DB_NAME/DB_USER/DB_PASSWORD trong `.env`. Dev dùng database độc lập có thể có key riêng, sinh một lần bằng initializer dành cho DB mới.

Giữ `NOTIFICATION_EMAIL_ENABLED=false` khi nhận bản dump; bản dump có thể mang email khách và hàng đợi chưa gửi. Không bật email trên bản sao dữ liệu thật để test. Dùng schema dữ liệu giả lập và hộp thư thử. Không trỏ nhiều bản dev độc lập vào cùng kênh payOS thật: orderCode giữa các DB có thể trùng; dùng kênh riêng và cấu hình thụ hưởng tương ứng.

## Kiểm tra và xử lý lỗi

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-backend.ps1 -Mode all -HttpLogin
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-payments.ps1
cd web-ui
node --test tests/payment-state.test.mjs
```

Script test-payment dùng tài khoản root local (`DB_ROOT_PASSWORD` hoặc `DB_ROOT_PASSWORD_FILE` nếu có), tạo schema `hotel_payments_<random>_test`, chạy kiểm thử với sender/gateway giả và xóa chỉ schema có đúng marker sở hữu. Không sửa `.env`, không gọi payOS và không gửi email. Cần MariaDB 11, JDK 17 và Maven; có thể truyền `-MavenCommand` đường dẫn Maven thực. Báo cáo nằm `.run/payment-build/surefire-reports`.

- Lỗi kết nối DB: kiểm tra MariaDB đang chạy, host/port, DB_URL ghi đè, schema và quyền. Xem `.run/backend-real-stderr.log`.
- Lỗi vault: dùng bộ AES/HMAC đi kèm chính database đó; không sinh key mới cho dữ liệu đã mã hóa.
- QR chưa sẵn sàng: kiểm tra biến payOS, callback HTTPS, thông tin thụ hưởng, quyền/kết nối API; xem cấu hình kênh trên payOS. Không đổi key vault.
- PARTIAL: đối chiếu số còn thiếu; tiếp tục thanh toán đúng tham chiếu hoặc lấy QR mới cho dư nợ.
- RECONCILIATION_ERROR: tra mã giao dịch và reason, đối chiếu ngân hàng; không xác nhận booking bằng cách sửa trạng thái trực tiếp.
- UNKNOWN email: tra trạng thái nhà cung cấp trước khi gửi lại. RETRY/FAILED: kiểm tra host/TLS/SMTP credential và email; không xóa bảng outbox để retry.

Trước migration trên môi trường triển khai, sao lưu database cùng bộ key tương ứng. Migration có thể chạy lại, nhưng DDL MariaDB không rollback như transaction thu tiền. Dừng backend cũ, áp dụng bằng quy trình start, kiểm tra health/login/vault và lịch sử trước khi mở lưu lượng.
