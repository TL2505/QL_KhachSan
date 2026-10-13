# PHASE 2 WORK BREAKDOWN STRUCTURE (WBS) & TECHNICAL EXECUTION PLAN
**Project:** 5★ Luxury Hotel PMS & AI Platform (`QL_KhachSan` — 200 Rooms, Ho Chi Minh City, Vietnam)  
**Engineering Team:** 4 Developers (Dev A, B, C, D) | **Duration:** 4 Weeks (Phase 2: Weeks 5–8)  
**Architectural Baseline:** Pure Java 17 (`com.sun.net.httpserver`), HikariCP 5.1.0 (`DatabaseConnection.java`), MariaDB 11 (Isolated Docker Network), Java Swing + FlatLaf 3.4.1, Pure Web Booking Engine (Vanilla HTML5/JS/CSS, 0 NPM).

---

## I. ARCHITECTURAL OVERVIEW & PHASE 2 BOUNDARY CONSTRAINTS

```
+---------------------------------------------------------------------------------------------------+
|                                  PHYSICAL HOST (UBUNTU 22.04 LTS)                                 |
|                                                                                                   |
|  [Staff Desktop Terminal]                    [Guest Web Client (Mobile/Desktop)]                  |
|   Java Swing + FlatLaf 3.4.1 (JDK 17)         Vanilla HTML5 / ES6+ / CSS3 (Zero NPM)              |
|   - Room Grid 5xN (80x60px), 24px DataTables  - Responsive Booking Engine, Guest OTP Auth         |
|   - Walk-in Wizard, VietQR Dialog, Minibar    - Dynamic VietQR Display, Voucher Real-time Quote   |
|   - EDT latency < 16ms via SwingWorker        - Direct Static Fetch from Java 8081 /booking/*     |
|           |                                           |                                           |
|           | HTTP/1.1 + Bearer JWT + Idempotency-Key   | HTTP/1.1 + CORS Preflight (OPTIONS 204)   |
|           v                                           v                                           |
|  +---------------------------------------------------------------------------------------------+  |
|  | DOCKER INTERNAL BRIDGE NETWORK: hotel_network (172.28.0.0/16)                               |  |
|  |                                                                                             |  |
|  |  +---------------------------------------------------------------------------------------+  |  |
|  |  | PMS Backend Core (app :8081)                                                          |  |  |
|  |  | - Native com.sun.net.httpserver (Executors.newFixedThreadPool(20))                     |  |  |
|  |  | - StaticFileHandler: Serves web-ui/public/ on /booking/* and / with MIME resolution    |  |  |
|  |  | - CorsFilter: Preflight OPTIONS 204 (Access-Control-Allow-Headers: Idempotency-Key...) |  |  |
|  |  | - JwtFilter: PUBLIC_PATHS Whitelist (/auth/*, /rooms/availability, /pricing/quote...)  |  |  |
|  |  | - VietQR Engine: EMVCo Napas 247 Dynamic QR + CRC16-CCITT Kermit                      |  |  |
|  |  | - Bank Webhook Controller: HMAC-SHA256 Signature Verification (X-Signature)           |  |  |
|  |  | - Housekeeping State Machine & RBAC Enforcer (Forbidden dirty -> clean for FrontDesk)|  |  |
|  |  | - Dynamic Pricing Engine: Occupancy-based BAR multiplier (<50%: 1.0x -> >85%: 1.5x)   |  |  |
|  |  | - Electronic VAT Invoicing: iTextPDF Circular 78/2021/TT-BTC with Unicode TrueType    |  |  |
|  |  +-------------------------------------------+-------------------------------------------+  |  |
|  |                                              | JDBC Connection Pool                         |  |
|  |                                              v (Pool: 20 max, 50 stress)                    |  |
|  |  +---------------------------------------------------------------------------------------+  |  |
|  |  | MariaDB 11 Database Engine (db :3306 - Physical Isolation, NOT exposed to Host/LAN)   |  |  |
|  |  | - Optimistic Locking on rooms.version via CAS SQL                                      |  |  |
|  |  | - Folio ID Parity: id (BIGINT PK) vs code (VARCHAR UNIQUE, e.g. FOLIO-MASTER-101)     |  |  |
|  |  | - Folio Accounting Immutability: CHECK (balance >= 0.00); excess -> credit_adjustment |  |  |
|  |  | - Bank Webhook Deduplication: bank_webhook_logs (bank_trans_id UNIQUE)                 |  |  |
|  |  +---------------------------------------------------------------------------------------+  |  |
|  +---------------------------------------------------------------------------------------------+  |
+---------------------------------------------------------------------------------------------------+
```

---

## II. GRANULAR WEEKLY TASK DECOMPOSITION (WEEKS 5 – 8)

---

### WEEK 5: CUSTOMER PORTAL, PUBLIC BOOKING ENGINE & WALK-IN CONCURRENCY

#### Dev A — Core API & Backend Systems
* **TASK-A5.1: Static File Handler for Web Booking Engine**
  * **Module:** HTTP Infrastructure & Asset Serving
  * **Assignee:** Dev A
  * **Dependencies (DAG):** None
  * **Target Deliverables:** `src/quanlykhachsan/backend/utils/StaticFileHandler.java`, `src/quanlykhachsan/backend/Main.java`.
  * **Definition of Done:** Implements `HttpHandler` resolving requests to `/booking/*` and static assets under `web-ui/public/`. Accurately resolves MIME types (`.html` -> `text/html; charset=UTF-8`, `.css` -> `text/css`, `.js` -> `application/javascript`, `.png` -> `image/png`, `.svg` -> `image/svg+xml`). Protects against Path Traversal (`..` attacks return `403 Forbidden`). 404 handler returns clean fallback for missing assets.
  * **Verification Method:** `curl -I http://localhost:8081/booking/index.html` returns `HTTP/1.1 200 OK` with `Content-Type: text/html; charset=UTF-8`. `curl -I http://localhost:8081/booking/../../etc/passwd` returns `HTTP/1.1 403 Forbidden`.

* **TASK-A5.2: CORS Preflight & Whitelist Filter Configuration**
  * **Module:** Security & Gateway Filters
  * **Assignee:** Dev A
  * **Dependencies (DAG):** None
  * **Target Deliverables:** `src/quanlykhachsan/backend/utils/CorsFilter.java`, `src/quanlykhachsan/backend/utils/JwtFilter.java`.
  * **Definition of Done:** `CorsFilter` intercepts `OPTIONS` requests returning HTTP `204 No Content` with headers: `Access-Control-Allow-Origin: *`, `Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS`, `Access-Control-Allow-Headers: Content-Type, Authorization, Idempotency-Key, X-Signature`. `JwtFilter` implements `PUBLIC_PATHS = {"/api/v1/auth/login", "/api/v1/auth/guest-otp", "/api/v1/rooms/availability", "/api/v1/pricing/quote", "/api/v1/bookings/public", "/booking"}` allowing unauthenticated access while verifying Bearer tokens for all protected resources.
  * **Verification Method:** `curl -X OPTIONS http://localhost:8081/api/v1/bookings/public -H "Access-Control-Request-Headers: Idempotency-Key" -I` returns `204 No Content`. `curl http://localhost:8081/api/v1/rooms/availability` succeeds without `Authorization` header.

* **TASK-A5.3: Public Guest OTP Generation & Verification Controller**
  * **Module:** Customer Authentication
  * **Assignee:** Dev A
  * **Dependencies (DAG):** TASK-B5.1
  * **Target Deliverables:** `src/quanlykhachsan/backend/auth/GuestAuthController.java`, `src/quanlykhachsan/backend/auth/GuestAuthService.java`.
  * **Definition of Done:** Exposes `POST /api/v1/auth/guest-otp/request` and `POST /api/v1/auth/guest-otp/verify`. Request payload validates phone/email format; generates cryptographically secure 6-digit OTP stored with 5-minute TTL. Verification validates OTP code against DB, issues guest-scoped JJWT token (`role: "guest"`, 24-hour expiration) containing `phone` and `customer_id`.
  * **Verification Method:** Run automated integration test `mvn test -Dtest=GuestAuthIntegrationTest`. Verify expired OTP returns `400 Bad Request` and valid OTP returns `200 OK` with JJWT string.

* **TASK-A5.4: Public Online Booking Submission Controller**
  * **Module:** Booking Engine API
  * **Assignee:** Dev A
  * **Dependencies (DAG):** TASK-A5.2, TASK-B5.2
  * **Target Deliverables:** `src/quanlykhachsan/backend/booking/PublicBookingController.java`.
  * **Definition of Done:** Exposes `POST /api/v1/bookings/public`. Mandates `Idempotency-Key` (UUIDv4) header. Validates guest JWT or guest contact payload in body. Delegates room allocation to `RoomAllocationService` specifying expected `room_version`. On success returns `201 Created` with booking code, assigned room number, and initial folio ID. On version mismatch returns `409 Conflict`.
  * **Verification Method:** `curl -X POST http://localhost:8081/api/v1/bookings/public -H "Idempotency-Key: $(uuidgen)" -H "Content-Type: application/json" -d '{"customer_name":"Nguyen Van A","phone":"0901234567","room_id":101,"room_version":0,"check_in_date":"2026-11-01 14:00:00","check_out_date":"2026-11-03 12:00:00"}'` returns `201 Created`.

#### Dev B — Data, Finance & Domain Logic
* **TASK-B5.1: Database Schema Migration for Guest OTP & Web Channels**
  * **Module:** Database Schema & Migration
  * **Assignee:** Dev B
  * **Dependencies (DAG):** None
  * **Target Deliverables:** `database/migrations/v2_0_week5_public_portal.sql`, `src/quanlykhachsan/backend/utils/UpdateDbUtility.java`.
  * **Definition of Done:** Creates `guest_otps` table (`id BIGINT AUTO_INCREMENT PRIMARY KEY, phone VARCHAR(20) NOT NULL, email VARCHAR(100), otp_code VARCHAR(6) NOT NULL, expires_at DATETIME NOT NULL, consumed_at DATETIME NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, INDEX idx_otp_phone_code (phone, otp_code, expires_at))`. Alters `bookings.customer_type` ENUM to include `'Web'`. Adds migration script runner to `UpdateDbUtility.java`.
  * **Verification Method:** Execute `mvn test -Dtest=DatabaseMigrationTest`. Inspect MariaDB schema; verify `guest_otps` table exists and `SHOW COLUMNS FROM bookings LIKE 'customer_type'` contains `'Web'`.

* **TASK-B5.2: Dual-Channel Inventory Allocation & Race Condition Defense**
  * **Module:** Room Inventory Domain Logic
  * **Assignee:** Dev B
  * **Dependencies (DAG):** TASK-B5.1
  * **Target Deliverables:** `src/quanlykhachsan/backend/room/RoomAllocationService.java`.
  * **Definition of Done:** Implements CAS optimistic reservation method:
    ```sql
    UPDATE rooms SET status = 'reserved', version = version + 1 
    WHERE id = ? AND version = ? AND status = 'vacant_clean';
    ```
    If `rowsAffected == 0`, re-queries room status: if status is not `vacant_clean`, throws `RoomUnavailableException` (HTTP 409); if version changed, throws `OptimisticLockException` (HTTP 409). Guarantees thread-safety when Web Booking and Walk-in channels compete for the same physical room.
  * **Verification Method:** Execute unit test `mvn test -Dtest=RoomAllocationServiceTest`. Verify CAS update increments room version by exactly 1 and rejects outdated version.

* **TASK-B5.3: Guest Profile Ingestion & CRM Deduplication Engine**
  * **Module:** Customer CRM Domain Logic
  * **Assignee:** Dev B
  * **Dependencies (DAG):** TASK-B5.1
  * **Target Deliverables:** `src/quanlykhachsan/backend/customer/GuestIngestionService.java`, `src/quanlykhachsan/backend/customer/CustomerDao.java`.
  * **Definition of Done:** Ingests guest identity from web bookings. Checks if normalized phone (`090...` or `+8490...`) exists in `customers` table. If exists, links existing `customer_id`; if new, creates customer record with `loyalty_level='Member'` and `total_loyalty_points=0`. Thread-safe insertion prevents duplicate customer records under concurrent bookings with same phone.
  * **Verification Method:** Run `mvn test -Dtest=GuestIngestionDeduplicationTest`. Send 10 concurrent booking calls with identical phone number; exactly 1 customer row is created in `customers`.

#### Dev C — Desktop UI & Customer Web UI
* **TASK-C5.1: Pure Web Booking Engine Foundation (Vanilla HTML5/JS/CSS)**
  * **Module:** Public Web Booking Engine
  * **Assignee:** Dev C
  * **Dependencies (DAG):** None (Consumes TASK-D5.1 Mock API)
  * **Target Deliverables:** `web-ui/public/index.html`, `web-ui/public/css/booking.css`, `web-ui/public/js/booking-core.js`.
  * **Definition of Done:** Implements 100% pure vanilla JavaScript (ES6+), HTML5, and CSS3 (0 NPM dependencies, 0 build tools). Responsive layout (375px mobile to 1920px desktop). Date-range picker for Check-in / Check-out, room category selector, live room pricing preview. Interacts with `/api/v1/rooms/availability` via native `fetch()` API.
  * **Verification Method:** Open `http://localhost:8081/booking/index.html` in Chrome & Mobile Device Viewfinder (375x667). 0 console errors logged; renders room cards within 200ms.

* **TASK-C5.2: Web Booking Engine OTP Modal & Instant Reservation Flow**
  * **Module:** Public Web Booking Engine
  * **Assignee:** Dev C
  * **Dependencies (DAG):** TASK-C5.1, TASK-D5.1
  * **Target Deliverables:** `web-ui/public/js/guest-otp.js`, `web-ui/public/js/booking-api.js`.
  * **Definition of Done:** Modal for OTP authentication: 6 input digit blocks with auto-focus advance, backspace navigation, and 60-second countdown resend timer. Generates RFC4122 UUIDv4 for `Idempotency-Key` header stored in `sessionStorage`. Submits payload to `POST /api/v1/bookings/public`, storing JWT in `sessionStorage` upon verification.
  * **Verification Method:** Manual/Headless test via Chrome DevTools: Enter phone `0901234567`, receive mock OTP `123456`, confirm booking; inspect network tab verifying `Idempotency-Key: <uuid>` is attached to `POST /api/v1/bookings/public`.

* **TASK-C5.3: Front Desk Fast Walk-in Check-in Wizard in Swing**
  * **Module:** Desktop Front Desk UI
  * **Assignee:** Dev C
  * **Dependencies (DAG):** None
  * **Target Deliverables:** `src/quanlykhachsan/frontend/view/WalkInWizardDialog.java`, `src/quanlykhachsan/frontend/view/StaffForm.java`.
  * **Definition of Done:** Front Desk shortcut `Ctrl+W` opens Walk-in Wizard modal dialog. Automatically filters and highlights current `vacant_clean` rooms. Collects Guest Full Name, Identity Card/Passport, Phone. Executes booking + room allocation in single atomic step via `SwingWorker` (<16ms EDT latency). Immediately updates Room Grid Map to `occupied_clean`.
  * **Verification Method:** Launch `MainUI.java`. Press `Ctrl+W`, complete walk-in form, click "Xác nhận nhận phòng". Room 101 changes color from green (`#4CAF50`) to blue (`#2196F3`) in < 200ms.

#### Dev D — DevOps, QA Automation & AI Subsystem
* **TASK-D5.1: Day-1 Mock API Server Extensions for Week 5 Endpoints**
  * **Module:** Test Harness & Mock Server
  * **Assignee:** Dev D
  * **Dependencies (DAG):** None
  * **Target Deliverables:** `src/test/java/quanlykhachsan/stubs/MockApiServer.java`, `src/test/resources/mock-server/mock-guest-otp.json`, `src/test/resources/mock-server/mock-public-booking.json`.
  * **Definition of Done:** Expands lightweight HTTP Mock Server (port 8089) by Day 1 12:00. Stubs: `POST /api/v1/auth/guest-otp/request` (returns 200 `{status: "OTP_SENT"}`), `POST /api/v1/auth/guest-otp/verify` (returns 200 `{token: "mock-guest-jwt"}`), `POST /api/v1/bookings/public` (returns 201 `{booking_id: 999, room_number: "101"}`).
  * **Verification Method:** Execute `curl -X POST http://localhost:8089/api/v1/auth/guest-otp/verify -H "Content-Type: application/json" -d '{"phone":"0901234567","otp":"123456"}'` returning valid mock JSON envelope.

* **TASK-D5.2: Automated Concurrency Test: Scenario 9 (Web vs Walk-in Race Condition)**
  * **Module:** Concurrency QA Automation
  * **Assignee:** Dev D
  * **Dependencies (DAG):** TASK-A5.4, TASK-B5.2
  * **Target Deliverables:** `src/test/java/quanlykhachsan/scenarios/Scenario9WebVsWalkInRaceTest.java`.
  * **Definition of Done:** JUnit 5 test utilizing `CountDownLatch(20)`: 10 threads simulating Web Booking calls and 10 threads simulating Front Desk Walk-in calls simultaneously targeting the exact last available room (Room 205, version 0).
  * **Verification Method:** Run `mvn test -Dtest=Scenario9WebVsWalkInRaceTest`. Assertions: Exactly 1 thread receives HTTP `200/201`; remaining 19 threads receive HTTP `409 Conflict`. Room 205 `version` in MariaDB equals exactly 1. Total bookings created for Room 205 equals 1.

* **TASK-D5.3: Automated CORS Preflight & Static Web Asset Integration Test**
  * **Module:** Security QA Automation
  * **Assignee:** Dev D
  * **Dependencies (DAG):** TASK-A5.1, TASK-A5.2
  * **Target Deliverables:** `src/test/java/quanlykhachsan/security/CorsAndStaticServingTest.java`.
  * **Definition of Done:** Automated test asserting:
    1. `OPTIONS /api/v1/bookings/public` returns 204 with `Access-Control-Allow-Headers` containing `Idempotency-Key`.
    2. `GET /booking/index.html` returns 200 with HTML content.
    3. Path traversal requests `GET /booking/../pom.xml` return 403 Forbidden.
  * **Verification Method:** Run `mvn test -Dtest=CorsAndStaticServingTest`. All 5 assertions pass in < 1500ms.

---

### WEEK 6: DYNAMIC VIETQR PAYMENT GATEWAY & IDEMPOTENT RECONCILIATION

#### Dev A — Core API & Backend Systems
* **TASK-A6.1: Dynamic VietQR Generation Controller**
  * **Module:** Financial Payment Gateway
  * **Assignee:** Dev A
  * **Dependencies (DAG):** TASK-B6.2
  * **Target Deliverables:** `src/quanlykhachsan/backend/booking/VietQrController.java`.
  * **Definition of Done:** Implements `GET /api/v1/folios/{id}/vietqr`. Locates folio by `id` (or `code`); verifies folio is open; fetches pending balance. Invokes `VietQrGenerator` to construct EMVCo Napas 247 payload and Base64-encoded PNG image (300x300px). Returns JSON envelope with `qr_code_string`, `qr_image_base64`, `amount`, and `reference_code`.
  * **Verification Method:** `curl http://localhost:8081/api/v1/folios/1/vietqr -H "Authorization: Bearer <token>"` returns 200 with non-empty `qr_code_string` starting with `00020101021238` and valid Base64 PNG.

* **TASK-A6.2: Bank Webhook Ingestion Controller & HMAC Verification**
  * **Module:** Bank Webhook Gateway
  * **Assignee:** Dev A
  * **Dependencies (DAG):** TASK-B6.1, TASK-B6.3
  * **Target Deliverables:** `src/quanlykhachsan/backend/booking/BankWebhookController.java`, `src/quanlykhachsan/backend/utils/HmacSignatureUtil.java`.
  * **Definition of Done:** Implements `POST /api/v1/webhooks/vietqr`. Reads raw HTTP body. Verifies header `X-Signature: hex(HMAC_SHA256(bank_secret, raw_body))`. If signature is invalid or missing, immediately returns `401 Unauthorized` without DB interaction. Parses payload: `bank_trans_id`, `amount`, `reference_code`, `trans_time`. Hands off to `FolioPaymentService`.
  * **Verification Method:** Run `mvn test -Dtest=BankWebhookControllerTest`. Test with tampered body returns 401; test with valid signature returns 200 `{ "status": "SETTLED" }`.

* **TASK-A6.3: Folio Payment Status Polling Endpoint**
  * **Module:** Payment Settlement API
  * **Assignee:** Dev A
  * **Dependencies (DAG):** TASK-B6.1
  * **Target Deliverables:** `src/quanlykhachsan/backend/booking/PaymentPollController.java`.
  * **Definition of Done:** Implements `GET /api/v1/folios/{id}/payment-status`. Queries folio balance and settlement state using primary key index lookup (<5ms execution). Returns `{ "folio_id": 1, "folio_code": "FOLIO-MASTER-101", "balance": 0.00, "status": "settled", "last_payment_at": "..." }`.
  * **Verification Method:** `curl http://localhost:8081/api/v1/folios/1/payment-status -H "Authorization: Bearer <token>"` returns JSON containing `status`.

#### Dev B — Data, Finance & Domain Logic
* **TASK-B6.1: Schema Migration: Folio ID Parity & Strict Non-Negative Constraints**
  * **Module:** Financial Ledger Schema
  * **Assignee:** Dev B
  * **Dependencies (DAG):** None
  * **Target Deliverables:** `database/migrations/v2_0_week6_vietqr_accounting.sql`, `src/quanlykhachsan/backend/utils/UpdateDbUtility.java`.
  * **Definition of Done:**
    1. Folio ID Parity: Adds `code VARCHAR(64) NOT NULL UNIQUE` to `folios` table (e.g., `FOLIO-MASTER-101`, `FOLIO-GUEST-101`), preserving `id BIGINT AUTO_INCREMENT PRIMARY KEY`.
    2. Immutability & Safety: Alters `folios` table to add MariaDB CHECK constraint: `CHECK (balance >= 0.00)`.
    3. Webhook Audit: Creates `bank_webhook_logs` (`id BIGINT AUTO_INCREMENT PRIMARY KEY, bank_trans_id VARCHAR(100) NOT NULL UNIQUE, folio_id INT NOT NULL, amount DECIMAL(12,2) NOT NULL, signature VARCHAR(128) NOT NULL, status ENUM('PROCESSED', 'REJECTED', 'DUPLICATE') NOT NULL, raw_payload LONGTEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)`.
  * **Verification Method:** Run migration; execute `INSERT INTO folios (booking_id, folio_type, balance, code) VALUES (1, 'master', -100.00, 'TEST-NEG')`. Assert MariaDB rejects insert with `CONSTRAINT CHECK 'balance >= 0.00' failed`.

* **TASK-B6.2: EMVCo Napas 247 Standard Payload & CRC16-CCITT Engine**
  * **Module:** Financial Calculation & QR Standards
  * **Assignee:** Dev B
  * **Dependencies (DAG):** None
  * **Target Deliverables:** `src/quanlykhachsan/backend/booking/VietQrGenerator.java`, `src/quanlykhachsan/backend/utils/Crc16Ccitt.java`.
  * **Definition of Done:** Implements pure Java EMVCo QR code generator:
    - Tag `00`: Payload Format Indicator ("01")
    - Tag `01`: Point of Initiation Method ("12" - Dynamic QR)
    - Tag `38`: Merchant Account Info (Napas GUID `A000000727`, Acquirer BIN `970422` [MBBank], Account Number)
    - Tag `53`: Transaction Currency ("704" - VND)
    - Tag `54`: Transaction Amount (Formatted without floating point error)
    - Tag `58`: Country Code ("VN")
    - Tag `62`: Additional Data Field (Subtag `08`: Folio Reference Code)
    - Tag `63`: CRC16-CCITT Kermit checksum calculation (polynomial `0x1021`, initial `0xFFFF`, appended as 4-character hex uppercase).
  * **Verification Method:** Unit test `mvn test -Dtest=VietQrGeneratorTest` verifies generated string CRC matches standard Napas specification check vector.

* **TASK-B6.3: Idempotent Payment Settlement & Non-Negative Balance Enforcer**
  * **Module:** Folio Accounting Engine
  * **Assignee:** Dev B
  * **Dependencies (DAG):** TASK-B6.1
  * **Target Deliverables:** `src/quanlykhachsan/backend/booking/FolioPaymentService.java`.
  * **Definition of Done:** Executes payment within atomic MariaDB transaction (`setAutoCommit(false)`):
    1. Locks folio row: `SELECT balance, status FROM folios WHERE id = ? FOR UPDATE`.
    2. Checks `bank_webhook_logs` for `bank_trans_id`: if already exists, returns cached settlement status immediately (idempotency hit).
    3. Handles overpayments safely: If incoming payment `P > balance B`:
       - Posts credit transaction: `charge_type = 'payment'`, `amount = B`.
       - Posts excess credit adjustment: `charge_type = 'credit_adjustment'`, `amount = (P - B)`, `description = 'Excess overpayment credit'`.
       - Sets `balance = 0.00` and `status = 'settled'`. (Balance NEVER drops below 0.00).
    4. Commits transaction and records entry in `bank_webhook_logs`.
  * **Verification Method:** Run `mvn test -Dtest=FolioPaymentServiceTest`. Tests exact payment, partial payment, and 150% overpayment; confirms `balance >= 0.00` in 100% of cases.

#### Dev C — Desktop UI & Customer Web UI
* **TASK-C6.1: Swing Dynamic VietQR Modal Dialog with Countdown Timer**
  * **Module:** Desktop Payment UI
  * **Assignee:** Dev C
  * **Dependencies (DAG):** TASK-D6.1
  * **Target Deliverables:** `src/quanlykhachsan/frontend/view/VietQrPaymentDialog.java`, `src/quanlykhachsan/frontend/view/PaymentForm.java`.
  * **Definition of Done:** Check-out payment screen provides "Thanh toán VietQR Napas 247" button. Launches modal dialog fetching and rendering 300x300px QR image, displaying Bank Name, Beneficiary, Amount in VND, and Bill Code. Includes a 5-minute visual countdown timer. Polls `/api/v1/folios/{id}/payment-status` every 2000ms via `SwingWorker` without freezing EDT.
  * **Verification Method:** Open VietQR dialog in Swing. Verify QR renders clearly; verify 5-minute timer counts down smoothly at 1s intervals with zero EDT lag warnings.

* **TASK-C6.2: Swing Audio-Visual Settlement Confirmation**
  * **Module:** Desktop Sound & Notifications
  * **Assignee:** Dev C
  * **Dependencies (DAG):** TASK-C6.1
  * **Target Deliverables:** `src/quanlykhachsan/frontend/utils/SoundNotifier.java`, `src/quanlykhachsan/frontend/view/VietQrPaymentDialog.java`.
  * **Definition of Done:** When polling detects status `settled`:
    1. Plays subtle luxury confirmation chime using `javax.sound.sampled`.
    2. Displays full-screen animated green checkmark overlay: `"Thanh toán thành công! Sổ cái đã cân bằng."`.
    3. Auto-closes dialog after 1500ms and transitions room to `vacant_dirty`.
  * **Verification Method:** Trigger mock payment callback; observe Swing UI smoothly transitioning to green checkmark with audio chime in < 50ms.

* **TASK-C6.3: Web Booking Engine VietQR Payment Screen**
  * **Module:** Public Web Booking Engine
  * **Assignee:** Dev C
  * **Dependencies (DAG):** TASK-D6.1
  * **Target Deliverables:** `web-ui/public/js/payment-qr.js`, `web-ui/public/css/payment.css`.
  * **Definition of Done:** Responsive Web payment step. Renders dynamic VietQR payload; provides "Tải ảnh QR" (Download QR) and "Sao chép số tài khoản" (Copy Account Number) buttons. Polls payment status via `fetch()` every 2.5s. On settlement, automatically redirects to booking confirmation receipt.
  * **Verification Method:** Complete web booking in browser. Payment screen displays QR. Trigger payment callback; browser redirects to receipt screen within 3 seconds.

#### Dev D — DevOps, QA Automation & AI Subsystem
* **TASK-D6.1: Bank Webhook Dispatcher on Mock Server**
  * **Module:** Test Harness & Stubs
  * **Assignee:** Dev D
  * **Dependencies (DAG):** None
  * **Target Deliverables:** `src/test/java/quanlykhachsan/stubs/BankWebhookSimulator.java`.
  * **Definition of Done:** Deploys automated webhook simulator on port 8089 by Day 1 12:00. Can generate signed HMAC-SHA256 payloads with configurable secrets, amounts, and duplicate transaction IDs to test idempotency defenses.
  * **Verification Method:** `curl http://localhost:8089/mock/fire-webhook?bank_trans_id=TXN-123&amount=1500000` dispatches signed POST to backend.

* **TASK-D6.2: Automated Scenario 7 Test: Duplicate Payment Webhook Defense**
  * **Module:** QA Stress & Concurrency Automation
  * **Assignee:** Dev D
  * **Dependencies (DAG):** TASK-A6.2, TASK-B6.3
  * **Target Deliverables:** `src/test/java/quanlykhachsan/scenarios/Scenario7DuplicateWebhookTest.java`.
  * **Definition of Done:** JUnit 5 test firing 50 concurrent webhooks with the exact same `bank_trans_id` ("TXN-DUP-50") and `folio_id` via `CountDownLatch(50)`.
  * **Verification Method:** Run `mvn test -Dtest=Scenario7DuplicateWebhookTest`. Assertions:
    1. Exactly 1 request executes ledger write and updates balance.
    2. 49 requests receive HTTP 200 with idempotent acknowledgement.
    3. `bank_webhook_logs` records exactly 1 `PROCESSED` and 49 `DUPLICATE` records.
    4. Folio balance is credited exactly once.

* **TASK-D6.3: Automated Overpayment & Non-Negative Folio Balance Test**
  * **Module:** Financial QA Automation
  * **Assignee:** Dev D
  * **Dependencies (DAG):** TASK-B6.1, TASK-B6.3
  * **Target Deliverables:** `src/test/java/quanlykhachsan/financial/OverpaymentCreditAdjustmentTest.java`.
  * **Definition of Done:** Automated test creating folio with balance 1,000,000 VND and posting webhook with 1,500,000 VND.
  * **Verification Method:** Run `mvn test -Dtest=OverpaymentCreditAdjustmentTest`. Asserts:
    1. Folio balance is exactly `0.00` (NOT -500,000).
    2. Two transactions created: `charge_type='payment'` for 1,000,000 and `charge_type='credit_adjustment'` for 500,000.
    3. MariaDB check constraint is never violated.

---

### WEEK 7: REAL-TIME HOUSEKEEPING, MAINTENANCE & MINIBAR MANAGEMENT

#### Dev A — Core API & Backend Systems
* **TASK-A7.1: Housekeeping Workflow Controller & Status Endpoints**
  * **Module:** Housekeeping Operations API
  * **Assignee:** Dev A
  * **Dependencies (DAG):** TASK-B7.2
  * **Target Deliverables:** `src/quanlykhachsan/backend/room/HousekeepingController.java`.
  * **Definition of Done:** Exposes `PUT /api/v1/rooms/{id}/housekeeping`. Requires Bearer JWT. Checks RBAC: only `housekeeping` or `admin` roles allowed. Rejects `front_desk` or `f&b` tokens with HTTP `403 Forbidden`. Updates room status (`vacant_dirty` -> `vacant_clean`) and logs cleaner ID, cleaning duration, and room inspection notes.
  * **Verification Method:** Run `mvn test -Dtest=HousekeepingControllerSecurityTest`. Verify receptionist token returns 403; housekeeping token returns 200 OK.

* **TASK-A7.2: Minibar Consumption Audit & Incidental Posting Controller**
  * **Module:** Minibar F&B API
  * **Assignee:** Dev A
  * **Dependencies (DAG):** TASK-B7.3
  * **Target Deliverables:** `src/quanlykhachsan/backend/room/MinibarController.java`.
  * **Definition of Done:** Exposes `POST /api/v1/rooms/{id}/minibar`. Mandates `Idempotency-Key` header. Ingests consumed items array: `[{ "item_id": 1, "quantity": 2 }]`. Verifies room is currently occupied; delegates to `MinibarPostingService` to decrement physical stock and post incidental charges directly to guest incidental folio (`FOLIO-GUEST-xxx`).
  * **Verification Method:** `curl -X POST http://localhost:8081/api/v1/rooms/101/minibar -H "Idempotency-Key: $(uuidgen)" -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d '{"items":[{"item_id":1,"quantity":2}]}'` returns 201 with created incidental transaction ID.

* **TASK-A7.3: Maintenance OOO/OOS Lock & Override Controller**
  * **Module:** Maintenance Operations API
  * **Assignee:** Dev A
  * **Dependencies (DAG):** TASK-B7.2
  * **Target Deliverables:** `src/quanlykhachsan/backend/room/MaintenanceController.java`.
  * **Definition of Done:** Exposes `PUT /api/v1/rooms/{id}/maintenance`. Allows transitioning room to `out_of_order` (OOO) or `out_of_service` (OOS). If room has active or upcoming reservations within the maintenance window, mandates `manager_pin` in request header or payload; otherwise rejects with `409 Conflict`.
  * **Verification Method:** `curl -X PUT http://localhost:8081/api/v1/rooms/101/maintenance -H "Authorization: Bearer <token>" -d '{"status":"out_of_order","reason":"AC Leak"}'` returns 409 without PIN, 200 with valid Manager PIN.

#### Dev B — Data, Finance & Domain Logic
* **TASK-B7.1: Schema Migration: Minibar Catalog, Room Audits & OOO Tracking**
  * **Module:** Housekeeping & Inventory Database Schema
  * **Assignee:** Dev B
  * **Dependencies (DAG):** None
  * **Target Deliverables:** `database/migrations/v2_0_week7_housekeeping_minibar.sql`, `src/quanlykhachsan/backend/utils/UpdateDbUtility.java`.
  * **Definition of Done:**
    1. Creates `minibar_items` table (`id INT AUTO_INCREMENT PRIMARY KEY, room_id INT NOT NULL, name VARCHAR(100) NOT NULL, unit_price DECIMAL(10,2) NOT NULL, standard_stock INT NOT NULL, current_stock INT NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, CONSTRAINT fk_minibar_rooms FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE)`.
    2. Creates `room_status_audits` table (`id BIGINT AUTO_INCREMENT PRIMARY KEY, room_id INT NOT NULL, from_status VARCHAR(30) NOT NULL, to_status VARCHAR(30) NOT NULL, updated_by_user_id INT NOT NULL, reason VARCHAR(255), created_at DATETIME DEFAULT CURRENT_TIMESTAMP)`.
  * **Verification Method:** Run migration; verify tables exist and FK relationships are enforced in MariaDB.

* **TASK-B7.2: Room State Machine Transition Engine & Strict RBAC Guard**
  * **Module:** Room State Domain Logic
  * **Assignee:** Dev B
  * **Dependencies (DAG):** TASK-B7.1
  * **Target Deliverables:** `src/quanlykhachsan/backend/room/RoomStateMachine.java`, `src/quanlykhachsan/backend/room/RoomStateTransitionException.java`.
  * **Definition of Done:** Enforces canonical state transition rules:
    - Front Desk: `vacant_clean` -> `occupied_clean` (Check-in), `occupied_*` -> `vacant_dirty` (Check-out).
    - Housekeeping: `vacant_dirty` -> `vacant_clean`, `occupied_dirty` -> `occupied_clean`.
    - Maintenance: Any state -> `out_of_order`, `out_of_order` -> `vacant_clean`.
    - ILLEGAL BLOCKS: Rejects Front Desk changing `vacant_dirty` -> `vacant_clean`. Rejects any direct transition from `occupied_*` -> `vacant_clean` (skipping cleaning step).
  * **Verification Method:** Run `mvn test -Dtest=RoomStateMachineTest`. 25 unit tests verify all allowed and prohibited transition combinations.

* **TASK-B7.3: Automated Minibar Inventory Decrement & Incidental Posting Engine**
  * **Module:** Inventory & Folio Posting Engine
  * **Assignee:** Dev B
  * **Dependencies (DAG):** TASK-B7.1
  * **Target Deliverables:** `src/quanlykhachsan/backend/room/MinibarPostingService.java`.
  * **Definition of Done:** Executes atomic transaction:
    1. Decrements `minibar_items.current_stock` for selected items. (Throws exception if requested quantity exceeds stock).
    2. Queries active booking for target room.
    3. Locates or creates `FOLIO-GUEST-xxx` (incidental folio).
    4. Inserts `folio_transactions` (`transaction_type = 'debit'`, `charge_type = 'minibar'`, `amount = quantity * unit_price`, `business_date = current_hotel_date`).
    5. Increases folio `balance` by transaction amount.
  * **Verification Method:** Run `mvn test -Dtest=MinibarPostingServiceTest`. Stock decrements accurately and incidental folio balance reflects exact item total.

#### Dev C — Desktop UI & Customer Web UI
* **TASK-C7.1: Housekeeping Fast Toggle Matrix in Swing**
  * **Module:** Desktop Housekeeping UI
  * **Assignee:** Dev C
  * **Dependencies (DAG):** TASK-D7.1
  * **Target Deliverables:** `src/quanlykhachsan/frontend/view/HousekeepingPanel.java`, `src/quanlykhachsan/frontend/view/StaffForm.java`.
  * **Definition of Done:** Dedicated Housekeeping tab in Swing. Displays room grid with prominent status chips. Provides one-click "Đã dọn phòng xong" (Mark Clean) button for dirty rooms. Includes batch filter: "Phòng bẩn cần dọn" (Dirty Rooms Only). Updates status via `SwingWorker` in < 50ms without blocking UI.
  * **Verification Method:** Open Housekeeping panel in Swing. Filter dirty rooms; click "Đã dọn phòng xong" on Room 302; room color transitions from Orange to Green in < 100ms.

* **TASK-C7.2: Minibar Consumption Checklist Dialog in Swing**
  * **Module:** Desktop Minibar UI
  * **Assignee:** Dev C
  * **Dependencies (DAG):** TASK-D7.1
  * **Target Deliverables:** `src/quanlykhachsan/frontend/view/MinibarChecklistDialog.java`, `src/quanlykhachsan/frontend/view/RoomActionDialog.java`.
  * **Definition of Done:** Check-out and housekeeping room inspect dialog includes "Kiểm kho Minibar" button. Opens modal with predefined items (Heineken, Evian, Snack, Chocolate) and increment/decrement buttons. Live subtotal calculation in VND. Clicking "Ghi nợ vào Folio" sends POST with UUIDv4 `Idempotency-Key` and closes modal.
  * **Verification Method:** Open Minibar dialog; increment Heineken to 2, snack to 1. Total displays `120,000 VND`. Click submit; verifies network call completes and folio view updates.

* **TASK-C7.3: Out-of-Order Maintenance Locker Dialog with Manager PIN Override**
  * **Module:** Desktop Maintenance UI
  * **Assignee:** Dev C
  * **Dependencies (DAG):** TASK-D7.1
  * **Target Deliverables:** `src/quanlykhachsan/frontend/view/MaintenanceDialog.java`.
  * **Definition of Done:** Room context menu allows selecting "Bảo trì / Sửa chữa (OOO)". Prompts for Reason (Water leak, AC issue, Furniture repair) and estimated finish date. If room has future bookings, displays amber warning dialog with Password/PIN input field for Manager Authorization.
  * **Verification Method:** Attempt to set booked room to OOO in Swing; verify Manager PIN prompt appears; entering valid PIN executes status update to Red (`#F44336`).

#### Dev D — DevOps, QA Automation & AI Subsystem
* **TASK-D7.1: Housekeeping & Minibar API Stubs on Mock Server**
  * **Module:** Test Harness & Stubs
  * **Assignee:** Dev D
  * **Dependencies (DAG):** None
  * **Target Deliverables:** `src/test/java/quanlykhachsan/stubs/HousekeepingMockStubs.java`.
  * **Definition of Done:** Deploys mock endpoints on port 8089 by Day 1 12:00 for `PUT /api/v1/rooms/{id}/housekeeping`, `POST /api/v1/rooms/{id}/minibar`, and `PUT /api/v1/rooms/{id}/maintenance` to unblock Dev C.
  * **Verification Method:** `curl -X PUT http://localhost:8089/api/v1/rooms/101/housekeeping -d '{"status":"vacant_clean"}'` returns 200 OK.

* **TASK-D7.2: Automated Scenario 8 Test: Housekeeping State Machine & RBAC Enforcement**
  * **Module:** Security & State Machine QA Automation
  * **Assignee:** Dev D
  * **Dependencies (DAG):** TASK-A7.1, TASK-B7.2
  * **Target Deliverables:** `src/test/java/quanlykhachsan/scenarios/Scenario8HousekeepingRbacTest.java`.
  * **Definition of Done:** Automated test asserting:
    1. Receptionist JWT attempting `PUT /rooms/101/housekeeping` with `vacant_clean` is blocked with HTTP `403 Forbidden`.
    2. Housekeeping JWT successfully updates `vacant_dirty` to `vacant_clean` returning HTTP `200 OK`.
    3. Any user attempting direct illegal transition `occupied_clean` -> `vacant_clean` receives HTTP `400/409 Conflict`.
  * **Verification Method:** Run `mvn test -Dtest=Scenario8HousekeepingRbacTest`. 100% of RBAC and state transition assertions pass in < 2000ms.

* **TASK-D7.3: Automated Minibar Concurrent Posting Stress Test**
  * **Module:** Financial Concurrency QA Automation
  * **Assignee:** Dev D
  * **Dependencies (DAG):** TASK-A7.2, TASK-B7.3
  * **Target Deliverables:** `src/test/java/quanlykhachsan/booking/MinibarConcurrentPostingTest.java`.
  * **Definition of Done:** Spawns 20 concurrent threads posting minibar usage across 20 distinct rooms. Verifies each folio receives exact charge, inventory stock is decremented accurately, and zero HikariCP connection leaks occur.
  * **Verification Method:** Run `mvn test -Dtest=MinibarConcurrentPostingTest`. Inspect pool metrics; active connections return to 0.

---

### WEEK 8: DYNAMIC PRICING ENGINE, PROMOTIONAL WALLET, VAT INVOICING & DBR REPORTING

#### Dev A — Core API & Backend Systems
* **TASK-A8.1: Dynamic Pricing Quote Controller**
  * **Module:** Pricing Engine API
  * **Assignee:** Dev A
  * **Dependencies (DAG):** TASK-B8.1
  * **Target Deliverables:** `src/quanlykhachsan/backend/pricing/PricingController.java`.
  * **Definition of Done:** Exposes `GET /api/v1/pricing/quote?room_type_id=1&check_in=2026-11-01&check_out=2026-11-03`. Public endpoint whitelisted in `JwtFilter`. Invokes `DynamicPricingEngine` to calculate tiered Best Available Rate (BAR) based on hotel occupancy forecast for the requested dates. Responds in < 10ms with base price, surge multiplier, tax breakdown, and total quote.
  * **Verification Method:** `curl "http://localhost:8081/api/v1/pricing/quote?room_type_id=1&check_in=2026-11-01&check_out=2026-11-03"` returns 200 with dynamic quote.

* **TASK-A8.2: Promotional Voucher Validation Controller**
  * **Module:** Promotions & Marketing API
  * **Assignee:** Dev A
  * **Dependencies (DAG):** TASK-B8.2
  * **Target Deliverables:** `src/quanlykhachsan/backend/promotion/PromotionController.java`.
  * **Definition of Done:** Exposes `POST /api/v1/promotions/validate`. Ingests coupon code, booking amount, and guest phone. Checks validity, expiration date, per-user usage limits, and minimum spend conditions. Returns calculated discount amount and discounted total.
  * **Verification Method:** `curl -X POST http://localhost:8081/api/v1/promotions/validate -H "Content-Type: application/json" -d '{"code":"SUMMER50","total_amount":3000000,"phone":"0901234567"}'` returns 200 with discount details.

* **TASK-A8.3: Circular 78 Electronic VAT Invoice PDF Controller**
  * **Module:** Electronic Invoicing API
  * **Assignee:** Dev A
  * **Dependencies (DAG):** TASK-B8.3
  * **Target Deliverables:** `src/quanlykhachsan/backend/invoice/InvoicePdfController.java`.
  * **Definition of Done:** Exposes `GET /api/v1/invoices/{id}/pdf`. Fetches finalized invoice data; calls `VatInvoicePdfGenerator` to build standard Circular 78/2021/TT-BTC PDF byte stream. Sets HTTP response headers: `Content-Type: application/pdf`, `Content-Disposition: inline; filename=HD-2026-{id}.pdf`. Streams binary payload efficiently.
  * **Verification Method:** `curl -s http://localhost:8081/api/v1/invoices/1/pdf -H "Authorization: Bearer <token>" -o test_invoice.pdf`. Inspect file; opens cleanly in Adobe Acrobat / PDF viewer.

* **TASK-A8.4: Daily Business Report (DBR) Financial Endpoint**
  * **Module:** Financial Reporting API
  * **Assignee:** Dev A
  * **Dependencies (DAG):** TASK-B8.4
  * **Target Deliverables:** `src/quanlykhachsan/backend/report/FinancialReportController.java`.
  * **Definition of Done:** Exposes `GET /api/v1/reports/financial/dbr?date=2026-10-14`. Requires `admin` or `front_desk` role. Aggregates daily financial performance metrics: Total Revenue, Room Revenue, F&B/Minibar Revenue, Service Charges (5%), VAT (8%/10%), Total Rooms Available, Occupied Rooms, ADR, RevPAR.
  * **Verification Method:** `curl "http://localhost:8081/api/v1/reports/financial/dbr?date=2026-10-14" -H "Authorization: Bearer <token>"` returns 200 with standard DBR JSON metrics.

#### Dev B — Data, Finance & Domain Logic
* **TASK-B8.1: Dynamic Pricing Tiers Algorithm & Occupancy Matrix Engine**
  * **Module:** Revenue Optimization Logic
  * **Assignee:** Dev B
  * **Dependencies (DAG):** None
  * **Target Deliverables:** `src/quanlykhachsan/backend/pricing/DynamicPricingEngine.java`, `src/quanlykhachsan/backend/pricing/PricingTier.java`.
  * **Definition of Done:** Implements hospitality occupancy formula:
    $$\text{Occupancy Rate} = \frac{\text{Occupied Rooms} + \text{Confirmed Bookings}}{\text{Total Operational Rooms (200 - OOO)}}$$
    Dynamically applies rate tiers:
    - Tier 1 (< 50% occupancy): Multiplier $1.00\times$ (Base BAR)
    - Tier 2 (50% – 69.9% occupancy): Multiplier $1.15\times$
    - Tier 3 (70% – 84.9% occupancy): Multiplier $1.30\times$
    - Tier 4 ($\ge 85\%$ occupancy): Multiplier $1.50\times$ (High demand surge)
    Calculates nightly pricing using `BigDecimal` with exact rounding (`ROUND_HALF_UP`).
  * **Verification Method:** Run `mvn test -Dtest=DynamicPricingEngineTest`. 15 test cases assert exact rate multipliers across all occupancy ranges.

* **TASK-B8.2: Promotional Wallet Engine & Anti-Stacking Rules**
  * **Module:** Promotion Domain Logic
  * **Assignee:** Dev B
  * **Dependencies (DAG):** None
  * **Target Deliverables:** `src/quanlykhachsan/backend/promotion/VoucherService.java`.
  * **Definition of Done:** Enforces promotion rules:
    1. Maximum discount cap (e.g., 20% discount capped at 500,000 VND).
    2. Single voucher per booking/folio (anti-stacking rule).
    3. Discount applies to pre-tax room charges before applying 5% service charge and 8%/10% VAT.
    4. Records voucher redemption atomically in `promotions` ledger table.
  * **Verification Method:** Run `mvn test -Dtest=VoucherServiceTest`. Verifies discount cap enforcement and tax calculation order.

* **TASK-B8.3: iTextPDF Electronic VAT Invoice Generator (Circular 78/2021/TT-BTC)**
  * **Module:** PDF Invoice Generation Engine
  * **Assignee:** Dev B
  * **Dependencies (DAG):** None
  * **Target Deliverables:** `src/quanlykhachsan/backend/invoice/VatInvoicePdfGenerator.java`, `pom.xml`.
  * **Definition of Done:** Integrates iText 7 (`com.itextpdf:itext7-core:7.2.5`) into `pom.xml`. Embeds Vietnamese TrueType Font (`DejaVuSans.ttf`). Builds official Vietnamese Circular 78 electronic invoice template:
    - Hotel Name, Address, Tax Code (MST: 0312345678).
    - Guest Name, Company Name, Customer Tax Code.
    - Tabular line items: Room Charge, Minibar, Laundry.
    - Tax Breakdown: Service Charge (5%), VAT (8% or 10%), Final Total in figures and words (Tiền viết bằng chữ).
    - Digitize verification QR code linking to tax authority lookup URL.
    - 0 missing font glyphs, 0 text wrapping bugs.
  * **Verification Method:** Run `mvn test -Dtest=VatInvoicePdfGeneratorTest`. Generates PDF; parses text using PDFBox asserting exact Vietnamese diacritics: "Hóa đơn giá trị gia tăng (tiền tệ: VNĐ)".

* **TASK-B8.4: DBR Financial Aggregator & Ledger Balance Reconciler**
  * **Module:** Financial Accounting & DBR
  * **Assignee:** Dev B
  * **Dependencies (DAG):** None
  * **Target Deliverables:** `src/quanlykhachsan/backend/report/DbrLedgerAggregator.java`, `src/quanlykhachsan/backend/report/DbrSnapshot.java`.
  * **Definition of Done:** Aggregates transaction rows from `folio_transactions` for the closed business date. Computes:
    $$\text{ADR} = \frac{\text{Room Revenue}}{\text{Occupied Rooms}}, \quad \text{RevPAR} = \frac{\text{Room Revenue}}{\text{Total Operational Rooms}}$$
    Enforces penny-level ledger reconciliation:
    $$\sum\text{Debits} == \sum\text{Credits} + \text{Receivables Balance}$$
    Stores daily snapshot in `daily_business_reports` table.
  * **Verification Method:** Run `mvn test -Dtest=DbrLedgerAggregatorTest`. Assert formula outputs match ledger transactions down to 0.00 VND variance.

#### Dev C — Desktop UI & Customer Web UI
* **TASK-C8.1: Swing Billing & PDF Invoice Viewer Integration**
  * **Module:** Desktop Invoicing UI
  * **Assignee:** Dev C
  * **Dependencies (DAG):** TASK-D8.1
  * **Target Deliverables:** `src/quanlykhachsan/frontend/view/InvoiceViewerDialog.java`, `src/quanlykhachsan/frontend/view/StaffForm.java`.
  * **Definition of Done:** Settle Bill dialog includes "Xuất Hóa Đơn Điện Tử (VAT)". Calls backend PDF endpoint via `SwingWorker`; saves temporary `.pdf` file in user cache; launches system default PDF reader via `java.awt.Desktop.getDesktop().open(file)` in < 500ms.
  * **Verification Method:** Click "Xuất Hóa Đơn Điện Tử" in Swing. Verify system PDF viewer opens invoice document containing accurate guest and item details.

* **TASK-C8.2: Web Booking Engine Dynamic Price Display & Voucher Apply Box**
  * **Module:** Public Web Booking Engine
  * **Assignee:** Dev C
  * **Dependencies (DAG):** TASK-D8.1
  * **Target Deliverables:** `web-ui/public/js/pricing-quote.js`, `web-ui/public/js/voucher.js`.
  * **Definition of Done:** Web booking summary displays dynamic room rate with real-time occupancy indicator badge ("Giá ưu đãi 1.15x"). Includes "Mã giảm giá / Voucher" input box with "Áp dụng" button. Displays loading spinner during validation; renders green discount badge on success or red error message on invalid code.
  * **Verification Method:** Enter coupon `SUMMER50` in web portal; total price updates instantly with animated price strikethrough.

* **TASK-C8.3: Swing Daily Business Report (DBR) Dashboard Panel**
  * **Module:** Desktop Analytics UI
  * **Assignee:** Dev C
  * **Dependencies (DAG):** TASK-D8.1
  * **Target Deliverables:** `src/quanlykhachsan/frontend/view/DbrReportPanel.java`.
  * **Definition of Done:** New "Báo Cáo Doanh Thu (DBR)" menu option in Swing. Displays KPI tiles: Occupancy Gauge (%), Room Revenue, Total Revenue, ADR, RevPAR. Tabular breakdown of revenue by department (Phòng, Minibar, Dịch vụ). "Xuất Excel / CSV" button exports report data.
  * **Verification Method:** Open DBR report tab; select date; KPI tiles and summary tables populate within 300ms.

#### Dev D — DevOps, QA Automation & AI Subsystem
* **TASK-D8.1: Mock Server Extensions for Pricing, Voucher & DBR Endpoints**
  * **Module:** Test Harness & Stubs
  * **Assignee:** Dev D
  * **Dependencies (DAG):** None
  * **Target Deliverables:** `src/test/java/quanlykhachsan/stubs/PricingAndReportMockStubs.java`.
  * **Definition of Done:** Deploys mock endpoints on port 8089 by Day 1 12:00 for `/api/v1/pricing/quote`, `/api/v1/promotions/validate`, and `/api/v1/reports/financial/dbr` to unblock Dev C.
  * **Verification Method:** `curl http://localhost:8089/api/v1/pricing/quote?room_type_id=1` returns mock quote.

* **TASK-D8.2: Dynamic Pricing High-Throughput Load Test (50 CCU)**
  * **Module:** Performance QA Automation
  * **Assignee:** Dev D
  * **Dependencies (DAG):** TASK-A8.1, TASK-B8.1
  * **Target Deliverables:** `src/test/java/quanlykhachsan/performance/DynamicPricingLoadTest.java`.
  * **Definition of Done:** Fires 50 concurrent threads querying pricing quotes over 10,000 requests. Inspects HikariCP connection usage and response latency.
  * **Verification Method:** Run `mvn test -Dtest=DynamicPricingLoadTest`. P95 response time < 15ms, P99 < 30ms, 0 connection leaks, 0 thread timeouts.

* **TASK-D8.3: Circular 78 PDF Invoice Compliance & Unicode Character Test**
  * **Module:** Compliance QA Automation
  * **Assignee:** Dev D
  * **Dependencies (DAG):** TASK-A8.3, TASK-B8.3
  * **Target Deliverables:** `src/test/java/quanlykhachsan/invoice/Circular78PdfInvoiceTest.java`.
  * **Definition of Done:** Automated test creating sample invoice with full Vietnamese character set ("Nguyễn Văn Đạt", "Phòng Hạng Sang Hướng Biển", "Tiền dịch vụ minibar và đồ uống"). Generates PDF, parses content via Apache PDFBox, checks for 100% character integrity (zero `?` or missing unicode glyphs).
  * **Verification Method:** Run `mvn test -Dtest=Circular78PdfInvoiceTest`. Assertions pass 100%.

* **TASK-D8.4: Phase 2 Full Regression Suite & Week 8 Go/No-Go Gate Validator**
  * **Module:** Regression Test Suite
  * **Assignee:** Dev D
  * **Dependencies (DAG):** All Week 5–8 Tasks
  * **Target Deliverables:** `src/test/java/quanlykhachsan/Phase2RegressionSuite.java`.
  * **Definition of Done:** Automated test runner executing:
    - 47 Phase 1 baseline tests.
    - Scenario 7 (Duplicate Webhook Defense).
    - Scenario 8 (Housekeeping State Machine & RBAC).
    - Scenario 9 (Web vs Walk-in Race Condition).
    - VietQR EMVCo CRC16 verification.
    - Folio non-negative balance & excess credit adjustment.
    - Circular 78 PDF invoice compliance.
    - DBR financial ledger reconciliation.
  * **Verification Method:** Run `mvn clean test -Dtest=Phase2RegressionSuite`. 100% tests pass (0 failures, 0 errors).

---

## III. DAY-1 MOCK PROTOCOL (PHASE 2 EARLY-WEEK UNBLOCKING)

To maintain absolute decoupling and prevent Dev C (Frontend) and Dev D (QA) from being blocked while Dev A and Dev B implement backend APIs, the following Day-1 Mock Protocol is strictly enforced:

| Week | Upstream API Needed | Producer | Downstream Consumer | Mock Interface / Contract | Day-1 SLA | Decoupling Strategy |
|---|---|:---:|:---:|---|:---:|---|
| **W5** | Guest OTP (`/auth/guest-otp/*`) & Public Booking (`/bookings/public`) | **Dev A / Dev B** | **Dev C (Web UI)** | `mock-guest-otp.json`, `mock-public-booking.json` on port 8089 | **W5 Day 1, 12:00** | Dev D deploys stubs on `MockApiServer` (port 8089). Dev C develops pure JS Web UI against `:8089` before switching base URL to `:8081`. |
| **W6** | VietQR Payload (`/folios/{id}/vietqr`) & Webhook (`/webhooks/vietqr`) | **Dev A / Dev B** | **Dev C (Swing/Web) & Dev D (QA)** | Dynamic EMVCo string stub & Base64 PNG stub on `:8089` | **W6 Day 1, 12:00** | Dev D deploys `BankWebhookSimulator` on `:8089`. Dev C integrates countdown timer and polling against mock responses. |
| **W7** | Housekeeping Status (`/rooms/{id}/housekeeping`) & Minibar (`/rooms/{id}/minibar`) | **Dev A / Dev B** | **Dev C (Swing Staff)** | `HousekeepingMockStubs.java` on `:8089` | **W7 Day 1, 12:00** | Dev C builds Swing toggle matrix and Minibar checklist modal using mock responses; verified on real API at W7 Day 4. |
| **W8** | Pricing Quote (`/pricing/quote`) & DBR (`/reports/financial/dbr`) | **Dev A / Dev B** | **Dev C (Swing/Web) & Dev D (QA)** | `PricingAndReportMockStubs.java` on `:8089` | **W8 Day 1, 12:00** | Dev C renders DBR charts and Web voucher input against stubbed data; Dev D writes JMeter load scripts against mock contracts. |

---

## IV. GO/NO-GO ACCEPTANCE CRITERIA FOR THE END OF WEEK 8

The system must satisfy **100% of the following criteria** before Phase 2 sign-off is granted and Phase 3 (AI Platform & Computer Vision) is permitted to commence:

### 1. Functional Integrity & New API Endpoints
* [ ] **Public Booking Engine:** Unauthenticated guests can fetch room availability, receive OTP on phone, and successfully book a room via Web Booking Engine without receiving HTTP 401 errors.
* [ ] **CORS Preflight:** Native Java `CorsFilter` responds with `204 No Content` to preflight `OPTIONS` requests containing `Idempotency-Key` and `X-Signature` headers.
* [ ] **Static Serving:** Native `StaticFileHandler` serves Web Booking Engine HTML/JS/CSS assets directly on port 8081 with proper MIME headers and 0 path traversal vulnerabilities.

### 2. VietQR Payment Gateway & Accounting Immutability
* [ ] **EMVCo Napas 247 Compliance:** Generated VietQR strings validate 100% against EMVCo Tag structure and CRC16-CCITT Kermit checksum.
* [ ] **Scenario 7 (Duplicate Webhook Defense):** 50 concurrent webhooks sharing the same `bank_trans_id` result in exactly 1 ledger commit and 49 idempotent confirmations.
* [ ] **Non-Negative Folio Balance:** `folios.balance` is strictly $\ge 0.00$. Overpayments post credit adjustment transaction with `charge_type = 'credit_adjustment'`, never violating DB check constraints.
* [ ] **Folio ID Parity:** Database separates `id` (BIGINT PK) and `code` (VARCHAR UNIQUE, e.g. `FOLIO-MASTER-101`, `FOLIO-GUEST-101`).

### 3. Housekeeping State Machine & Minibar Automation
* [ ] **Scenario 8 (Housekeeping State Machine & RBAC):** Receptionists are strictly blocked (HTTP 403) from setting dirty rooms to clean. Direct transitions from occupied to clean are rejected (HTTP 400/409).
* [ ] **Automated Minibar Incidental Posting:** Submitting consumed minibar items decrements inventory and posts charges directly to `FOLIO-GUEST-xxx`.
* [ ] **Maintenance OOO Lock:** Setting a room to OOO requires Manager PIN if future reservations exist.

### 4. Dynamic Pricing, Circular 78 Invoicing & DBR Reporting
* [ ] **Dynamic Pricing Tiers:** BAR dynamically scales across 4 occupancy tiers (<50%: 1.0x, 50-70%: 1.15x, 70-85%: 1.3x, $\ge 85\%$: 1.5x) without floating point drift.
* [ ] **Circular 78 VAT Invoices:** Generated electronic VAT invoices render Vietnamese Unicode text perfectly (0 missing glyphs) and compute 5% service fee + 8%/10% VAT accurately.
* [ ] **DBR Ledger Balance Reconciliation:** Daily Business Report balances debits, credits, and receivables to 0.00 VND variance.

### 5. Performance, Concurrency & EDT Responsiveness
* [ ] **Scenario 9 (Web vs Walk-in Race Condition):** 10 Web bookings and 10 Walk-ins competing for the last available room result in exactly 1 booking and 19 HTTP 409 conflicts.
* [ ] **Swing EDT Latency:** All UI interactions (Walk-in Wizard, VietQR Dialog, Minibar checklist) execute in $< 16\text{ms}$ on the EDT (60 FPS maintained).
* [ ] **HikariCP Pool Stability:** 50 concurrent connections under load test show 0 connection leaks and return active connections to 0 post-test.

---

## V. SIGN-OFF & APPROVAL RECORD

| Role | Name / Title | Signature / Status | Date |
|---|---|:---:|:---:|
| **Technical Lead** | Chief Software Architect | `[ APPROVED - PHASE 2 WBS BASELINE ]` | `2026-10-14` |
| **QA & DevOps Lead** | Principal QA Engineer (Dev D) | `[ VERIFIED TEST HARNESS & GATES ]` | `2026-10-14` |
| **Core Backend Lead** | Senior Backend Engineer (Dev A & B) | `[ COMMITTED - WEEKS 5-8 BACKEND ]` | `2026-10-14` |
| **Frontend Lead** | Senior Desktop/Web Engineer (Dev C) | `[ COMMITTED - SWING & WEB BOOKING ]` | `2026-10-14` |
