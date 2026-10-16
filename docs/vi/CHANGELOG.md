# NHẬT KÝ THAY ĐỔI (CHANGELOG) — Tài liệu hệ thống PMS Khách sạn

## [2026-10-16] — @codex — Phase 3, tài liệu nghiệm thu và đồng bộ CHANGELOG tiếng Việt
- Bổ sung `docs/phase3-implementation-report.md` với trạng thái từng task, các xung đột đã được người dùng quyết định, lệnh kiểm thử/build, bằng chứng đo được và tiêu chí chưa kiểm chứng. Bổ sung `docs/phase3-release-checklist.md` cho chuyển đổi vault/HMAC, quản lý khóa, mTLS, mô hình, giao diện và nghiệm thu triển khai. Các đường dẫn này trỏ đến tài liệu gốc, chưa phải bản dịch tiếng Việt.
- Bổ sung `docs/security/owasp_zap_scan_report.md` với trạng thái **CHỜ KIỂM CHỨNG**; chưa chạy ZAP hoặc triển khai sản xuất. Đồng bộ mục Phase 3 của `docs/CHANGELOG.md` sang nhật ký tiếng Việt này.
- Cập nhật phụ lục tương thích P3 trong `docs/dev-spec/api-contracts.md`: giữ API frozen làm dạng chuẩn, thêm alias tương thích WBS cho vision/concierge/revenue; forecast mặc định dùng `data.forecast`, hỗ trợ `?format=array`. Duyệt giá chấp nhận `approved_multiplier` hoặc `multiplier`; version mặc định 0 chỉ tạo mới, cập nhật phải khớp version hiện tại. Không tự điền phòng hoặc thời gian phản hồi chưa có nguồn xác thực.
- Triển khai nền tảng backend/UI/service Phase 3: vault mã hóa AES-256-GCM với IV ngẫu nhiên riêng cho từng giá trị, chỉ mục HMAC phục vụ tìm kiếm CCCD chính xác/chống trùng và quy trình chuyển đổi dữ liệu cũ; gateway OCR/đối chiếu khuôn mặt; mTLS bắt buộc từ Java đến `ai-service:8082`; concierge có bộ lọc prompt và chat ticket mã hóa theo quyền sở hữu; duyệt giá có version/audit; retention/ẩn danh hóa; thu hồi token khi đăng xuất và giới hạn request theo IP. Không mở rộng quyền `ai_reader`.
- Ghi nhận các quyết định đã được chấp thuận: hỏi chính sách hủy trung tính được gắn MONITOR và trả nguyên văn nguồn có thẩm quyền; hệ số đề xuất mặc định từ 0,80×, ngoại lệ từ 0,70× đến dưới 0,80× cần quản lý phê duyệt, lý do và audit; ngoài khoảng 0,70×–2,50× bị từ chối. Do chưa đủ dữ liệu công suất/phòng còn bán theo ngày và hạng phòng, doanh thu minh họa được hiển thị là chưa khả dụng, không đưa ra mức tăng doanh thu thiếu căn cứ.
- Bổ sung màn hình Swing check-in/copilot/quản trị giá, splash và quy trình đóng gói desktop kèm JRE. Build thành công bản preview cuối; chưa khởi chạy hoặc phát hành bản này.
- Hồi quy Java cuối: **162 test đạt, 0 thất bại, 0 lỗi, 0 bỏ qua**, thời gian **32,374 giây**, gồm benchmark đã bật tường minh. Bộ test Python dùng fixture: **5 test đạt**, 1 cảnh báo deprecation, **0,45 giây**, chạy trên Python 3.12; kiểm chứng container Python 3.11 theo yêu cầu vẫn còn chờ.
- Benchmark trên CSDL dùng riêng cho kiểm thử: 100.000 booking lịch sử và 730 snapshot báo cáo ngày đã đóng, đo 20 lượt truy vấn tổng hợp sau khi làm nóng; p95 cuối **8,455 ms**, tối đa **10,162 ms**. Kết quả áp dụng cho DAO tổng hợp từ snapshot, không phải quét toàn bảng booking hoặc thử tải hai giờ. Dữ liệu test đã được rollback và MariaDB do tác vụ tạo đã được dừng.
- Kiểm thử mTLS cục bộ với chứng thư dùng riêng cho test đã đạt, gồm từ chối client thiếu chứng thư và hostname không khớp. Đã tạo 100 ảnh OCR synthetic có đánh dấu rõ tài liệu không hợp lệ và bản nháp JMeter; các fixture này không chứng minh độ chính xác mô hình hoặc khả năng chịu tải thực tế.
- **Phase 3 vẫn CHƯA HOÀN TẤT (PARTIAL) / CHƯA PHÁT HÀNH.** Còn chờ đo độ chính xác, độ trễ và tài nguyên của mô hình thật; camera/EDT/giao diện/RAM trên máy thực tế; Docker Python 3.11 và PKI; tải 200 người dùng trong hai giờ; ZAP/kiểm thử xâm nhập thủ công; cùng các tiêu chí Phase 2 còn tồn đọng. Phần triển khai còn thiếu gồm escalation ngữ nghĩa đa ngôn ngữ, hoàn thiện lưu ảnh/quét khuôn mặt vào vault và vòng đời luân chuyển khóa, fixture tải có liên kết dữ liệu và chữ ký hợp lệ. Chưa chạy migration hay triển khai sản xuất; AI cho khách vẫn mặc định tắt.

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
