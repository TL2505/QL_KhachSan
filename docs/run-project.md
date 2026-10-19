# Start the backend on Windows

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

Open PowerShell and run:

```powershell
cd D:\dulieucam\JAVA\QL_KhachSan
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1
```

The launcher compiles the current source, generates a random local JWT signing secret when none is set, sets the default VAT rate to 8%, and starts `quanlykhachsan.backend.Main`. Keep the terminal open. Startup also runs the application's database schema updates and scheduled jobs.

If Maven is not on `PATH`, specify its location (this is the installed path on the current computer):

```powershell
.\scripts\start-backend.ps1 -MavenCommand 'C:\Users\Admin\AppData\Local\Programs\Apache\Maven\apache-maven-3.10.0\bin\mvn.cmd'
```

Database defaults are `localhost:3306`, database `hotel_prod_db`, user `root`, and an empty password. If your database differs, set these variables in the same terminal before starting:

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
| Login crashes with `Configure a JWT signing secret with at least 32 bytes` | Restart using the launcher above. Health alone does not verify JWT configuration. |
| Login returns HTTP 401 | Check the account username and password. |
| `Communications link failure` | Start MySQL/MariaDB and check the `DB_*` settings. |
| `Access denied for user` | Correct the database username/password and database permissions. |
| `Unknown database` | Initialize your local schema or correct `DB_NAME`. |
| `Address already in use` | Stop the previous backend in its terminal before restarting. |
| NetBeans path is missing | Use the launcher or pass the full installed Maven path. |
| Maven cannot download dependencies | Check network access and Maven settings, then run the launcher again. |
