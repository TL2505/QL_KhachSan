# Start the backend on Windows

For the persistent database workflow, per-machine `.env` setup and transferring a database with its matching keys, use the [real database runbook (Vietnamese)](vi/real-database-startup.md). Double-click `start-backend.bat` to invoke the existing PowerShell launcher with `-Background`, or `stop-backend.bat` to stop the recorded workspace backend. Desktop build and standalone web batch wrappers remain removed.

## Local demo for frontend testing

With local MariaDB running on port `3306`, run from the repository root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-demo.ps1
```

This creates a new `hotel_frontend_demo_<random>` schema and automatically loads the sample data from `database/hotel.sql`, including rooms, customers, bookings, invoices, payments, reviews, and promotions. It skips the dump's server-wide AI account/grants, applies all manifest migrations and runtime schema updates, assigns distinct `SAMPLE_<id>` codes to legacy promotions, and encrypts imported identity numbers with the persistent demo keys. The launcher targets only localhost and does not select the application's existing schema. By default it uses local database user `root` with an empty password; pass `-DbUser` and `-DbPassword` if needed.

- Website: http://localhost:8081/
- Guest portal: http://localhost:8081/portal.html
- Accounts: `demo_admin` (administrator) and `demo_guest` (guest).
- Demo password for both accounts: `DemoHotel2026!`.

The original sample accounts and their password hashes are retained. `demo_guest` is linked to the first imported customer so the guest portal shows that customer's sample stays. The supplied sample and migrations currently provide 15 rooms, 13 customers, 131 bookings, 10 services, six invoices, and six payments. Historical dates are retained as supplied.

Keep the terminal open. Press Ctrl+C to stop, then run the same command to restart. The schema name and generated AES, HMAC, and JWT keys persist in the Git-ignored `.run/demo/` folder. Retain that folder to keep using the same demo database. The mock OTP setting is for this local demo; bank/payment and AI integrations still require their own configuration.

The first run after upgrading from the minimal demo automatically switches to a new schema loaded from the sample. Normal restarts reuse it without reimporting or duplicating rows. To load a fresh copy of the current sample file later, stop the demo and run:

```powershell
.\scripts\start-demo.ps1 -ReloadSample
```

Reloading creates another new demo schema and switches to it only after successful initialization. Earlier demo schemas remain available, including any test data you added. An initialization failure leaves the previous saved demo selection intact.

## 1. Prerequisites

- Install JDK 17 and Maven. Set `JAVA_HOME` to the JDK folder and add Maven's `bin` folder to `PATH`.
- Start MySQL/MariaDB (or MySQL in XAMPP) on port `3306`.
- The database configured by `DB_URL` or `DB_NAME` in `.env` must already exist. The current local QA setup uses `hotel_runtime_20261022`; initialize a new local schema from `database/hotel.sql` only when you intend to create a fresh demo database.

Check Java and Maven in PowerShell:

```powershell
java -version
Get-Command mvn.cmd -All
```

The root `mvn.cmd` is a legacy NetBeans shortcut and may point to a missing installation. The launcher below selects installed Maven instead.

## 2. Start the backend

Supply the existing database's identity encryption and lookup keys in the same PowerShell terminal. Both must be Base64 encodings of exactly 32 bytes. Use the original keys for a database that already has vault records; changing them makes existing identities unreadable or unsearchable. Secret files contain only the Base64 value:

```powershell
$env:AES_VAULT_KEY_FILE = 'C:\path\to\existing-aes-vault.key'
$env:HMAC_IDENTITY_KEY_FILE = 'C:\path\to\existing-hmac-identity.key'
```

Alternatively set `AES_VAULT_KEY` and `HMAC_IDENTITY_KEY` directly in the process environment. Configure only one source per key. The launcher validates these settings before starting Maven. Store key files outside Git and retain them across restarts.

Open PowerShell and run:

```powershell
cd D:\dulieucam\JAVA\QL_KhachSan
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1
```

The launcher reads the repository-root `.env`, validates the stable AES/HMAC/JWT configuration, compiles current source into `.run/backend-build`, runs read-only database/vault preflight and starts `quanlykhachsan.backend.Main` directly. It never generates replacement keys or imports sample data. Keep the terminal open, or pass `-Background` for a detached backend with logs in `.run/backend-real-stdout.log` and `.run/backend-real-stderr.log`. Startup also runs the application's existing database schema updates and scheduled jobs after preflight succeeds.

If Maven is not on `PATH`, specify its location (this is the installed path on the current computer):

```powershell
.\scripts\start-backend.ps1 -MavenCommand 'C:\Users\Admin\AppData\Local\Programs\Apache\Maven\apache-maven-3.10.0\bin\mvn.cmd'
```

The checked-in `config.properties` and local `.env` target the MariaDB QA database on `localhost:3306` (`hotel_runtime_20261022`). `start-backend.bat` loads `.env`; keep `DB_URL` and `DB_HOST` pointed at the same local instance used by HeidiSQL. To select that database in the terminal instead, set:

```powershell
$env:DB_HOST = 'localhost'
$env:DB_PORT = '3306'
$env:DB_NAME = 'hotel_runtime_20261022'
$env:DB_USER = 'root'
$env:DB_PASSWORD = 'your-local-database-password'
```

The Windows launcher loads `.env`; process variables take precedence, treating a secret value and its `_FILE` form as one setting. Direct Java invocation still requires exported environment variables. Put a stable `JWT_SECRET` with at least 32 UTF-8 bytes in `.env` or a secret file; no secret is generated at startup. Also configure `HOTEL_VAT_RATE=0.08`. New independent local databases can be provisioned once with `scripts/initialize-database.ps1`; received SQL dumps must retain the sender's original AES/HMAC keys instead. Do not commit `.env` or transfer unrelated SMTP/API credentials.

## 3. Verify and test

Wait for the server startup message, then open another PowerShell terminal:

```powershell
Invoke-RestMethod http://localhost:8081/api/v1/health
```

Expected: `status` is `UP` and `database` is `CONNECTED`.

- Website: http://localhost:8081/
- API base: http://localhost:8081/api/v1
- Login: `POST http://localhost:8081/api/v1/auth/login` with JSON containing `username` and `password` for your existing account.

## 4. Stop or restart

For a foreground launch, press **Ctrl+C** in the backend terminal. For a background launch, double-click `stop-backend.bat` or run `scripts/stop-backend.ps1`. Wait for shutdown before starting again. Only one backend can use port `8081` at a time.

## Troubleshooting

| Problem | Action |
| --- | --- |
| `AES_VAULT_KEY is required` or `HMAC_IDENTITY_KEY is required` | Supply the original database keys using the environment variables or `_FILE` settings above. |
| JWT signing secret is missing/too short | Supply a stable `JWT_SECRET` in `.env`; startup now checks it before migration and HTTP traffic. |
| Vault `VLT01` / `VLT02` | Restore the matching original AES / HMAC key; do not rewrite existing ciphertext or indexes. See the real database runbook. |
| Login returns HTTP 401 | Check the account username and password. |
| `Communications link failure` | Start MySQL/MariaDB and check the `DB_*` settings. |
| `Access denied for user` | Correct the database username/password and database permissions. |
| `Unknown database` | Initialize your local schema or correct `DB_NAME`. |
| `Address already in use` | Stop the previous backend in its terminal before restarting. |
| NetBeans path is missing | Use the launcher or pass the full installed Maven path. |
| Maven cannot download dependencies | Check network access and Maven settings, then run the launcher again. |
