# Cấu hình database thật và bàn giao database giữa các máy dev

## Nguyên tắc dùng key

| Tình huống | AES_VAULT_KEY / HMAC_IDENTITY_KEY | Cấu hình kết nối |
| --- | --- | --- |
| Các dev cùng kết nối một DB | Dùng cùng hai key gốc của DB đó | Host/schema giống nhau; tài khoản DB có thể khác nhau |
| Dev nhận bản SQL của DB khác | Dùng hai key đi kèm đúng bản SQL đó | Đổi host/port/schema và tài khoản DB theo máy nhận |
| Dev tạo DB mới độc lập | Tạo ngẫu nhiên một lần cho DB mới | Lưu toàn bộ trong `.env` và sao lưu |
| Chạy lại hoặc đổi máy nhưng giữ dữ liệu | Giữ nguyên key | Chỉ điều chỉnh kết nối khi cần |

AES key dùng giải mã; HMAC key dùng tìm kiếm/đối chiếu định danh. Database không lưu hai key này để lấy lại. SQL dump không đủ để chuyển dữ liệu định danh đã mã hóa. Không tạo key mới, chạy backfill lại hoặc ghi đè ciphertext/index để chữa lỗi key trên DB đang có dữ liệu.

`JWT_SECRET` là key ký phiên đăng nhập. Giữ ổn định qua các lần chạy. Các backend cùng phục vụ phiên đăng nhập cần dùng chung JWT secret; các môi trường dev độc lập có thể dùng JWT secret riêng. Đổi JWT secret làm token cũ hết hiệu lực, không giải mã lại dữ liệu định danh.

## 1. Cấu hình từng máy

Cài JDK 17, Maven và MariaDB 11. Đặt `JAVA_HOME` tới JDK 17. Script ưu tiên Java trong `JAVA_HOME`; có thể truyền `-JavaCommand` / `-MavenCommand` khi cần. Khởi động MariaDB trước khi chạy backend.

Sao chép `.env.example` thành `.env` nếu máy chưa có file này. Không ghi đè `.env` hiện hữu. Điền:

```dotenv
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=ten_schema_tren_may_nay
DB_USER=tai_khoan_database
DB_PASSWORD=mat_khau_database
DB_URL=
DB_ALLOW_LOCAL_FALLBACK=false
AES_VAULT_KEY=<key-AES-goc-cua-database>
HMAC_IDENTITY_KEY=<key-HMAC-goc-cua-database>
JWT_SECRET=<secret-ky-phien-on-dinh>
HOTEL_VAT_RATE=0.08
HOTEL_BIND_ADDRESS=127.0.0.1
```

Hai key định danh phải là Base64 của đúng 32 byte. JWT secret phải có ít nhất 32 byte UTF-8. Các dấu `<...>` ở trên là chỗ điền, không phải giá trị chạy được. DB user/password là tài khoản MariaDB, khác tài khoản đăng nhập ứng dụng trong bảng `users`.

Nếu `DB_URL` hoặc `JDBC_URL` có giá trị, URL đó ưu tiên hơn `DB_HOST`/`DB_PORT`/`DB_NAME`; để trống URL khi dùng ba biến riêng. Không để sót URL từ máy gửi. Backend mặc định không tự chuyển từ VM sang localhost.

Launcher đọc `.env` tại gốc dự án, kể cả khi chạy từ thư mục khác. Biến môi trường tiến trình đang có giá trị ưu tiên hơn `.env`. Các script start/check/export khôi phục môi trường của terminal khi kết thúc, nên `-EnvFile` kiểm tra một DB khác không giữ lại key cho lần chạy kế tiếp. Nếu chẩn đoán in sai `[TARGET]`, kiểm tra các biến `DB_*`/`JDBC_URL` đã đặt thủ công trong terminal hoặc mở terminal mới. Không in toàn bộ environment hay `.env` vào log/chat. File hỗ trợ `NAME=VALUE`, dòng chú thích bắt đầu bằng `#`, và dấu nháy bao quanh toàn bộ giá trị; không dùng shell expansion hoặc chú thích cuối dòng.

Có thể dùng các biến `*_FILE` thay cho giá trị trực tiếp. Mỗi secret chỉ có một nguồn. Với cách cấu hình thông thường trên máy dev, lưu giá trị trong `.env`. File này và `.data/`, `.run/` đã được Git bỏ qua; chỉ commit `.env.example` không có secret. Sao lưu `.env` cùng đúng bản DB, qua nơi lưu bí mật được nhóm thống nhất.

## 2. Tạo DB mới độc lập

Chỉ dùng quy trình này khi cần DB hoàn toàn mới, không dùng cho bản SQL nhận từ đồng đội:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\initialize-database.ps1 -DatabaseName hotel_runtime_dev_alice
```

Initializer chỉ kết nối MariaDB local và yêu cầu tên schema bắt đầu `hotel_runtime_`. Mặc định dùng DB admin `root`; nếu có mật khẩu, truyền `-DbAdminUser` và `-DbAdminPasswordFile` trỏ tới file chứa mật khẩu của admin. Tài khoản admin cần quyền tạo database/user và cấp quyền cho schema mới.

Script từ chối schema đã tồn tại; không xóa hay import đè DB cũ. Nó tạo cấu trúc từ phần schema của `hotel.sql`, prerequisites, manifest migration và cập nhật runtime. Không nạp tài khoản/khách/booking từ seed cũ. Nó tạo DB user riêng có quyền trong schema mới (bao gồm DDL cần cho migration khi startup), tài khoản ứng dụng `hotel_admin` và một khách hệ thống mang nhãn `System vault verification (synthetic)` để kiểm chứng dữ liệu mã hóa qua restart. Thêm `-NoActivate` nếu chỉ muốn tạo DB và giữ cấu hình trong thư mục provisioning, chưa thay `.env` đang dùng.

AES/HMAC/JWT và mật khẩu DB mới được tạo một lần, lưu trước vào `.data/provisioning/<schema>/database.env`. Sau khi tạo DB thành công, script cập nhật `.env`, giữ nguyên cấu hình SMTP/Google và sao lưu `.env` trước đó tại `previous.env`. Mật khẩu `hotel_admin` nằm trong `.data/provisioning/<schema>/admin-password.txt`; không có mật khẩu cố định dùng chung và script không in mật khẩu.

Nếu khởi tạo bị gián đoạn, giữ thư mục provisioning và đọc log. Không chạy lại với key mới. `-Resume` dùng lại chính cấu hình đã lưu nhưng vẫn từ chối schema đã tồn tại. Chỉ sau khi xác nhận các bước schema/admin/bản ghi kiểm chứng đã hoàn tất, có thể dùng `-Resume -FinishBootstrap`: helper kiểm tra đúng một admin khớp mật khẩu đã lưu, đúng một khách synthetic, không có booking và vault khớp AES/HMAC trước khi hoàn tất DB user. Các trạng thái khác cần kiểm tra thủ công; không có thao tác reset tự động.

## 3. Khi đồng đội gửi database

**Máy gửi:**

1. Tạo SQL backup nhất quán bằng công cụ MariaDB/MySQL. Bao gồm bảng, trigger, routine, event và cột nhị phân; giữ `identity_hash`, `guest_identity_vault` nguyên vẹn. Ví dụ với client đã có trên PATH:

   ```powershell
   mysqldump --host=127.0.0.1 --port=3306 --user=<db-user> --password --single-transaction --routines --triggers --events --hex-blob --result-file=D:/hotel-transfer/database.sql <schema>
   ```

   Nhập mật khẩu tại prompt của client; không đặt mật khẩu trên dòng lệnh. Không dùng `--databases` nếu muốn import vào tên schema khác. Dùng thư mục bàn giao được bảo vệ, ngoài Git.

2. Xuất file key đi kèm từ đúng `.env` đã dùng để vận hành DB:

   ```powershell
   .\scripts\export-identity-keys.ps1 -Destination D:/hotel-transfer/identity-keys.env
   ```

   Thư mục đích phải tồn tại; script từ chối ghi đè file đã có. File này chỉ chứa hai key định danh, không gửi SMTP/API key, mật khẩu DB hoặc tài khoản admin của máy gửi. Bàn giao file key qua kênh bí mật; ghi nhận nó đi với SQL backup nào.

**Máy nhận:**

1. Tạo một schema local mới trống và DB user có quyền trong schema đó. Kiểm tra SQL trước khi import: không cho phép `USE`, `CREATE DATABASE`, tên schema đầy đủ hoặc `GRANT`/`CREATE USER` trong dump chuyển việc ghi sang DB khác. Các `DEFINER` của trigger/routine có thể cần điều chỉnh theo DB admin tại máy nhận.
2. Import bản dump vào schema mới bằng MariaDB client/GUI. **Không chạy `initialize-database.ps1` lên DB vừa import và không import `hotel.sql` đè lên nó.**
3. Tạo `.env` từ template. Chép **chính xác** AES/HMAC từ `identity-keys.env`; điền DB host/port/schema/user/password của máy nhận và JWT secret cho môi trường này. Tài khoản DB của máy gửi không tự được chuyển qua SQL dump. Tài khoản đăng nhập ứng dụng và BCrypt password hash được chuyển cùng bảng `users`; lấy mật khẩu đăng nhập hợp lệ từ người gửi, không dùng tài khoản demo.
4. Chạy kiểm tra kết nối, vault, khởi động backend rồi kiểm tra schema/đăng nhập theo mục dưới. Nếu schema cũ có định danh plaintext, dừng ghi, backup, xác nhận key và thực hiện maintenance `IdentityVaultMigration --apply` theo quy trình được duyệt; không tự backfill khi startup. Nếu dump chứa vault đã mã hóa mà mất key, giữ nguyên dump; không thể khôi phục định danh bằng key mới.

## 4. Khởi động, dừng và kiểm tra lần sau

```powershell
# Kiểm tra riêng, chỉ đọc, không chạy migration hay thay password
.\scripts\check-backend.ps1 -Mode connection
.\scripts\check-backend.ps1 -Mode vault

# Chạy trực tiếp Main; dùng cấu hình và key đã lưu, không tạo lại DB/key
.\scripts\start-backend.ps1 -Background

# Kiểm tra schema sau migration và health
.\scripts\check-backend.ps1 -Mode schema
Invoke-RestMethod http://127.0.0.1:8081/api/v1/health

# Dừng đúng tiến trình đã ghi nhận của workspace này
.\scripts\stop-backend.ps1
```

Nhấp đúp `start-backend.bat` ở thư mục gốc để chạy sẵn lệnh `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-backend.ps1 -Background`. File dùng đường dẫn tương đối với chính nó nên chạy được từ thư mục làm việc khác; không thay key hoặc tạo database. Khi lỗi, cửa sổ giữ thông báo cùng gợi ý kiểm tra kết nối DB, AES/HMAC/JWT và log. Nhấp đúp `stop-backend.bat` để gọi `scripts/stop-backend.ps1`, xác minh PID và dừng đúng backend của workspace. Nếu cổng `8081` đang bận, kiểm tra backend hiện tại rồi dừng trước khi chạy lại. Các lệnh PowerShell ở trên vẫn dùng được; không còn task phím tắt VS Code hoặc các file batch build desktop/chạy web riêng.

Chạy foreground bằng `scripts/start-backend.ps1` và dừng bằng Ctrl+C. Nếu PowerShell chặn script, dùng `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <script>` như initializer.

Log backend nền nằm tại `.run/backend-real-stdout.log` và `.run/backend-real-stderr.log`, PID tại `.run/backend-real.pid`. Sao lưu log cần giữ trước lần khởi động tiếp theo. Chẩn đoán ghi `[TARGET]`, `[PASS/FAIL CONNECTION]`, `[SCHEMA]`, `[KEY_CONFIG]`, `[VAULT]`, `[ACCOUNT]`; không in key, password, token hoặc định danh. Mode `startup` được launcher/Main gọi trước mọi migration để kiểm tra ciphertext/index hiện có. Startup sau đó chạy migration hiện hữu, kiểm tra lại vault và mới mở HTTP.

Kiểm tra đăng nhập bằng tài khoản được phép. Có thể đặt biến sau trong terminal kiểm tra hoặc `.env` của riêng máy đó:

```powershell
$env:HOTEL_VERIFY_USERNAME = 'ten_tai_khoan_ung_dung'
$env:HOTEL_VERIFY_PASSWORD_FILE = 'C:\private\hotel-login-password.txt'
.\scripts\check-backend.ps1 -Mode all -HttpLogin
```

Kiểm tra account chỉ SELECT và đối chiếu BCrypt; tùy chọn HTTP thực sự gọi login, nhận token rồi truy cập `/api/v1/account/profile`. Login HTTP ghi nhận thiết bị như một lần đăng nhập bình thường. Không ghi token/profile/password vào log. DB mới do initializer tạo đã cấu hình tài khoản kiểm chứng trong `.env` bằng đường dẫn file mật khẩu local; máy khác cần thay đường dẫn/tài khoản phù hợp hoặc bỏ cấu hình kiểm chứng này.

Để nghiệm thu restart: chạy kiểm tra `all -HttpLogin`, dừng backend, mở terminal mới, chạy lại start, chạy lại kiểm tra. `[TARGET]` phải đúng DB, health `UP/CONNECTED`, account và HTTP login đạt, số bản ghi `[VAULT]` như trước. Vault rỗng chỉ xác nhận cấu hình hợp lệ; không chứng minh key khớp dữ liệu lịch sử. Bản ghi synthetic trong DB mới chứng minh việc đọc qua restart cho **DB mới**, không chứng minh khôi phục dữ liệu của DB cũ mất key.

## 5. Xử lý lỗi theo nhóm

| Lỗi | Kiểm tra / xử lý |
| --- | --- |
| Timeout / SQLState `08...` | Bật MariaDB/VM; kiểm tra `[TARGET]`, port 3306, mạng host-only/bridge và firewall. Không tự chuyển sang DB local khác. |
| `1045` | Sai DB user/password hoặc grant theo host máy khách. Đây là xác thực MariaDB, không phải tài khoản ứng dụng. |
| `1049` | Sai schema hoặc chưa import/tạo schema. Không nạp seed lên DB đã có dữ liệu. |
| SQLState `42...`, thiếu table/column | Kiểm tra baseline, `database/bootstrap/migrations.txt`, quyền DDL và log `DATABASE_MIGRATION`. Chẩn đoán không tự chạy SQL sửa lỗi. |
| `KEY_CONFIG`, thiếu/không hợp lệ | Khôi phục key từ `.env`/secret backup đi cùng DB. Chọn một nguồn trực tiếp hoặc `_FILE`. Không dùng key demo. |
| `VLT01` | AES không xác thực được ciphertext/AAD, hoặc payload hỏng. Khôi phục đúng AES key, đối chiếu backup; không mã hóa lại dữ liệu đang lỗi. |
| `VLT02` | AES đọc được nhưng HMAC không khớp, hoặc index bị hỏng. Khôi phục đúng HMAC key, không tự ghi lại hash để che lỗi. |
| `VLT03` | Thiếu vault/index/constraint hoặc còn plaintext. Đánh giá migration riêng, backup và dừng writers trước maintenance. |
| `JWT` | Cấu hình JWT secret ổn định tối thiểu 32 byte. Thay secret yêu cầu đăng nhập lại. |
| HTTP `401` | Sai tài khoản/password, account không có trong DB đang chọn hoặc password hash không được hỗ trợ. Không tự reset password. |
| HTTP `403` | Role ứng dụng không được cấp quyền. |
| HTTP `503`, `LOGIN DEVICE_FAILURE` | Kiểm tra bảng `user_login_devices` và kết nối/quyền ghi DB. |
| HTTP `500`, `LOGIN DB_FAILURE` | Lỗi đọc bảng account/DB hoặc JWT; xem SQLState/code và stage trong log. Lỗi DB không còn bị trả nhầm là sai mật khẩu. |
| Port `8081` bận | Dừng đúng backend trước khi đổi DB. Launcher không coi một backend khác đang khỏe là backend của cấu hình mới. |

Nếu bản dữ liệu lịch sử từng dùng HMAC fallback hoặc key không đúng định dạng, bản backend này sẽ yêu cầu cấu hình rõ ràng thay vì tự đệm/cắt key. Giữ nguyên backup và xác định chính xác byte key lịch sử trong một quy trình phục hồi riêng; không đoán key hoặc sửa index trên DB gốc.
