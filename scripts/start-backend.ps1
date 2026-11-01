param([string]$MavenCommand, [string]$JavaCommand = 'java', [string]$EnvFile, [switch]$Background)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'backend-common.ps1')
$environmentBefore=Save-BackendEnvironment
if (-not $EnvFile) { $EnvFile = Join-Path $projectRoot '.env' }
Push-Location $projectRoot
try {
    Import-BackendEnvironment $EnvFile
    $JavaCommand = Get-BackendJava $JavaCommand
    if ($env:DB_ALLOW_LOCAL_FALLBACK -eq 'true') { throw 'Disable DB_ALLOW_LOCAL_FALLBACK for the real backend. Select the database explicitly.' }
    if (-not $env:DB_URL -and -not $env:JDBC_URL -and (-not $env:DB_HOST -or -not $env:DB_NAME)) { throw 'Set DB_HOST and DB_NAME, or DB_URL, in .env. No implicit database selection is allowed.' }
    foreach ($name in @('AES_VAULT_KEY','HMAC_IDENTITY_KEY','JWT_SECRET')) {
        $value = [Environment]::GetEnvironmentVariable($name, 'Process')
        $file = [Environment]::GetEnvironmentVariable("${name}_FILE", 'Process')
        if ($value -and $file) { throw "Configure only one of $name or ${name}_FILE." }
        if ($file) { try { $value = [IO.File]::ReadAllText($file).Trim() } catch { throw "Required secret file for $name is unavailable." } }
        if (-not $value) { throw "$name is required in .env. Restore existing keys; never generate replacements for an existing vault." }
        if ($name -eq 'JWT_SECRET') {
            if ([Text.Encoding]::UTF8.GetByteCount($value) -lt 32) { throw 'JWT_SECRET must contain at least 32 UTF-8 bytes.' }
        } else {
            try { $bytes = [Convert]::FromBase64String($value) } catch { throw "$name must be Base64 encoded." }
            try { if ($bytes.Length -ne 32) { throw "$name must decode to exactly 32 bytes." } } finally { [Array]::Clear($bytes,0,$bytes.Length) }
        }
    }
    $MavenCommand = Get-BackendMaven $MavenCommand $projectRoot
    $classpath = Build-BackendClasspath $MavenCommand $projectRoot
    & $JavaCommand '-cp' $classpath 'quanlykhachsan.backend.utils.BackendDiagnostics' 'startup'
    if ($LASTEXITCODE -ne 0) { throw 'Database/key preflight failed. See diagnostics above; no startup migration was run.' }
    $runRoot = Join-Path $projectRoot '.run'
    if ($Background) {
        if ([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners().Port -contains 8081) { throw 'Port 8081 is occupied. Stop the existing process before starting this configured database.' }
        $javaPath = (Get-Command $JavaCommand -ErrorAction Stop).Source
        $process = Start-Process -FilePath $javaPath -ArgumentList @('-cp',('"'+$classpath+'"'),'quanlykhachsan.backend.Main') -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runRoot 'backend-real-stdout.log') -RedirectStandardError (Join-Path $runRoot 'backend-real-stderr.log') -PassThru
        $process.Id | Set-Content (Join-Path $runRoot 'backend-real.pid')
        for ($attempt = 0; $attempt -lt 45; $attempt++) {
            Start-Sleep -Seconds 1
            $process.Refresh()
            if ($process.HasExited) { throw 'Backend exited during startup. See .run/backend-real-stderr.log and backend-real-stdout.log.' }
            try { $health = Invoke-RestMethod 'http://127.0.0.1:8081/api/v1/health' -TimeoutSec 2 } catch { continue }
            if ($health.status -eq 'UP' -and $health.database -eq 'CONNECTED') { Write-Host 'Backend ready: http://localhost:8081/'; return }
        }
        throw 'Startup timed out. Inspect .run/backend-real-*.log and stop the recorded PID before retrying.'
    } else {
        Write-Host 'Starting backend with the saved .env configuration. Press Ctrl+C to stop.'
        & $JavaCommand '-cp' $classpath 'quanlykhachsan.backend.Main'
        if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE." }
    }
} finally { Restore-BackendEnvironment $environmentBefore; Pop-Location }
