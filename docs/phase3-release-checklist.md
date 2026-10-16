# Phase 3 release and migration checks

Status: **NOT RELEASED / NO GO-NO-GO ACCEPTANCE**. No production migration, Docker startup, two-hour load or ZAP scan was performed. Existing Phase 2 manual gates remain outstanding.

## Identity cutover

1. Rehearse on an explicitly disposable MariaDB 11 clone. Inventory all integrations that write `customers.identity_card`; stop writers for the cutover. Take an access-controlled encrypted backup and verify restore. Legacy backups contain clear identity data and must remain subject to restricted backup retention.
2. Operations supplies independent random 32-byte AES and HMAC keys, Base64 encoded, through `AES_VAULT_KEY_FILE` and `HMAC_IDENTITY_KEY_FILE`. Never put values in commands, Git, logs or default configuration. Use only one of an environment value and its `_FILE` equivalent. Provision JWT and database secrets separately.
3. With reviewed database settings and the application runtime classpath, execute `quanlykhachsan.backend.customer.IdentityVaultMigration --apply` during maintenance. The CLI applies the additive vault schema, encrypts legacy identity values transactionally, writes unique HMAC blind indexes and removes clear values, then installs the NULL-only constraint. Duplicate identities must be reconciled before retrying; do not bypass uniqueness. Re-running is supported.
4. Verify no non-NULL clear identity remains, the constraint is present, exact identity search and dedup work, staff CRUD succeeds, unauthorized profiles are denied, and accounting/FK balances match pre-cutover evidence. Startup refuses unfinished cutovers or missing keys.
5. Start the backend only after those checks. Do not revert to an old writer against the new schema. Rollback requires a coordinated application/database restore from the verified encrypted backup, not copying clear values back while services run. A key rotation tool/key-version migration is not supplied: plan and test rotation before production lifecycle sign-off. Loss of either key breaks supported behavior; escrow and restore testing are mandatory operational work.

Each encrypted value is a versioned AES-GCM envelope containing its own random 96-bit IV and authentication tag, bound to customer/purpose by AAD. A shared row IV is deliberately not used. Customers retain only HMAC identity indexes; partial identity search is replaced by exact normalized identity matching. Only Admin/Front Desk profile endpoints decrypt. Raw scans and face captures are optional vault fields; complete scan-to-vault capture persistence still requires acceptance work.

## Docker and certificates

Supply the required `${PMS_*}` variables listed in `docker-compose.prod.yml`, approved MariaDB/Nginx image digests, reviewed Phase 2 backend env file, pre-provisioned external database volume, models, audience-specific SOP indexes, web TLS certs, and AI PKCS12/PEM secrets. No database seed import runs in production. Resolve UID 10000/10001 access to mounted secrets and persistent volumes. Java identity/trust PKCS12 stores share the configured password file; AI server certificate SAN must include `ai-service`, and client certificates need appropriate clientAuth usage.

Review `docker compose -f docker-compose.prod.yml config --quiet`, build the images on staging, and inspect dependency/model compatibility before startup. `deploy/production_deploy_checklist.sh` starts containers and checks backend DB health, authenticated mTLS AI health, trusted public HTTPS health and elapsed startup <90s. It does not grant release acceptance. The AI health endpoint is liveness, not proof of trained models. Only Nginx publishes host ports; no AI, DB or backend host ports. Nginx overwrites X-Real-IP; Java trusts it only from the configured exact proxy address. Confirm spoof resistance and subnet/IP consistency. Validate resource limits with `docker stats` throughout measured inference/load, not merely by reading Compose.

## Model and native UI gates

Run Python 3.11 tests and actual approved models. Record dataset hashes, license/version, CPU, RAM, model warm/cold state, sample counts and distributions. Measure OCR >=96%, <=800ms; face <=400ms, FAR <0.01%, FRR <1.5% at 0.68; resource cap; isolated Faiss <15ms and retrieval precision; chronological forecast MAPE <8.5%. No synthetic fixture result replaces these measurements. The current heuristic escalation is not a validated multilingual sentiment/semantic-repeat model; evaluate and extend it against reviewed examples before enabling guest AI.

On the intended staff terminal, verify real camera 30 FPS, EDT p95/max <16ms, OCR autofill <1.2s, copilot lookup <500ms, native window layout, amber/manual decisions, orange blink/audio, scoped live chat, disconnect/retry and camera cleanup. Validate splash/cold start and idle RAM <180MB on the bundled JRE. The preview app uses an intentionally unreachable `pms.invalid` URL; rebuild with an approved API origin for acceptance. No native launch was performed here.

## Load, security and sign-off

Follow `tests/performance/jmeter_benchmark_report.md` and `docs/security/owasp_zap_scan_report.md`. Run only on authorized isolated staging, with synthetic accounts/rooms, reviewed endpoints, idempotency and signed financial fixtures. Do not run long load or active scans from this implementation session. Measure all WBS gates, retain redacted evidence, restore test state, and obtain the human release signatures. Do not reuse the WBS's preprinted approval text as execution evidence.
