$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$runRoot = Join-Path $projectRoot '.run'
$stdoutLog = Join-Path $runRoot 'backend-stdout.log'
$stderrLog = Join-Path $runRoot 'backend-stderr.log'
$port = 8081

New-Item -ItemType Directory -Force -Path $runRoot | Out-Null

function Test-BackendHealth {
    try {
        $response = Invoke-RestMethod -Uri "http://127.0.0.1:$port/api/v1/health" -TimeoutSec 2
        return $response
    } catch {
        return $null
    }
}

$health = Test-BackendHealth
if ($health -and $health.status -eq 'UP' -and $health.database -eq 'CONNECTED') {
    Write-Host "Backend is already running: http://localhost:$port/"
    Write-Host "Health: $($health.status); database: $($health.database)"
    exit 0
}

$listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) {
    throw "Port $port is occupied by PID $($listener.OwningProcess), but the portal health check failed. Stop that process before retrying."
}

$dbClient = [System.Net.Sockets.TcpClient]::new()
try {
    $connect = $dbClient.BeginConnect('127.0.0.1', 3306, $null, $null)
    if (-not $connect.AsyncWaitHandle.WaitOne(1200)) { throw 'Database connection timeout.' }
    $dbClient.EndConnect($connect)
} catch {
    $mysql = 'C:\xampp\mysql\bin\mysqld.exe'
    $mysqlConfig = 'C:\xampp\mysql\bin\my.ini'
    if (-not (Test-Path -LiteralPath $mysql) -or -not (Test-Path -LiteralPath $mysqlConfig)) {
        throw 'MariaDB is not listening on 127.0.0.1:3306, and XAMPP MariaDB was not found at C:\xampp. Start MariaDB, then click this file again.'
    }
    Write-Host 'MariaDB is stopped. Starting XAMPP MariaDB...'
    Start-Process -FilePath $mysql -ArgumentList @("--defaults-file=$mysqlConfig", '--standalone') -WorkingDirectory 'C:\xampp' -WindowStyle Hidden | Out-Null

    $dbReady = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        Start-Sleep -Seconds 1
        $probe = [System.Net.Sockets.TcpClient]::new()
        try {
            $pending = $probe.BeginConnect('127.0.0.1', 3306, $null, $null)
            if ($pending.AsyncWaitHandle.WaitOne(500)) {
                $probe.EndConnect($pending)
                $dbReady = $true
                break
            }
        } catch {
        } finally {
            $probe.Dispose()
        }
    }
    if (-not $dbReady) { throw 'MariaDB did not open port 3306. Check C:\xampp\mysql\data\mysql_error.log.' }
} finally {
    $dbClient.Dispose()
}

$powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
$launcher = Join-Path $PSScriptRoot 'start-demo.ps1'
Write-Host 'Starting the hotel demo backend...'
$process = Start-Process -FilePath $powershell -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $launcher) -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog -PassThru

for ($attempt = 0; $attempt -lt 60; $attempt++) {
    Start-Sleep -Seconds 2
    $health = Test-BackendHealth
    if ($health -and $health.status -eq 'UP' -and $health.database -eq 'CONNECTED') {
        Write-Host "Backend is ready: http://localhost:$port/"
        Write-Host "Health: $($health.status); database: $($health.database)"
        Write-Host "Demo login: demo_admin or demo_guest / DemoHotel2026!"
        exit 0
    }
    if ($process.HasExited) {
        $details = Get-Content -LiteralPath $stderrLog -Tail 20 -ErrorAction SilentlyContinue
        if ($details) { $details | ForEach-Object { Write-Host $_ } }
        $details = Get-Content -LiteralPath $stdoutLog -Tail 20 -ErrorAction SilentlyContinue
        if ($details) { $details | ForEach-Object { Write-Host $_ } }
        throw "Backend launcher exited with code $($process.ExitCode). See $stderrLog and $stdoutLog."
    }
}

throw "Backend did not become healthy within 120 seconds. Check $stderrLog and $stdoutLog."
