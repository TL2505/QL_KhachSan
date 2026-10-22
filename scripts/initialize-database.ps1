param([Parameter(Mandatory=$true)][ValidatePattern('^hotel_runtime_[a-z0-9_]{1,40}$')][string]$DatabaseName,
    [string]$DbAdminUser = 'root', [string]$DbAdminPasswordFile, [int]$Port = 3306,
    [string]$MavenCommand, [string]$JavaCommand = 'java', [switch]$Resume, [switch]$FinishBootstrap, [switch]$NoActivate)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'backend-common.ps1')
$configPath = Join-Path $projectRoot '.env'
$pendingRoot = Join-Path $projectRoot ".data/provisioning/$DatabaseName"
if ((Test-Path -LiteralPath $pendingRoot) -and -not $Resume) { throw 'This provisioning attempt already has saved keys. Inspect it before using -Resume; existing schemas require the explicit guarded -FinishBootstrap workflow.' }
$names = @('DB_URL','JDBC_URL','DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','DB_PASSWORD_FILE','AES_VAULT_KEY','AES_VAULT_KEY_FILE','HMAC_IDENTITY_KEY','HMAC_IDENTITY_KEY_FILE','JWT_SECRET','JWT_SECRET_FILE','HOTEL_NEW_DB_USER','HOTEL_NEW_DB_PASSWORD','HOTEL_BOOTSTRAP_PASSWORD')
$saved = @{}
foreach ($name in $names) { $saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process') }
function New-LocalSecret {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes); return [Convert]::ToBase64String($bytes) }
    finally { $rng.Dispose(); [Array]::Clear($bytes,0,$bytes.Length) }
}
Push-Location $projectRoot
try {
    New-Item -ItemType Directory -Path $pendingRoot -Force | Out-Null
    $JavaCommand = Get-BackendJava $JavaCommand
    if ($Resume) {
        $pending = Join-Path $pendingRoot 'database.env'
        if (-not (Test-Path -LiteralPath $pending -PathType Leaf)) { throw 'No saved pending configuration to resume.' }
        $newSettings = [ordered]@{}
        foreach ($line in [IO.File]::ReadAllLines($pending)) {
            $separator=$line.IndexOf('=')
            if ($separator -gt 0) { $newSettings[$line.Substring(0,$separator)]=$line.Substring($separator+1) }
        }
        if ($newSettings.DB_NAME -ne $DatabaseName) { throw 'Saved configuration selects a different database.' }
        $adminPassword=[IO.File]::ReadAllText($newSettings.HOTEL_VERIFY_PASSWORD_FILE).Trim()
        Write-Host 'Resuming with the saved keys. Existing data will not be reset.'
    } else {
        $newSettings = [ordered]@{
            DB_HOST='127.0.0.1'; DB_PORT=[string]$Port; DB_NAME=$DatabaseName
            DB_URL="jdbc:mysql://127.0.0.1:$Port/${DatabaseName}?useSSL=false&serverTimezone=UTC"
            DB_USER=('hotel_app_'+[Guid]::NewGuid().ToString('N').Substring(0,8)); DB_PASSWORD=(New-LocalSecret)
            DB_ALLOW_LOCAL_FALLBACK='false'; AES_VAULT_KEY=(New-LocalSecret); HMAC_IDENTITY_KEY=(New-LocalSecret)
            JWT_SECRET=(New-LocalSecret); HOTEL_VAT_RATE='0.08'; HOTEL_BIND_ADDRESS='127.0.0.1'
            HOTEL_VERIFY_USERNAME='hotel_admin'; HOTEL_VERIFY_PASSWORD_FILE=(Join-Path $pendingRoot 'admin-password.txt')
        }
        $adminPassword = New-LocalSecret
        [IO.File]::WriteAllText($newSettings.HOTEL_VERIFY_PASSWORD_FILE,$adminPassword)
        $pending = Join-Path $pendingRoot 'database.env'
        [IO.File]::WriteAllLines($pending,@($newSettings.GetEnumerator() | ForEach-Object { $_.Key+'='+$_.Value }))
    }
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$null,'Process') }
    $env:DB_URL="jdbc:mysql://127.0.0.1:$Port/?useSSL=false&serverTimezone=UTC"
    $env:DB_NAME=$DatabaseName; $env:DB_USER=$DbAdminUser
    if ($DbAdminPasswordFile) { $env:DB_PASSWORD_FILE=$DbAdminPasswordFile }
    $env:AES_VAULT_KEY=$newSettings.AES_VAULT_KEY; $env:HMAC_IDENTITY_KEY=$newSettings.HMAC_IDENTITY_KEY
    $env:JWT_SECRET=$newSettings.JWT_SECRET; $env:HOTEL_NEW_DB_USER=$newSettings.DB_USER
    $env:HOTEL_NEW_DB_PASSWORD=$newSettings.DB_PASSWORD; $env:HOTEL_BOOTSTRAP_PASSWORD=$adminPassword
    $classpath=Build-BackendClasspath (Get-BackendMaven $MavenCommand $projectRoot) $projectRoot
    $setupArguments=@('-cp',$classpath,'scripts/FreshDatabaseSetup.java')
    if ($FinishBootstrap) {
        if (-not $Resume) { throw '-FinishBootstrap requires -Resume and the saved pending keys.' }
        $setupArguments+='--finish-bootstrap'
    }
    & $JavaCommand @setupArguments
    if ($LASTEXITCODE -ne 0) { throw 'New database provisioning failed. The current .env was not changed. Pending keys are retained under .data/provisioning for recovery.' }
    if ($NoActivate) {
        Write-Host "New database $DatabaseName is ready; configuration is retained in .data/provisioning/$DatabaseName/database.env. Current .env was not changed."
        return
    }
    $oldLines = @()
    if (Test-Path -LiteralPath $configPath) {
        $previousConfig=Join-Path $pendingRoot 'previous.env'
        if (-not (Test-Path -LiteralPath $previousConfig)) { Copy-Item -LiteralPath $configPath -Destination $previousConfig }
        $oldLines = [IO.File]::ReadAllLines($configPath)
    }
    $replace = @($newSettings.Keys) + @('JDBC_URL','DB_PASSWORD_FILE','AES_VAULT_KEY_FILE','HMAC_IDENTITY_KEY_FILE','JWT_SECRET_FILE','HOTEL_VERIFY_PASSWORD')
    $keep = @($oldLines | Where-Object { $_ -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=' -or $matches[1] -notin $replace })
    $newLines = $keep + @('','# Persistent database configuration. Back up together with the matching SQL dump.') + @($newSettings.GetEnumerator() | ForEach-Object { $_.Key+'='+$_.Value })
    $staged = Join-Path $pendingRoot 'activated.env'
    [IO.File]::WriteAllLines($staged,$newLines)
    # Publish the complete file atomically; keep the first previous.env backup.
    if (Test-Path -LiteralPath $configPath) {
        $activationBackup=Join-Path $pendingRoot ('activation-backup-'+[Guid]::NewGuid().ToString('N')+'.env')
        [IO.File]::Replace($staged,$configPath,$activationBackup)
    }
    else { [IO.File]::Move($staged,$configPath) }
    Write-Host "Activated new database $DatabaseName at 127.0.0.1:$Port in .env."
    Write-Host 'Administrator: hotel_admin. Password is stored in .data/provisioning/<database>/admin-password.txt; no secrets are printed.'
    Write-Host 'Next: scripts/start-backend.ps1 -Background; scripts/check-backend.ps1 -HttpLogin'
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$saved[$name],'Process') }
    $adminPassword=$null; $newSettings=$null
    Pop-Location
}
