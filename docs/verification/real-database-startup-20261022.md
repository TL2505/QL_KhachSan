# Persistent database startup verification — 2026-10-22

## Scope and original failure

The operator confirmed the original database is `192.168.56.102:3306/hotel_prod_db` and its original identity keys are no longer available. A direct TCP/JDBC check timed out; local MariaDB was reachable. The original real launcher stopped at missing `AES_VAULT_KEY` before opening HTTP. Its `.env` contained Google/SMTP settings but no DB/vault/JWT configuration. `start-backend.bat` previously called the demo launcher, and the database resolver could silently fall back to localhost on Windows.

After the operator authorized a new database, created `127.0.0.1:3306/hotel_runtime_20261022` on the existing local **MariaDB 11.4.13** service. This is an explicit new environment, not a successful connection to the original VM. No original schema or identity key was replaced. Historical ciphertext in the old database has not been recovered or verified; recovery still requires its original AES/HMAC keys and a reachable VM.

## Verified behavior

| Check | Result |
| --- | --- |
| Startup without demo launcher | PASS: direct `quanlykhachsan.backend.Main` through `scripts/start-backend.ps1` |
| Persistent configuration | PASS: `.env` retains DB credentials and independent AES/HMAC/JWT values; existing Google/SMTP settings preserved |
| Database grants | PASS: generated DB user authenticates and runs startup migrations within the new schema |
| Schema | PASS: baseline, prerequisites, manifest migrations and runtime updates; required auth/device/vault columns and identity constraint checked |
| Login | PASS: `hotel_admin` password accepted; HTTP login returns token; authenticated account profile returns 200 |
| Vault persistence | PASS: one explicitly labelled synthetic identity saved during provisioning, authenticated with AES and its HMAC compared after process restart |
| Restart | PASS: stopped the created backend process; started it again from saved `.env`; health `UP/CONNECTED`, login and vault checks passed |
| Wrong AES | PASS: read-only diagnostics exit 2, SQLState `VLT01`; original key and stored data untouched |
| Wrong HMAC | PASS: read-only diagnostics exit 2, SQLState `VLT02`; ciphertext remains readable with correct AES but mismatched index is rejected |
| Wrong application password | PASS: HTTP 401; server records a credential rejection without printing username/password |
| Original VM unavailable | PASS: independent SQLState `08...` connection failure, with no localhost fallback |
| Saved configuration safety | PASS: `.env` checksum unchanged by negative diagnostics |
| Fresh initializer path | PASS: separate `hotel_runtime_bootstrap_test_20261022` created from scratch with `-NoActivate`; schema/vault/account checked without switching active `.env` |
| Per-dev configuration isolation | PASS: check a different schema through `-EnvFile`, then check active `.env` in the same terminal; targets and key pairs remain correct |

The backend runs on `http://localhost:8081/`. Its current startup and login logs are in ignored `.run/backend-real-stdout.log` and `.run/backend-real-stderr.log`. The local admin password is in `.data/provisioning/hotel_runtime_20261022/admin-password.txt`; no credential values are copied into this report. The DB service was already running; restarting the entire operating system or automatically starting MariaDB after an OS reboot was not tested or configured.

## Regression coverage

- Maven: **13 tests passed** across `GuestIdentityVaultVerificationTest`, `AesGcmEncryptionUtilTest`, `ApiConfigFallbackTest`, `LoginDeviceControllerTest`, `AccountSecurityControllerTest`.
- Environment loader: **8 assertions passed**, covering quotes/embedded equals and hashes, process precedence, paired value/`_FILE` overrides, source conflicts, duplicate/malformed entries and restoration of caller configuration. Checked with Windows PowerShell as well as the working shell.
- Fresh provisioning and live HTTP/SQL checks used the local MariaDB service. No test email or payment was submitted.

Private raw evidence is retained under `.run/real-db-*.log`. These files and all secret/configuration files are Git-ignored. The backend remains running with the new environment. The temporary bootstrap test schema and its generated DB user were removed after verifying the exact target, synthetic data, empty bookings and matching vault keys; the active database and original VM database were retained. Exported key companion files were compared to their respective source configurations without printing values.

## Operational guidance

See the [per-machine configuration, database/key handoff and troubleshooting runbook](../vi/real-database-startup.md). A received SQL dump must be paired with its original AES/HMAC keys; unrelated local database passwords and JWT/SMTP/API secrets should not be copied indiscriminately. An empty vault cannot prove historical key ownership. The synthetic marker proves persistence for the newly created database only.
