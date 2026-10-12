# PHASE 1 WORK BREAKDOWN STRUCTURE (WBS) & TECHNICAL EXECUTION PLAN
**Project:** 5★ Luxury Hotel PMS Platform (QL_KhachSan_main — 200 Rooms, Ho Chi Minh City, Vietnam)  
**Engineering Team:** 4 Developers (Dev A, B, C, D) | **Duration:** 4 Weeks (Phase 1: Core PMS & Desktop Integration)  
**Architectural Baseline:** Pure Java 17 (`com.sun.net.httpserver`), HikariCP 5.1.0 (`DatabaseConnection.java`), MariaDB 11 (Isolated Docker Network), Java Swing + FlatLaf 3.4.1.

---

## I. ARCHITECTURAL OVERVIEW & TECHNICAL CONSTRAINTS

```
+---------------------------------------------------------------------------------------------------+
|                                  PHYSICAL HOST (UBUNTU 22.04 LTS)                                 |
|                                                                                                   |
|  [Staff Desktop Terminal]                                                                         |
|   Java Swing + FlatLaf (JDK 17) - StaffForm.java, RoomForm.java, BookingForm.java                 |
|   - Designed for 8-hour Front Desk & Housekeeping shifts (Speed > Aesthetics)                      |
|   - High-density tables (24px/row), Room Grid Map 5×N Matrix (80×60px/cell)                        |
|   - java.net.http.HttpClient + SwingWorker (Never blocks Event Dispatch Thread)                   |
|           |                                                                                       |
|           | HTTP/1.1 (JSON) + Bearer JWT + Idempotency-Key (UUIDv4)                               |
|           v (Port 8081 exposed to internal hotel network)                                         |
|  +---------------------------------------------------------------------------------------------+  |
|  | DOCKER INTERNAL BRIDGE NETWORK: hotel_network (172.28.0.0/16)                               |  |
|  |                                                                                             |  |
|  |  +---------------------------------------+      +----------------------------------------+  |  |
|  |  | PMS Backend Core (app)                |      | MariaDB 11 (db)                        |  |  |
|  |  | - Native com.sun.net.httpserver        |      | - Port 3306 NOT exposed to Host/LAN    |  |  |
|  |  | - Executors.newFixedThreadPool(20)    | JDBC | - InnoDB Optimistic Lock (rooms.version|  |  |
|  |  | - HikariCP 5.1.0 (Pool max 20 conns)  |----->| - InnoDB Buffer Pool Memory            |  |  |
|  |  |   Timeout: 30s | Leak: 5000ms         |      | - Physical network isolation           |  |  |
|  |  | - System Freeze Lockout (00:00-00:05) |      +----------------------------------------+  |  |
|  |  +---------------------------------------+                                                   |  |
|  +---------------------------------------------------------------------------------------------+  |
+---------------------------------------------------------------------------------------------------+
```

---

## II. GRANULAR WEEKLY TASK DECOMPOSITION (WEEKS 1 – 4)

---

### WEEK 1: INFRASTRUCTURE, CONNECTION POOLING & AUTHENTICATION

#### Dev A — Backend API & Infrastructure
* **TASK-A1.1: Initialize HikariCP Connection Pool & Fixed Thread Pool**
  * **Target Files:** `src/quanlykhachsan/backend/utils/DatabaseConnection.java`, `src/quanlykhachsan/backend/Main.java`, `pom.xml`.
  * **Description:** Add HikariCP 5.1.0, SLF4J (`slf4j-api`, `slf4j-simple`), and `jbcrypt` (0.4) to `pom.xml`. Implement `DatabaseConnection.java` as a thread-safe Singleton: `maximumPoolSize=20`, `minimumIdle=5`, `idleTimeout=300000`, `connectionTimeout=30000` (30s), `leakDetectionThreshold=5000` (5s). Configure `server.setExecutor(Executors.newFixedThreadPool(20))` in `Main.java`.
  * **DoD:** Connection acquisition $< 5\text{ms}$. Under 50 concurrent threads, 0 leak warnings. Connections automatically return to pool on `try-with-resources` exit.
* **TASK-A1.2: HTTP Router & Regex Path-Variable Matching**
  * **Target Files:** `src/quanlykhachsan/backend/utils/Router.java`, `src/quanlykhachsan/backend/utils/RouteEntry.java`, `src/quanlykhachsan/backend/Main.java`.
  * **Description:** Build standalone Router resolving dynamic regex path variables (supporting `/api/v1/bookings/{id}`, `/api/v1/rooms/{id}`) and dispatching HTTP methods `GET`, `POST`, `PUT`, `DELETE`.
  * **DoD:** Extracts path variables into `Map<String, String>`. Returns `404 Not Found` for unmapped paths and `405 Method Not Allowed` for unhandled verbs formatted in standard JSON envelope.
* **TASK-A1.3: Standardized JSON Envelope & Global Exception Pipeline**
  * **Target Files:** `src/quanlykhachsan/backend/utils/ApiResponseUtil.java`, `src/quanlykhachsan/backend/utils/GlobalExceptionHandler.java`.
  * **Description:** Use Gson to serialize responses: Success `{"data": {...}, "meta": {...}}`; Error `{"error_code": "ERR_...", "message": "...", "timestamp": "..."}`. Catch uncaught exceptions and convert to HTTP `500` with 0 stack trace leakage.
  * **DoD:** Unit tests achieve 100% pass rate for status codes 400, 401, 403, 404, 409, 500 per `docs/dev-spec/api-contracts.md`.
* **TASK-A1.4: JWT Authentication Filter & Login Controller**
  * **Target Files:** `src/quanlykhachsan/backend/auth/AuthController.java`, `src/quanlykhachsan/backend/utils/JwtFilter.java`, `src/quanlykhachsan/backend/utils/SecurityUtil.java`.
  * **Description:** Implement `POST /api/v1/auth/login`. Verify user credentials from `users` table via `BCrypt`. Issue JJWT token (1-hour expiry) with claims: `userId`, `username`, `role`. `JwtFilter.java` verifies `Authorization: Bearer <token>` and binds claims to request context.
  * **DoD:** Valid credentials return `200 OK` with token; invalid credentials return `401 Unauthorized`. Unauthenticated requests to protected endpoints are rejected immediately with 401.

#### Dev B — Core Business Logic & Financials
* **TASK-B1.1: Database Schema Migration for Versioning & Idempotency**
  * **Target Files:** `database/hotel.sql`, `src/quanlykhachsan/backend/utils/UpdateDbUtility.java`.
  * **Description:** Add `version INT NOT NULL DEFAULT 0` column to `rooms` table. Create `idempotency_keys` table (`id VARCHAR(64) PRIMARY KEY, request_hash VARCHAR(64), response_body LONGTEXT, status_code INT, created_at DATETIME)` along with `folios`, `folio_transactions`, and `audit_checkpoints` tables.
  * **DoD:** SQL script runs clean on MariaDB 11 without FK errors. Indexes created on `rooms(room_number, version)` and `idempotency_keys(created_at)`.
* **TASK-B1.2: Idempotency Interceptor & Deduplication Engine**
  * **Target Files:** `src/quanlykhachsan/backend/utils/IdempotencyFilter.java`, `src/quanlykhachsan/backend/booking/IdempotencyService.java`.
  * **Description:** Intercept `POST` requests to `/api/v1/bookings` and `/api/v1/folios/{id}/pay`. Mandate `Idempotency-Key` (UUIDv4) header. If key exists, return stored response with `200/201`. If new, lock and commit on completion.
  * **DoD:** Sending 2 duplicate requests with same `Idempotency-Key` creates exactly 1 DB record; both requests receive identical successful response.
* **TASK-B1.3: Accurate VAT (8%/10%) & Service Charge (5%) Calculation Engine**
  * **Target Files:** `src/quanlykhachsan/backend/booking/FinancialCalculator.java`, `src/quanlykhachsan/backend/booking/TaxConfig.java`.
  * **Description:** Calculate all financial values using `BigDecimal` (`ROUND_HALF_UP`, scale 2). Accurately compute 8%/10% VAT, 5% luxury service charge, and surcharges without floating point drift.
  * **DoD:** 20 unit tests confirm: $\text{Room Fee} + \text{Service Fee} + \text{VAT} - \text{Discount} == \text{Total Amount}$ with exactly 0.00 penny difference.

#### Dev C — Desktop Frontend (Java Swing)
* **TASK-C1.1: Asynchronous HttpClient Core & SwingWorker Wrapper**
  * **Target Files:** `src/quanlykhachsan/frontend/api/ApiClient.java`, `src/quanlykhachsan/frontend/api/ApiCallback.java`.
  * **Description:** Build central API client wrapping `java.net.http.HttpClient` (`connectTimeout=3000ms`, `requestTimeout=5000ms`). Auto-attach `Authorization: Bearer <token>` and `Content-Type: application/json`. Return responses to UI via `SwingWorker`.
  * **DoD:** Zero network I/O executes on Event Dispatch Thread (EDT). Simulated 3s network latency maintains fluid UI with loading indicator.
* **TASK-C1.2: Global Hardware Shortcut Listener (F1–F10)**
  * **Target Files:** `src/quanlykhachsan/frontend/utils/GlobalKeyDispatcher.java`, `src/quanlykhachsan/frontend/view/StaffForm.java`.
  * **Description:** Register `KeyEventDispatcher` to `KeyboardFocusManager` capturing F1–F10 globally: `F1` (Help), `F2` (Check-in), `F3` (Check-out), `F4` (Quick Search), `F5` (Refresh), `F8` (Open Folio), `F9` (New Booking).
  * **DoD:** Pressing `F2` opens Check-in modal regardless of focus state. `Esc` immediately closes active modal.
* **TASK-C1.3: Synchronize Theme Tokens & FlatLaf Standardization**
  * **Target Files:** `src/quanlykhachsan/frontend/utils/UIThemeTokens.java`, `src/quanlykhachsan/frontend/view/StaffForm.java`.
  * **Description:** Centralize color tokens from `ui-tokens.md`: Primary `#1B2A4A`, Success `#2E7D32`, Warning `#F57C00`, Error `#C62828`. Configure FlatLaf: `Table.rowHeight = 24`, `Component.arc = 4`.
  * **DoD:** Application runs fluidly under `FlatLightLaf`. Tables render exactly 24px/row, 0 hardcoded hex colors in views.

#### Dev D — DevOps & QA / Testing
* **TASK-D1.1: Docker Bridge Network Isolation & MariaDB Hardening**
  * **Target Files:** `docker-compose.yml`, `Dockerfile`.
  * **Description:** Create `hotel_network` bridge. Block ports 3306 and 5432 from host/LAN. Only publish ports 8081 (Backend) and 80 (Nginx). Add MariaDB healthcheck via `mysqladmin ping -h localhost`.
  * **DoD:** `docker-compose up` runs cleanly. Host `nmap -p 3306 localhost` returns `closed`/`filtered`. Backend connects to DB via DNS `db:3306`.
* **TASK-D1.2: Mock API Server for Frontend Unblocking**
  * **Target Files:** `src/test/resources/mock-server/`, `src/test/java/quanlykhachsan/stubs/MockApiServer.java`.
  * **Description:** Spin up lightweight HTTP server serving 10 Core Endpoints matching `api-contracts.md` within 48h of Week 1.
  * **DoD:** Dev C points `ApiClient` to `http://localhost:8089` to construct Room and Booking views without waiting for Dev A backend.
* **TASK-D1.3: JUnit 5 Integration Test Framework Setup**
  * **Target Files:** `pom.xml`, `src/test/java/quanlykhachsan/BaseIntegrationTest.java`.
  * **Description:** Configure Maven Surefire and Failsafe. Implement `BaseIntegrationTest` initializing schema and HTTP request utilities.
  * **DoD:** `mvn test` in CI pipeline runs 100% of baseline test cases.

---

### WEEK 2: CORE DOMAIN LOGIC, OPTIMISTIC LOCKING & SWING ROOM MATRIX

#### Dev A — Backend API & Infrastructure
* **TASK-A2.1: Room Catalog & Dynamic Availability Engine**
  * **Target Files:** `src/quanlykhachsan/backend/room/RoomController.java`, `src/quanlykhachsan/backend/room/RoomDao.java`.
  * **Endpoints:** `GET /api/v1/rooms`, `GET /api/v1/rooms/availability`.
  * **Description:** Return 200 rooms with canonical statuses (`vacant_clean`, `vacant_dirty`, `occupied_clean`, `occupied_dirty`, `out_of_order`, `reserved`) and `version` number. Support date filter `?check_in=...&check_out=...`.
  * **DoD:** Query execution $< 15\text{ms}$ on 200 rooms via `idx_booking_dates` and `idx_room_status`.
* **TASK-A2.2: Customer Profile Management & Fast Lookup**
  * **Target Files:** `src/quanlykhachsan/backend/customer/CustomerController.java`, `src/quanlykhachsan/backend/customer/CustomerDao.java`.
  * **Endpoints:** `POST /api/v1/customers`, `GET /api/v1/customers?search=...`.
  * **Description:** Ingest customer identity data (ID Card/Passport, Phone, Email). Prevent duplicate identity numbers. Fast search by keyword.
  * **DoD:** Duplicate ID card returns `409 Conflict`. Search responds in $< 20\text{ms}$ via `idx_customer_identity`.
* **TASK-A2.3: Booking Lifecycle & Optimistic Locking Integration**
  * **Target Files:** `src/quanlykhachsan/backend/booking/BookingController.java`, `src/quanlykhachsan/backend/booking/BookingDao.java`.
  * **Endpoints:** `GET /api/v1/bookings`, `GET /api/v1/bookings/{id}`, `POST /api/v1/bookings`, `PUT /api/v1/bookings/{id}`, `POST /api/v1/bookings/{id}/cancel`.
  * **Description:** Booking creation specifying `room_id` and expected `room_version`. Transition room from `vacant_clean` to `reserved`.
  * **DoD:** Check-in date $\ge$ check-out date returns `400 Bad Request`. Outdated room version throws exception and returns `409 Conflict`.

#### Dev B — Core Business Logic & Financials
* **TASK-B2.1: Optimistic Locking Room Allocation Engine**
  * **Target Files:** `src/quanlykhachsan/backend/room/RoomAllocationService.java`.
  * **Description:** Enforce atomic room reservation update with strict version checking:
    ```sql
    UPDATE rooms SET status = 'reserved', version = version + 1 
    WHERE id = ? AND version = ? AND status = 'vacant_clean';
    ```
    If `rowsUpdated == 0`, throw `OptimisticLockException` mapped to HTTP `409 Conflict` with message `"Room state changed by another concurrent transaction"`.
  * **DoD:** Test with 2 concurrent threads competing for the same room ensures exactly 1 gets `200/201` and 1 gets `409 Conflict`. Version increment is exactly 1.
* **TASK-B2.2: Folio Ledger & Atomic Payment Processing**
  * **Target Files:** `src/quanlykhachsan/backend/booking/FolioService.java`, `src/quanlykhachsan/backend/booking/FolioDao.java`.
  * **Endpoints:** `POST /api/v1/folios/{id}/pay`.
  * **Description:** Initialize primary booking folio. Record debits (room fee, services) and credits (deposit, payments). Enforce ACID payment transaction (`setAutoCommit(false)` $\rightarrow$ insert payment $\rightarrow$ update balance $\rightarrow$ `commit()`).
  * **DoD:** Post-payment folio balance 100% accurate. Mid-transaction failures automatically roll back with 0 dangling records.
* **TASK-B2.3: No-Show Detection & Automatic Penalty Job**
  * **Target Files:** `src/quanlykhachsan/backend/booking/NoShowEngine.java`.
  * **Description:** Scan confirmed bookings where guest did not check in past cutoff time. Mark as `no_show`, post 1-night room charge penalty to folio, and release room to `vacant_dirty`.
  * **DoD:** Batch processing of 50 overdue bookings executes atomically, writing audit penalty logs to ledger.

#### Dev C — Desktop Frontend (Java Swing)
* **TASK-C2.1: Purge Mock Data & Connect Real APIs to Swing Forms**
  * **Target Files:** `src/quanlykhachsan/frontend/view/RoomForm.java`, `src/quanlykhachsan/frontend/view/BookingForm.java`.
  * **Description:** Remove all hardcoded vectors/arrays. Connect `RoomAPI` and `BookingAPI` to fetch real data from backend into `DefaultTableModel`.
  * **DoD:** 100% of data rendered in Swing forms directly reflects MariaDB database state.
* **TASK-C2.2: Room Grid Map 5×N Matrix (80×60px) with Semantic Tokens**
  * **Target Files:** `src/quanlykhachsan/frontend/view/RoomGridPanel.java`, `src/quanlykhachsan/frontend/view/RoomCellComponent.java`.
  * **Description:** Render 200 rooms in scrollable 5-column grid. Cell size exactly 80×60px. Room number bold 12px, guest name 12px (if occupied). Palette adheres to `ui-tokens.md`: VC (`#4CAF50`), VD (`#FF9800`), OC (`#2196F3`), OD (`#FF5722`), OOO (`#F44336`), RESERVED (`#9C27B0`).
  * **DoD:** Grid paints 200 rooms in $< 50\text{ms}$. Single click updates right 300px panel; double click opens Folio (`F8`).
* **TASK-C2.3: High-Density Table Renderer (24px Row Height)**
  * **Target Files:** `src/quanlykhachsan/frontend/utils/DenseTableRenderer.java`, `src/quanlykhachsan/frontend/view/BookingForm.java`.
  * **Description:** Implement custom `TableCellRenderer`: fixed row height 24px; zebra striping (`#FFFFFF` & `#F5F5F5`); 8px status indicator circle next to text.
  * **DoD:** At least 15 visible rows without vertical scrolling on $1280\times 720\text{px}$. Header sort responds in $< 10\text{ms}$.

#### Dev D — DevOps & QA / Testing
* **TASK-D2.1: Automated Concurrency Test for Scenario 5 (50 Threads on `POST /bookings`)**
  * **Target Files:** `src/test/java/quanlykhachsan/concurrency/ConcurrentRoomAssignmentTest.java`.
  * **Description:** Use `CountDownLatch` to trigger 50 concurrent threads submitting `POST /api/v1/bookings` targeting the same vacant room at the exact same millisecond.
  * **DoD:** Exactly 1 request receives `200/201 OK`; 49 receive `409 Conflict`. Room version increments by 1; zero double-booking occurs.
* **TASK-D2.2: RBAC Matrix Security Verification**
  * **Target Files:** `src/test/java/quanlykhachsan/security/RbacSecurityTest.java`.
  * **Description:** Automated test suite exercising all endpoints using role-scoped JWTs: `FrontDesk`, `Housekeeping`, `F&B`, `Admin`.
  * **DoD:** Housekeeping accessing payment endpoint receives `403 Forbidden`. Front Desk accessing user admin endpoint receives `403`. Admin accesses all.
* **TASK-D2.3: HikariCP Connection Leak Test**
  * **Target Files:** `src/test/java/quanlykhachsan/database/HikariConnectionLeakTest.java`.
  * **Description:** Send 200 randomized consecutive requests; inspect `HikariPoolMXBean.getActiveConnections()`.
  * **DoD:** Active connections return to 0 post-test. Zero leak warnings logged under 5000ms threshold.

---

### WEEK 3: FINANCIAL ENGINE, FOLIO ROUTING, NIGHT AUDIT & EDGE CASES

#### Dev A — Backend API & Infrastructure
* **TASK-A3.1: System Freeze Middleware (00:00 – 00:05 Lockout)**
  * **Target Files:** `src/quanlykhachsan/backend/utils/SystemStatusFilter.java`, `src/quanlykhachsan/backend/system/SystemStateHolder.java`.
  * **Description:** Implement `volatile boolean isSystemFrozen`. During Night Audit, all mutating requests (`POST`, `PUT`, `DELETE`) are blocked at Filter level returning HTTP `503 Service Unavailable` with `Retry-After: 300`. `GET` reads remain operational.
  * **DoD:** Write requests during freeze return 503 in $< 1\text{ms}$ without consuming HikariCP database connections.
* **TASK-A3.2: Store-and-Forward POS Integration Sync Receiver**
  * **Target Files:** `src/quanlykhachsan/backend/booking/PosSyncController.java`, `src/quanlykhachsan/backend/booking/PosSyncDao.java`.
  * **Endpoints:** `POST /api/v1/pos/sync`.
  * **Description:** Receive batches of offline POS F&B/Spa charges created during system freeze. Verify idempotency keys; post charges to folios under the **new business date**.
  * **DoD:** Scenario 3: Charges created at 00:02 synced at 00:10 are posted to the new business date, leaving closed date intact.
* **TASK-A3.3: Router Regex & Query Parameter Parser Optimization**
  * **Target Files:** `src/quanlykhachsan/backend/utils/Router.java`, `src/quanlykhachsan/backend/utils/HttpExchangeHelper.java`.
  * **Description:** Pre-compile regex patterns for Router. Safely decode query parameters and `Idempotency-Key` / `Authorization` headers.
  * **DoD:** Average routing dispatch latency per request drops below 0.5ms.

#### Dev B — Core Business Logic & Financials
* **TASK-B3.1: 7-Step Night Audit Batch Engine via Checkpoint Chunking**
  * **Target Files:** `src/quanlykhachsan/backend/system/NightAuditEngine.java`, `src/quanlykhachsan/backend/system/NightAuditStep.java`.
  * **Description:** Decompose 7-step audit into independent chunked transaction boundaries with state recorded in `audit_checkpoints` to avoid MariaDB Undo Log bloat and lock timeouts:
    * Step 1: Initiate Freeze (`SystemStateHolder.setFrozen(true)`).
    * Step 2: Roll Room Rates (chunked 50 rooms/transaction, post room charge + tax).
    * Step 3: Process No-Shows (transition statuses and post penalties).
    * Step 4: Auxiliary Transactions Rollup (close and reconcile POS/service postings).
    * Step 5: Ledger Balance Reconciliation ($\sum\text{Debits} == \sum\text{Credits} + \text{Receivables}$).
    * Step 6: Create Daily Business Report (DBR) Snapshot.
    * Step 7: Advance Hotel Date & Unfreeze (`SystemStateHolder.setFrozen(false)`).
  * **DoD:** Full audit executes in $< 45\text{s}$ for 200 rooms. Errors record checkpoint failure state, allowing clean restart from breakpoint without pool starvation.
* **TASK-B3.2: Multi-Folio Routing & Bill Splitting (Scenario 1)**
  * **Target Files:** `src/quanlykhachsan/backend/booking/FolioRoutingEngine.java`.
  * **Endpoints:** `POST /api/v1/folios/{id}/split`, `PUT /api/v1/folios/{id}/routing`.
  * **Description:** Support split folio routing for group reservations: `FOLIO-MASTER` (covers room rates & taxes); `FOLIO-GUEST` (covers incidentals: Mini-bar, Laundry).
  * **DoD:** Incidental charges auto-route exclusively to `FOLIO-GUEST`. Settling Master Folio blocks closure if guest incidentals remain unpaid.
* **TASK-B3.3: Late Arrival Downgrade Handler for OOO Rooms (Scenario 2)**
  * **Target Files:** `src/quanlykhachsan/backend/booking/RoomDowngradeService.java`.
  * **Description:** When reserved room goes `out_of_order`, allow manager override to assign lower category, recalculating folio room rate to lower tier automatically.
  * **DoD:** Assigning OOO room returns 409 Conflict; successful downgrade recalculates total folio price.

#### Dev C — Desktop Frontend (Java Swing)
* **TASK-C3.1: Check-in / Check-out Wizard Dialogs & Real-time Folio View**
  * **Target Files:** `src/quanlykhachsan/frontend/view/BookingWizardDialog.java`, `src/quanlykhachsan/frontend/view/RoomActionDialog.java`, `src/quanlykhachsan/frontend/view/PaymentForm.java`.
  * **Description:** Connect Check-in (`F2`) and Check-out (`F3`) to real backend. Render split folio balances (Master vs Guest) clearly.
  * **DoD:** Settle & Check-out dispatches payment with UUIDv4 `Idempotency-Key`. On 200 OK, changes room to `vacant_dirty` and refreshes map.
* **TASK-C3.2: System Freeze Warning Banner & POS Store-and-Forward Simulator**
  * **Target Files:** `src/quanlykhachsan/frontend/view/SystemStatusBanner.java`, `src/quanlykhachsan/frontend/view/OfflinePosDialog.java`.
  * **Description:** On 503 response during Night Audit, render amber warning banner: `"Night Audit in progress (00:00 - 00:05). Mutations temporarily locked."`. Provide offline queue dialog.
  * **DoD:** UI disables mutation buttons during freeze. Queued offline items auto-sync post-unfreeze.
* **TASK-C3.3: Fast Search Bar & Live Filtering (F4)**
  * **Target Files:** `src/quanlykhachsan/frontend/view/QuickFilterBar.java`, `src/quanlykhachsan/frontend/view/StaffForm.java`.
  * **Description:** `F4` focuses search input. Live-filter by Room Number, Guest Name, Booking ID with 300ms debounce.
  * **DoD:** Typing "501" filters room grid in $< 10\text{ms}$. Clearing input restores full 200-room layout.

#### Dev D — DevOps & QA / Testing
* **TASK-D3.1: Automated Scenario 1 Test (Group Split Check-in)**
  * **Target Files:** `src/test/java/quanlykhachsan/scenarios/GroupSplitCheckinTest.java`.
  * **Description:** Automated test: Book 20 rooms. Check in 5 early arrivals independently. Confirm 5 rooms become `occupied_clean`, 15 remain `reserved`. Verify incidentals route to Guest Folio.
  * **DoD:** 100% of Scenario 1 assertions pass in $< 3$ seconds.
* **TASK-D3.2: Automated Scenario 3 Test (POS Offline during Night Audit)**
  * **Target Files:** `src/test/java/quanlykhachsan/scenarios/PosOfflineNightAuditTest.java`.
  * **Description:** Simulate POS charge at 00:02 (frozen) receiving 503 `Retry-After: 300`. Simulate resubmission at 00:06 recorded under new date.
  * **DoD:** All 3 assertions in `docs/dev-spec/test-scenarios.md` pass.
* **TASK-D3.3: HikariCP Stress & Leak Audit (50 Connections, 500 req/s)**
  * **Target Files:** `src/test/java/quanlykhachsan/performance/HikariStressTest.java`.
  * **Description:** Fire 500 req/s load for 5 minutes against room availability and booking endpoints. Monitor connections via JMX MBean.
  * **DoD:** Zero leak warnings under `leakDetectionThreshold=5000ms`. Max wait time to acquire connection $< 20\text{ms}$.

---

### WEEK 4: FULL INTEGRATION, STRESS TESTING, SECURITY AUDIT & SPEC FREEZE

#### Dev A — Backend API & Infrastructure
* **TASK-A4.1: Graceful Shutdown Hook & Safe HikariCP Teardown**
  * **Target Files:** `src/quanlykhachsan/backend/Main.java`, `src/quanlykhachsan/backend/utils/DatabaseConnection.java`.
  * **Description:** Register `Runtime.getRuntime().addShutdownHook()`. On `SIGTERM`, allow in-flight requests 10 seconds to finish, then terminate `HttpServer` and close `HikariDataSource`.
  * **DoD:** `docker stop` logs clean container exit with 0 aborted transactions in DB.
* **TASK-A4.2: Optimize Gson TypeAdapters & JVM Memory Profiling**
  * **Target Files:** `src/quanlykhachsan/backend/utils/JsonUtil.java`, `src/quanlykhachsan/backend/utils/LocalDateTimeAdapter.java`.
  * **Description:** Register custom TypeAdapters for `LocalDateTime` and `BigDecimal`. Use VisualVM to inspect heap memory and eliminate GC pressure.
  * **DoD:** JSON serialization throughput > 5,000 ops/s per worker thread; stable heap post-Full GC.
* **TASK-A4.3: OpenAPI 3.0 Contract Freeze**
  * **Target Files:** `docs/dev-spec/api-contracts.md`, `docs/dev-spec/openapi-p1.json`.
  * **Description:** Publish final OpenAPI 3.0 specification for 10 Phase 1 Core Endpoints. Verify all error codes, DTOs, and headers match runtime code 100%.
  * **DoD:** Automated validation script confirms 0 discrepancies between spec and actual API responses.

#### Dev B — Core Business Logic & Financials
* **TASK-B4.1: Daily Business Report (DBR) Ledger Aggregator (ADR & RevPAR)**
  * **Target Files:** `src/quanlykhachsan/backend/report/DbrReportService.java`, `src/quanlykhachsan/backend/report/ReportDao.java`.
  * **Description:** Aggregate nightly figures into `daily_business_reports`: Total Revenue, Room Revenue, Occupancy Rate, ADR, RevPAR.
  * **DoD:** Metric calculations strictly follow hospitality industry standards:
    $$\text{ADR} = \frac{\text{Room Revenue}}{\text{Occupied Rooms}}, \quad \text{RevPAR} = \frac{\text{Room Revenue}}{\text{Total Available Rooms}}$$
    Penny-perfect balance verified against raw transaction ledger.
* **TASK-B4.2: Disaster Recovery & Rollback Handler for Night Audit**
  * **Target Files:** `src/quanlykhachsan/backend/system/NightAuditRollbackHandler.java`.
  * **Description:** Simulate connection crash during Step 4. Ensure recovery reads `audit_checkpoints` to roll back partial data and restore pre-audit state.
  * **DoD:** Mid-process failure recovery leaves DB clean; rerun does not double-charge rooms.
* **TASK-B4.3: Financial Boundary Edge-Case Tests (Split Bill, Refunds)**
  * **Target Files:** `src/test/java/quanlykhachsan/financial/FinancialEdgeCaseTest.java`.
  * **Description:** Test complex settlement: Split across 3 cards, credit adjustments, FX rounding.
  * **DoD:** Final checkout folio balance equals exactly $0.00$.

#### Dev C — Desktop Frontend (Java Swing)
* **TASK-C4.1: Swing Event Dispatch Thread Performance Audit (0 EDT Lock > 16ms)**
  * **Target Files:** `src/quanlykhachsan/frontend/utils/EdtViolationDetector.java`, `src/quanlykhachsan/frontend/view/StaffForm.java`.
  * **Description:** Add EDT execution timer. Warn on any UI task blocking the thread longer than 16ms (60 FPS threshold).
  * **DoD:** Heavy continuous interactions (tab switches, scrolling 200 rooms, modals) produce 0 EDT lock warnings > 16ms.
* **TASK-C4.2: Multi-Monitor Detached Room Grid Window**
  * **Target Files:** `src/quanlykhachsan/frontend/view/DetachedRoomMapFrame.java`, `src/quanlykhachsan/frontend/view/StaffForm.java`.
  * **Description:** Support detaching Room Grid Map into independent `JFrame` on secondary monitor. Save X, Y, dimensions, and monitor index in `.properties` on exit.
  * **DoD:** Opening app on dual-monitor setup automatically opens main window on Monitor 1 and detached grid on Monitor 2.
* **TASK-C4.3: 2-Hour Front Desk Simulation & Keyboard Shortcut Polish**
  * **Target Files:** Entire `src/quanlykhachsan/frontend/view/` package.
  * **Description:** Simulate 2-hour front desk shift: Check-in 30 guests, check-out 20 guests, 3 room moves, 40 service charges. Polish tab navigation order and focus rings.
  * **DoD:** Front Desk agent executes full check-in flow in $< 15$ seconds using keyboard shortcuts only (`F2` $\rightarrow$ `Tab` $\rightarrow$ `F4` $\rightarrow$ `Enter`).

#### Dev D — DevOps & QA / Testing
* **TASK-D4.1: OWASP Security Vulnerability Scan & Production Hardening**
  * **Target Files:** `pom.xml`, `.github/workflows/security.yml`.
  * **Description:** Run OWASP Dependency-Check and container image scan via Trivy. Audit SQL Injection, permission boundaries, and secrets.
  * **DoD:** 0 Critical and 0 High severity CVEs in dependencies and containers.
* **TASK-D4.2: 50-Concurrent User Soak Test (30 Minutes)**
  * **Target Files:** `src/test/resources/load-test/phase1-soak-test.jmx`.
  * **Description:** Simulate 50 front desk/housekeeping agents interacting with 10 Core Endpoints over 30 minutes.
  * **DoD:**
    * P95 Response Time $< 30\text{ms}$; P99 Response Time $< 50\text{ms}$.
    * Error rate $= 0.00\%$ (excluding intentional 409 test cases).
    * Host CPU $< 25\%$; stable memory footprint (no leaks).
* **TASK-D4.3: Automated Deployment Script & Healthcheck Verification**
  * **Target Files:** `docker-compose.yml`, `scripts/deploy-phase1.sh`, `scripts/healthcheck.sh`.
  * **Description:** Automated script deploying stack from scratch on Ubuntu 22.04 LTS: network setup, volumes, SQL schema, containers, and health validation.
  * **DoD:** Running `./deploy-phase1.sh` completes with all containers healthy in $< 90$ seconds.

---

## III. BẢNG MA TRẬN PHỤ THUỘC & BÀN GIAO CHÉO (CROSS-DEVELOPER DEPENDENCY MATRIX)

| Upstream Task | Chủ sở hữu | Downstream Task | Bên tiêu thụ | Thành phần bàn giao / Interface | Hạn chót (SLA) | Phương án dự phòng (Decoupling) |
|---|:---:|---|:---:|---|:---:|---|
| **TASK-A1.3 / A1.4** | **Dev A** | **TASK-C1.1** | **Dev C** | OpenAPI JSON Spec & DTO Auth/Booking | **W1 Ngày 2, 17:00** | Dev D cung cấp `MockApiServer` (TASK-D1.2) để Dev C code UI không phải chờ backend. |
| **TASK-B1.1** | **Dev B** | **TASK-A2.1 / A2.3** | **Dev A** | SQL Migration có `rooms.version` và `idempotency_keys` | **W1 Ngày 3, 12:00** | Dev A định nghĩa DTO và truy vấn trên schema stub cục bộ cho đến khi SQL được commit. |
| **TASK-B2.1** | **Dev B** | **TASK-A2.3** | **Dev A** | Service gán phòng ném `OptimisticLockException` | **W2 Ngày 2, 17:00** | Dev A định nghĩa Interface Java, ném ngoại lệ giả lập để hoàn thiện Controller. |
| **TASK-A2.1 / A2.3** | **Dev A** | **TASK-C2.1 / C2.2** | **Dev C** | API chạy thực tế tại `:8081/api/v1/rooms` và `/bookings` | **W2 Ngày 4, 12:00** | Dev C kiểm thử trên `MockApiServer`, chỉ đổi base URL sang `:8081` khi backend sẵn sàng. |
| **TASK-A3.1** | **Dev A** | **TASK-B3.1** | **Dev B** | Cờ `SystemStateHolder.isSystemFrozen` và Filter 503 | **W3 Ngày 2, 12:00** | Dev B dùng cờ `volatile boolean` cục bộ trong unit test trước khi tích hợp vào Filter. |
| **TASK-B3.1** | **Dev B** | **TASK-D3.2** | **Dev D** | API kích hoạt Night Audit thủ công & Event log hooks | **W3 Ngày 4, 12:00** | Dev D viết trước script test với mock response, sau đó trỏ sang endpoint thật. |
| **TASK-C2.2** | **Dev C** | **TASK-C4.2** | **Dev C** | `RoomGridPanel` sẵn sàng cho việc tách Frame | **W4 Ngày 1, 12:00** | Nhiệm vụ nội bộ của Dev C; khung cửa sổ được dựng độc lập với logic vẽ lưới phòng. |

---

## IV. BẢNG TIÊU CHÍ NGHIỆM THU MỐC GO/NO-GO CUỐI TUẦN 4

Hệ thống phải đạt **100% các tiêu chí dưới đây** trước khi được phép chuyển sang Phase 2 (AI Gateway & Guardrails). Bất kỳ một tiêu chí nào không đạt đều kích hoạt quyết định "NO-GO":

### 1. Tính toàn vẹn chức năng & 10 Core Endpoints
* [ ] **Hoàn thiện đủ 10 Core REST Endpoints:** `POST /auth/login` (cấp JWT hợp lệ), `GET /rooms`, `GET /rooms/availability`, `POST /customers`, `GET /bookings`, `GET /bookings/{id}`, `POST /bookings`, `PUT /bookings/{id}`, `POST /bookings/{id}/cancel`, `POST /folios/{id}/pay` vượt qua 100% các ca kiểm thử tích hợp.
* [ ] **Kịch bản 1 (Check-in đoàn phân tách Master/Guest Folio):** Cho phép 5 phòng đến sớm nhận phòng độc lập; tiền phòng đổ về `FOLIO-MASTER`, chi phí dịch vụ cá nhân đổ về `FOLIO-GUEST`.
* [ ] **Kịch bản 2 (Hạ hạng phòng do sự cố OOO):** Cấm check-in vào phòng OOO (báo mã 409); ghi nhận quyền can thiệp của quản lý gán phòng hạng dưới và tự động điều chỉnh giá trên Folio.
* [ ] **Kịch bản 3 (POS ngoại tuyến trong kỳ Night Audit):** Giao dịch phát sinh lúc 00:00–00:05 nhận mã `503 Retry-After: 300`. Giao dịch đồng bộ lại sau khi mở khóa được ghi nhận chuẩn xác vào ngày kinh doanh mới.

### 2. Tải đồng thời cao & Khóa lạc quan (Optimistic Locking)
* [ ] **Kịch bản 5 (Xung đột gán phòng qua `POST /bookings`):** 50 luồng đồng thời tranh chấp gán cùng 1 phòng: đúng 1 luồng nhận mã thành công `200/201`, 49 luồng còn lại nhận `409 Conflict`. Cột `version` của phòng tăng đúng 1 lần. Tuyệt đối không xảy ra đặt trùng.
* [ ] **Thực thi Idempotency:** Gửi lại request tạo đặt phòng hoặc thanh toán trùng lặp mang cùng `Idempotency-Key` (UUIDv4) trả về nguyên vẹn kết quả cũ từ cache/DB, không sinh thêm dòng dữ liệu mới.

### 3. Động cơ tài chính & Kiểm toán đêm Chunking
* [ ] **Night Audit 7 bước hoàn tất trong $< 45\text{s}$:** Hệ thống tự động đóng băng các request ghi trong 5 phút (00:00–00:05). Thực hiện chia nhỏ giao dịch theo khối phòng kết hợp ghi nhận `audit_checkpoints`.
* [ ] **Cân bằng sổ cái tuyệt đối:** $\sum\text{Bút toán Nợ} == \sum\text{Bút toán Có} + \text{Công nợ}$ với độ lệch bằng đúng $0.00$. Báo cáo DBR tổng hợp chuẩn xác các chỉ số ADR và RevPAR.
* [ ] **Phục hồi sau thảm họa (Disaster Recovery):** Cố tình ngắt kết nối giữa chừng ở Bước 4: hệ thống rollback an toàn dựa trên checkpoint, không làm sai lệch số liệu tài chính khi chạy lại.

### 4. Quản lý kết nối HikariCP & Hiệu năng
* [ ] **Kiểm soát rò rỉ kết nối HikariCP tuyệt đối:** Pool cố định 20 kết nối, `connectionTimeout=30000ms`, `leakDetectionThreshold=5000ms`. Chạy liên tục 10,000 request tải cao: số kết nối active quay về 0 khi kết thúc test, không phát sinh log cảnh báo rò rỉ giả.
* [ ] **Cam kết độ trễ (SLA):** P95 Response Time $< 30\text{ms}$, P99 Response Time $< 50\text{ms}$ trên toàn bộ các endpoint đọc dưới tải 50 luồng đồng thời.
* [ ] **Tính ổn định của Native HttpServer:** `com.sun.net.httpserver` phối hợp cùng `Executors.newFixedThreadPool(20)` duy trì ổn định mức tải 500 req/s mà không bị tràn bộ nhớ hay nghẽn luồng.

### 5. Trải nghiệm ứng dụng Desktop Swing FlatLaf
* [ ] **Loại bỏ 100% Mock Data:** Toàn bộ giao diện Desktop chạy trực tiếp trên API Backend thực tế.
* [ ] **Độ phản hồi của giao diện (0 EDT Lock > 16ms):** Toàn bộ tương tác mạng đều chạy qua `SwingWorker` hoặc `HttpClient` bất đồng bộ, bảo đảm tốc độ khung hình mượt mà 60 FPS.
* [ ] **Tuân thủ chặt chẽ Tokens UI:** Sơ đồ phòng hiển thị 200 phòng (5 cột × 40 hàng, ô $80\times 60\text{px}$) với các mã màu chuẩn xác tại `ui-tokens.md`. Bảng dữ liệu có chiều cao dòng chính xác $24\text{px}$.
* [ ] **Phím tắt phần cứng (F1–F10):** Hoạt động nhạy bén trên toàn hệ thống, không bị kẹt focus. Thao tác Check-in hoàn tất trong $< 15$ giây hoàn toàn bằng bàn phím.

### 6. An toàn hạ tầng & An ninh mạng
* [ ] **Cách ly mạng vật lý:** Cổng 3306 (MariaDB) và cổng 5432 (pgvector) hoàn toàn không bị lộ ra máy chủ host hoặc mạng LAN, chỉ có thể giao tiếp trong cụm mạng nội bộ Docker.
* [ ] **Quét mã độc & Lỗ hổng:** 0 lỗ hổng bảo mật mức độ Critical và High từ báo cáo quét OWASP Dependency-Check.
* [ ] **Khả năng triển khai:** Khởi động hệ thống mới từ script `./deploy-phase1.sh` đạt trạng thái healthy và sẵn sàng phục vụ trong $< 90$ giây.

---

## V. BIÊN BẢN KÝ DUYỆT NGHIỆM THU

| Chức danh | Chữ ký / Trạng thái | Ngày phê duyệt |
|---|:---:|:---:|
| **Technical Lead (Kiến trúc trưởng)** | `[ PHÊ DUYỆT THÔNG QUA BẢN HIỆU CHỈNH ]` | `2026-10-12` |
| **QA & DevOps Lead (Trưởng nhóm kiểm thử)** | `[ XÁC NHẬN BỘ TEST HARNESS ]` | `2026-10-12` |
| **Core PMS Backend Lead (Dev A & B)** | `[ CAM KẾT TIẾN ĐỘ WBS HIỆU CHỈNH ]` | `2026-10-12` |
| **Desktop Frontend Lead (Dev C)** | `[ XÁC NHẬN GIAO DIỆN KHÔNG PHỤ THUỘC ]` | `2026-10-12` |
