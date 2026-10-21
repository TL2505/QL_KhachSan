# Cấu hình đăng nhập Google kèm OTP email

Trang `portal.html` dùng **một nút Google chung cho Đăng nhập và Tạo tài khoản**. Người mới xác nhận Google và OTP email để tạo tài khoản khách hàng; người đã dùng Google đăng nhập lại bằng cùng tài khoản. Facebook đã được gỡ.

Nút GIS dùng kích thước `medium` để hiển thị lời mời Google chung, không đưa tên, email hoặc ảnh tài khoản Google đang đăng nhập lên trang. Theo [quy tắc hiển thị của Google](https://developers.google.com/identity/gsi/web/guides/personalized-button), nút `medium` không được cá nhân hoá. Người dùng chọn tài khoản trong luồng Google sau khi bấm nút.

Khung nút dùng màu nền/viền của portal. Nút Google bên trong chọn `outline_dark` ở chế độ tối và `outline` ở chế độ sáng theo [các mẫu được Google hỗ trợ](https://developers.google.com/identity/gsi/web/reference/js-reference#theme). Thay đổi giao diện không khởi tạo lại phiên Google; thiết lập color-scheme riêng cho vùng GIS tránh dải nền trắng quanh iframe.

Tính năng chỉ bật khi **đủ Client ID và cấu hình SMTP**. Nếu thiếu, trang hiển thị nút Google bị vô hiệu hoá và giải thích rõ, không dùng Client ID giả. Đây là OAuth Client ID loại **Web application**, khác với Gemini API key trong Google AI Studio.

## 1. Tạo Google OAuth Client ID lần đầu

1. Mở [Google Cloud Console](https://console.cloud.google.com/), tạo hoặc chọn project cho khách sạn.
2. Mở **Google Auth Platform → Branding** (giao diện cũ: **APIs & Services → OAuth consent screen**). Nhập tên ứng dụng, ví dụ `Aurelia Palace`, email hỗ trợ và email liên hệ của bạn.
3. Trong **Audience**, giữ **External** để dùng tài khoản Gmail cá nhân. Luồng hiện tại chỉ dùng **Sign in with Google** (thông tin định danh cơ bản), nên không bắt buộc thêm email vào **Test users**, kể cả khi project ở chế độ Testing. Nếu sau này yêu cầu thêm quyền Gmail/Drive, quy tắc này thay đổi. Nút **Make internal** bị vô hiệu hoá với tài khoản không thuộc Google Workspace là bình thường.
4. Mở **Clients → Create client → Web application**. Đặt tên tuỳ ý, ví dụ `Aurelia Web Local`.
5. Thêm các **Authorized JavaScript origins** đang dùng:

   ```text
   http://localhost
   http://localhost:8081
   http://localhost:3000
   ```

   Origins chỉ có giao thức, host và cổng; **không thêm `/portal.html`**. Dùng `localhost` nhất quán khi thử. Khi triển khai, thêm origin HTTPS thật của website. Không đưa địa chỉ dev khác vào nếu không dùng.
6. Bấm **Create**, sao chép **Client ID** có đuôi `.apps.googleusercontent.com`. Luồng hiện tại dùng JavaScript callback/popup, không cần điền Authorized redirect URI hoặc dùng Client Secret.
7. Lưu Client ID vào biến môi trường `GOOGLE_CLIENT_ID` của backend như phần dưới. Google có thể cần một khoảng thời gian để áp dụng cấu hình mới.

Tham khảo [hướng dẫn tạo Client ID chính thức](https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid) và [nút Google chính thức](https://developers.google.com/identity/gsi/web/guides/display-button).

Google xác nhận ngoại lệ về danh sách tài khoản thử nghiệm cho Sign in with Google tại [Manage App Audience](https://support.google.com/cloud/answer/15549945?hl=en). Không cần Publish app hoặc chuyển sang Internal chỉ để thử luồng đăng nhập này.

## 2. Đặt biến môi trường trong PowerShell

Không đưa mật khẩu ứng dụng hoặc JWT secret vào Git. Launcher demo `scripts/start-demo.ps1` nạp file `.env` tại thư mục gốc dự án; biến đã có trong môi trường tiến trình được ưu tiên. Nếu đã điền các giá trị Google/SMTP trong `.env`, chạy lại launcher demo để áp dụng. Java chạy trực tiếp và `scripts/start-backend.ps1` vẫn cần các biến được đặt trong terminal trước khi khởi động. Các giá trị dưới đây là chỗ điền, không phải thông tin có thể chạy ngay.

```powershell
$env:GOOGLE_CLIENT_ID = '...apps.googleusercontent.com'
$env:SMTP_HOST = 'smtp.gmail.com'
$env:SMTP_PORT = '465'
$env:SMTP_USERNAME = 'dia-chi-gui@gmail.com'
$env:SMTP_APP_PASSWORD = 'mat-khau-ung-dung-16-ky-tu'
$env:SMTP_FROM = 'dia-chi-gui@gmail.com'
```

Giữ cấu hình DB/JWT đang hoạt động của bạn; chỉ bổ sung Google/SMTP. Nếu dùng launcher demo, để launcher giữ schema và các khoá demo hiện có, không dùng `-ReloadSample` để bật Google.

Launcher `scripts/start-demo.ps1` cũng đọc Client ID từ `.run/demo/google-client-id.txt` nếu chưa đặt `GOOGLE_CLIENT_ID`. File local này chỉ chứa một dòng Client ID và được Git bỏ qua; không sao chép file JSON tải từ Google hoặc Client Secret vào repository. Biến môi trường có ưu tiên cao hơn file. SMTP vẫn cần được cấu hình riêng trước khi nút Google bật.

## 3. Gmail gửi OTP

1. Bật xác minh 2 bước cho Gmail gửi thư.
2. Trong Google Account, tạo **App password** cho Mail.
3. Điền email Gmail gửi thư cho `SMTP_USERNAME` và `SMTP_FROM`; điền App password cho `SMTP_APP_PASSWORD`.
4. Dùng `smtp.gmail.com` và cổng SSL `465`.

Chỉ tài khoản email gửi OTP cần SMTP App password. Người đăng nhập bằng Google không cung cấp mật khẩu Gmail cho khách sạn. Không gửi App password hoặc Client Secret vào chat.

## 4. Khởi động và chạy thử

1. Dừng backend cũ ở terminal đang chạy bằng `Ctrl+C`.
2. Trong terminal đã đặt biến Google/SMTP, chạy lại launcher đang dùng, ví dụ `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-demo.ps1`. Launcher demo giữ schema và khoá đã tạo. Xem [hướng dẫn máy dev](chatbot-ai-dev-setup.md) nếu cần chỉ định đường dẫn Maven/Java.
3. Kiểm tra:

   ```powershell
   Invoke-RestMethod http://localhost:8081/api/v1/auth/google/config | ConvertTo-Json -Depth 4
   ```

   `data.enabled` phải là `true`, `data.client_id` phải khớp Client ID vừa tạo. `false` nghĩa là backend đang thiếu Google/SMTP hoặc chưa được chạy lại từ terminal đúng.
4. Mở `http://localhost:8081/portal.html`, tải lại trang. Chọn **Tiếp tục với Google**, chọn tài khoản Google và hoàn tất màn hình Google.
5. Nhận OTP bốn số qua email, nhập và xác nhận. Chỉ sau khi OTP đúng, ứng dụng mới lưu phiên JWT và mở cổng thành viên.
6. Lần đầu dùng Google, hệ thống tự tạo hồ sơ/quyền **customer**. Thử đăng xuất rồi dùng lại cùng Google để kiểm tra không tạo tài khoản trùng.

Các yêu cầu tạo, xác nhận và gửi lại OTP Google có thời gian chờ 45 giây để backend xác thực Google và gửi SMTP. Trong lúc chờ, không bấm đăng nhập nhiều lần. Những yêu cầu này không tự gửi lại qua host dự phòng khi lỗi mạng. Sau khi backend trả về phiên xác thực, giao diện mở và đặt con trỏ vào ô nhập OTP; tài khoản mới chỉ được tạo sau khi xác nhận mã đúng.

## Lỗi thường gặp

| Hiện tượng | Cách xử lý |
| --- | --- |
| Nút Google chưa được bật | Đặt đủ `GOOGLE_CLIENT_ID`, `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_APP_PASSWORD`, `SMTP_FROM` trong môi trường Java; khởi động lại backend. |
| `origin_mismatch` hoặc `invalid_client` | Kiểm tra Web client, Client ID và origin có đúng cổng `8081`/`3000`; không nhập Gemini API key hoặc thêm đường dẫn vào origin. |
| Không tải được nút Google | Backend phải dùng CSP đã cho phép thư viện, stylesheet, kết nối và iframe Google Identity theo hướng dẫn chính thức. Sau khi build/khởi động lại backend, tải lại trang; nếu vẫn lỗi, kiểm tra mạng/bộ chặn rồi bấm Thử lại Google. |
| Không nhận OTP | Kiểm tra Spam và cấu hình Gmail App password/SSL 465; không dùng mật khẩu Gmail thông thường. |
| Đã nhận email nhưng không thấy ô OTP / `Request timed out after 2.5s` | Cập nhật bản web mới và tải lại trang. Bản cũ hủy yêu cầu trước khi nhận được phiên nhập mã. Chọn Google lại để lấy phiên và mã mới; mã của lần bị timeout không thể khôi phục chỉ từ email. |
| Email OTP mất dấu tiếng Việt | Build và khởi động lại backend mới: tiêu đề và nội dung đều dùng UTF-8; nội dung được truyền bằng MIME Base64 để giữ dấu qua SMTP. Đổi font trên trình duyệt không sửa được email cũ. |
| Email đã thuộc tài khoản Aurelia | Dùng mật khẩu Aurelia để đăng nhập. Không tự động ghép tài khoản theo email; giao diện liên kết tài khoản hiện chưa được triển khai. |
| OTP sai/hết hạn | Dùng Gửi lại mã hoặc quay lại chọn Google; mã mới vô hiệu hoá mã cũ. |

Mã OTP có hiệu lực 5 phút tính từ khi SMTP gửi thành công. Phiên Google đã xác minh được giữ 15 phút từ lần gửi thành công gần nhất để có thể gửi lại mã sau khi mã cũ hết hạn; vẫn giới hạn hai lần gửi lại và tổng cộng năm lần nhập sai. Khi backend khởi động lại, phiên trong bộ nhớ sẽ mất: giao diện trở về nút Google để bắt đầu lại, thay vì giữ một ô OTP không thể dùng. Nút gửi lại hiển thị trạng thái đang gửi và lỗi nếu không gửi được.

Nếu bước tạo tài khoản gặp lỗi database, mã đúng chưa bị dùng mất; có thể thử xác nhận lại trong thời hạn còn lại. Hồ sơ Google mới giữ số điện thoại chưa cung cấp là chuỗi trống để tương thích cột `customers.phone NOT NULL`; không tạo số điện thoại hoặc giấy tờ giả.

## Quy tắc bảo mật đã triển khai

- ID token được backend kiểm tra với Google, đúng Client ID, issuer và `email_verified` trước khi gửi OTP.
- OTP gồm bốn chữ số từ `SecureRandom`, chỉ lưu bản băm, hiệu lực năm phút, dùng một lần.
- Mỗi phiên tối đa năm lần nhập sai và hai lần gửi lại; gửi lại làm mã cũ mất hiệu lực.
- Google account chỉ tạo vai trò `customer`. Tài khoản Aurelia có cùng email nhưng chưa liên kết Google sẽ bị từ chối để tránh chiếm quyền theo email.
- Liên kết Google sử dụng `google_subject` duy nhất, không dựa vào email.
- Frontend không lưu ID token Google vào localStorage; chỉ gửi bằng POST đến backend. Backend kiểm tra Google rồi mới tạo challenge; chỉ OTP hợp lệ mới cấp JWT.
- Hai endpoint minh hoạ cũ `POST /api/v1/auth/google` và `POST /api/v1/auth/facebook` trả `410`, không cấp phiên hoặc giải mã token chưa xác thực để đăng nhập.

Các trạng thái frontend Google/OTP được kiểm thử bằng SDK/API giả lập. Kiểm thử đường dẫn backend xác nhận token giả và challenge không tồn tại bị từ chối. Cần Client ID và SMTP thật để nghiệm thu đăng nhập/đăng ký Google từ đầu đến cuối.

Kiểm thử hồi quy bổ sung xác nhận phản hồi sau hơn 2,5 giây vẫn mở ô OTP, lỗi mạng không tự phát lại yêu cầu OTP qua host khác, và nội dung MIME giải mã ra đúng tiếng Việt. Không gửi email thật trong các kiểm thử này.

Trang web cho phép đúng các nguồn Google Identity trong CSP: `script-src` cho `https://accounts.google.com/gsi/client`, `style-src` cho `https://accounts.google.com/gsi/style`, `connect-src` và `frame-src` cho `https://accounts.google.com/gsi/`. Chính sách API vẫn là `default-src 'self'`. Tham khảo [cấu hình CSP của Google](https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid#content_security_policy).

Kiểm tra local ngày 22/10/2026: `.env` đã được launcher demo nạp, API báo Google enabled, nút GIS thật hiển thị trên trang đăng nhập và Gmail chấp nhận xác thực SMTP qua TLS. Không gửi email thử trong lượt kiểm tra này; chọn tài khoản Google và nhận/xác nhận OTP vẫn cần được người dùng chạy từ đầu đến cuối.
