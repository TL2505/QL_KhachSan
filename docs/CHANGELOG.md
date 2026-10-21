# CHANGELOG — Hotel PMS Documentation

## [2026-10-21] — @codex — New Admin rooms first, visible after creation

- Added an Admin-only newest room-list option ordered by the existing `rooms.created_at DESC, id DESC`; default Staff/desktop ordering remains unchanged. Room DTOs expose the real creation date and successful inserts return the committed generated ID.
- Admin room tables preserve creation order through filters/pages, support legacy responses through descending insert IDs, clear filters/return to page one after creation and verify the refreshed list before reporting success. Local controls restore access to later pages when the shared pagination renderer is absent; stale responses cannot overwrite another view.
- Preserved the existing Admin layout, room validation, duplicate protection and operational workflows. Added isolated frontend/JDBC/HTTP regressions and documented real Windows verification without generating application records or changing database schema. Windows persistence remains an operator check. Validation: 105 web tests and 10 isolated backend tests passed; Chromium verified filtering, page reset, legacy/modern responses, reload and error handling without application database writes.


## [2026-10-21] — @codex — Booking runtime diagnostics, owned-account booking and safe retries

- Accept verified customer booking through a server-resolved owned profile/phone, and lock that specific profile during identity capture; preserve existing guest and staff workflows and encrypted identity constraints.
- Separate business conflicts from runtime/configuration failures, retaining redacted 503 references and exposing only support/retry headers across origins. Preserve authoritative SQL creation-time ordering and the existing transactional booking/folio/idempotency write.
- Retain pending booking UUIDs across retry/reload using a request hash without guest data, send the actual guest count, preserve actionable 4xx errors and report uncertain outcomes honestly. Public review GET no longer sends stale implicit credentials; unavailable/empty reviews do not become fabricated testimonials.
- Add isolated HTTP/JWT, ownership and browser transport regressions, and a read-only Windows diagnostic script. Actual Windows startup is blocked by missing original AES configuration; the supplied launcher log is not the real POST exception. Windows database persistence and real Staff/Admin first-row verification remain unverified.
- Validation: all 101 web tests and 17 focused backend tests passed. Chromium exercised the actual booking button, guest count/auth headers, 503 reference, reload-safe replay key/body, actionable 409, 201 receipt and honest review states using isolated HTTP fixtures with no application database writes.


## [2026-10-21] — @codex — Revert Staff sidebar collapse

- Removed the Staff-only sidebar module, its mandatory portal import, state/storage handlers, tooltips, avatar insertion and layout overrides. Restored the pre-change portal stylesheet and sidebar layout; retained unrelated views, authentication and other roles.
- Staff no longer renders the shared collapse control or restores the shared collapse preference. Existing Admin/customer controls are preserved. Portal asset versions were advanced to invalidate the affected cached entrypoint and stylesheet.
- All 95 remaining web tests passed. Chromium verified fresh login/register tabs, login error/retry and success redirection, icons, theme controls, all eight Staff routes, full desktop width despite stale collapse preferences, reload, responsive layouts at 320–1600px, preserved Admin/customer controls and logout. Browser authentication responses were isolated fixtures; Windows/backend authentication was not verified.


## [2026-10-21] — @codex — Collapsible Staff sidebar

- Enhanced the existing Staff sidebar with a keyboard-accessible toggle and a separate persisted collapse preference. Desktop width changes from the existing 248px to 80px; the active item, centered icons, employee avatar and logout remain visible. Vietnamese hover/focus tooltips are positioned outside scrolling navigation so they are not clipped.
- Scoped layout rules to Staff, including its content-specific grid overrides. The main workspace grows when collapsed and room cards fit the available width. Narrow screens retain the existing horizontal navigation, with compact icon items, a visible toggle and avatar/logout controls. Storage failures fall back to in-memory state.
- Retained menu ordering, routing, role resolution and logout code. Staff-only controls/listeners are removed on cleanup; Admin/customer markup is not enhanced. Both deployment copies and portal asset cache versions were updated.
- All 101 web tests passed. Chromium checks covered persistence, all eight Staff views, active states, centered icons, tooltips and keyboard activation, room-card sizing at 320–1920px, short viewports, same-page cleanup, blocked preference storage, dark/light themes and the existing logout flow. Admin layout geometry matched its pre-change baseline. Verification used isolated API fixtures with no application writes; no API or database code changed.

## [2026-10-21] — @codex — Direct account icons and compact landing header

- Replaced the signed-in greeting/dropdown with three equal-sized account, member-portal and logout icons, including escaped-name Vietnamese tooltips and keyboard labels. Account opens the existing profile tab; portal navigation and the logout handler retain their existing behavior. Signed-in actions remain directly visible on mobile rather than being duplicated in its navigation drawer.
- Reused the existing scroll observer at a 100px threshold to reduce header spacing, logo size and slogan height. A border-box resize observer keeps anchor clearance and the existing mobile navigation drawer aligned with the changing header height. Included reduced-motion styles and retained dark/light themes.
- Updated the existing login/logout regression for direct shortcuts. All 95 web tests passed. Chromium checks covered three actions, hover/focus tooltips, profile/portal navigation, logout/reload/storage clearing, escaped long names, language changes, expanded/compact transitions, anchor clearance and responsive layouts from 320px to 1920px without horizontal overflow. Browser API fixtures were isolated to verification; no backend/database files or application records changed.

## [2026-10-21] — @codex — Booking identity persistence and stable list reloads

- Fixed guest booking identity writes that conflicted with the existing `identity_card IS NULL` vault constraint: store through the encrypted vault in the existing transaction, retaining matching hashes and rejecting identity changes.
- Retained the shared DAO's SQL `created_at DESC, id DESC` order, aligned the customer portal's independent query, removed Staff's frontend re-sort, and preserved API order through filters, pagination and reloads. Staff refresh returns to page one; Staff/Admin tab-return events reuse their existing loaders without polling or new push infrastructure.
- Propagated booking-list SQL errors as API failures instead of successful empty lists. Added safe, correlated booking-failure diagnostics and `no-store` booking responses without changing authorization or reservation/folio/idempotency transaction logic.
- Added seven backend and five frontend unit regressions, a read-only Windows API verifier and explicit database/browser/log verification instructions. The full 95-test web suite passed. Real Windows booking creation, database persistence and Staff/Admin first-row confirmation remain unverified pending operator execution; no application database was seeded or modified by these checks.

## [2026-10-21] — @codex — Staff membership directory and point history

- Added staff/admin-only read routes for the complete customer membership directory and each customer's existing `loyalty_histories` ledger, with explicit database-error responses. Stored tiers, point balances and join dates are returned without recalculation or fabricated events.
- Added the staff membership table, name/phone search, tier filters, local pagination, full-directory tier counts and a detail dialog separating cumulative and available points. Legacy/unranked values remain visible separately from Silver/Gold/VIP.
- Retained the existing Silver/Gold/VIP policy cards and all award/redemption/tier logic. Both served copies use the same staff layout, with scoped dark/light and mobile styles, loading/empty/error states and retry actions.
- Added regression coverage for more than 50 members, stored-rank preservation, signed ledger entries, missing data, authorization against spoofed role headers, read-only methods and database failures.

## [2026-10-20] — @codex — Staff customer directory fields and summary alignment

- Included existing email and address values in customer collection and keyword-search responses, retaining the current row limit, ordering and search parameters.
- Normalized snake_case/camelCase customer names and identity numbers in the staff directory so the table, detail dialog, search and contact summaries use the same values.
- Aligned the three summary cards with matching icon columns, value baselines and label spacing. Scoped avatar and contact-cell styles to this staff view; long email/address values can wrap instead of being clipped.
- Added DAO response-field checks and frontend regressions for data normalization, summary counts, searching and detail rendering.


## [2026-10-20] — @codex — `web-ui/portal-admin.css` — Keep customer content within the viewport

- Matched the administrator layout on desktop: the customer portal uses a fixed viewport with independently scrollable content while the header and sidebar remain in place. Restored natural page scrolling on narrow screens.

## [2026-10-20] — @codex — Responsive admin navigation controls

- Kept the administrator brand and theme header in one row, added a persistent manual sidebar collapse, and automatically reduced the vertical navigation to labeled icons in a narrow desktop viewport. The mobile navigation remains horizontal and icon-only.
- Removed the redundant hotel-home shortcut from the portal title bar.
- Removed the shield mark and account avatar from the administrator sidebar while keeping the account details and logout action.

## [2026-10-20] — @codex — Responsive customer navigation

- Matched the administrator navigation breakpoint: vertical customer sidebar above 820px, horizontally scrolling tabs at 820px and below. Kept the single account header and synchronized tab orientation for assistive technology.
- Verified desktop and phone layouts, switching to the profile tab, no horizontal page overflow, and nine portal regression checks.

## [2026-10-20] — @codex — CCCD scanning in the customer's own profile

- Removed the redundant shared title bar on the customer profile tab, retaining the profile form's own heading.
- Placed the member account information card above the profile inputs on desktop and mobile.
- Positioned the scanner after the personal information fields and before the save controls.
- Added local QR-image decoding and QR-reader text input, review-before-apply, clearing scan data, and an optional customer OCR gateway using the existing internal vision service. Bundled jsQR 1.4.0 and its Apache-2.0 license for offline QR decoding.
- Replaced profile access through browser-selected customer IDs and unconditional local success/cache with authenticated own-profile GET/PUT, transactional user/customer updates, encrypted identity writes, and honest API failure feedback. Profile reads expose only whether an identity is saved, and no longer claim identity verification.
- Verified a synthetic QR image in the browser, review/application without automatic persistence, successful profile save, and honest degradation when OCR is not configured. Backend tests cover customer/staff isolation, input validation, ownership, vault encryption, preserving a blank identity and rollback on identity conflicts.

## [2026-10-20] — @codex — Compact customer portal header

- Consolidated the customer account controls into the main navbar, leaving one brand and one row of shorter navigation tabs. Removed duplicate sidebar branding, content eyebrow and repeated return/close links from the customer layout.
- Retained the existing account/logout elements and restored their sidebar placement outside the customer portal. Added compact mobile controls and horizontal navigation scrolling.
- Verified the customer portal using `customer_test` at desktop, 390px and 320px widths; checked that account controls remain within the header and the page does not overflow horizontally.

## [2026-10-20] — @codex — Browser verification of administrator login

- Reproduced valid administrator sessions being cleared by customer-only notification HTTP 401 responses and static scripts being blocked by the shared unauthenticated API rate limit. Scoped lifecycle notifications to customers and excluded static handlers from that API budget while retaining API limits and security headers.
- Corrected the administrator login destination and role-specific page titles; guarded late dashboard responses after navigation; fixed portal JS/CSS asset paths and included the existing administrator stylesheet in the served public tree.
- Verified `admin_main` login through the browser, populated dashboard/rooms/bookings, reload persistence, and no JavaScript console errors in the verified flow. Regression checks: 54 web tests passed; focused static-file and API-budget backend tests passed.

## [2026-10-20] — @codex — Automatic sample import for the local demo

- Updated the demo launcher to import `database/hotel.sql` into a newly generated local schema, excluding server-wide user/grant statements, then apply migrations and encrypt sample identities with persistent demo keys.
- Added unique legacy promotion codes, retained the original sample accounts, and linked the additional demo guest login to an imported customer. Added automatic upgrade from the minimal demo and `-ReloadSample`; normal restarts reuse the saved schema.
- Verified imported counts (15 rooms, 13 customers, 131 bookings, 13 encrypted identities with no plaintext identities) and successful administrator/guest authentication and rooms, services, bookings, customers, promotions, profile and guest-stays HTTP requests.

## [2026-10-20] — @codex — Backend launcher correction

- Added `scripts/start-demo.ps1` and a schema-only Java bootstrap for a separate localhost demo database with persistent keys and synthetic accounts/inventory. Verified live health, both pages, both account logins, and authenticated rooms/services requests.
- Fixed Maven's hardcoded desktop main class so the backend launcher's `-Dexec.mainClass` override takes effect.
- Documented required persistent identity vault keys and the checked-in VM database configuration; added early key validation to the launcher.

## [2026-10-20] — @codex — Phase 4 reports, baseline and changelogs — Implementation evidence and pending release acceptance

- Added [implementation checklist](PHASE_4_IMPLEMENTATION.md), [implementation report](PHASE_4_REPORT.md), and [verification evidence](PHASE_4_VERIFICATION.md); linked current evidence from `PHASE_4_AND_BEYOND_BASELINE.md` and updated the root and Vietnamese documentation changelogs. Historical entries remain unchanged.
- Recorded Phase 4 database bootstrap, transactional service orders/vouchers, signed check-in passes, revenue approval controls, reconciliation, and authentication/payment/booking lifecycle fixes. Removed fabricated booking/payment success and inventory fallbacks.
- Verification: Maven test/package **187 tests, 0 failures, 0 errors, 38 skipped**; web UI **51 passed, 0 failures or skips**; isolated fresh MariaDB **2 integration tests passed**, applying all ten migrations. Skipped tests are not acceptance evidence.
- **Phase 4 release acceptance remains pending:** GATE-01 and GATE-03 passed; GATE-02 passed on a reproducible empty-schema equivalent, with Docker startup unverified; **GATE-04 blocked** by localhost/VM HTTP timeouts and unreachable VM database.
- Outstanding acceptance work: reachable deployed portal/API/webhook/database checks, Docker Compose/entrypoint verification, approved camera/OCR accuracy and timing trials, Python AI tests (`pytest` unavailable), invoice signing/issuer verification, supervised guest pilot, and configured load benchmark. Current PDFs are unsigned internal records.
- Recorded the initial legacy-fixture risk: synthetic rows may have been written to the application schema before disposable-schema guards were added. No prior snapshot exists to identify safe cleanup; no speculative deletion was performed.

## [2026-10-19] — @codex — docs/run-project.md, scripts/start-backend.ps1 — Local backend startup and login configuration
- Added Windows startup instructions, database settings, health verification, and troubleshooting.
- Added a Maven launcher that generates a local JWT secret to prevent token creation failures during login.

## [3.0.0] - 2026-10-17 — @architect — Phase 3 Final Audit, Customer Portal Enhancements, FlatLaf Theme & Go Decision
- **Added:** Web Concierge & Voucher Wallet module (`concierge.js`), Express Check-in QR Pass modal, E-Folio electronic invoice modal, auto-fill for customer bookings, `/api/v1/auth/change-password`, `/api/v1/services`, `/api/v1/service-usage` route registrations, Netplan static IP `192.168.56.102/24` configuration (`deploy/01-netplan-static-ip.yaml`), `DBConnection.java` compatibility wrappers.
- **Fixed:** Complete prevention of UI button freezing across all forms (`#profile-form`, `#password-form`, `#concierge-form`, cancel booking) via `try...catch...finally`; 100% binary synchronization between `web-ui/` and `web-ui/public/` verified with `fc /b`; MariaDB container port `3306:3306` exposure; flexible database configuration in `DatabaseConnection.java` and `config.properties`.
- **Changed:** Full RFC 7807 Problem Details compliance in `GlobalExceptionHandler.java` and `ApiResponseUtil.java` while preserving backward compatibility; FlatLaf Dark Luxury 5★ desktop theme standardization (`Table.rowHeight = 36`, `Button.arc = 12`, `Component.arc = 10`, `TextComponent.arc = 10`, `ProgressBar.arc = 10`, Slate Dark `#0F172A`, Panels `#1E293B`, Gold `#F59E0B`, Light `#F8FAFC`); static asset version parameter `?v=3.5.0` and Nginx `Cache-Control: no-cache, must-revalidate`.
- **Security:** Strict BCrypt + JWT Bearer protection, verified cancellation state guard, physical database isolation.
- **Verification:** 37/37 automated tests passing (**100% Pass Rate, 0 failures**). Official **GO** decision formalized in `docs/PHASE_3_GO_NOGO_ACCEPTANCE.md`.

## [2026-10-16] — @codex — Phase 3 implementation and acceptance evidence
- Synchronized the Phase 3 implementation, final verification evidence and explicit PARTIAL/unreleased status into `vi/CHANGELOG.md`; existing historical entries are preserved.
- Added `phase3-implementation-report.md` with per-task status, approved conflict resolutions, exact test/build evidence and unmeasured gates; Phase 3 is not accepted for release.
- Added `phase3-release-checklist.md` for vault/HMAC cutover, operational secrets/mTLS, model/native/load gates and rollback constraints.
- Added `security/owasp_zap_scan_report.md` as explicitly PENDING scan evidence; no scan or production deployment is claimed.
- Updated `dev-spec/api-contracts.md` with the approved P3 compatibility addendum; recorded approved frozen API compatibility aliases for P3 vision/concierge/revenue, conservative unavailable illustrative revenue, neutral cancellation MONITOR, and management-only low-price override. Original frozen examples remain available for comparison.
- Implemented Phase 3 backend/UI/service foundations: encrypted identity vault with HMAC exact-search index and explicit legacy cutover, OCR/face gateway, mandatory Java-to-AI mTLS, guarded concierge and scoped encrypted ticket chat, versioned rate approvals, retention/anonymization, logout revocation and IP rate limiting. Added Swing check-in/copilot/yield screens and self-contained desktop preview packaging.
- Final Java regression: **162 tests passed, 0 failures, 0 errors, 0 skipped** (32.374s), including the explicitly enabled aggregate benchmark. Python fixture suite: **5 passed**, 1 deprecation warning (0.45s), on Python 3.12; required Python 3.11 container validation remains pending.
- Disposable-database benchmark: 100,000 historical bookings and 730 closed daily snapshots, 20 warmed aggregate reads; final-run p95 **8.455ms**, maximum **10.162ms**. This measures the snapshot aggregate DAO, not a full booking-table scan or the two-hour load gate. Test data was rolled back and the task-created MariaDB instance was shut down.
- Verified local mutual TLS with disposable certificates, including rejection of missing client identity and wrong hostname. Built the final desktop preview with bundled JRE; it was not launched or released. Generated 100 visibly invalid synthetic OCR fixtures and a JMeter draft; neither establishes model accuracy or load acceptance.
- **Phase 3 remains PARTIAL / NOT RELEASED.** Pending gates include approved real-model accuracy/latency/resource measurements, camera/EDT/native UI/RAM checks, Python 3.11 Docker deployment and PKI validation, 200 CCU/two-hour soak, ZAP/manual penetration tests, and outstanding Phase 2 acceptance. Remaining implementation work includes semantic multilingual escalation, complete scan/face capture persistence and key-rotation lifecycle, and correlated/signed load fixtures. No production migration or deployment was performed; guest AI stays disabled by default.


## Rules
- Every change to `/docs/` MUST be logged here before commit.
- Format: `## [YYYY-MM-DD] — @author — file(s) — reason — what changed`
- If a constraint rule is added/modified → note which edge case or incident triggered it.
- If an API endpoint is added/removed → reference the phase (P1/P2/P3).
- Retention: permanent (do not delete old entries).

## [2026-10-16] — @copilot — docs/dev-spec/api-contracts.md, README.md — Phase 3 guest concierge gateway — Document customer JWT/RAG contract, confidence gate, local upstream configuration, and escalation ticket behavior.

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


## [2026-10-14] — @codex — Dev B/C room allocation and desktop room-map files — Phase 1 remaining-task audit and partial implementation
- Added a MariaDB 11 opt-in race test for two room reservations; it requires `HOTEL_RUN_DB_INTEGRATION=true` and a disposable schema whose name ends in `_test`, then checks one success, one conflict, and one version increment.
- Added the fixed 80×60 room tile, 5-column detached map, six canonical status colors, in-memory room/guest/booking filtering, dense 24px booking status renderer, safe detached-window bounds persistence, and an EDT dispatch duration reporter.
- Connected the compact map to `RoomForm`, quick filtering, booking table rendering, and `MainUI` startup. These additions advance B2.1, C2.2, C2.3, C3.3, C4.1, and C4.2; their full WBS definitions of done remain partially unverified.
- Changed classes and focused tests compiled with JDK 17; focused UI tests passed (3 passed). The MariaDB race test was aborted by its environment assumption because no test schema was configured. Maven could not start: the user-level repository lock was unwritable, and the workspace-local retry could not resolve dependencies because Maven Central DNS was unavailable.

## [2026-10-14] — @codex — Dev B/C Phase 1 service completion and integration review
- Added atomic folio payment persistence, master/guest routing and split handling, scheduled 50-record no-show processing, seven-step persisted night-audit checkpoints with resume support, DBR aggregation, credit/refund math, and manager-authorized OOO-room downgrade handling.
- Connected API load failures to the Swing room-map error state, retained the 5-column room map and detached-monitor persistence, and documented the repeatable two-hour front-desk simulation procedure.
- Corrected new booking folios to begin with zero outstanding balance so nightly ledger postings do not duplicate the booking estimate; standardized audit freeze responses on `Retry-After: 300`.
- JDK 17 compiled all production and test Java sources. Focused JUnit run: 6 passed, 0 failed, 2 opt-in MariaDB tests aborted because no disposable MariaDB test schema was configured. `mvn -o test` remains unavailable due an unwritable user-level Maven repository lock. The two-hour simulation and live MariaDB transaction/recovery scenarios still require execution in their target environments.

## [2026-10-15] — @codex — Phase 2 backend, desktop/web UI, migrations, tests, scripts, and documentation — Phase 2 implementation and acceptance status
- Added the guest portal, OTP, public booking, server-authoritative pricing and vouchers, concurrent room allocation, VietQR, HMAC-verified webhooks, idempotent folio settlement, and overpayment handling.
- Added housekeeping and room-state workflows, minibar posting, OOO/OOS maintenance, PDF invoices with explicitly required tax configuration, DBR reporting, and corresponding Swing/web interfaces; included Phase 2 migrations, test doubles, and regression coverage.
- Added the changed-file inventory, required production configuration, implementation/acceptance report, and verification evidence. Synthetic values are limited to test fixtures; no production legal, banking, or OTP values were fabricated.
- The Phase 2 report records 109 automated tests passing with 0 failures and 0 skips on Java 17 and isolated MariaDB 11.4.2. Overall acceptance remains partial pending manual Swing/render-timing checks, Docker verification, and approved production configuration.

## [2026-10-15] — @codex — scripts/validate-phase2-acceptance.py, tests/acceptance/, docs/phase2-manual-acceptance.md, docs/verification/phase2/manual-acceptance.json — Phase 2 Go/No-Go evidence gate
- Added a read-only acceptance validator that reports pending task IDs and rejects missing measurements, skipped/failed automated tests, missing verifier/evidence, and unverified Docker/baseline reconciliation.
- Added four isolated unit checks; retained previously verified browser width/console checks without inventing render or native timing measurements. Overall Phase 2 remains partially accepted.
- Git fetch from origin/PHASE_1 was not executed: automatic approval review was unavailable because its usage limit was reached. No incoming merge/conflict resolution is claimed.

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

## [2026-10-15] — @codex — web-ui/public/app.js, docs/dev-spec/web-ui-unification-review.md, web-ui tests — Role shell and session recovery
- Wired explicit presentation roles into the portal shell; Admin, Customer, and Front Desk retain separate menus, while Housekeeping, Maintenance, and F&B receive a restricted informational shell without Front Desk actions. Unknown roles fail closed.
- Connected expired-session notifications to shell logout and login feedback. Updated the review record for the agreed vanilla/navy/Inter direction and persisted light plus legacy dark modes; versioned-route and ownership backend changes remain unapproved.
- Focused Node suite: 15 passed, 0 failed, 0 skipped. Browser verified login light/dark toggle and persisted preference on reload; role end-to-end flows and target viewport matrix remain outstanding.
- `git fetch origin` could not read `.git/FETCH_HEAD` due environment permission denial; upstream movement was not verified. No merge, push, or backend changes were made.

## [2026-10-15] — @codex — web-ui/public/js/staff/room-map.js, web-ui/public/portal.html, shared-theme.css — Canonical room map and API setup copy
- Reworked the room map into a seven-state, text-labeled status overview using the shared canonical room-status helper, safe DOM text, responsive legend styles, and request-generation guards. Removed legacy click actions that posted client-calculated payments or reset room status to `available`; operational tasks remain in their separate modules.
- Corrected the portal API configuration placeholder and ngrok guidance to use `/api/v1`.
- Browser theme checks passed for light/dark toggle and preference persistence. Full role workflow/viewport acceptance has not been run because authenticated test access is unavailable here; backend checkout/ownership dependencies remain unresolved.
- Reworked Staff checkout to read booking folios from existing APIs, show server balances, settle with UUIDv4 idempotency keys, create configured VietQR and poll server payment status, then require a separate explicit checkout action. No client amount field, external QR account or payment-rule change remains in this screen.
- Added authenticated PDF blob retrieval for Admin and Staff invoices; session 401 handling is shared. Dashboard metrics now show loading/unavailable states rather than false zeros, with retry and theme-token chart styling.
- Re-ran focused suite after integration: 16 passed, 0 failed, 0 skipped. JavaScript syntax and `git diff --check` passed. Authenticated financial workflows still require isolated fixture browser acceptance.
- Elevated `git fetch origin` succeeded after the initial metadata permission failure. `origin/PHASE_1` and local `PHASE_1` remain at 3844597; `PHASE_2` remains 7 commits ahead of `origin/PHASE_2` with 0 incoming commits. No merge or push was performed.
- Generic `STAFF` sessions now fail closed unless the backend supplies a distinct supported department role; label normalization accepts `Front Desk` and `FRONT_DESK` without merging departmental menus.
- Replaced unsupported Staff loyalty discount promises with a shared status notice. Customer dashboard/bookings, account booking discovery, and personal loyalty views now show explicit blockers instead of sending customer-ID-dependent requests or calculating account booking prices locally. The distinct OTP Guest flow and unrelated Customer promotions/chat remain separate.

## [2026-10-15] — @codex — web-ui/public/css/theme-overrides.css — Light-mode readability and mobile floating controls
- Improved light-mode text contrast for muted labels and gold-gradient text, and restored a pale gold logo treatment over the unscrolled dark header.
- Set mobile hotline and chat controls to consistent 48px touch targets with 20px icons.
- Removed the experimental hero and footer gradients after review; no test run recorded for these CSS-only updates.

## [2026-10-15] — @codex — web-ui theme surfaces, docs/WEB_UI_ISSUES.md, docs/CHANGELOG.md, docs/vi/CHANGELOG.md — Global theme and UI issue tracking
- Added one shared Auto/Light/Dark theme control across the hotel landing pages and portal, with browser-preference tracking, persistent cross-page selection, accessible control labels, and matching light palettes; preserved customer and management layouts.
- Added theme behavior tests and recorded browser theme-color updates. Focused Node tests passed (6/6); JavaScript syntax and `git diff --check` passed.
- Created GitHub issues for outstanding UI/integration findings: #36 unsupported social icons, #37 lightbox close control, #38 room identity/pricing, #39 incomplete English localization, #40 missing party/room selectors, #41 admin statistics endpoint, #42 customer dashboard/loyalty loading, and #43 offers loading state.
- Documented the issue references in `docs/WEB_UI_ISSUES.md`. No issues already marked resolved were re-opened; `.phase2-runtime/` remains excluded from version control.
# 2026-10-16 — Room status labels

- Added the optional read-only `include_status=true` availability projection to `dev-spec/api-contracts.md`; existing endpoint behavior remains unchanged by default. No schema or data changes.
