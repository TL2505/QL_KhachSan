# Booking persistence, order and HTTP 503 verification

## Verified source findings

- The real creation field is `bookings.created_at`, a `DATETIME DEFAULT CURRENT_TIMESTAMP`. It is separate from stay dates and `updated_at`. `Booking.createdAt` exposes it to the shared booking API.
- `BookingDAOImpl.selectBooking` already had `ORDER BY created_at DESC, id DESC`. Staff, Admin, desktop lists and the room/customer-specific DAO filters share this query. Keep this order before frontend filtering and slicing pages. The customer portal's independent SQL query now uses the same creation-time/ID order instead of ID alone.
- The public form sends `identity_card`. The previous `GuestIngestionService` wrote that value into `customers.identity_card`. `IdentityVaultMigration.finalizeSchema`, invoked during backend startup, enforces `chk_no_plain_identity CHECK(identity_card IS NULL)`. Those code paths conflict: after vault readiness, a nonempty identity attempts a prohibited write and reaches the public controller's generic 503 handler. The ingestion now uses the existing encrypted vault in its current transaction, compares existing identity hashes and refuses conflicting identities. The CHECK constraint is retained.
- Public booking reservation already commits the booking, room allocation, initial folio charges and durable idempotency receipt together before returning 201. This transaction and replay behavior have not been weakened or replaced. A failed database operation still fails the request.
- Previously the shared DAO swallowed a failed list query and returned an empty array. It now propagates that failure so the API reports 503. No missing data is replaced with sample bookings.

The code conflict above is verified from source. It is **not** proof that it caused a particular Windows request. That requires the Windows server log and actual database schema. The cloud session cannot access the operator's Windows localhost. No order was created in that database during this change.

## Capture the actual Windows failure

Use the existing database configuration and original vault keys. Do not run the demo initializer or import `database/hotel.sql` into the existing database. Do not generate replacement vault keys. In the PowerShell session already configured for this backend, stop the previous backend process normally, then compile/start while capturing output:

```powershell
Set-Location "$env:USERPROFILE\Documents\QL_KhachSan"
& .\scripts\start-backend.ps1 2>&1 | Tee-Object -FilePath .\backend-booking.log
```

The launcher uses installed Maven and preserves existing process database/key configuration. See `docs/run-project.md` if installed Maven needs to be passed with `-MavenCommand`. Keep the terminal open and reproduce the failed booking **once**, retaining the same draft/idempotency key after an ambiguous result. In another PowerShell window:

```powershell
Set-Location "$env:USERPROFILE\Documents\QL_KhachSan"
Select-String -Path .\backend-booking.log -Pattern '\[BookingFailure\]' | Select-Object -Last 10
```

New booking failure lines include a reference, operation, exception type, SQLState, vendor code and application source location, without SQL text, request bodies, phone/identity values, passwords or tokens. The HTTP response has the same reference in `X-Booking-Error-Id`. Share these bounded lines and the HTTP status/response headers; do not share credential values, authorization headers or an unredacted log.

Configuration is selected by `ApiConfig` and consumed by the shared Hikari pool: explicit `DB_URL`/`JDBC_URL` or `DB_HOST` settings take precedence over `config.properties`; DB user/password are taken from existing process settings or reviewed file configuration. The checked-in configuration is not evidence of the running Windows process's effective database. Do not point the app at another schema just to make an error disappear.

A 503 with `Retry-After: 300` and a Night Audit response is the existing write freeze, distinct from a booking persistence failure; keep that protection. SQLState classes `08` indicate connection failures and `23` integrity failures; vendor codes and the source location narrow the diagnosis. Do not assume every 503 is the identity constraint.

To confirm the actual schema without reading customer identities, run this read-only metadata query in the backend's database:

```sql
SELECT CONSTRAINT_NAME, CHECK_CLAUSE
FROM information_schema.CHECK_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'chk_no_plain_identity';
```

## Real booking verification on the operator's database

1. After resolving any actual 503, use an existing customer and a genuinely available room/date range in the normal customer UI. Submit one intended booking and record its returned `booking_id` and HTTP 201. Do not insert a fabricated row directly into SQL. Avoid concurrent booking creation while checking the first row.
2. In the SQL client connected to the **same database used by the backend**, run the read-only query below, replacing `123` with the returned ID:

   ```sql
   SELECT DATABASE() AS active_database;
   SELECT id, customer_id, room_id, status, created_at
   FROM bookings WHERE id = 123;
   SELECT id, created_at FROM bookings
   ORDER BY created_at DESC, id DESC LIMIT 20;
   SELECT b.id, f.id AS folio_id, f.balance, COUNT(t.id) AS ledger_rows
   FROM bookings b JOIN folios f ON f.booking_id = b.id
   LEFT JOIN folio_transactions t ON t.folio_id = f.id
   WHERE b.id = 123 GROUP BY b.id, f.id, f.balance;
   ```

   Verify the order exists with a non-null database-created timestamp, appears first, and has its initial folio/ledger rows. Retain the order; these instructions do not delete records.
3. Run the read-only API verifier using real Staff and Admin accounts. It prompts for credentials, does not save them or print tokens, checks the API aliases and customer-specific lists, repeats reads, and verifies the full `createdAt DESC, id DESC` sequence:

   ```powershell
   .\scripts\verify-booking-order.ps1 -BookingId 123
   ```

4. Open **Staff → Đặt phòng** and **Admin → Đặt phòng**, with search/status filters cleared. Confirm the same ID is first. In Staff, move to page 2 and back; confirm pages retain API order. Refresh while on page 2; it should return to page 1 with the new order first. Reload the browser and repeat. Filtered results retain the same relative order.
5. An already-open list reloads via its existing loader when returning to its tab/window, and the existing refresh buttons still work. There is no new polling or cross-device push infrastructure; a different device requires opening/reloading its list. Confirm the new ID after returning from customer booking to each staff/admin tab. Navigation must not allow an old request to overwrite another view.

## Checks completed in cloud

- Compilation of changed backend classes and focused unit tests for encrypted identity capture, matching/conflicting identity preservation, propagated SQL failures, ordered JDBC reads and redacted failure diagnostics.
- Frontend unit checks for preservation of server order, Staff pagination, page-one reset on refresh, filters, Admin reloads, tab-return refresh, duplicate-event coalescing and listener disposal.
- No claim of real Windows database persistence, a successful Windows booking, or first-row verification under actual Staff/Admin accounts is made until the steps above return evidence.

## Runtime repair and the two independent browser errors

The Windows evidence received in this session is a failed **launcher** (`AES_VAULT_KEY is required`), not a stack trace from the Java process currently answering POST requests. A successful source checkout does not reload running `.class` files. Do not identify that launcher error as the confirmed cause of a particular HTTP 503. Restore the original key configuration before restarting; absence of `.run/*.key` alone does not prove keys were never configured. If the original keys cannot be found, do not substitute generated keys for existing encrypted records.

From CMD, gather read-only diagnostics without printing passwords, tokens, key values or customer records:

```bat
cd /d "%USERPROFILE%\Documents\QL_KhachSan"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose-booking.ps1
```

This checks health/database readiness, anonymous public reviews, source markers, key-configuration presence in process/user/machine scopes, the Java process and its classpath/working directory, and bounded redacted booking logs. It does not POST a booking or migrate a schema. Key availability in this new terminal is not evidence of availability in the existing Java process. The script requires Windows PowerShell/.NET and was not executed against the operator's Windows machine in cloud.

The landing booking form supplies a verified `CUSTOMER` token. The former public controller accepted only guest sessions, causing a separate 403 after upgrading. The corrected customer branch resolves `users.customer_id` by the verified JWT user ID and matches the profile phone before any write; owned ingestion locks exactly that profile instead of selecting another customer with a shared phone. Staff/admin still use their existing staff workflows; a caller-controlled role header cannot grant ownership. Identity mismatch and stale room/request data stay 409. Configuration failures now reach redacted 503 diagnostics instead of being incorrectly reported as room conflicts.

Booking retries retain the UUID on the caller draft and, when available, save only its UUID, SHA-256 request fingerprint and timestamp in sessionStorage (24-hour retention), allowing the same request to replay after reload. No guest contact/identity fields or locally fabricated receipt are persisted. The server remains authoritative: booking, room allocation, folio, ledger and idempotency receipt commit together. An uncertain result is described as unconfirmed, rather than claiming the database definitely rolled back. A valid receipt clears the pending browser attempt; 400/403/409 keep their actual meaning. Cross-origin responses expose only the support reference and Retry-After.

Public review **GET** requests no longer automatically attach a possibly invalid session token to a public endpoint. Review writes and protected reads still send authentication. Review failures or an empty database render honest unavailable/empty states, without invented guests, counts or ratings. If anonymous `/reviews/public` still returns 403, inspect the running backend version/filters; the current review controller and JWT whitelist already support that GET. This does not authorize public review writes.

`reload.js` and `/ws/ws` are absent from the repository's website/server. They are not required by the booking transaction. The diagnostic script checks whether the actual port-3000 HTML contains an injected `reload.js`. Use the existing Node server instead of a third-party live-reload server to test the unmodified application:

```bat
cd /d "%USERPROFILE%\Documents\QL_KhachSan\web-ui"
npm start
```

Stop the existing port-3000 development server in its own terminal first. No dummy WebSocket endpoint or authentication bypass is introduced.

After restoring the existing database/key settings in the backend terminal, restart and capture output (CMD):

```bat
cd /d "%USERPROFILE%\Documents\QL_KhachSan"
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "& .\scripts\start-backend.ps1 2>&1 | Tee-Object -FilePath .\backend-booking.log"
```

This must compile successfully and reach the server-ready message; an AES preflight failure means the new backend has not started. Sign in again after restart if the JWT signing secret changed. Capture the POST response body and `X-Booking-Error-Id`; match the latter to `[BookingFailure]` in the real backend log. For uncertain retries keep the same form contents; the browser reuses the request key. With one valid booking receipt, verify the real database row, initial folio and Staff/Admin order using the earlier steps. Replaying that request must return the same booking ID and leave the booking count unchanged. Do not claim success from a toast or HTTP health check alone.

Read-only schema checks against the **same active database**, without displaying identities:

```sql
SELECT DATABASE() AS active_database;
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND ((TABLE_NAME = 'bookings' AND COLUMN_NAME IN ('created_at','customer_type'))
    OR (TABLE_NAME = 'rooms' AND COLUMN_NAME = 'version')
    OR (TABLE_NAME = 'customers' AND COLUMN_NAME IN ('identity_hash','identity_card')));
SELECT TABLE_NAME FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('idempotency_keys','guest_identity_vault','folios','folio_transactions','hotel_business_state');
SELECT COUNT(*) AS business_state_rows FROM hotel_business_state WHERE id=1;
```

The `Web` customer type comes from the existing v2 public-portal migration. Missing ledger/idempotency/business-state structures or a missing `Web` enum can fail persistence; check the SQLState/vendor code before running the project's reviewed migrations. These are possible failure points established from schema/query inspection, not confirmed causes of the operator's 503.

Validation for this update: 101 web tests and 17 isolated backend tests passed. Chromium exercised the actual landing form and submit button through a 503, reload, 409 and valid 201 receipt, verifying identical replay key/body, authentication headers, guest count, support reference and pending-state cleanup. It also checked unavailable, empty and populated review responses and observed no required WebSocket. These were HTTP fixtures, not real database writes or proof of Windows booking success.

Public review route verification also covers normalized trailing slashes and rejects POST/PUT/DELETE on the read-only summary path with 405; the protected review collection still requires a JWT even with a spoofed Staff header.
