# Start the backend on Windows

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
- The `hotel_prod_db` database must already exist. For a new local database, initialize it from `database/hotel.sql`; do not import this dump over a database containing data you need.

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

The launcher compiles the current source, generates a random local JWT signing secret when none is set, sets the default VAT rate to 8%, and starts `quanlykhachsan.backend.Main`. The Maven main class is configurable through `-Dexec.mainClass`; its default remains the desktop frontend. Keep the terminal open. Startup also runs the application's database schema updates and scheduled jobs.

If Maven is not on `PATH`, specify its location (this is the installed path on the current computer):

```powershell
.\scripts\start-backend.ps1 -MavenCommand 'C:\Users\Admin\AppData\Local\Programs\Apache\Maven\apache-maven-3.10.0\bin\mvn.cmd'
```

The checked-in `config.properties` targets the VM at `192.168.56.102:3306`; on Windows, an unreachable VM falls back to localhost on the same port. To explicitly select your local database, set these variables in the same terminal before starting:

```powershell
$env:DB_HOST = 'localhost'
$env:DB_PORT = '3306'
$env:DB_NAME = 'hotel_prod_db'
$env:DB_USER = 'root'
$env:DB_PASSWORD = 'your-local-database-password'
```

The Java backend does not automatically load `.env` files. Pass settings through environment variables. `JWT_SECRET` must contain at least 32 UTF-8 bytes; the launcher handles this for local testing. A new generated secret invalidates tokens from previous runs, so log in again after restarting. For deployment, provide a stable secret through your deployment's secret configuration.

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

Press **Ctrl+C** in the backend terminal. Wait for shutdown before starting again. Only one backend can use port `8081` at a time.

## Troubleshooting

| Problem | Action |
| --- | --- |
| `AES_VAULT_KEY is required` or `HMAC_IDENTITY_KEY is required` | Supply the original database keys using the environment variables or `_FILE` settings above. |
| Login crashes with `Configure a JWT signing secret with at least 32 bytes` | Restart using the launcher above. Health alone does not verify JWT configuration. |
| Login returns HTTP 401 | Check the account username and password. |
| `Communications link failure` | Start MySQL/MariaDB and check the `DB_*` settings. |
| `Access denied for user` | Correct the database username/password and database permissions. |
| `Unknown database` | Initialize your local schema or correct `DB_NAME`. |
| `Address already in use` | Stop the previous backend in its terminal before restarting. |
| NetBeans path is missing | Use the launcher or pass the full installed Maven path. |
| Maven cannot download dependencies | Check network access and Maven settings, then run the launcher again. |
