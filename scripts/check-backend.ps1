param([ValidateSet('connection','schema','vault','account','all')][string]$Mode = 'all', [string]$EnvFile, [string]$MavenCommand, [string]$JavaCommand = 'java', [switch]$HttpLogin)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'backend-common.ps1')
$environmentBefore=Save-BackendEnvironment
if (-not $EnvFile) { $EnvFile = Join-Path $projectRoot '.env' }
Push-Location $projectRoot
try {
    Import-BackendEnvironment $EnvFile
    $JavaCommand = Get-BackendJava $JavaCommand
    $classpath = Build-BackendClasspath (Get-BackendMaven $MavenCommand $projectRoot) $projectRoot
    & $JavaCommand '-cp' $classpath 'quanlykhachsan.backend.utils.BackendDiagnostics' $Mode
    if ($LASTEXITCODE -ne 0) { throw 'Read-only diagnostics failed. Review the stage labels above.' }
    if ($HttpLogin) {
        $password = $env:HOTEL_VERIFY_PASSWORD
        if ($env:HOTEL_VERIFY_PASSWORD_FILE) { $password = [IO.File]::ReadAllText($env:HOTEL_VERIFY_PASSWORD_FILE).Trim() }
        if (-not $env:HOTEL_VERIFY_USERNAME -or -not $password) { throw 'Supply HOTEL_VERIFY_USERNAME and HOTEL_VERIFY_PASSWORD or its _FILE setting.' }
        $body = @{ username=$env:HOTEL_VERIFY_USERNAME; password=$password } | ConvertTo-Json -Compress
        try {
            $result = Invoke-RestMethod 'http://127.0.0.1:8081/api/v1/auth/login' -Method Post -ContentType 'application/json; charset=utf-8' -Body $body -TimeoutSec 10
            if (-not $result.data.token) { throw 'Login returned no token' }
            $headers = @{Authorization=('Bearer '+$result.data.token)}
            $profile = Invoke-WebRequest 'http://127.0.0.1:8081/api/v1/account/profile' -Headers $headers -UseBasicParsing -TimeoutSec 10
            if ($profile.StatusCode -ne 200) { throw 'Profile check failed' }
            Write-Host '[PASS HTTP_LOGIN] Token issued and accepted by authenticated profile endpoint; token and profile are not logged.'
        } catch { throw '[FAIL HTTP_LOGIN] Check backend-real logs. HTTP 401: account/password; 403: role; 503: login-device schema/DB; 500: server/JWT/DB. No credentials are logged.' }
        finally { $password=$null; $body=$null; $result=$null; $headers=$null; $profile=$null }
    }
} finally { Restore-BackendEnvironment $environmentBefore; Pop-Location }
