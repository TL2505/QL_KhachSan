# Phase 4 implementation and verification ledger

Final status: see [completed software work and remaining operational dependencies](PHASE_4_REPORT.md)
and [release-gate evidence](PHASE_4_VERIFICATION.md). The table below preserves
the initial inspection checklist, not the final gate certification.

Baseline: `PHASE_4_AND_BEYOND_BASELINE.md`, Weeks 11–15. Initial branch
`PHASE_4-AND_BEYOND`, commit `0d03010`; working tree clean before changes.

| Requirement | Inspection and work needed | Verification |
|---|---|---|
| W11 test integrity | Initial `mvn test`: 198 tests, 16 failures, 0 errors, 36 skipped. Fix auth bypass, API URI resolution, unsafe DB fixture setup and obsolete AI tests. | Focused tests, full Maven run |
| W11 clean DB initialization | Development mounts migrations in an ignored subdirectory; production has no init scripts. Provide ordered fail-fast bootstrap and clean-schema test. | MariaDB integration when available; static wiring checks |
| W11 supervised AI | Default false already exists; preserve it. Verify prompt blocking, actor budget and persisted escalation failure handling. | Concierge/guardrail/rate tests |
| W12 guest orders | DAO posts folio atomically but permits arbitrary prices, has weak validation and no request deduplication. Complete catalog validation, ownership under lock and idempotency. | Validation tests and disposable DB HTTP/concurrency tests |
| W12 desktop alerts | Inspect polling, delivery and UI notification. | Reproducible delivery test; real latency only when measured |
| W13 identity vault | Inspect AES-GCM/HMAC configuration and existing camera/OCR path. | Encryption/tamper tests; hardware accuracy remains conditional |
| W13 offline QR | Portal uses public qrserver and unsigned fabricated payload. Replace with authenticated local pass generation. | Offline generation/verification tests |
| W14 revenue | Forecast and approval exist; inspect data validity, ADR/RevPAR, roles and audit. | Revenue policy/authorization/approval tests |
| W15 DBR | Existing snapshot checks folio arithmetic, lacks explicit three-way source reconciliation. Complete bank/cash/POS comparison. | Exact decimal mismatch and persistence tests |
| W15 payments/states | Inspect webhook account/reference validation, replay, checkout and confirmation transitions. | Webhook, payment and booking tests |
| Cross-cutting frontend | Multiple served JS copies fabricate booking IDs and use simulation flags. Remove fallback success and sync served copies. | Node behavioral tests and targeted scans |
| GATE-01 | Failing at baseline; no suppressed timeouts allowed. | `mvn test`, actual count and skips |
| GATE-02 | Pending; Docker/MariaDB executables absent from PATH. | Clean MariaDB init with all migrations |
| GATE-03 | Failing at baseline. | Search plus frontend failure tests |
| GATE-04 | Baseline claim unverified. | Local and configured VM HTTP probes; configuration tests |

No physical card dispenser, post-Week-16 infrastructure, secrets or real identity
fixtures are in scope. OCR accuracy, external invoice signing and remote deployment
must not be inferred from compilation or synthetic tests.
