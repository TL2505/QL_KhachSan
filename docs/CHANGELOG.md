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

## [2026-10-13] — @team — src/*, database/hotel.sql, pom.xml — Phase 1 Week 1: Infrastructure, Connection Pooling & Authentication
- **TASK-A1.1 (HikariCP Connection Pool & Fixed Thread Pool)**:
  - Added HikariCP 5.1.0, SLF4J, and `jbcrypt` (0.4) to `pom.xml`.
  - Implemented thread-safe `DatabaseConnection.java` (`maximumPoolSize=20`, `minimumIdle=5`, `connectionTimeout=30000ms`, `leakDetectionThreshold=5000ms`).
  - Configured `Executors.newFixedThreadPool(20)` in `Main.java` to service HTTP requests without thread starvation.
- **TASK-A1.2 (HTTP Router & Regex Path-Variable Matching)**:
  - Created standalone `Router.java` and `RouteEntry.java` supporting regex path variable extraction (e.g., `/api/v1/bookings/{id}`).
  - Structured routing error handling with `404 Not Found` and `405 Method Not Allowed` JSON responses.
- **TASK-A1.3 (Standardized JSON Envelope & Global Exception Pipeline)**:
  - Implemented `ApiResponseUtil.java` and `GlobalExceptionHandler.java` (RFC-7807 problem details) ensuring zero stack-trace leakage.
- **TASK-A1.4 (Stateless JWT Authentication & Login Controller)**:
  - Added endpoint `POST /api/v1/auth/login` (Phase 1).
  - Implemented `AuthController.java`, `JwtFilter.java`, and `SecurityUtil.java` issuing 1-hour expiration HMAC-SHA256 tokens with role-scoped claims (`userId`, `username`, `role`).
- **TASK-B1.1 (Database Schema Migration & Concurrency Columns)**:
  - Updated `database/hotel.sql` to include `version INT NOT NULL DEFAULT 0` on `rooms` table.
  - Added `idempotency_keys`, `folios`, `folio_transactions`, and `audit_checkpoints` tables with clean foreign keys and indexes.
- **TASK-B1.2 & B1.3 (Idempotency Filter & Financial Calculation Engine)**:
  - Implemented `IdempotencyFilter.java` enforcing UUIDv4 header validation and duplicate request suppression.
  - Created `FinancialCalculator.java` ensuring penny-perfect VAT (8%/10%) and 5% luxury service charge calculations using `BigDecimal` (`ROUND_HALF_UP`, scale 2).
- **TASK-D1.1 & D1.3 (Docker Isolation & JUnit 5 Testing Framework)**:
  - Configured `hotel_network` bridge blocking database port 3306 from external host exposure.
  - Established `BaseIntegrationTest.java` baseline and verified clean test runner execution in Maven Surefire.

## [2026-10-13] — @team — src/*, database/hotel.sql, docs/dev-spec/api-contracts.md — Phase 1 Week 2: Core Domain Logic, Optimistic CAS & Folio Ledger
- **TASK-A2.1 (Room Catalog & Dynamic Availability Engine)**:
  - Added endpoints `GET /api/v1/rooms` and `GET /api/v1/rooms/availability` (Phase 1).
  - Implemented date range query filtering (`check_in`, `check_out`) excluding overlapping active bookings (`confirmed`, `checked_in`).
- **TASK-A2.2 (Customer Profile Management & Deduplication)**:
  - Added endpoints `POST /api/v1/customers` and `GET /api/v1/customers?search=...` (Phase 1).
  - Enforced unique identity constraints: duplicate ID card or phone number returns `409 Conflict`.
- **TASK-A2.3 (Booking Lifecycle & Optimistic CAS Concurrency Control)**:
  - Added endpoints `GET /api/v1/bookings`, `GET /api/v1/bookings/{id}`, `POST /api/v1/bookings`, `PUT /api/v1/bookings/{id}`, `POST /api/v1/bookings/{id}/cancel` (Phase 1).
  - Integrated optimistic compare-and-swap concurrency control on room versions, preventing double-booking anomalies under concurrent load.
  - Added stay date modification guardrail preventing reservation overlaps on amended dates.
- **TASK-B2.1 & B2.2 (Atomic Room Allocation & Folio Ledger Engine)**:
  - Enforced atomic room state transitions (`vacant_clean` -> `reserved`) with `OptimisticLockException` mapping to HTTP `409 Conflict`.
  - Implemented `FolioDAOImpl.java` and `POST /api/v1/folios/{id}/pay` for ACID transaction recording of room charges, incidentals, and payments.
- **TASK-D2.1 - D2.3 (Concurrency & Security Test Suites)**:
  - Implemented `ConcurrentRoomAssignmentTest.java` (50 threads competing for 1 room via `CountDownLatch` verifying exactly 1 succeeds and 49 receive 409).
  - Verified role-based access control (`RbacSecurityTest.java`) and HikariCP zero-leak bounds (`HikariConnectionLeakTest.java`).

## [2026-10-14] — @team — src/*, docs/dev-spec/architecture.md, docs/dev-spec/api-contracts.md — Phase 1 Week 3: Night Audit Isolation, Multi-Folio Routing & POS Sync
- **TASK-A3.1 (Night Audit System Freeze & Zero Connection Pool Architecture)**:
  - Implemented `SystemStatusFilter.java` and `SystemStateHolder.java` enforcing 00:00–00:05 Night Audit Lockout.
  - Intercepted all mutating HTTP requests (`POST`, `PUT`, `DELETE`, `PATCH`) with HTTP `503 Service Unavailable` (`Retry-After: 300`) in $< 1\text{ms}$ with zero HikariCP database pool consumption.
  - Configured zero-pool in-memory credential verification (`CREDENTIAL_CACHE`) in `AuthController.java` for front-desk audit staff during the freeze window.
- **TASK-A3.2 (Store-and-Forward POS Integration Sync Receiver)**:
  - Added endpoint `POST /api/v1/pos/sync` (Phase 1).
  - Implemented `PosSyncController.java` and `PosSyncDao.java` ingesting queued offline F&B/Spa charges created during system freeze, posting them to folios under the new business date.
- **TASK-B3.1 (7-Step Night Audit Batch Engine)**:
  - Structured Night Audit pipeline into chunked transaction checkpoints: rate rollover, no-show processing, auxiliary charge rollups, ledger balance reconciliation ($\sum\text{Debits} == \sum\text{Credits} + \text{Receivables}$), Daily Business Report snapshot, and date advancement.
- **TASK-B3.2 (ADR-06 Multi-Folio Routing & Incidentals Bill Splitting)**:
  - Added endpoints `POST /api/v1/folios/{id}/split` and `PUT /api/v1/folios/{id}/routing` (Phase 1).
  - Implemented `FolioRoutingEngine.java` routing room rates and taxes to Master Folio and incidentals (Mini-bar, Dining) to Guest Folio, preventing checkout disputes (Scenario 1).
- **TASK-B3.3 (Out-of-Order Downgrade Handler)**:
  - Added automatic category downgrade recalculation for rooms transitioned to `out_of_order` (Scenario 2).
- **TASK-D3.1 - D3.3 (Scenario & Stress Testing)**:
  - Added automated tests for Scenario 1 (group split check-in), Scenario 3 (offline POS night audit sync), and connection pool stress testing.

## [2026-10-14] — @team — docs/dev-spec/*, pom.xml, scripts/*, src/* — Phase 1 Week 4 Finalization (Dev A & Dev D)
- **TASK-A4.1 (Graceful Shutdown & Pool Teardown)**:
  - Registered JVM shutdown hook in `Main.java` granting 10s drain window for in-flight requests and cleanly closing HikariCP data source.
- **TASK-A4.2 (Gson Streaming & JVM Memory Profiling)**:
  - Created `LocalDateTimeAdapter`, `LocalDateAdapter`, and `BigDecimalAdapter` inheriting from `com.google.gson.TypeAdapter`.
  - Registered streaming adapters into `JsonUtil.java` to eliminate scientific notation (e.g. `1.8E6` -> `1800000.00`) and ensure exact decimal precision for financial ledgers.
  - Added `JsonThroughputMemoryTest.java` verifying heap profiling and microbenchmark throughput (> 90,000 ops/s).
- **TASK-A4.3 (OpenAPI 3.0 Contract Freeze & Parity Verification)**:
  - Published complete OpenAPI 3.0 specification in `docs/dev-spec/openapi-p1.json` covering all 10 Phase 1 Core Endpoints.
  - Updated `docs/dev-spec/api-contracts.md` with complete request/response schemas, Room Status RBAC Transitions, AI Gateway Guardrails, and RFC-7807 error envelopes.
  - Implemented `OpenApiContractParityTest.java` enforcing 100% parity between runtime routes, schemas, and OpenAPI contract.
- **TASK-B4.1 - B4.3 (DBR Metrics & Financial Boundary Edge Cases)**:
  - Implemented Daily Business Report aggregator calculating standard hospitality metrics (ADR, RevPAR, Occupancy Rate).
  - Executed financial edge-case settlement tests verifying penny-perfect balances.
- **TASK-D4.1 (OWASP DevSecOps Hardening & Production Posture)**:
  - Integrated `dependency-check-maven` 9.0.9 in `pom.xml` with `failBuildOnCVSS: 7.0`.
  - Configured DevSecOps pipeline `.github/workflows/security.yml` with Aqua Security Trivy container scanning.
  - Created `owasp-suppressions.xml` to suppress non-production desktop GUI false positives.
- **TASK-D4.2 (50-CCU 30-Minute Soak Test Scenario)**:
  - Created JMeter 5.6.3 test plan `src/test/resources/load-test/phase1-soak-test.jmx` (50 threads, 1,800s duration, across 10 core endpoints).
  - Implemented headless automation runner `scripts/run-soak-test.sh` with embedded percentile parser enforcing SLAs (P95 < 30ms, P99 < 50ms, Error rate = 0.00%, CPU < 25%).
- **TASK-D4.3 (Automated Deployment & Deep Healthcheck Verification)**:
  - Created hardened multi-stage `Dockerfile` (Alpine JRE 17, non-root `appuser`, tuned G1GC flags).
  - Hardened `docker-compose.yml` with MariaDB 11.2, network isolation, and healthchecks.
  - Implemented `scripts/deploy-phase1.sh` (one-click deployment under 90s) and `scripts/healthcheck.sh` (deep health validation).

## [2026-10-15] — @architect — docs/audits/phase1-go-nogo-certificate.md, docs/dev-spec/*, src/* — Phase 1 Architectural Re-Audit & Codebase Freeze
- **Audit Findings Remediation**:
  - **Issue 1 (Future Availability CAS Contention)**: In `BookingDAOImpl.java`, updated future stay booking to `UPDATE rooms SET version = version + 1 WHERE id = ? AND status != 'out_of_order'` (omitting `AND version = ?`), eliminating false 409 Conflict across non-overlapping months while preserving schedule integrity via `overlapSql`. Verified via Test 12 in `Week2DevAIntegrationTest`.
  - **Issue 2 (ADR-06 Immutable Ledger Compliance)**: In `FolioRoutingEngine.java` and `FolioDAOImpl.java`, eradicated direct `UPDATE folios SET balance = 0.00`. Implemented append-only offsetting reversal credit transactions (`REV-xxx`, amount = `-balance`) into `folio_transactions`, strictly maintaining $\text{balance} \equiv \sum \text{charges} - \sum \text{payments} == 0.00$. Verified via `EdgeCasesOperationalGovernanceTest`.
  - **Issue 3 (Testing Taxonomy Decoupling)**: Clarified distinction in `docs/audits/phase1-go-nogo-certificate.md` between the 108-test automated JUnit 5 regression suite (~71s execution) and the TASK-D4.2 Staging Soak Test operational benchmark harness (30 minutes, 50 CCUs).
  - **Issue 4 (Lock Ordering Paradox in cancelBooking)**: Standardized monotonic write lock hierarchy (`rooms` -> `bookings` -> `folios`) in `BookingDAOImpl.cancelBooking` using non-locking MVCC consistent snapshot read for `booking_id` lookup, eliminating MySQL Deadlock 1213 risk.
  - **Issue 5 (Docker MariaDB Account Alignment)**: Enhanced `DatabaseConnection.java` to support priority evaluation of `DB_USER` / `MYSQL_USER` and `DB_PASSWORD` / `MYSQL_PASSWORD` with container-aware fallbacks (`hotel_user` / `hotel_pass`), guaranteeing zero Error 1045 Access Denied in containerized environments.
  - **Temporal Boundary Fix**: Aligned `DEFAULT_INITIAL_DATE` in `SystemStateHolder.java` to `LocalDate.now()` to prevent midnight boundary date divergence in automated test suites.
- **Specification & Documentation Synchronization**:
  - **`docs/dev-spec/architecture.md`**: Formally codified Section 4 (Night Audit Database Isolation & Zero Connection Pool Architecture) and Section 5 (Real-World Domain Governance, Decoupled Room Availability vs Physical Status, and Ledger Invariants).
  - **`docs/dev-spec/api-contracts.md`**: Fully aligned specifications for Endpoints 5 through 12, Room Status RBAC Transitions, and AI Gateway Guardrails.
  - **`docs/dev-spec/openapi-p1.json`**: Sealed OpenAPI 3.0 schema matching runtime routes, DTOs, and RFC-7807 error envelopes.
- **Official Sealing**: Published `docs/audits/phase1-go-nogo-certificate.md` certifying 10/10 PASS criteria, 108/108 passing tests, and declaring Phase 1 officially frozen and sealed under tag `v1.0.0-phase1-sealed`.


