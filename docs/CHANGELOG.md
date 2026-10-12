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

## [2026-10-13] — @codex — Dev C C3 frontend/backend files — C3 workflow review fixes
- Added Phase 1 folio payment and POS batch-sync routes with idempotency handling; connected the desktop clients to the configured API and authenticated session.
- Wired HTTP 503 responses to the Night Audit warning banner, disabled registered mutation controls during the freeze, and auto-syncs queued POS charges after unfreeze.
- Routed F2 to check-in and F3 to check-out for the selected room; displayed master and guest folio balances in checkout.
- Standardized room-state handling to the documented six status values and prevented bookings unless a room is `vacant_clean`.
- Added a request-generation guard to prevent stale room refresh results from replacing newer results; aligned the quick-search bar with theme tokens.

## [2026-10-13] — @codex — B1/C1 backend, frontend, tests, and build files — B1/C1 implementation and verification status
- Implemented the B1 migration/versioning and idempotency paths, connected booking pricing to the VAT/service-charge calculator, and added unit and opt-in MariaDB integration coverage.
- Implemented the C1 shared asynchronous API client, global F1–F10 shortcut dispatcher, and shared UI theme tokens; added API/EDT tests and Maven test-runner configuration.
- `mvn -o test` passed the available unit/API tests (26 passed); the three MariaDB integration checks were skipped because no MariaDB 11 test database was configured. B1.1/B1.2 database verification remains outstanding.

## [2026-10-13] — @codex — UpdateDbUtility.java, MariaDbMigrationIntegrationTest.java, database/hotel.sql — MariaDB 11 integration verification
- Verified the full suite on an isolated MariaDB 11.8.6 instance: 26 tests passed, 0 failures, 0 skipped. Coverage includes repeatable legacy-schema migration, the complete `database/hotel.sql` import, concurrent idempotent requests, and persisted failure replay.
- Scoped migration metadata lookups to the active database, taught the schema integration test to honor SQL `DELIMITER` directives, and corrected booking seed 63 from invoice status `paid` to valid booking status `checked_out`.

