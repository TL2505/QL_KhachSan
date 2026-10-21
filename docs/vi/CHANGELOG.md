# NHẬT KÝ THAY ĐỔI (CHANGELOG) — Tài liệu hệ thống PMS Khách sạn

## [2026-10-22] — @codex — Backend/web-ui — Quản lý thành viên, OTP tài khoản và đồng bộ màu portal

- Thay placeholder Thành viên của admin bằng thống kê, tìm kiếm, lọc hạng, sắp xếp, danh sách phân trang, lịch sử điểm và điều chỉnh điểm có ghi nhận người thực hiện. Backend kiểm tra quyền admin, số dư và cập nhật điểm/lịch sử trong cùng giao dịch; trừ điểm không làm giảm tổng tích lũy dùng xét hạng.
- Dùng chung pagination cho danh sách và lịch sử; bỏ tiêu đề lặp, nút Đóng thừa và khung modal không phù hợp; sửa phông tiêu đề và tự hiện dấu trừ khi chọn trừ điểm. Chuẩn hóa tiêu đề các trang admin và nối trang Đặt phòng admin với bộ hiển thị đặt phòng hiện có.
- Làm lại cấu hình AI bằng daisyUI được build cục bộ, chia cột theo chiều rộng nội dung và trình bày rõ các bước nhà cung cấp/model. Bỏ dòng thương hiệu thừa, giữ nguyên luồng cấu hình đang có.
- Mở rộng luồng tài khoản JWT dùng chung của portal Phase 4 với `GET/POST /api/v1/account/profile` và `POST /api/v1/account/email/request`, `/email/verify`, `/password/request`, `/password/confirm`. Backend xác định chủ hồ sơ từ JWT đã xác minh; cập nhật user/customer liên kết trong một giao dịch. Migration thêm cột nullable `users.verified_email` để trạng thái xác minh gắn với email đã lưu; các route đổi mật khẩu cũ cũng bắt buộc OTP.
- Thêm OTP email 6 số cho xác minh email và đổi mật khẩu: hiệu lực 5 phút sau khi gửi thành công, gửi lại sau 60 giây, tối đa 10 lần gửi mỗi giờ theo tài khoản/mục đích; sai 5 lần khóa 10 phút. Đổi mật khẩu yêu cầu email đã xác minh, mật khẩu hiện tại và OTP đúng mục đích; thành công thu hồi phiên đăng nhập hiện tại. Mã được băm trong bộ nhớ tiến trình; yêu cầu OTP không tự phát lại sang backend khác khi lỗi mạng.
- Dùng chung thành phần tài khoản daisyUI cho admin/staff/customer, đồng thời tách nội dung customer: Hồ sơ dành cho thông tin cá nhân/lưu trú; Bảo mật dành cho xác minh email đã lưu và đổi mật khẩu. Sửa màu nền cũ ghi đè màu hạng; đồng bộ Tổng quan, thẻ, ô nhập, điều hướng và nút theo Member/Silver/Gold/VIP ở cả sáng/tối. Ưu tiên tổng điểm tích lũy đã lưu thay vì lịch sử thiếu dữ liệu; kết quả tải cũ không được ghi đè tab hoặc màu hạng hiện tại.
- Xác minh: **119/119 kiểm thử web đạt**, **12 kiểm thử backend hồ sơ/OTP đạt**, gồm giao dịch MySQL trên schema cô lập. Kiểm thử quyền/điều chỉnh điểm, database thành viên và các test OTP Google hiện có cũng đạt. Kiểm tra trình duyệt bằng dữ liệu giả bao phủ 24 tổ hợp hạng/theme/trang và 6 bố cục Gold thu gọn, không tràn ngang; không gửi email hoặc đổi mật khẩu tài khoản thật trong kiểm thử OTP. Xem [bằng chứng và giới hạn vận hành OTP](../verification/web-ui/account-security.md), [kết quả đồng bộ màu hạng](../verification/web-ui/customer-tier-consistency-checks.json) và [kiểm tra bố cục AI](../verification/web-ui/admin-ai-settings-layout-checks.json). Đã đồng bộ source/public và xóa trang kiểm thử tạm.

## [2026-10-22] — @codex — Đăng nhập Google, OTP email và giao diện portal — Hoàn thiện xác thực và đồng bộ nút Google

- Dùng chung nút Google cho đăng nhập và đăng ký; Gmail mới chỉ được tạo tài khoản khách hàng sau khi xác nhận OTP đúng. Giữ `customerId` trong phiên đăng nhập, liên kết bằng Google subject đã xác minh và không tự ghép tài khoản mật khẩu chỉ vì trùng email. Gỡ đăng nhập Facebook; các route social cũ trả `410`.
- Cho launcher demo nạp cấu hình Google/SMTP từ `.env`, ưu tiên biến môi trường đã có; bổ sung CSP cho Google Identity Services. Kiểm tra backend, database và xác thực SMTP qua TLS; không đưa secret vào changelog hoặc repository.
- Tăng thời gian chờ các yêu cầu OTP Google từ 2,5 lên 45 giây, ngăn tự phát lại yêu cầu sang host khác khi lỗi mạng. Khi nhận được phiên xác thực, giao diện mở và đặt con trỏ vào ô OTP.
- Mã OTP có hiệu lực 5 phút sau khi gửi thành công; phiên xác thực được giữ 15 phút từ lần gửi thành công gần nhất. Giới hạn hai lần gửi lại và tổng cộng năm lần nhập sai. Mã hết hạn trả `422` để gửi lại; phiên mất/đã dùng/hết hạn trả `410` để trở về nút Google.
- Nút gửi lại hiển thị trạng thái chờ và kết quả. Gửi thư lỗi giữ nguyên mã trước đó và số lượt gửi lại; lỗi tạo tài khoản không dùng mất mã đúng. Sửa tạo hồ sơ Google khi chưa có số điện thoại để tương thích cột `phone NOT NULL`.
- Sửa email mất dấu tiếng Việt bằng nội dung UTF-8 truyền dưới dạng MIME Base64.
- Thiết kế lại vùng nút Google với nền theo giao diện, khung bo góc, viền vàng nhẹ và hiệu ứng hover/focus. Dùng nhãn “Đăng nhập bằng Google”, tự đổi mẫu sáng/tối và loại bỏ dải nền trắng quanh iframe. Kích thước `medium` tránh hiển thị tên, email hoặc ảnh tài khoản trên nút; đổi giao diện không khởi tạo lại xác thực.
- Đồng bộ tài nguyên `web-ui/` và `web-ui/public/`, cập nhật phiên bản cache portal lên `3.6.35`. Bổ sung [hướng dẫn Google/SMTP và xử lý lỗi OTP](../google-otp-setup.md), [hợp đồng API](../dev-spec/api-contracts.md#google-customer-authentication--2026-10-21) và [hướng dẫn máy dev](../chatbot-ai-dev-setup.md).
- Xác minh đã thực hiện: bộ web đạt **104/104**, backend liên quan Google/OTP đạt **10/10**; sau lần chỉnh nút cuối cùng, **23/23** kiểm thử web liên quan đạt. Kiểm tra trực tiếp cả giao diện sáng/tối. Câu lệnh tạo hồ sơ được thử trong transaction đã rollback; kiểm thử OTP dùng Google/SMTP giả lập, không gửi thư thật. Luồng đăng nhập Google và nhập OTP thật vẫn cần người dùng nghiệm thu.

## [2026-10-21] — @codex — Giao diện staff/customer và tài liệu UI audit — Đồng bộ bố cục với admin

- Đồng bộ header, sidebar, thu gọn, vị trí tài khoản và điều hướng responsive; bỏ tài khoản/đăng xuất lặp, căn brand về trái và ẩn nút dành cho phiên đăng nhập ở màn hình login. Hover/chọn tab customer dùng cùng hiệu ứng admin với màu riêng theo hạng.
- Chuẩn hóa trang Khách hàng, Đặt phòng, Hóa đơn, Hồ sơ & bảo mật của staff; chỉnh spacing sơ đồ phòng. Làm lại thẻ nhận/trả phòng và hộp thư hỗ trợ với tìm kiếm, bản nháp, tự cập nhật, cuộn responsive và nút gửi căn giữa cạnh ô soạn. Sửa lỗi lịch sử chat rỗng khi tin mới nhất do staff gửi.
- Thống nhất ngưỡng Member 0, Silver 500, Gold 1.000, VIP 5.000 theo tổng điểm đã tích lũy; đổi điểm không giảm hạng. Thêm màu thẻ theo hạng và tab Thành viên placeholder cho admin dưới Khách hàng.
- Sửa dropdown thông báo bị nội dung che, màu sáng/tối, trạng thái rỗng/voucher và giới hạn kích thước theo viewport. Đồng bộ root/public và tăng phiên bản tài nguyên để tránh cache cũ.
- Bổ sung test membership/chat, script Chromium, ảnh và kết quả giả lập trong `docs/verification/web-ui/`. Lượt xác minh thông báo/shell mới nhất đạt 40 tổ hợp hạng/theme/viewport và 16 test auth/theme/portal; các lượt xác minh staff/bảng/checkout/chat trước đó được ghi trong [UI audit](../../web-ui/MANUAL_UI_AUDIT.md).
- Ghi rõ các lỗi customer còn mở: tương phản ở chế độ sáng, màu nhấn, tiêu đề trùng, bố cục thẻ/bảng và fallback thông báo. Kiểm tra trình duyệt dùng API giả lập, thiếu font/icon/utility CDN trong môi trường này; chưa nghiệm thu backend thật.
- Xác minh trước khi push: toàn bộ bộ kiểm thử web UI đạt **69/69**, không thất bại hoặc bỏ qua. Các test HTTP được chạy ngoài sandbox hạn chế để kết nối được máy chủ localhost.

## [2026-10-20] — @codex — Báo cáo Phase 4, baseline và changelog — Ghi nhận triển khai và các điều kiện nghiệm thu còn thiếu

- Bổ sung [danh sách triển khai](../PHASE_4_IMPLEMENTATION.md), [báo cáo triển khai](../PHASE_4_REPORT.md), [bằng chứng xác minh](../PHASE_4_VERIFICATION.md); liên kết từ baseline Phase 4 và cập nhật changelog gốc/của tài liệu. Giữ nguyên lịch sử.
- Ghi nhận bootstrap cơ sở dữ liệu, giao dịch đơn dịch vụ/voucher, mã check-in có chữ ký, phê duyệt giá, đối soát và sửa lỗi xác thực/thanh toán/vòng đời đặt phòng; loại bỏ kết quả đặt phòng/thanh toán thành công và tồn phòng giả lập.
- Kết quả: Maven test/package **187 kiểm thử, 0 thất bại, 0 lỗi, 38 bỏ qua**; web UI **51 đạt, 0 thất bại hoặc bỏ qua**; MariaDB cô lập **2 kiểm thử tích hợp đạt**, áp dụng đủ 10 migration. Kiểm thử bỏ qua không phải bằng chứng nghiệm thu.
- **Phase 4 chưa hoàn tất nghiệm thu phát hành:** GATE-01 và GATE-03 đạt; GATE-02 đạt trên schema rỗng tương đương, chưa xác minh khởi động Docker; **GATE-04 bị chặn** do HTTP localhost/VM hết thời gian chờ và không kết nối được cơ sở dữ liệu VM.
- Còn thiếu: nghiệm thu portal/API/webhook/database đã triển khai, Docker Compose/entrypoint, thử nghiệm camera/OCR với dữ liệu được duyệt, kiểm thử Python AI (thiếu `pytest`), xác minh nhà phát hành/chữ ký hóa đơn, pilot có giám sát và benchmark tải được cấu hình. PDF hiện tại là bản ghi nội bộ chưa ký.
- Ghi nhận rủi ro fixture cũ ban đầu có thể đã ghi dữ liệu kiểm thử vào schema ứng dụng trước khi bổ sung chốt bảo vệ schema dùng một lần. Không có snapshot trước đó để xác định dữ liệu có thể xóa an toàn; không xóa dữ liệu theo suy đoán.

## Các quy tắc
- Mọi thay đổi đối với `/docs/` BẮT BUỘC phải được ghi lại ở đây trước khi commit.
- Định dạng: `## [YYYY-MM-DD] — @author — file(s) — reason — what changed`
- Nếu một quy tắc ràng buộc được thêm/sửa → ghi rõ tình huống ngoại lệ hoặc sự cố nào đã kích hoạt nó.
- Nếu một API endpoint được thêm/xóa → tham chiếu đến giai đoạn (P1/P2/P3).
- Lưu trữ: vĩnh viễn (không xóa các mục cũ).

## [2026-10-09] — @toan — Toàn bộ 17 files — Bộ tài liệu khởi tạo
- Đã tạo `/docs/ai-context/` (6 files): constraints, data-access, escalation-rules, edge-cases, monitoring, incident-response
- Đã tạo `/docs/system-prompts/` (2 files): chatbot-guest, staff-assistant
- Đã tạo `/docs/dev-spec/` (8 files): architecture, api-contracts, test-scenarios, prompt-loading, implementation-plan, tech-stack, integration, deployment
- Hợp đồng Hành vi AI v1.0 (11 quy tắc cứng)
- Đã xác định phạm vi Giai đoạn 1 (5 endpoints cốt lõi)
- Triển khai: Kiến trúc Docker 6-container

## [2026-10-11] — @devops — docs/vi/dev-spec/*, docs/vi/system-prompts/* — Cập nhật Kiến trúc Nền tảng & Đồng bộ Công nghệ
- Nhúng Triết lý Thiết kế Nền tảng: "Platform = NƠI NGƯỜI DÙNG NGỒI" (Desktop Swing Nhân viên Giai đoạn 1-3 vs Web React Khách/Quản trị Giai đoạn 3+).
- Chuẩn hóa tài liệu theo plain HttpServer, Maven (pom.xml) và cấu hình healthcheck container.

## [2026-10-12] — @devops — docs/vi/dev-spec/ui-*.md — Quy chuẩn Thiết kế Giao diện UI/UX
- Khởi tạo 4 tài liệu đặc tả giao diện người dùng: `ui-tokens.md`, `ui-desktop-staff.md`, `ui-web-guest.md`, `ui-web-admin.md`.

## [2026-10-12] — @toan — docs/*, database/hotel.sql, docker-compose.yml — Tinh chỉnh Kiến trúc & Giảm thiểu Rủi ro
- Thống nhất MUI 5.x cho toàn bộ Web UI, dời Admin Web sang Phase 4 để ưu tiên Guest Web.
- Tái phân bổ trách nhiệm AI Gateway: Dev D dựng khung FastAPI/pgvector, Dev A làm API client, Dev B tập trung Night Audit & guardrails.
- Thay thế JEditorPane trong Swing bằng Native Components (JPanel + BoxLayout + FlatRoundBorder).
- Thiết lập ranh giới bảo mật PII vật lý: tạo database user `ai_reader` chỉ có quyền đọc trên danh mục phòng và khuyến mãi.
- Chuẩn hóa cấu hình HikariCP Connection Pool và Fixed Thread Pool cho máy chủ Java HttpServer.

## [2026-10-12] — @toan — docs/vi/dev-spec/tasks-phase1.md, pom.xml — Đặc tả WBS Giai đoạn 1 & Thư viện phụ thuộc
- Chuẩn hóa kế hoạch WBS Giai đoạn 1 chi tiết cho 4 tuần và 4 lập trình viên.
- Bổ sung TASK-A1.4 cho xác thực JWT / Controller đăng nhập; bổ sung HikariCP, SLF4J, và jbcrypt vào pom.xml.
- Hiệu chỉnh đúng 6 mã trạng thái phòng chuẩn (`vacant_clean`, `reserved`) trong câu lệnh khóa lạc quan.
- Điều chỉnh ngưỡng cảnh báo rò rỉ HikariCP lên 5000ms và timeout 30s.
- Định dạng chuẩn Ma trận Phụ thuộc Bàn giao chéo và Biên bản Ký duyệt Nghiệm thu.
## [2026-10-13] — @devops — root, SVN_Repo_KhachSan — Dọn dẹp các thư mục & tệp SVN
- Đã xóa bỏ hoàn toàn thư mục bản sao `SVN_Repo_KhachSan/` khỏi Git tracking và ổ đĩa.
- Đã xóa tệp script đồng bộ `DongBoSVN.bat`.
- Đã dọn dẹp các thư mục khung SVN không sử dụng (`trunk/`, `branches/`, `tags/`).

## [2026-10-13] — @codex — Các tệp frontend/backend Dev C C3 — Sửa lỗi quy trình C3
- Bổ sung các route thanh toán folio và đồng bộ POS theo lô của Phase 1 với xử lý idempotency; kết nối client desktop tới API đã cấu hình và phiên xác thực.
- Kết nối HTTP 503 với banner Night Audit, khóa các nút thay đổi đã đăng ký trong thời gian đóng băng và tự đồng bộ hàng POS sau khi mở khóa.
- Gán F2 cho check-in và F3 cho check-out của phòng đã chọn; hiển thị riêng số dư folio Master và Guest khi trả phòng.
- Chuẩn hóa trạng thái phòng theo đúng sáu giá trị tài liệu quy định và chỉ cho phép đặt phòng khi trạng thái là `vacant_clean`.
- Thêm kiểm tra thế hệ request để kết quả tải phòng cũ không ghi đè dữ liệu mới; đồng bộ thanh tìm kiếm nhanh với màu giao diện.

## [2026-10-13] — @codex — Backend/frontend, kiểm thử và cấu hình build B1/C1 — Triển khai B1/C1 và trạng thái xác minh
- Triển khai migration/versioning và idempotency cho B1, kết nối tính giá đặt phòng với bộ tính VAT/phí dịch vụ, đồng thời bổ sung kiểm thử đơn vị và kiểm thử tích hợp MariaDB tùy chọn.
- Triển khai API client bất đồng bộ dùng chung, bộ điều phối phím tắt F1–F10 và token giao diện dùng chung cho C1; bổ sung kiểm thử API/EDT và cấu hình Maven test runner.
- `mvn -o test` đã chạy qua các kiểm thử đơn vị/API hiện có (26 đạt); ba kiểm thử tích hợp MariaDB bị bỏ qua do chưa cấu hình cơ sở dữ liệu MariaDB 11. Việc xác minh CSDL cho B1.1/B1.2 vẫn còn thiếu tại thời điểm đó.

## [2026-10-13] — @codex — UpdateDbUtility.java, MariaDbMigrationIntegrationTest.java, database/hotel.sql — Xác minh tích hợp MariaDB 11
- Xác minh toàn bộ bộ kiểm thử trên MariaDB 11.8.6 cô lập: 26 kiểm thử đạt, 0 lỗi, 0 bị bỏ qua. Phạm vi gồm migration lặp lại trên schema cũ, nạp toàn bộ `database/hotel.sql`, xử lý đồng thời các request idempotency và phát lại phản hồi lỗi đã lưu.
- Giới hạn tra cứu metadata migration trong đúng cơ sở dữ liệu hiện tại, cập nhật trình chạy kiểm thử schema để hỗ trợ chỉ thị SQL `DELIMITER`, và sửa trạng thái booking mẫu số 63 từ trạng thái invoice `paid` thành trạng thái booking hợp lệ `checked_out`.

## [2026-10-14] — @codex — Các tệp phân bổ phòng Dev B/C và sơ đồ phòng desktop — Rà soát và triển khai một phần các việc Phase 1 còn lại
- Bổ sung kiểm thử tranh chấp đặt cùng một phòng trên MariaDB 11; chỉ chạy khi bật `HOTEL_RUN_DB_INTEGRATION=true` và schema dùng riêng có tên kết thúc bằng `_test`. Kiểm thử xác nhận một lần thành công, một xung đột và version chỉ tăng một lần.
- Bổ sung ô phòng 80×60, sơ đồ tách rời 5 cột, sáu màu trạng thái chuẩn, lọc bộ nhớ theo phòng/khách/booking, bộ hiển thị trạng thái bảng 24px, lưu kích thước/vị trí cửa sổ an toàn và bộ đo thời gian xử lý EDT.
- Kết nối sơ đồ gọn vào `RoomForm`, bộ lọc nhanh, renderer bảng đặt phòng và khởi động `MainUI`. Các thay đổi hỗ trợ một phần B2.1, C2.2, C2.3, C3.3, C4.1 và C4.2; Definition of Done đầy đủ của các việc này vẫn chưa được xác minh hết.
- Các lớp đã sửa và kiểm thử tập trung biên dịch bằng JDK 17; kiểm thử UI tập trung đạt (3 đạt). Kiểm thử tranh chấp MariaDB bị bỏ qua theo điều kiện môi trường do chưa cấu hình schema kiểm thử. Maven chưa chạy được: không ghi được lock của kho Maven cấp người dùng; lần thử dùng kho trong workspace không tải được dependency vì DNS Maven Central không khả dụng.
## [2026-10-14] — @codex — Hoàn thiện dịch vụ Phase 1 cho Dev B/C
- Bổ sung thanh toán folio nguyên tử, định tuyến Master/Guest, tách folio, tác vụ no-show theo lô 50, night audit 7 bước có checkpoint và khả năng phục hồi, tổng hợp DBR, phép tính điều chỉnh/hoàn tiền và hạ hạng phòng OOO có quyền quản lý.
- Kết nối lỗi tải API với trạng thái lỗi trên bản đồ phòng, giữ hỗ trợ cửa sổ bản đồ màn hình phụ và ghi lại quy trình mô phỏng ca lễ tân hai giờ.
- Đặt số dư folio mới về 0 để tránh tính trùng tiền phòng giữa ước tính booking và bút toán night audit; chuẩn hóa phản hồi đóng băng với `Retry-After: 300`.
- JDK 17 biên dịch thành công toàn bộ mã Java production và test. Chạy JUnit tập trung: 6 đạt, 0 lỗi, 2 test MariaDB bị bỏ qua do chưa có schema kiểm thử riêng. `mvn -o test` chưa chạy được vì khóa kho Maven cấp người dùng không có quyền truy cập. Mô phỏng hai giờ và kiểm thử giao dịch/khôi phục trên MariaDB vẫn cần chạy trong môi trường tương ứng.

## [2026-10-15] — @codex — Backend, desktop/web UI, migration, tests, scripts và docs Phase 2 — Triển khai Phase 2 và ghi nhận trạng thái nghiệm thu
- Bổ sung luồng portal/OTP/đặt phòng công khai, phân bổ phòng cạnh tranh an toàn, báo giá và voucher phía máy chủ; tích hợp VietQR, xác minh HMAC webhook, thanh toán idempotent, folio và xử lý thanh toán thừa.
- Bổ sung housekeeping, state machine phòng, minibar, maintenance OOO/OOS, hóa đơn PDF/thuế cấu hình tường minh, DBR và các giao diện Swing/web tương ứng; thêm migration, mock/stub và bộ kiểm thử Phase 2.
- Thêm tài liệu kiểm kê tệp thay đổi, cấu hình production bắt buộc, báo cáo nghiệm thu và bằng chứng kiểm thử; chỉ dùng cấu hình/dữ liệu tổng hợp trong môi trường test, không đặt thông tin pháp lý/ngân hàng/OTP production giả định.
- Xác minh theo báo cáo Phase 2: Java 17, MariaDB 11.4.2 cô lập; `mvn clean test -Dtest=Phase2RegressionSuite` đạt 109 test, 0 lỗi, 0 bỏ qua. Nghiệm thu tổng thể vẫn một phần: còn kiểm tra Swing/thời gian hiển thị thủ công, kiểm tra Docker và cấu hình production/nhà cung cấp cần phê duyệt.

## [2026-10-15] — @codex — scripts/validate-phase2-acceptance.py, tests/acceptance/, docs/phase2-manual-acceptance.md, docs/verification/phase2/manual-acceptance.json — Cổng bằng chứng nghiệm thu Phase 2
- Bổ sung bộ kiểm tra chỉ đọc, liệt kê task ID còn thiếu bằng chứng/thời gian đo, yêu cầu người xác minh và nhật ký; không coi kiểm thử tự động đạt là nghiệm thu đầy đủ.
- Bổ sung bốn kiểm thử độc lập; giữ bằng chứng trình duyệt đã có, không tự tạo kết quả đo Swing hay thời gian render. Phase 2 vẫn chưa được nghiệm thu đầy đủ.
- Chưa fetch origin/PHASE_1: dịch vụ xét duyệt tự động hết hạn mức sử dụng. Không xác nhận đã merge hoặc giải quyết xung đột mã incoming.

## [2026-10-15] — @codex — Incoming Phase 1 integration, baseline recovery, configuration, and acceptance handoff
- Fetched origin/PHASE_1 at 3844597 and refreshed PHASE_2. Resolved conflicts while preserving Phase 2 transactional and authorization behavior; retained incoming test source and recovered core endpoint cases in isolated test fixtures.
- Reconciled the designated 47-case baseline (34 original + 13 recovered); clean merged regression passed 125 tests with 0 failures, errors, or skips, including a production server registration check.
- Added deep database health and incoming response hardening; corrected Compose schema/configuration forwarding and removed default production credentials.
- C5.1 passed at 375/800/1920 px with rendering below 83 ms and no console errors. Saved evidence and a strict read-only acceptance validator.
- User has no QA host access currently. Documented later Windows Swing and Docker-host execution; C5.3, C6.1, C6.2, C7.1, C7.2, C7.3, C8.1, C8.3 and final D8.4 sign-off remain open. No native or Docker pass is claimed.

## [2026-10-15] — @codex — docs/dev-spec/web-ui-unification-review.md — Existing web inventory and unification proposal
- Inventoried 23 existing web screen/workflow surfaces across Customer, Staff, Admin and shared authentication/profile; recorded keep/fix/replace dispositions and verified source-level integration bugs.
- Proposed shared documented navy/Inter/light tokens, role-specific layouts/components, phased implementation and browser end-to-end acceptance; identified contradictory/future-phase documentation without silently expanding feature or framework scope.
- Recorded versioned-route and customer-ownership backend blockers for prior alignment. No application/backend changes or new browser E2E pass are claimed; style guide and implementation order await agreement.

## [2026-10-15] — @codex — web-ui/public/app.js, docs/dev-spec/web-ui-unification-review.md, kiểm thử web-ui — Phân vai shell và khôi phục phiên
- Kết nối vai trò trình bày tường minh vào shell portal; Admin, Customer và Front Desk giữ menu riêng. Housekeeping, Maintenance và F&B chỉ thấy màn hình thông tin giới hạn, không được nhận các thao tác Front Desk. Vai trò không nhận diện sẽ bị từ chối.
- Kết nối thông báo phiên hết hạn với đăng xuất khỏi shell và phản hồi trên màn hình đăng nhập. Cập nhật biên bản theo hướng vanilla/navy/Inter đã thống nhất, đồng thời giữ giao diện sáng và tối cũ cùng tùy chọn được lưu; thay đổi route phiên bản và kiểm tra quyền sở hữu phía backend vẫn chưa được duyệt.
- Bộ Node tập trung: 15 đạt, 0 lỗi, 0 bỏ qua. Trình duyệt đã xác minh nút đổi sáng/tối ở trang đăng nhập và tùy chọn được giữ sau khi tải lại; luồng E2E theo vai trò và ma trận kích thước màn hình vẫn còn.
- `git fetch origin` không đọc được `.git/FETCH_HEAD` do môi trường từ chối quyền; chưa xác minh thay đổi upstream. Không merge, push hay sửa backend.

## [2026-10-15] — @codex — web-ui/public/js/staff/room-map.js, web-ui/public/portal.html, shared-theme.css — Sơ đồ phòng theo trạng thái chuẩn và hướng dẫn API
- Chuyển sơ đồ phòng thành màn hình tổng quan bảy trạng thái chuẩn, có nhãn chữ, dùng helper trạng thái chung, tạo nội dung DOM an toàn, bố cục chú giải đáp ứng và chặn kết quả request cũ. Bỏ thao tác cũ đặt phòng/thanh toán dùng số tiền phía client hoặc đặt trạng thái thành `available`; nghiệp vụ vẫn nằm trong các module riêng.
- Sửa placeholder cấu hình API và hướng dẫn ngrok của portal để dùng `/api/v1`.
- Đã kiểm tra nút giao diện sáng/tối và khả năng lưu tùy chọn bằng trình duyệt. Chưa chạy nghiệm thu workflow từng vai trò/ma trận kích thước vì chưa có quyền truy cập tài khoản kiểm thử; phụ thuộc backend cho checkout/quyền sở hữu vẫn còn.
- Viết lại checkout Staff để đọc folio từ API hiện có, hiển thị số dư máy chủ, tất toán với khóa idempotency UUIDv4, tạo VietQR theo cấu hình server và dò trạng thái thanh toán; sau đó yêu cầu xác nhận trả phòng riêng. Màn hình không còn ô nhập tiền phía client, tài khoản QR ngoài hay thay đổi quy tắc thanh toán.
- Bổ sung tải PDF bằng fetch có xác thực cho hóa đơn Admin và Staff; dùng chung xử lý phiên 401. Dashboard hiển thị trạng thái đang tải/không tải được thay cho số 0 sai, có thử lại và biểu đồ theo token màu.
- Chạy lại bộ tập trung sau tích hợp: 16 đạt, 0 lỗi, 0 bỏ qua. Kiểm tra cú pháp JavaScript và `git diff --check` đạt. Workflow tài chính đã xác thực vẫn cần nghiệm thu bằng fixture cô lập trên trình duyệt.
- `git fetch origin` với quyền Git metadata đã thành công sau lần đầu bị từ chối. `origin/PHASE_1` và `PHASE_1` cục bộ vẫn ở 3844597; `PHASE_2` vẫn đi trước `origin/PHASE_2` 7 commit, không có commit mới từ xa. Không merge hoặc push.
- Phiên có vai trò chung `STAFF` nay bị từ chối nếu backend không cung cấp bộ phận cụ thể được hỗ trợ; chuẩn hóa nhãn nhận cả `Front Desk` và `FRONT_DESK` mà không gộp menu các bộ phận.
- Thay lời hứa giảm giá loyalty không có căn cứ bằng thông báo trạng thái chung. Dashboard/booking Customer, đặt phòng bằng tài khoản và loyalty cá nhân hiện hiển thị lý do chặn thay vì gửi request dựa trên customerId hoặc tự tính giá. Luồng OTP Guest vẫn tách biệt; khuyến mãi/chat Customer không liên quan vẫn giữ riêng.

## [2026-10-15] — @codex — web-ui/public/css/theme-overrides.css — Tăng độ dễ đọc giao diện sáng và chỉnh nút nổi trên mobile
- Tăng độ tương phản cho nhãn chữ phụ và chữ màu vàng trong giao diện sáng; khôi phục màu vàng nhạt cho logo trên header tối khi ở đầu trang.
- Chuẩn hóa nút gọi và chat nổi trên mobile thành vùng chạm 48px với biểu tượng 20px.
- Gỡ các gradient thử nghiệm ở hero và footer sau khi rà soát; không ghi nhận chạy kiểm thử cho các cập nhật CSS này.

## [2026-10-15] — @codex — các giao diện theme web-ui, docs/WEB_UI_ISSUES.md, docs/CHANGELOG.md, docs/vi/CHANGELOG.md — Theme toàn cục và theo dõi lỗi UI
- Bổ sung một nút theme Tự động/Sáng/Tối dùng chung cho trang khách sạn và portal; hỗ trợ theo dõi cài đặt trình duyệt, lưu lựa chọn dùng xuyên các trang, nhãn truy cập rõ ràng và bảng màu sáng tương ứng; giữ nguyên bố cục riêng của khách hàng và giao diện quản lý.
- Bổ sung kiểm thử hành vi theme và cập nhật màu trình duyệt theo theme. Bộ Node tập trung đạt 6/6; kiểm tra cú pháp JavaScript và `git diff --check` đạt.
- Tạo GitHub issue cho các lỗi UI/tích hợp còn tồn đọng: #36 biểu tượng mạng xã hội không hỗ trợ, #37 nút đóng lightbox bị che, #38 nhận diện/giá phòng, #39 bản dịch tiếng Anh chưa đầy đủ, #40 thiếu bộ chọn khách/phòng, #41 endpoint thống kê admin, #42 trạng thái tải dashboard/loyalty khách hàng và #43 trang ưu đãi bị kẹt khi tải.
- Gắn số issue vào `docs/WEB_UI_ISSUES.md`. Không mở lại các lỗi đã được đánh dấu khắc phục; giữ `.phase2-runtime/` ngoài Git.
