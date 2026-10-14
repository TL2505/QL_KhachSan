# NHẬT KÝ THAY ĐỔI (CHANGELOG) — Tài liệu hệ thống PMS Khách sạn

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
