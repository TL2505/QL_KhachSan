# Quanlykhachsan

## Hướng dẫn chạy dự án

1. Chuẩn bị môi trường
   - Cài đặt JDK 17.
   - Cài đặt MySQL và đảm bảo service đang chạy trên cổng `3306`.
   - Nếu dùng XAMPP, bật MySQL.

2. Cấu hình database
   - Mở `config.properties` và đảm bảo `server.url=http://localhost:8081/api`.
   - Nếu MySQL không có mật khẩu cho root, cấu hình mặc định đã dùng `root` với mật khẩu rỗng.
   - Nếu dùng cấu hình khác, set biến môi trường:
     - `DB_HOST`
     - `DB_PORT`
     - `DB_NAME`
     - `DB_USER`
     - `DB_PASSWORD`

3. Build và chạy backend
   - Chạy `build_project.bat` để biên dịch và tạo class.
   - Khởi chạy backend bằng `Main`:
     - `quanlykhachsan.backend.Main`
   - Backend hiện đang lắng nghe trên cổng `8081`.

4. Chạy giao diện frontend
   - Chạy lớp chính:
     - `quanlykhachsan.Quanlykhachsan`
   - Frontend sẽ gọi API tới `http://localhost:8081/api`.

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