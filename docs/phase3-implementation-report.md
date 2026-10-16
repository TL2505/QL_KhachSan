# Phase 3 implementation report — 2026-10-16

**PARTIAL implementation; mandatory acceptance gates remain PENDING. No production Go/No-Go.** Work was performed in the existing Java 17/MariaDB/Swing repository; no Spring, ORM, PostgreSQL or pgvector was introduced. No deployment, production data migration, long load, ZAP or real-camera test was run. Existing `.phase2-runtime/` was left untouched. Generated builds, dependencies, database and test keys live under ignored `target/`, not source control.

## Task scope, dependency and status

DONE below means the stated implementation and deterministic checks are delivered, not that external model/device/release gates passed. No task needing absent external evidence is marked DONE solely because code compiles.

| Task | Dependency | Scope / acceptance and delivered behavior | Status / remaining gate |
|---|---|---|---|
| A9.1 | B9.1, D9.1 | Staff-only OCR/face gateway, <=5MB JPEG/PNG, bounded bodies/responses, <=3s deadline, manual degradation, frozen API plus aliases, vision mock | PARTIAL: HTTP/RBAC/offline and actual local mTLS checked; real inference latency/integration pending |
| B9.1 | none | AES-256-GCM envelopes, random 96-bit IV per value, AAD, vault/HMAC, transactional legacy migration and NULL-only constraint; exact ID dedup/search | PARTIAL: crypto/DB tests pass; operational keys, rotation/restore and complete optional scan/face persistence acceptance pending |
| C9.1 | D9.1 | Camera worker, 30Hz preview, card guides, async scan/face dialog, manual amber badge, customer/walk-in autofill | PARTIAL: fake blocked-camera responsiveness checked; real FPS/EDT <16ms/autofill <1.2s/native layout pending |
| D9.1 | none | Python FastAPI, lazy offline Paddle/InsightFace adapters, two bounded workers, mTLS-only image, 1.5GB/2CPU Compose limits | PARTIAL: Python fixture checks pass; Python3.11 image/dependency build, approved models and resource/latency measurements pending |
| D9.2 | D9.1 | 100 visibly invalid synthetic OCR fixtures, face vector/threshold validation, disconnected-AI safe degradation | PARTIAL: no real OCR accuracy/FAR/FRR evidence; no real faces or valid-looking ID numbers generated |
| A10.1 | B10.1, D10.1 | Owner-bound sessions, 10/min token budget, guest default-off kill switch, conservative escalation, scoped encrypted human chat | PARTIAL: DB/HTTP isolation and token-budget checks pass; multilingual sentiment/semantic repeats and <1.2s real RAG pending |
| A10.2 | none | Bounded pre-model guardrail, normalized injection patterns, safe errors/fingerprint audit | DONE for deterministic filter scope; general adaptive adversarial robustness remains release work |
| B10.1 | none | Bounded async metadata audit, persistent prioritized tickets, owner-role enforcement, ack CAS; no raw prompts in audit logs | PARTIAL: persistence/isolation checked; external audit durability/operations sign-off pending |
| C10.1 | D10.1 | 320px collapsible copilot, cited plain text, orange blinking/chime banner, ack/live chat, operations guest switch | PARTIAL: built; native sound/layout and <500ms lookup pending |
| D10.1 | none | Offline MiniLM/384 normalized Faiss IndexFlatIP, 300-token/50-overlap original-text chunks, audience/language separation, Top3 extractive citations; 50-question evaluator | PARTIAL: approved SOP/model/index/questions absent; retrieval precision/Faiss <15ms/memory pending |
| D10.2 | A10.2, D10.1 | 100 distinct attack fixtures, HTTP400 before upstream, benign and malformed/oversize controls | DONE for supplied deterministic harness: all 100 blocked, zero upstream calls; 25 attack families with casing/framing variants, not 100 independent novel exploits |
| A11.1 | B11.1, D11.1 | Forecast canonical object/array opt-in, 6h cache, explicit on-books fallback, atomic versioned approvals and audit | PARTIAL: RBAC/contracts/cache boundary/transactions checked; real ML and end-to-end cache-hit <5ms pending |
| B11.1 | none | Closed-date occupancy/RevPAR views over reconciled DBR snapshots; bounded aggregate-only DAO | DONE for implemented snapshot path and 100k-booking benchmark; historical snapshot completeness must be reviewed before model training |
| C11.1 | D11.1 | Dark dual-axis 30-day forecast/last-year chart, editable approvals/reasons/versions; scenario preview unavailable when category demand/inventory undefined | PARTIAL: build/contract checked; native UX pending; no misleading revenue number shown |
| D11.1 | none | Prophet native serialization, VN holidays, training and chronological holdout evaluation scripts, no sensitive rows | PARTIAL: no approved >=400-day history/model; MAPE <8.5%, inference/resource gates pending |
| D11.2 | A11.1, D11.1 | 0.70–2.50 bounds, >=0.80 recommendation, explicit management override audit, atomic batch rollback; primary inventory pricing and full-capacity promo guard | PARTIAL: deterministic price/CAS/ledger/Phase2 inventory regression passes; actual ML/full-load safety pending |
| A12.1 | none | Shared 60-token IP bucket, explicit trusted-proxy handling, security headers, expiry-bound token fingerprint blacklist/logout | DONE for single-process implementation and HTTP tests; blacklist is not distributed or restart-persistent |
| B12.1 | none | 00:10 retention, 30-day raw/face purge, 3-year settled inactive guest anonymization, ledger/FKs preserved, hash-chained non-PII audit, six-month chat retention | PARTIAL: deterministic DB/idempotence/audit checks pass; operations scheduling, historical edge review and backup retention acceptance pending |
| C12.1 | all C tasks | Async splash/preload, Shade, jdeps/jlink and jpackage app-image with JRE, external config path | PARTIAL: Windows preview app-image built; cold start/native launch/idle RAM and installer acceptance pending |
| D12.1 | A12.1, B12.1 | 200CCU/2h 60/20/10/10 JMeter draft plus reproducible data/review requirements | PARTIAL harness; execution PENDING; signing/correlation fixtures still required |
| D12.2 | D12.1, C12.1 | Four-service production Compose, secrets/mTLS/internal AI, startup/HTTPS checklist, security evidence template | PARTIAL configuration; Docker <90s, ZAP, penetration testing and release signatures PENDING |

## Source changes and policy decisions

- New backend `ai/`, vault/migration/retention services, forecast/approval services and four `v3_*` migrations; existing customer ingestion, pricing, promotions, authentication and server registration integrate them. Desktop adds check-in/copilot/tickets/forecast/splash components and worker API helper. AI service, Compose, packaging/test scripts and tests are new. No `ai_reader` grants were broadened.
- User approved `https://ai-service:8082` over Docker DNS with mutual TLS and secret-mounted certificates, replacing erroneous container-local `127.0.0.1`/HTTP examples. ADR-08's Faiss supersedes older pgvector/deployment material. No Phase 4 web UI was added.
- User approved replacing legacy clear identity storage with vault + HMAC blind index and a maintenance cutover. Each ciphertext carries its own IV; one shared IV column would be unsafe for multiple encryptions. Independent operational keys have no defaults. Plain ID output is restricted to authorized staff profile/vision flows; no plaintext prompt/image logging was introduced.
- User approved soft MONITOR for neutral cancellation-policy questions, consistent with escalation-rules section 3; actual cancellation/refund/manager/strong negative/repeated exact requests escalate. Answers use original approved SOP passages. Semantic repetition and robust sentiment are not claimed from the current heuristic.
- User approved default >=0.80 pricing, absolute 0.70–2.50 bounds, reason/management audit below0.80. Existing bookings retain their financial semantics. Forecast cache never authorizes room availability.
- User approved an illustrative constant-demand comparison only per day/category on remaining sellable inventory, excluding booked rooms/tax/fees. Current forecast is hotel-wide including on-books occupancy, so preview explicitly returns unavailable. No projected uplift or commitment is invented.
- User approved frozen canonical APIs plus WBS aliases. Vision accepts both image field sets (conflicting aliases rejected); canonical `id_number`/`is_match` and WBS fields coexist. Unsupported OCR fields are null. Concierge returns `reply`/`confidence_score` plus `answer`/`confidence`, citations doc/source and nullable section. Escalation never invents room/ETA; both are null. Forecast defaults to `data.forecast`; `?format=array` opts into WBS shape. Apply accepts `approved_multiplier`/`multiplier`, rejects conflicting values, returns `updated_count` plus `applied`. Version0 only creates; existing rules need matching version. Actor comes from JWT, never `approved_by_user_id` body. `revenue_manager` reads only.
- Audits deliberately store fingerprints/decision metadata instead of unneeded raw chat text. Human handoff text is separately AES-encrypted and ticket-owner-scoped. Reconciled historical DBR inventory snapshots avoid incorrectly applying today's room count to historical bookings. Prophet is serialized natively as JSON rather than pretending a generic ONNX export exists.
- The current app root is `MainUI`, not the outdated StaffForm target. New tests use repository `tests/` Maven source root. Prompt/source text is rendered plain to avoid Swing HTML/script behavior. Protected room inventory and removal of caller-supplied guest-role spoofing align with ADR/API JWT requirements; regression tests now assert those boundaries.

## Verification evidence

Host: Windows, Java17.0.20.1, Maven3.10.0, isolated MariaDB11.4.13 at127.0.0.1:33318/schema `hotel_phase3_test`; no production database used. Test-only random keys/password remain ignored. Python fixture runtime is3.12.14, explicitly **not** proof of the required3.11 deployment. Lightweight FastAPI test dependencies were installed into `target/phase3-python`; heavy models were not downloaded.

Commands run from repository root (Maven path supplied because the repository's pre-existing NetBeans wrapper points to an absent installation):

```powershell
$maven='C:\Users\Admin\AppData\Local\Programs\Apache\Maven\apache-maven-3.10.0\bin\mvn.cmd'
./scripts/test-phase3.ps1 -LocalFixture -Offline -Benchmark -MavenCommand $maven
./scripts/test-phase3.ps1 -LocalFixture -Offline -Benchmark -Test RevenueAggregatorDaoBenchmarkTest -MavenCommand $maven
$env:PYTHONPATH=(Join-Path (Get-Location) 'target/phase3-python')
& 'C:\Users\Admin\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -m pytest ai-service/tests/test_vision.py -q
$env:HOTEL_DESKTOP_API_URL='https://pms.invalid/api/'
./scripts/build-desktop.ps1 -MavenCommand $maven -JdkHome 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
```

Recorded evidence under ignored `target/` (the disposable MariaDB process was stopped after verification):

- `phase3-final-regression.log`: final full run with benchmark enabled:162 tests,0 failures,0 errors,0 skipped;32.374s. Warm benchmark in this final run:p95=8.455ms,max=10.162ms. Earlier `phase3-regression.log` run:162 tests,0 failures,0 errors,1 skipped explicit benchmark;28.565s. Covers Phase1/2 accounting, inventory races, idempotency, security and new Phase3 checks. Earlier contract regression failed because the old escalation fixture omitted canonical reason; fixture and validation order corrected, then passed. The final log supersedes earlier runs.
- `phase3-benchmark.log`: opt-in benchmark1 test passed;100,000 historical booking rows +730 known DBR snapshots in a rollback-only transaction;20 warmed aggregate reads; p95=16.511ms,max=26.329ms (<80ms). This measures the implemented snapshot DAO, not rescanning100k booking rows per request. Hikari's short leak detector warned during intentional fixture seeding; connection was returned and explicitly reported unleaked. This is not the2h leak gate.
- `phase3-contract.log`:4 focused tests passed including actual local mTLS server accepting correct client and rejecting missing identity/wrong hostname using disposable generated certificates. Docker PKI remains unverified.
- `phase3-pytest.log`:5 passed,1 AnyIO deprecation warning,0.45s. Synthetic vector boundaries/parser/invalid-image/no-echo/offline/model-missing/metric checks; no real OCR, recognition, Faiss or Prophet inference measured.
- `phase3-packaging.log`: Shade build success11.869s plus successful jdeps/jlink/jpackage app-image at `target/desktop-c7333d123606478fb2c475ad0158af75/package/QLKhachSan-Phase3Preview`. Reserved `.invalid` API origin; not released or launched. Final rebuilt artifact: `target/desktop-69ef1ce785c744308a9d956e23949d53/package/QLKhachSan-Phase3Preview`; `phase3-final-packaging.log` records successful final Shade build8.407s plus jlink/jpackage completion. It remains a preview, not a released candidate.
- `target/phase3-synthetic-cccd/`:100 generated visibly invalid cards and manifest, no real face/identity data. This is fixture-generation evidence only.

Crypto tests include1000 fresh IVs, tamper/AAD/wrong-key rejection, HMAC search and cutover repeatability/constraint; retention checks preserve ledger rows and verify chained audit. HTTP guardrail runs all100 attacks with no upstream invocation. Fake camera test proves blocked camera work is off EDT, not real EDT latency. Cache test proves exact6h expiry, not end-to-end <5ms. See pending gates in `phase3-release-checklist.md`, AI README, load and ZAP reports for required devices/data/commands.

## Remaining work and limits

No user decision is outstanding. Remaining blockers are operational datasets/models/certificates/devices and explicit acceptance execution, plus the documented implementation extensions (semantic multilingual escalation, capture persistence/rotation lifecycle, fully correlated/signed load fixtures). Guest AI remains default-off. A process-local blacklist loses revocations on restart and is appropriate only to the current single backend process; assess restart/token lifecycle before production sign-off. Hold the release until every mandatory target is measured and all Phase2/Phase3 sign-offs are complete.
