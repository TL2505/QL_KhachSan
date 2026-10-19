# Phase 4 implementation report — 2026-10-20

Baseline: `PHASE_4_AND_BEYOND_BASELINE.md`, Weeks 11–15 only. Initial branch
`PHASE_4-AND_BEYOND`, commit `0d03010`; tracked working tree was clean.
No reset, credential provisioning, deployment or volume reset was performed.
See [verification evidence and gates](PHASE_4_VERIFICATION.md).

| Scope | Final implementation | Verification / operational dependency |
|---|---|---|
| W11 test integrity | Fixed guest-header auth bypass, API-client URI resolution, API CSP, Windows module file URLs and stale insecure assertions. DB fixtures now require explicit disposable-schema configuration and propagate setup failures. Benchmark prerequisites are checked before spawning its JVM. | Full Maven/Node suites. The load benchmark remains an explicit prerequisite, not an invented measurement. |
| W11 database | Both Compose files invoke a fail-fast initializer: schema-only base, prerequisites, ten ordered migrations. Reference roles are installed without production seed accounts/PII. BIGINT folio references agree. Runtime migration failures stop startup. | Reproduced automatically on empty MariaDB 11.4.13 schemas. Actual Docker execution unavailable. |
| W11 supervised AI | Guest AI remains off by default. Actor-bound budgets survive token/session changes. Prompt blocking precedes model calls. Queue failure returns 503. Existing encrypted tickets and bounded audits remain. Removed built-in JWT secret and desktop token minting. | Concierge/guardrail/TLS/auth tests. A real 20–50 guest pilot and universal injection-proof guarantee are not established by a corpus. |
| W12 catalog/orders | Portal loads active categorized database services. Staff categorize real services through `PUT /services/{id}/category`. F&B/spa require catalog IDs; free-text housekeeping ignores client prices. Strict server fields, ownership under lock, in-house state, service availability, schedules and prices are enforced. | Real JWT/HTTP/MariaDB tests, including IDOR, inactive service and invalid schedule. Operators must classify their actual catalog; no imaginary menu is seeded. |
| W12 vouchers | `/service-orders/quote` shares order pricing, rolls back without posting, and uses decimal discounts. Scope, spend, per-customer use, VIP/room-type/minimum-stay, verified birthday and recent-checkout windows are enforced. Unsupported occasions require verified eligibility. Removed browser discount fallbacks. | Real quote/charge, exhaustion, VIP, scope, spend, birthday and retention tests. |
| W12 atomicity | Request hash/replay commits with order, voucher use and folio debit. Booking/folio locks serialize updates. Customer, room and folio-transaction references persist. Validated cancellation posts one balancing reversal; paid charges require refund review. | Six concurrent retries produce one order/debit; conflicting replay and injected rollback tests. |
| W12 alerts | Fixed snake_case wire fields expected by desktop, one-second polling, bounded deduplication, sound and EDT dispatch. | Actual DAO → JSON → worker → EDT delivery about 1.1 seconds; HTTP excluded. No production percentile/100% delivery claim. |
| W13 identity | AES-256-GCM and HMAC blind indexes use external configuration. Startup never erases identities when keys are missing. Explicit maintenance backfill is transactional and enforces no plaintext. | Fresh-IV/tamper/AAD tests and real preservation/backfill/HMAC/constraint tests. |
| W13 QR | Local SVG uses existing iText encoder. Fifteen-minute domain-separated HMAC passes contain references, not raw identity documents. Ownership, confirmed booking, signature, expiry and current room/customer references are checked. Removed qrserver dependency. | Local generation, verification, tampering, wrong-key and expiry tests. |
| W13 vision | Existing Swing camera and protected OCR/face gateways inspected and retained. No physical card dispenser added. | Approved models, dataset and hardware trials unavailable; >98.5% OCR and <45-second check-in remain uncertified. |
| W14 forecast | Uses reconciled persisted business days plus booked occupancy. Decimal ADR/RevPAR and advisory recommendations; insufficient history leaves metrics unavailable. Desktop handles missing values. | Pure valid/invalid/missing-data tests and real database/HTTP metrics. Seven recent reconciled days required. No unsupported seasonal/holiday accuracy claim. |
| W14 approval | Login/JWT admit required management roles. Revenue managers cannot apply. Authorized approval uses deterministic locks, expected versions, audit actor/effective date/time and cache invalidation. Low-floor override requires admin/GM. | HTTP authorization, advisory-only read, approval, version conflict and persisted audit tests. |
| W15 reconciliation | Matches folios to independent verified-bank and cash/POS collections by reference, folio, business date and exact amount. `/reports/financial/reconciliation` exposes mismatches. Unverified historical snapshots cannot appear reconciled. | Exact-decimal and real matched/mismatched database tests; no production zero-variance audit claimed. |
| W15 payments | Signature/configured account checked before settlement. Permanent transaction deduplication rejects conflicting replay. Partial payments stay pending; excess is a separate liability. Cash/POS actor and business date commit with ledger. Legacy payment routes use folio service and deny guest self-confirmation. | Signed HTTP/MariaDB replay, partial-to-full confirmation and rollback tests. |
| Booking states | Creation is pending, including walk-ins. Verified settlement confirms; reception then checks in. Unpaid checkout/invalid state is rejected. Fifteen-minute unpaid holds expire with reversals; overdue arrivals are handled after 02:00. | Real confirmation, unpaid checkout, cancellation/expiry replay and rollback tests. |
| Frontend integrity | No random receipt, simulation flag, cached receipt authority or fallback bookable inventory. A valid backend receipt is mandatory. Uncertain retry retains its key and reports unconfirmed transaction. Bank details, QR and reference come from backend. Explicit desktop configuration never switches financial ledgers silently. | 51 Node tests and targeted scans. PAID comparisons are display-only, not assignments. |
| Invoice boundary | PDF explicitly identifies an unsigned internal record and configured reference link. | PDF/font/totals checks do not prove issuer integration, regulatory lookup or digital signature. Provider/signing acceptance blocked. |

Operational changes:

- Provide reviewed external `JWT_SECRET`, Base64 32-byte `AES_VAULT_KEY` and
  `HMAC_IDENTITY_KEY` for development. Production retains secret-file support.
  No replacement secrets, certificates or real identities were committed.
- Compose creates an approved named volume if absent. Initialization runs only
  on empty data directories and excludes the legacy production seed section.
- Existing plaintext identities require backup, stopped writers, approved keys
  and the established `IdentityVaultMigration --apply` maintenance command.
  Startup preserves them and refuses to accept traffic until ready.
- Classify real catalog services before publishing them. Retry uncertain writes
  with the same UUIDv4 key. Front desk settles folios before requesting checkout
  on the final folio. Transfers require verified bank webhooks.
- Historical bank logs without verifiable evidence/business date and old DBRs
  require operator reconciliation; they are not automatically certified.

Data-safety limitation: the initial existing API fixtures attempted writes and
migrations against the configured application schema before their unsafe setup
was diagnosed. They may have left synthetic fixture rows. There was no before-run
database snapshot identifying safe deletions, so no speculative cleanup occurred.
Later integration work used uniquely generated schemas and removed only those.
Tracked existing files/configuration were preserved.

No Redis/Prometheus/Grafana, card hardware, external invoice provider or unattended
guest AI was introduced. Operational release remains blocked as documented in
the [verification report](PHASE_4_VERIFICATION.md).
