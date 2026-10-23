# Quanlykhachsan

## Chạy thử chatbot AI trên máy dev khác

Xem [hướng dẫn cấu hình chatbot AI riêng cho máy dev](docs/chatbot-ai-dev-setup.md): tạo database/key demo riêng, chạy backend và web, nhập key trong admin, lấy model, kiểm thử hội thoại và xử lý lỗi. Nhánh tích hợp: `CHATBOT_IMP`.

## Hướng dẫn chạy dự án

1. Chuẩn bị môi trường
   - Cài đặt JDK 17.
   - Cài đặt MariaDB 11 và đảm bảo service đang chạy trên cổng `3306`.
   - Nếu dùng XAMPP, bật MySQL.

2. Cấu hình database
   - Mở `config.properties` và đảm bảo `server.url=http://localhost:8081/api`.
   - Sao chép `.env.example` thành `.env` và cấu hình đúng database cùng key đi kèm. Backend mặc định không tự chuyển host khi DB không truy cập được. Docker Compose dùng service `mariadb` trong network nội bộ.
   - Xem [cấu hình DB thật, tạo DB mới và nhận DB từ dev khác](docs/vi/real-database-startup.md). DB riêng có key riêng; các máy dùng chung DB hoặc cùng SQL dump phải giữ cùng AES/HMAC key.
   - Nếu dùng cấu hình khác, set biến môi trường:
     - `DB_HOST`
     - `DB_PORT`
     - `DB_NAME`
     - `DB_USER`
     - `DB_PASSWORD`
   - Mức VAT được phê duyệt hiện tại là `HOTEL_VAT_RATE=0.08` (8%); cấu hình này được dùng khi báo giá, đặt phòng và lập hóa đơn. Nếu quy định áp dụng thay đổi, chỉ đổi sang `0.10` sau khi được phê duyệt. Booking không được tính giá nếu chưa có mức thuế hợp lệ.
   - PDF bảng kê thanh toán được đánh dấu là tài liệu nội bộ. Tích hợp MISA meInvoice, hồ sơ cần xác nhận với kế toán/nhà cung cấp và luồng đồng bộ/hủy xem tại [docs/electronic-invoices.md](docs/electronic-invoices.md); phát hành production chưa bật khi chưa cấu hình tài khoản và chế độ đã đăng ký.
   - Chatbot trên trang chủ hỗ trợ nhà cung cấp cloud cấu hình tại **Admin > API key AI**: Gemini, Groq, Claude và OpenAI. Luồng này không cần chạy RAG/Python. Nếu dùng RAG nội bộ thay cho cloud, cấu hình `AI_SERVICE_URL` cùng mTLS theo `ai-service/README.md`; backend không chấp nhận HTTP ở cấu hình transport production hiện tại.

3. Build và chạy backend
   - Trong PowerShell, chạy `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1` để biên dịch và khởi chạy backend. Cấu hình khóa vault theo `docs/run-project.md` trước khi chạy.
   - Nhấp đúp `start-backend.bat` để chạy sẵn lệnh PowerShell khởi động backend nền với `.env`. Nếu lỗi, cửa sổ giữ lại thông báo và gợi ý kiểm tra DB/key. Nhấp đúp `stop-backend.bat` để dừng đúng backend của dự án qua `scripts/stop-backend.ps1`. Demo là lựa chọn riêng qua `scripts/start-demo.ps1`.
   - Khởi chạy backend bằng `Main`:
     - `quanlykhachsan.backend.Main`
   - Backend hiện đang lắng nghe trên cổng `8081`.

4. Chạy giao diện frontend
   - Chạy `node web-ui/server.js`, rồi mở `http://localhost:3000/`. Backend cần đang chạy tại `http://localhost:8081` để web UI gọi API.
   - Trang tích hợp sẵn của backend cũng có thể mở tại `http://localhost:8081/`.

5. Chạy kiểm thử Maven
   - `mvn test` chạy các kiểm thử đơn vị và kiểm thử UI bất đồng bộ.
   - Kiểm thử migration MariaDB cần `HOTEL_MARIADB_TEST_URL` trỏ tới URL JDBC không kèm tên database (ví dụ `jdbc:mysql://localhost:3306/?serverTimezone=UTC`), cùng `HOTEL_MARIADB_TEST_USER` và `HOTEL_MARIADB_TEST_PASSWORD`. Test tạo rồi xóa schema riêng.
   - Kiểm thử idempotency cần `HOTEL_RUN_DB_INTEGRATION=true` và `DB_NAME` trỏ tới schema dùng riêng có tên kết thúc bằng `_test`; test sẽ tạo lại bảng `idempotency_keys` trong schema đó.

5. Tham khảo thêm
   - Xem hướng dẫn chi tiết trong `docs/run-project.md`.



--------------------------------------------------------
# GETTING STARTED — Dev Team

## Read in this order:
1. `docs/dev-spec/implementation-plan.md` — Timeline & phases
2. `docs/dev-spec/tech-stack.md` — Setup your environment
3. `docs/dev-spec/architecture.md` — Data flow & Night Audit
4. `docs/dev-spec/api-contracts.md` — Build these endpoints
5. `docs/dev-spec/test-scenarios.md` — Write tests for these cases

## Phase 2 (Week 5+):
6. `docs/dev-spec/prompt-loading.md` — AI Gateway architecture
7. `docs/dev-spec/integration.md` — OTA, Payment, POS

## Phase 3 (Week 13+):
8. `docs/dev-spec/deployment.md` — Docker & production

## AI Context files (DO NOT edit without @toan approval):
- `docs/ai-context/` — Read-only reference for AI runtime
- `docs/system-prompts/` — System prompts (English only)

## Rules:
- Every doc change → log in `docs/CHANGELOG.md`
- Every API change → update `api-contracts.md` + CHANGELOG
- Do NOT modify `constraints.md` or `data-access.md` without Business sign-off   

---------------------------------------
*How to use the 19 markdown files in this repo.*

## Quick Start (Dev)

**Day 1 — Read in this order:**
1. `dev-spec/timeline-4dev.md` → Know your role, week, task
2. `dev-spec/tech-languages.md` → Setup your environment
3. `dev-spec/tech-stack.md` → Install tools
4. `dev-spec/implementation-plan.md` → Phase scope + Go/No-Go
5. `dev-spec/api-contracts.md` → Build these endpoints
6. `dev-spec/architecture.md` → Understand data flow + Night Audit
7. `dev-spec/test-scenarios.md` → Write tests alongside code

**Phase 2 (Week 5+):** Add `dev-spec/prompt-loading.md` + `dev-spec/integration.md`

**Phase 3 (Week 8+):** Add `dev-spec/deployment.md`

---

## File Map (19 files)

### `/docs/ai-context/` — AI Runtime Rules (8 files → 6 here)

| File | Who reads | When |
|---|---|---|
| `constraints.md` | **LLM** (injected every call) | Never edit without Business approval |
| `data-access.md` | **LLM** (RAG) + **Dev** (RBAC code) | Dev: implement middleware. AI: knows what to read/write |
| `escalation-rules.md` | **LLM** (RAG) | When to stop + transfer to human |
| `edge-cases.md` | **LLM** (RAG) | 13 scenarios AI must handle |
| `monitoring.md` | **Dev** (build dashboard) + **You** (review) | KPIs + alert thresholds |
| `incident-response.md` | **You** + **Duty Manager** | Kill switch + apology + RCA |

### `/docs/system-prompts/` — AI Persona (2 files)

| File | Who reads | When |
|---|---|---|
| `chatbot-guest.md` | **LLM** (system prompt, guest-facing) | Never edit without Business approval |
| `staff-assistant.md` | **LLM** (system prompt, staff-facing) | Never edit without Business approval |

### `/docs/dev-spec/` — Dev Team Reference (9 files)

| File | Who reads | When |
|---|---|---|
| `timeline-4dev.md` | **All devs** | Every Monday (what to do this week) |
| `tech-languages.md` | **All devs** | Day 1 (setup) |
| `tech-stack.md` | **All devs** | Day 1 (install) |
| `implementation-plan.md` | **All devs** + **You** | Phase start + Go/No-Go |
| `architecture.md` | **Dev A, B** | Phase 1 (data flow, Night Audit) |
| `api-contracts.md` | **Dev A, B** | Every day (build endpoints) |
| `test-scenarios.md` | **Dev D** + **Dev A, B** | Phase 1-2 (write tests) |
| `prompt-loading.md` | **Dev B** | Phase 2 (AI Gateway) |
| `integration.md` | **Dev B** | Phase 3 (OTA, Payment, POS) |
| `deployment.md` | **Dev D** | Phase 1 (Docker) + Phase 3 (production) |

### Root

| File | Who reads | When |
|---|---|---|
| `CHANGELOG.md` | **Everyone** | Every commit that touches `/docs/` |

---

## Rules

| Rule | Who |
|---|---|
| Every doc change → add entry to `CHANGELOG.md` | All devs |
| Do NOT edit `constraints.md`, `data-access.md`, `system-prompts/*` without Business (@toan) sign-off | All devs |
| API change → update `api-contracts.md` + `CHANGELOG.md` | Dev A, B |
| New edge case discovered in production → add to `edge-cases.md` + `CHANGELOG.md` | Dev D + You |
| All files in English. Vietnamese translations (if any) go in `/docs/reference/` | All |

## File Count

| Directory | Count |
|---|---|
| `/docs/ai-context/` | 6 |
| `/docs/system-prompts/` | 2 |
| `/docs/dev-spec/` | 9 |
| Root (`CHANGELOG.md`) | 1 |
| **Total** | **18** |
