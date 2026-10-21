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
