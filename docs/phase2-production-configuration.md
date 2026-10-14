# Required Phase 2 production configuration

Production values must be supplied by the approved hotel/operator. This document contains no synthetic legal identity, beneficiary account, tax rate default, signing secret, or PIN.

| Setting | Requirement |
| --- | --- |
| `JWT_SECRET` | Approved secret of at least 32 bytes; missing/short secret prevents token initialization |
| `HOTEL_VAT_RATE` or JVM `hotel.vat.rate` | Explicit `0.08` or `0.10`; missing configuration prevents pricing/invoice calculations |
| `BANK_BIN` | Approved six-digit receiving bank BIN |
| `BANK_ACCOUNT_NUMBER` | Approved bank account supported by the VietQR generator |
| `BANK_ACCOUNT_HOLDER` | Approved beneficiary; displayed to payer |
| `BANK_WEBHOOK_SECRET` | At least 32 bytes; shared with the approved webhook producer; HMAC-SHA256 signs raw request bytes |
| `MANAGER_PIN_HASH` | Approved BCrypt hash; no plaintext/default manager PIN is stored in production code |
| `HOTEL_LEGAL_NAME` | Approved invoice issuer legal name |
| `HOTEL_LEGAL_ADDRESS` | Approved invoice issuer address |
| `HOTEL_TAX_CODE` | Approved hotel tax registration; the WBS's example identifier is not a production default |
| `INVOICE_VERIFICATION_URL` | Approved HTTPS destination; invoice ID is appended as a query parameter |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Explicit deployment database configuration, separate from disposable test credentials |
| OTP delivery | The request approved mock delivery for development/tests. `HOTEL_OTP_MODE=mock` uses a silent development sink; real delivery needs an approved `GuestAuthService.Delivery` implementation/configuration |

Each active voucher requires an explicit `promotions.code`, `max_discount`, `minimum_spend`, and `max_uses_per_user` in addition to the existing validity, discount, condition, and status fields. Null policy fields are not treated as unlimited usage, zero spend, or an invented cap. Existing promotion rows are preserved and must be configured by an authorized operator before voucher redemption is enabled for them.

Guest/company tax identity is optional customer data, stored as supplied. Do not fabricate missing identity details. Identity mismatches on walk-in ingestion require staff review.

Operational notes:

- Apply checked-in migrations against an approved backup/maintenance plan; unsupported legacy booking channels fail migration instead of being silently reclassified.
- Keep mock server/test fixtures outside production deployments. Run Maven from the repository root so migration and static asset paths resolve correctly.
- The container uses Maven to include HikariCP, iText 7, fonts, and all runtime dependencies; no npm or web build step is introduced.
- Native server transport deployment, live bank onboarding, actual OTP delivery, and tax-provider issuance/signing must use the approved deployment setup. The PDF generator does not register or digitally issue an invoice with a tax authority.
- Verify whole-dong balances and supported payment references before bank use. Do not round or change financial records to make a QR succeed.
- The desktop API base remains configurable through the existing `config.properties` `server.url`. Authentication uses signed JWT claims; `X-User-Role` does not grant authority.

Synthetic legal/bank/VAT values appear only in isolated test fixtures and marked verification PDFs. The test runner restores its environment after execution.
