# Chạy thử chatbot AI trên một máy dev mới

Hướng dẫn dành cho **Windows + PowerShell**, dùng database demo riêng trên máy dev. Chatbot chạy qua backend Java hiện có; không cần cài Python, `@google/genai`, hoặc chạy `ai-service` để thử Gemini/Groq/Claude/OpenAI.

## 1. Chuẩn bị

| Thành phần | Yêu cầu |
| --- | --- |
| Git | Clone được repository này |
| Java | JDK 17 trở lên, có `java` trong `PATH`; backend biên dịch với release 17 |
| Maven | Maven cài riêng, có `mvn.cmd` trong `PATH` |
| Database | MariaDB/MySQL local tại `127.0.0.1:3306`; tài khoản được tạo database demo |
| Node.js | Node.js 22 trở lên và npm; bản kiểm thử gần nhất dùng Node.js 24 |
| Mạng | Backend truy cập được HTTPS tới nhà cung cấp AI đã chọn |
| AI key | Dev tự tạo API key của nhà cung cấp; quyền model và hạn mức được quản lý tại tài khoản đó |

Các cổng local cần dùng: database `3306`, backend `8081`, web `3000`. Không cần cổng `8082` trong luồng cloud của hướng dẫn này.

## 2. Lấy đúng nhánh và cài web dependencies

```powershell
git clone --branch CHATBOT_IMP https://github.com/TL2505/QL_KhachSan.git
Set-Location QL_KhachSan
java -version
Get-Command mvn.cmd -All
node --version
npm --prefix web-ui ci
```

Nếu đã clone, commit hoặc cất các thay đổi riêng của bạn trước khi chuyển nhánh:

```powershell
git fetch origin
git switch --track origin/CHATBOT_IMP
```

Nếu nhánh local đã tồn tại thì dùng `git switch CHATBOT_IMP`.

File `mvn.cmd` ở gốc repository là shortcut NetBeans cũ, có thể trỏ tới đường dẫn không tồn tại. Không dùng `./mvn.cmd` để build; launcher bên dưới tìm Maven đã cài riêng.

## 3. Chạy database và backend — terminal thứ nhất

Bật MariaDB/MySQL local, sau đó chạy từ gốc repository:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-demo.ps1
```

Mặc định dùng tài khoản DB local `root` với mật khẩu rỗng. Nếu DB có mật khẩu, lấy credential vào biến thay vì ghi mật khẩu thật vào lệnh hoặc file hướng dẫn:

```powershell
$devDbCredential = Get-Credential -UserName 'root' -Message 'Tai khoan MariaDB local'
.\scripts\start-demo.ps1 -DbUser $devDbCredential.UserName -DbPassword ($devDbCredential.GetNetworkCredential().Password)
```

Nếu Java/Maven không có trong `PATH`, truyền đường dẫn cài trên máy dev, ví dụ:

```powershell
.\scripts\start-demo.ps1 `
  -MavenCommand 'C:\Tools\apache-maven\bin\mvn.cmd' `
  -JavaCommand 'C:\Program Files\Java\jdk-17\bin\java.exe'
```

Launcher sẽ:

- Tạo schema mới `hotel_frontend_demo_<random>` và nạp dữ liệu mẫu cùng migrations; không import đè database ứng dụng có sẵn.
- Tạo key AES, HMAC và JWT riêng cho máy này trong `.run/demo/`.
- Lưu tên schema để lần chạy sau dùng lại, không nạp trùng dữ liệu.
- Bật OTP mock cho demo local và khởi động backend tại `8081`.

Giữ terminal này mở. Không cần import thủ công `database/hotel.sql` khi dùng launcher demo.

Mở terminal khác và kiểm tra:

```powershell
Invoke-RestMethod http://127.0.0.1:8081/api/v1/health
```

Kết quả cần có `status: UP` và `database: CONNECTED`. Health chỉ xác nhận backend/database, chưa xác nhận AI key.

## 4. Chạy website — terminal thứ hai

Tại gốc repository:

```powershell
$env:HOST = '127.0.0.1'
$env:PORT = '3000'
$env:HOTEL_API_ORIGIN = 'http://127.0.0.1:8081'
npm --prefix web-ui start
```

- Trang chủ: <http://127.0.0.1:3000/index.html>
- Admin/cổng thành viên: <http://127.0.0.1:3000/portal.html>

Dùng nhất quán `127.0.0.1:3000` để đăng nhập và mở chatbot. `localhost`, `127.0.0.1`, cổng `3000` và cổng `8081` có vùng lưu phiên trình duyệt khác nhau; đăng nhập ở một origin không đồng nghĩa origin khác đã đăng nhập.

CSS chatbot đã được build và đưa vào Git, nên chỉ chạy thử không cần build CSS. Nếu sửa `web-ui/concierge-ui.input.css`, chạy `npm --prefix web-ui run build:concierge`, sau đó đồng bộ file sinh ra:

```powershell
Copy-Item web-ui/public/css/concierge-ui.css web-ui/css/concierge-ui.css
```

## 5. Cấu hình API key và chọn model

Đăng nhập admin bằng tài khoản **chỉ dành cho demo local**:

- Username: `demo_admin`
- Password: `DemoHotel2026!`

Mở menu **API key AI**, thực hiện theo thứ tự:

1. Chọn nhà cung cấp, ví dụ **Google Gemini**.
2. Dán API key của chính dev vào ô **API key**.
3. Bấm **Tiếp tục: Lấy danh sách model**. Bước này **không yêu cầu Model ID** và chưa lưu key.
4. Chọn một model hỗ trợ chat văn bản từ danh sách trả về. Tên tự đặt khi tạo key, như `ChatbotHotel`, chỉ là nhãn key; không phải Model ID.
5. Xem **Ngữ cảnh mặc định của dự án**. Backend đã nạp chính sách nhận/trả phòng, liên hệ, tiện ích và hướng dẫn website. Ô thông tin bổ sung có thể để trống hoặc nhập thông tin khách sạn đã xác nhận.
6. Đánh dấu **Bật nhà cung cấp này cho chatbot** và bấm **Lưu API key và model đã chọn**.
7. Bấm **Kiểm tra kết nối đã lưu**. Chỉ khi có thông báo thành công mới xác nhận được key/model có thể tạo nội dung thật.

Lưu ý theo nhà cung cấp:

| Nhà cung cấp | Cách cấu hình trong ứng dụng |
| --- | --- |
| Gemini | Lấy key từ Google AI Studio. Chọn model hỗ trợ `generateContent`; không mặc định sao chép mã model cũ từ ví dụ trên mạng. |
| Groq | Dùng Groq API key từ Groq Console. Model ID có thể dạng `nhà-phát-triển/model`; không dùng key Gemini cho Groq. |
| Claude | Dùng Claude API key từ Claude Console. Workspace ID có thể để trống với key dành cho một workspace; key dùng nhiều workspace cần chọn đúng Workspace ID. |
| OpenAI | Dùng API key và model được cấp quyền cho tài khoản API của dev. |

Danh sách model có thể gồm model chuyên dụng cho tác vụ khác. Lấy được danh sách chưa bảo đảm có quyền sinh nội dung hoặc còn hạn mức. Không có model/key nào được cam kết miễn phí trong hướng dẫn này.

Không cần đặt `GEMINI_API_KEY` trong frontend hoặc sửa mã JavaScript. Luồng hiện tại đọc key từ cấu hình admin mã hóa trên backend. Backend Java cũng không tự đọc `.env` khi chạy bằng launcher.

Launcher đặt `AI_GUEST_ENABLED=false` để không tự dùng RAG nội bộ chưa cấu hình. Khi **nhà cung cấp cloud đã được bật trong admin**, chatbot vẫn dùng nhà cung cấp đó; không cần đổi flag hoặc bật Python/RAG.

## 6. Chạy thử chatbot

Sau khi lưu, mở lại trang chủ trên cùng origin và bấm nút chat góc dưới bên phải. Có thể dùng `demo_admin` đang đăng nhập; để thử đúng vai khách, đăng xuất rồi đăng nhập `demo_guest` / `DemoHotel2026!`.

Các câu hỏi kiểm tra:

| Câu hỏi | Kỳ vọng theo ngữ cảnh dự án |
| --- | --- |
| Giờ nhận phòng và trả phòng là khi nào? | Nhận từ **14:00**, trả trước **12:00 trưa** theo giờ Việt Nam |
| Còn hồ bơi mở cửa từ mấy giờ đến mấy giờ? | Hồ bơi tầng 32, **06:00–22:00** |
| Tôi xem lịch sử đặt phòng ở đâu? | Hướng dẫn tới cổng thành viên, mục Đặt phòng/Lịch sử đặt phòng |
| Tôi muốn đặt dịch vụ thì làm thế nào? | Hướng dẫn tới Dịch vụ & Ví ưu đãi; không tự nhận đã đặt thành công |

Nội dung AI có thể khác cách diễn đạt trong bảng. Giá, số phòng còn trống và đơn cá nhân phải kiểm tra bằng chức năng nghiệp vụ; chatbot chưa có quyền tự tra cứu hoặc tạo đơn qua câu trả lời. Một số câu hỏi về giá, hoàn tiền hoặc tình huống cần nhân viên sẽ được lớp chính sách chuyển tới lễ tân.

Gemini giữ tối đa 6 cặp hỏi/đáp, 32.000 ký tự mỗi phiên và TTL 30 phút không hoạt động, trong RAM của backend. Phiên gắn với tài khoản và session chat; tải lại trang tạo phiên mới, khởi động lại backend xóa bộ nhớ hội thoại. Các provider khác hiện nhận câu hỏi hiện tại và ngữ cảnh khách sạn, chưa có lịch sử nhiều lượt như Gemini.

## 7. Chạy kiểm thử tự động

Trong terminal tại gốc repository, chọn đúng Maven cài riêng. Thay đường dẫn dưới đây theo máy dev:

```powershell
$devMaven = 'C:\Tools\apache-maven\bin\mvn.cmd'
& $devMaven -q '-Dtest=HotelProjectContextTest,GeminiConversationTest,AiProviderSettingsTest,AdditionalAiProvidersTest,AiModelDiscoveryTest,VirtualConciergeControllerTest,CloudConciergeIntegrationTest' test
npm --prefix web-ui test
```

Các kiểm thử này dùng credential tổng hợp và phản hồi AI giả lập, không cần key thật, không gọi nhà cung cấp và không xác nhận hạn mức/quyền tài khoản thật. Kiểm thử HTTP cần máy dev cho phép kết nối localhost. Không cần bật database integration test cho bộ kiểm thử chatbot này.

## 8. Giữ cấu hình riêng của từng máy

- `.run/demo/`: key mã hóa/JWT và tên database demo của máy này. Giữ nguyên để tiếp tục dùng dữ liệu demo sau khi khởi động lại.
- `.data/ai/provider.enc`: cấu hình AI mã hóa. Với demo, file này gắn với key AES trong `.run/demo/`; sao chép riêng ciphertext sang máy khác sẽ không giải mã được.
- `.run/`, `.data/`, `.env`, `node_modules/` đã bị Git bỏ qua. Không đưa key thật, file vault hoặc log chứa dữ liệu riêng vào commit.
- Máy dev khác clone mã nguồn rồi nhập API key riêng qua admin. Không cần sao chép `.data/` hay `.run/` từ máy tác giả.
- Key trống khi lưu cùng nhà cung cấp giữ key cũ; đổi nhà cung cấp cần key mới. **Xóa key** cũng tắt nhà cung cấp cloud, nên sau đó chatbot có thể chuyển lễ tân thay vì trả lời AI.

Ngữ cảnh mặc định nằm ở `src/main/resources/ai/hotel-project-context.txt`. Khi đổi nội dung/chính sách website, cập nhật file này và build/khởi động lại backend. Phần ghi chú admin được bổ sung độc lập và có hiệu lực ngay sau khi lưu.

## 9. Lỗi thường gặp

| Hiện tượng | Cách kiểm tra |
| --- | --- |
| Maven báo đường dẫn NetBeans không tồn tại | Dùng Maven cài riêng; truyền `-MavenCommand` cho launcher, không gọi `./mvn.cmd`. |
| `java` không được tìm thấy | Cài JDK, cấu hình `JAVA_HOME`/`PATH`, hoặc truyền `-JavaCommand`. Maven cũng cần JDK hợp lệ. |
| DB không kết nối / bị từ chối đăng nhập | Bật MariaDB local tại `3306`, kiểm tra `-DbUser`/`-DbPassword` và quyền tạo schema demo. |
| Cổng `8081` hoặc `3000` đã được dùng | Dừng đúng terminal/dịch vụ cũ trước khi chạy lại; không chạy hai backend trên cùng cổng. |
| Trang chủ vẫn yêu cầu đăng nhập | Đăng nhập cùng origin, ví dụ luôn dùng `127.0.0.1:3000`; tải lại trang sau khi đổi tài khoản. |
| Không thấy tab API key AI | Đăng nhập `demo_admin`; tài khoản khách/nhân viên không có quyền quản lý key. |
| Nhập key mà bị yêu cầu model trước khi lấy danh sách | Tải lại trang admin bằng `Ctrl+F5` để nhận giao diện hai bước mới; dùng nút ở bước 1. |
| HTTP 400 hoặc model không hợp lệ | Dùng Model ID từ danh sách, không nhập tên key/dự án; chọn model hỗ trợ chat văn bản. |
| Nhà cung cấp trả 401/403 | Key/model chưa được cấp quyền hoặc key không còn hiệu lực; cập nhật qua admin. Lỗi nhà cung cấp được ứng dụng chuyển thành HTTP 422 để không xóa phiên khách. |
| Gemini báo `ACCESS_TOKEN_TYPE_UNSUPPORTED`, `API_KEY_INVALID` hoặc key bị giới hạn | Kiểm tra/tạo lại Gemini API key và cấu hình dự án/quyền truy cập trong Google AI Studio; đổi model không sửa được lỗi loại credential. |
| HTTP 404 từ nhà cung cấp | Model không tồn tại hoặc không được cấp quyền; lấy lại danh sách và thử kết nối. |
| HTTP 429 từ nhà cung cấp | Kiểm tra hạn mức/quota/billing của tài khoản API. HTTP 429 từ ứng dụng cũng có thể là giới hạn câu hỏi theo tài khoản; chờ theo `Retry-After`. |
| Nhà cung cấp báo HTTP 503/529 | Dịch vụ tạm không khả dụng/quá tải; thử lại sau hoặc chọn model khác được cấp quyền. Không phải bằng chứng key đã sai. |
| `AI_RESPONSE_BLOCKED` / `AI_RESPONSE_INCOMPLETE` | Gemini chặn nội dung hoặc chưa tạo câu trả lời đầy đủ; diễn đạt lại/hỏi ngắn hơn. Không lưu lượt lỗi vào lịch sử. |
| `AI_CONVERSATION_BUSY` / HTTP 409 | Cùng phiên đang xử lý câu hỏi khác; đợi câu trả lời trước khi thử tiếp. |
| Hết thời gian chờ | Backend chờ nhà cung cấp tối đa 45 giây, giao diện chờ 55 giây, proxy web dành 60 giây cho tuyến AI. Kiểm tra mạng/model; không kết luận đã trả lời thành công. |
| Chatbot chưa biết giờ nhận/trả phòng | Xem phần Ngữ cảnh mặc định trong admin; kiểm tra backend được build từ nhánh này. Restart backend sau khi cập nhật file context và tải lại trang để mở phiên mới. |
| Không giải mã được cấu hình đã lưu | Giữ đúng key AES của máy này; không trộn `.data/` với key mới. Nếu chỉ thử máy dev mới, cấu hình lại bằng key API riêng trên bản clone sạch. |

Để dừng, nhấn `Ctrl+C` ở hai terminal backend và web. Chạy lại cùng lệnh để tiếp tục dùng schema/key demo hiện có. `-ReloadSample` tạo một schema demo mới; không cần dùng cho việc khởi động lại thông thường.
