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
