param([string]$MavenCommand, [string]$JavaCommand = 'java', [string]$DbUser = 'root', [string]$DbPassword = '', [switch]$ReloadSample)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$demoRoot = Join-Path $projectRoot '.run/demo'
if (-not $MavenCommand) {
    $shim = Join-Path $projectRoot 'mvn.cmd'
    $maven = Get-Command mvn.cmd -All -ErrorAction SilentlyContinue |
        Where-Object { $_.Source -ne $shim } | Select-Object -First 1
    if (-not $maven) { throw 'Install Maven or pass -MavenCommand with its full path.' }
    $MavenCommand = $maven.Source
}
if ([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners().Port -contains 8081) {
    throw 'Port 8081 is occupied. Stop the existing backend before starting the demo.'
}
$keys = @('GOOGLE_CLIENT_ID','SMTP_HOST','SMTP_PORT','SMTP_USERNAME','SMTP_APP_PASSWORD','SMTP_FROM','HOTEL_BIND_ADDRESS',
    'DB_URL','JDBC_URL','DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','DB_PASSWORD_FILE',
    'AES_VAULT_KEY','AES_VAULT_KEY_FILE','HMAC_IDENTITY_KEY','HMAC_IDENTITY_KEY_FILE',
    'JWT_SECRET','JWT_SECRET_FILE','HOTEL_VAT_RATE','HOTEL_OTP_MODE','AI_GUEST_ENABLED')
$saved = @{}
foreach ($key in $keys) { $saved[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
Push-Location $projectRoot
try {
    New-Item -ItemType Directory -Force -Path $demoRoot | Out-Null
    $envFile = Join-Path $projectRoot '.env'
    if (Test-Path -LiteralPath $envFile) {
        $lineNumber = 0
        foreach ($line in [IO.File]::ReadAllLines($envFile)) {
            $lineNumber++
            $entry = $line.Trim()
            if (-not $entry -or $entry.StartsWith('#')) { continue }
            $separator = $entry.IndexOf('=')
            if ($separator -lt 1) { throw "Invalid .env entry on line $lineNumber. Expected NAME=VALUE." }
            $name = $entry.Substring(0, $separator).Trim()
            if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') { throw "Invalid variable name in .env on line $lineNumber." }
            $value = $entry.Substring($separator + 1).Trim()
            if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            if (-not [Environment]::GetEnvironmentVariable($name, 'Process') -and $value) {
                [Environment]::SetEnvironmentVariable($name, $value, 'Process')
            }
        }
        Write-Host 'Loaded local environment settings from .env (existing process variables take precedence).'
    }
    $googleClientFile = Join-Path $demoRoot 'google-client-id.txt'
    if (-not $env:GOOGLE_CLIENT_ID -and (Test-Path -LiteralPath $googleClientFile)) {
        $googleClientId = [IO.File]::ReadAllText($googleClientFile).Trim()
        if ($googleClientId -notmatch '^[A-Za-z0-9_-]+\.apps\.googleusercontent\.com$') {
            throw 'Invalid local Google OAuth client ID. Store only the Web client ID, not the downloaded JSON or Client Secret.'
        }
        $env:GOOGLE_CLIENT_ID = $googleClientId
        Write-Host 'Google OAuth client ID loaded from local demo config; SMTP is configured separately.'
    }
    $schemaFile = Join-Path $demoRoot 'database-name.txt'
    $readyFile = Join-Path $demoRoot 'ready'
    $seedVersionFile = Join-Path $demoRoot 'seed-version.txt'
    $seedVersion = 'hotel-sql-v1'
    $initialize = $ReloadSample -or -not (Test-Path -LiteralPath $readyFile) -or
        -not (Test-Path -LiteralPath $seedVersionFile)
    if (-not $initialize) { $initialize = [IO.File]::ReadAllText($seedVersionFile).Trim() -ne $seedVersion }
    if ((Test-Path -LiteralPath $readyFile) -and -not (Test-Path -LiteralPath $schemaFile)) {
        throw 'Demo database-name.txt is missing. Restore it before restarting.'
    }
    if ($initialize) { $schema = 'hotel_frontend_demo_' + [Guid]::NewGuid().ToString('N').Substring(0,12) }
    else { $schema = [IO.File]::ReadAllText($schemaFile).Trim() }
    if ($schema -notmatch '^hotel_frontend_demo_[0-9a-f]{12}$') { throw 'Invalid saved demo schema name.' }
    if (-not $initialize -and [IO.File]::ReadAllText($readyFile).Trim() -ne $schema) {
        throw 'Demo schema and readiness marker do not match.'
    }
    foreach ($name in @('AES_VAULT_KEY','HMAC_IDENTITY_KEY','JWT_SECRET')) {
        $keyFile = Join-Path $demoRoot "$name.key"
        if (-not (Test-Path -LiteralPath $keyFile)) {
            if (Test-Path -LiteralPath $readyFile) { throw "Demo key $name is missing. Restore the original demo key file." }
            $bytes = New-Object byte[] 32
            $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
            try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
            [IO.File]::WriteAllText($keyFile, [Convert]::ToBase64String($bytes))
            [Array]::Clear($bytes,0,$bytes.Length)
        }
        [Environment]::SetEnvironmentVariable($name, [IO.File]::ReadAllText($keyFile).Trim(), 'Process')
        [Environment]::SetEnvironmentVariable("${name}_FILE", $null, 'Process')
    }
    $env:DB_HOST = '127.0.0.1'; $env:DB_PORT = '3306'; $env:DB_NAME = $schema
    $env:DB_USER = $DbUser; $env:DB_PASSWORD = $DbPassword
    $env:DB_PASSWORD_FILE = $null; $env:JDBC_URL = $null
    $env:DB_URL = "jdbc:mysql://127.0.0.1:3306/${schema}?useSSL=false&serverTimezone=UTC"
    $env:HOTEL_BIND_ADDRESS = '127.0.0.1'
    $env:HOTEL_VAT_RATE = '0.08'; $env:HOTEL_OTP_MODE = 'mock'; $env:AI_GUEST_ENABLED = 'false'
    & $MavenCommand '-q' '-DskipTests' 'compile' 'dependency:build-classpath' '-Dmdep.outputFile=target/demo-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Demo backend build failed.' }
    $classpath = (Join-Path $projectRoot 'target/classes') + ';' + [IO.File]::ReadAllText((Join-Path $projectRoot 'target/demo-classpath.txt')).Trim()
    if ($initialize) {
        & $JavaCommand '-cp' $classpath 'scripts/DemoDatabaseSetup.java'
        if ($LASTEXITCODE -ne 0) { throw 'Demo initialization failed. Existing schemas have not been reset; inspect the error before retrying.' }
        [IO.File]::WriteAllText($schemaFile, $schema)
        [IO.File]::WriteAllText($readyFile, $schema)
        [IO.File]::WriteAllText($seedVersionFile, $seedVersion)
    }
    Write-Host "Demo database: $schema (local only)"
    Write-Host 'Website: http://localhost:8081/ | Portal: http://localhost:8081/portal.html'
    Write-Host 'Accounts: demo_admin / demo_guest | Password: DemoHotel2026!'
    Write-Host 'Keep this terminal open; press Ctrl+C to stop.'
    & $JavaCommand '-cp' $classpath 'quanlykhachsan.backend.Main'
    if ($LASTEXITCODE -ne 0) { throw "Demo backend exited with code $LASTEXITCODE." }
} finally {
    foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key,$saved[$key],'Process') }
    Pop-Location
}
