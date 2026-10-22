# Aurelia Hotel - Web UI

Giao diện web quản lý khách sạn Aurelia, viết bằng HTML, CSS và JavaScript thuần (không cần build).

## 🚀 Cách chạy (Phát triển cục bộ)

1. Đảm bảo **Node.js** đã được cài đặt.
2. Trên Windows, chạy `start-web.bat` trong thư mục `web-ui`; script khởi động web server và tự mở trình duyệt sau khi server sẵn sàng.
3. Hoặc mở terminal tại thư mục `web-ui` này và khởi động web server:
   ```bash
   node server.js
   ```
4. Khi chạy thủ công, truy cập `http://localhost:3000` trên trình duyệt.

*Lưu ý: Backend Java phải đang chạy tại `http://localhost:8081`. Trên Windows, `start-backend.bat` ở thư mục gốc khởi động backend demo và tự mở website tích hợp tại `http://localhost:8081/`.*

### Chatbot AI trên trang chủ

Máy dev mới: làm theo [hướng dẫn cấu hình và chạy thử chatbot AI](../docs/chatbot-ai-dev-setup.md) để dùng database, khóa mã hóa và API key riêng.

Nút chat góc dưới bên phải mở Quản gia AI ngay trên trang. Khách đăng nhập bằng hộp đăng nhập hiện có, sau đó gửi câu hỏi tới `POST /api/v1/ai/concierge/chat` bằng JWT của phiên hiện tại. Widget hiển thị nguồn trích dẫn, mã ticket khi backend chuyển lễ tân (`escalate`), và cho phép thử lại/chuyển lễ tân khi dịch vụ lỗi. Nội dung hiển thị giữ trong bộ nhớ trang; tải lại hoặc đổi tài khoản sẽ tạo phiên chat mới.

Admin mở **API key AI**, chọn OpenAI, Gemini, Claude (Anthropic) hoặc Groq, nhập model/API key và thông tin khách sạn, lưu cấu hình rồi chọn **Kiểm tra kết nối đã lưu**. Khi bật nhà cung cấp, chatbot dùng cấu hình này ngay, không cần khởi động lại. Để trống ô key khi lưu cùng nhà cung cấp sẽ giữ key cũ; đổi nhà cung cấp cần nhập key mới. Nút xóa key đồng thời tắt kết nối nhà cung cấp. Chỉ JWT có role `admin` được gọi `GET/PUT/DELETE /api/v1/admin/ai-settings` và `POST /api/v1/admin/ai-settings/test`.

Tên key tự đặt khi tạo chỉ là nhãn quản lý. Giao diện có hai biểu mẫu: **1. Nhập API key và lấy model** chỉ cần key, nhấn Enter hoặc **Tiếp tục: Lấy danh sách model** không kiểm tra Model ID; **2. Chọn model và lưu cấu hình** cho chọn trong danh sách rồi bật nút lưu. Key nháp chỉ được lưu khi hoàn tất bước 2. Có thể dùng key đã lưu cùng nhà cung cấp. Ô Model ID thủ công nằm trong mục nâng cao. `POST /api/v1/admin/ai-settings/models` chỉ cho admin, gọi API danh sách tại host cố định của nhà cung cấp và không lưu key nháp/cấu hình. Gemini chỉ hiển thị model hỗ trợ `generateContent`; các nhà cung cấp khác có thể trả cả model chuyên dụng cho tác vụ khác. Danh sách không bảo đảm model có quyền sinh câu trả lời hay đủ hạn mức: cần lưu và kiểm tra kết nối. Kết quả có giới hạn trang đầu (tối đa 1000 cho Gemini/Claude), giao diện báo nếu còn trang khác.

API key không được trả về trình duyệt hoặc lưu vào localStorage. Máy chủ lưu cấu hình mã hóa AES-GCM tại `.data/ai/provider.enc` (có thể đổi thư mục bằng `-Dhotel.ai.settings.dir=...`). Nếu có `AES_VAULT_KEY`/`AES_VAULT_KEY_FILE`, dùng khóa mã hóa hiện có; nếu chưa có, lần lưu đầu tạo `master.key` riêng với quyền chỉ chủ sở hữu. Giữ nguyên khóa mã hóa khi khởi động lại và sao lưu cùng cấu hình trong kho bí mật; không đưa các file này vào Git. Docker mount volume riêng để giữ cấu hình khi tạo lại container. Không đổi nguồn khóa mã hóa nếu chưa di chuyển lại cấu hình.

Chế độ nhà cung cấp ngoài gửi câu hỏi và thông tin khách sạn do admin nhập. Gemini còn nhận các lượt hội thoại gần nhất của cùng phiên; backend không tự thêm thông tin tài khoản hoặc dữ liệu đặt phòng. AI không có dữ liệu giá/phòng trống trực tiếp. Khi chưa bật nhà cung cấp ngoài, backend giữ chế độ RAG cũ: cần `AI_GUEST_ENABLED=true` và dịch vụ RAG/mTLS theo `ai-service/README.md`. Backend vẫn áp dụng giới hạn yêu cầu, bộ lọc prompt và chuyển lễ tân đối với tình huống cần nhân viên xử lý. Lỗi nhà cung cấp được báo rõ để khách thử lại hoặc chuyển lễ tân, không tạo câu trả lời giả.

Mọi nhà cung cấp cloud tự động nhận ngữ cảnh mặc định ở `src/main/resources/ai/hotel-project-context.txt`, đóng gói vào classpath khi build backend. Nội dung được tổng hợp từ website và các màn hình hiện có: nhận phòng từ 14:00/trả trước 12:00, số lễ tân, địa chỉ công bố, các tiện ích và cách tra cứu/đặt phòng, đặt dịch vụ, quản lý hồ sơ. Phần này xuất hiện dạng chỉ đọc trong tab API key AI; `knowledge` là ghi chú bổ sung, để trống vẫn có ngữ cảnh dự án. Khi website/chính sách thay đổi, cập nhật tài liệu nguồn này và build lại backend; đây không phải đồng bộ PMS hay tự đọc toàn bộ mã nguồn khi chạy. Không lấy giá/số phòng từ SQL mẫu. Nếu các giá trị hiện hành mâu thuẫn nhau, AI cần nhờ lễ tân xác nhận. Không gắn citation cho câu trả lời cloud khi chưa thực hiện truy hồi nguồn theo từng câu hỏi.

Gemini dùng REST `generateContent` trên backend Java với `x-goog-api-key`, `systemInstruction` và `contents` xen kẽ role `user`/`model`, tương ứng cơ chế chat nhiều lượt trong hướng dẫn GenAI. Không cần thêm dịch vụ Node/Python hoặc đưa key vào trình duyệt. Các content parts/signature của phản hồi Gemini được giữ nguyên cho lượt sau; widget chỉ nhận văn bản trả lời, không nhận thought/signature. Cấu hình giới hạn 2048 output token và yêu cầu văn bản ngắn; phản hồi bị chặn hoặc chưa hoàn chỉnh được báo riêng bằng HTTP 422, không lưu vào lịch sử.

Ngữ cảnh Gemini chỉ ở RAM của tiến trình Java, tối đa 1000 phiên, 6 cặp hỏi/đáp và 32.000 ký tự (tính cả phần nội dung trả lại cho Gemini) mỗi phiên. TTL 30 phút không hoạt động, dọn khi mở lượt chat mới; không ghi lịch sử này ra file/DB. Khóa phiên gắn với tài khoản/role được xác thực và `session_id`, không tin lịch sử do trình duyệt gửi lên. Lượt lỗi không được ghi, gửi đồng thời trong cùng phiên trả HTTP 409; thay model/key/thông tin khách sạn hoặc chuyển lễ tân làm mới ngữ cảnh. Khởi động lại máy chủ cũng xóa ngữ cảnh; triển khai nhiều tiến trình cần sticky routing hoặc kho phiên dùng chung trước khi yêu cầu giữ hội thoại xuyên máy chủ.

Groq dùng endpoint cố định `https://api.groq.com/openai/v1/chat/completions` với Bearer API key và `max_completion_tokens`. Ô Model chấp nhận cả mã có tiền tố `nhà-phát-triển/model`; lấy Model ID từ Groq Console. Claude dùng `https://api.anthropic.com/v1/messages`, Bearer API key và phiên bản API `2023-06-01`; có ô Workspace ID tùy chọn cho key dùng nhiều workspace. Key của từng nhà cung cấp không được dùng chéo. Nút kiểm tra chỉ khả dụng khi nhà cung cấp, model và key đã được lưu.

Tài liệu giao thức: [OpenAI Responses](https://developers.openai.com/api/docs/guides/text), [Gemini generateContent](https://ai.google.dev/api/generate-content), [Claude API](https://platform.claude.com/docs/en/api/overview), [Groq API](https://console.groq.com/docs/api-reference). Model được nhập theo quyền trong tài khoản của admin; không tự chọn model hoặc gửi yêu cầu thật khi chưa có key. Kiểm thử tự động Claude/Groq dùng phản hồi giả lập, không xác nhận quyền truy cập hoặc hạn mức tài khoản thật.

Với Gemini, ô Model phải dùng mã model do Google cấp, không phải tên dự án/chatbot/API key. Một model vẫn có thể xuất hiện trong `models.list` nhưng bị từ chối khi sinh nội dung do vòng đời hoặc quyền truy cập. Nút kiểm tra phân biệt HTTP 400 (cấu hình/model), 401/403 (key/quyền), 404 (model không khả dụng), 429 (hạn mức), 503 (dịch vụ quá tải) và hết thời gian chờ 45 giây. Giao diện chờ tối đa 55 giây để nhận lỗi từ máy chủ.

Kết nối dùng HTTPS qua HTTP/1.1 để nhận lỗi Google ổn định. Khi Google trả `ACCESS_TOKEN_TYPE_UNSUPPORTED`, `API_KEY_INVALID` hoặc lỗi giới hạn key, tab admin hiển thị đúng mã đã được lọc an toàn; không đưa nguyên phản hồi Google vào giao diện/nhật ký. Lỗi xác thực của nhà cung cấp được đổi sang HTTP 422 của ứng dụng để không xóa nhầm phiên đăng nhập khách. Tra cứu được danh sách model chưa xác minh key có thể sinh nội dung; luôn dùng nút kiểm tra với yêu cầu tạo nội dung thật.

CSS daisyUI/Tailwind 4 của widget được biên dịch sẵn, cô lập trong Shadow DOM để giữ nguyên giao diện trang chủ. Chạy web không cần build. Khi sửa CSS nguồn thư viện:

```bash
npm ci
npm run build:concierge
```

Đồng bộ `public/css/concierge-ui.css` sang `css/concierge-ui.css` nếu phục vụ bản nguồn ngoài `public/`. Hai file `concierge-chat.js` và `concierge-chat.css` cũng được giữ đồng bộ giữa hai cây thư mục. Chạy `npm test` để kiểm tra, bao gồm luồng chat với API giả lập; kiểm thử không chứng minh mô hình RAG thực tế đã được cấu hình.

### Đăng nhập và cổng theo vai trò

- Trang khách sạn: `http://localhost:3000/`
- Đăng nhập / đăng ký khách hàng: `http://localhost:3000/portal.html`
- Sau khi đăng nhập, quyền trong phản hồi backend sẽ đưa khách hàng, nhân viên hoặc quản trị viên tới đúng cổng và menu tương ứng. Cổng dùng cùng API Java trên cổng 8081.

---

## 🐳 Triển khai Docker (Ubuntu Server)

Từ thư mục gốc dự án (`Quanlykhachsan/`), chạy:

```bash
docker compose up -d --build
```

Sau khi khởi động, người dùng có thể truy cập:

| Dịch vụ | URL | Mô tả |
|---|---|---|
| 🌐 Web UI | `http://<IP-server>:3000` | Giao diện quản lý |
| ⚙️ Backend API | `http://<IP-server>:8081/api` | REST API Java |
| 🗄️ Adminer (DB) | `http://<IP-server>:8082` | Quản trị database |

---

## 📁 Cấu trúc mã nguồn

```
web-ui/
├── server.js              ← Node.js static file server
├── public/
│   ├── index.html         ← Khung HTML chính
│   ├── style.css          ← Toàn bộ giao diện (Dark mode)
│   ├── api.js             ← Client fetch wrapper (tự detect server URL)
│   ├── app.js             ← App shell, router, login/logout
│   └── js/
│       ├── view-admin.js  ← Coordinator Admin
│       ├── view-staff.js  ← Coordinator Staff
│       ├── view-customer.js ← Coordinator Customer
│       ├── view-shared.js ← Hồ sơ cá nhân
│       ├── admin/         ← 6 module tính năng Admin
│       ├── staff/         ← 6 module tính năng Lễ tân
│       └── customer/      ← 5 module tính năng Khách hàng
```
