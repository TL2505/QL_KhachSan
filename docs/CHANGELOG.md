# CHANGELOG — Hotel PMS Documentation

## Rules
- Every change to `/docs/` MUST be logged here before commit.
- Format: `## [YYYY-MM-DD] — @author — file(s) — reason — what changed`
- If a constraint rule is added/modified → note which edge case or incident triggered it.
- If an API endpoint is added/removed → reference the phase (P1/P2/P3).
- Retention: permanent (do not delete old entries).

## [2026-10-09] — @toan — All 17 files — Initial documentation set
- Created `/docs/ai-context/` (6 files): constraints, data-access, escalation-rules, edge-cases, monitoring, incident-response
- Created `/docs/system-prompts/` (2 files): chatbot-guest, staff-assistant
- Created `/docs/dev-spec/` (8 files): architecture, api-contracts, test-scenarios, prompt-loading, implementation-plan, tech-stack, integration, deployment
- AI Behavior Contract v1.0 (11 hard rules)
- Defined Phase 1 scope (5 core endpoints)
- Deployment: 6-container Docker architecture

## [2026-10-11] — @devops — docs/dev-spec/*, docs/system-prompts/* — Platform Architecture & Tech-Stack Realignment
- Embedded Platform Design Philosophy: "Platform = WHERE THE USER SITS" (Swing Staff Phase 1-3 vs React Web Guest/Admin Phase 3+).
- Realigned documentation to plain HttpServer, Maven (pom.xml), and container health configurations.

## [2026-10-12] — @devops — docs/dev-spec/ui-*.md, docs/vi/dev-spec/ui-*.md — UI/UX Design System Specification
- Created 4 UI specification files: `ui-tokens.md`, `ui-desktop-staff.md`, `ui-web-guest.md`, `ui-web-admin.md` and complete Vietnamese translations.

## [2026-10-12] — @toan — docs/*, database/hotel.sql, docker-compose.yml — Architectural Refinements & Risk Mitigation
- Unified MUI 5.x across Web UIs; deferred Admin Web to Phase 4 to focus Dev C 100% on Guest Web.
- Reallocated AI Gateway responsibilities: Dev D on FastAPI/pgvector skeleton, Dev A on Java API client, Dev B on Night Audit & guardrails.
- Replaced Swing JEditorPane with Native Components (JPanel + BoxLayout + FlatRoundBorder + JTextArea).
- Enforced physical PII database boundary: configured MariaDB user `ai_reader` with read-only SELECT permissions on rooms and promotions.
- Standardized HikariCP Connection Pool and Fixed Thread Pool on plain Java HttpServer.

## [2026-10-12] — @toan — docs/dev-spec/tasks-phase1.md, pom.xml — Phase 1 WBS Specification & Dependencies
- Standardized Phase 1 WBS execution plan across 4 weeks and 4 developers.
- Added TASK-A1.4 for JWT auth / login controller; added HikariCP, SLF4J, and jbcrypt to pom.xml.
- Corrected room domain status codes (`vacant_clean`, `reserved`) in optimistic locking queries.
- Adjusted HikariCP leak detection threshold to 5000ms and timeout to 30s.
- Formatted clean Cross-Developer Dependency Matrix and Go/No-Go Acceptance Checklist.
## [2026-10-13] — @devops — root, SVN_Repo_KhachSan — Clean SVN Folders & Artifacts
- Removed legacy `SVN_Repo_KhachSan/` mirror directory from git tracking and disk.
- Removed `DongBoSVN.bat` synchronization script.
- Cleaned unused SVN placeholder folders (`trunk/`, `branches/`, `tags/`).

