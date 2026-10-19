# Cấu hình đăng nhập Google kèm OTP email

Tính năng chỉ bật khi **đủ toàn bộ** biến môi trường dưới đây. Không đưa mật khẩu ứng dụng hoặc JWT secret vào Git.

```powershell
$env:GOOGLE_CLIENT_ID = '...apps.googleusercontent.com'
$env:SMTP_HOST = 'smtp.gmail.com'
$env:SMTP_PORT = '465'
$env:SMTP_USERNAME = 'dia-chi-gui@gmail.com'
$env:SMTP_APP_PASSWORD = 'mat-khau-ung-dung-16-ky-tu'
$env:SMTP_FROM = 'dia-chi-gui@gmail.com'
$env:JWT_SECRET = 'chuoi-rieng-it-nhat-32-byte'
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '3307'
$env:DB_NAME = 'hotel_prod_db'
$env:DB_USER = 'root'
$env:DB_PASSWORD = ''
```

## Google Identity Services

1. Mở Google Cloud Console, chọn hoặc tạo project của bạn.
2. Vào **APIs & Services → OAuth consent screen**, hoàn tất thông tin ứng dụng và thêm tài khoản Gmail thử nghiệm nếu ứng dụng vẫn ở trạng thái Testing.
3. Vào **Credentials → Create credentials → OAuth client ID**, chọn **Web application**.
4. Thêm `http://localhost:3000` vào **Authorized JavaScript origins**. Khi triển khai, thêm chính xác origin HTTPS chính thức, ví dụ `https://hotel.example.vn`.
5. Sao chép **Client ID** vào `GOOGLE_CLIENT_ID`. Không cần Google Client Secret cho luồng Google Identity Services đang dùng.

## Gmail SMTP

1. Bật xác minh 2 bước cho Gmail gửi thư.
2. Trong Google Account, tạo **App password** cho Mail.
3. Điền email Gmail gửi thư cho `SMTP_USERNAME` và `SMTP_FROM`; điền App password cho `SMTP_APP_PASSWORD`.
4. Dùng `smtp.gmail.com` và cổng SSL `465`.

## Khởi động kiểm thử cục bộ

Sau khi đặt biến trong đúng cửa sổ PowerShell, khởi động backend từ cùng cửa sổ. Mở `http://localhost:3000/portal.html`, nút **Đăng nhập bằng Google** sẽ xuất hiện. Chọn một Gmail đã được thêm vào OAuth consent screen, nhận OTP bốn số, rồi xác nhận.

## Quy tắc bảo mật đã triển khai

- ID token được backend kiểm tra với Google, đúng Client ID, issuer và `email_verified` trước khi gửi OTP.
- OTP gồm bốn chữ số từ `SecureRandom`, chỉ lưu bản băm, hiệu lực năm phút, dùng một lần.
- Mỗi phiên tối đa năm lần nhập sai và hai lần gửi lại; gửi lại làm mã cũ mất hiệu lực.
- Google account chỉ tạo vai trò `customer`. Tài khoản Aurelia có cùng email nhưng chưa liên kết Google sẽ bị từ chối để tránh chiếm quyền theo email.
- Liên kết Google sử dụng `google_subject` duy nhất, không dựa vào email.
