param([string]$BackendOrigin = 'http://localhost:8081', [string]$WebOrigin = 'http://localhost:3000')
# Read-only diagnostics. No booking POST, database migration, credential output or key generation.
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
Add-Type -AssemblyName System.Net.Http
function Read-Endpoint([string]$Label, [string]$Url, [bool]$Health = $false) {
    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromSeconds(10)
    try {
        $reply = $client.GetAsync($Url).GetAwaiter().GetResult()
        try {
            Write-Host "$Label HTTP $([int]$reply.StatusCode)"
            if ($Health -and $reply.IsSuccessStatusCode) {
                $data = $reply.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
                Write-Host "  status=$($data.status); database=$($data.database)"
            }
            if ($Label -eq 'Website' -and $reply.IsSuccessStatusCode) {
                $html = $reply.Content.ReadAsStringAsync().GetAwaiter().GetResult()
                Write-Host "  reload.js injected/present: $($html -match 'reload\.js')"
            }
        } finally { $reply.Dispose() }
    } catch { Write-Host "$Label could not be reached ($($_.Exception.GetType().Name))." }
    finally { $client.Dispose() }
}
Read-Endpoint 'Backend health' "$($BackendOrigin.TrimEnd('/'))/api/v1/health" $true
Read-Endpoint 'Public reviews (anonymous GET)' "$($BackendOrigin.TrimEnd('/'))/api/v1/reviews/public?page=1&limit=6"
Read-Endpoint 'Website' "$($WebOrigin.TrimEnd('/'))/index.html"
Write-Host 'Key configuration availability (values are never printed):'
foreach ($scope in @('Process','User','Machine')) {
    foreach ($name in @('AES_VAULT_KEY','HMAC_IDENTITY_KEY')) {
        $direct = [Environment]::GetEnvironmentVariable($name, $scope)
        $file = [Environment]::GetEnvironmentVariable("${name}_FILE", $scope)
        Write-Host "  $scope ${name}: direct configured=$([bool]$direct); file configured=$([bool]$file)"
        $direct = $null; $file = $null
    }
}
Write-Host 'Source checks:'
$controller = Get-Content .\src\quanlykhachsan\backend\booking\PublicBookingController.java -Raw
$guest = Get-Content .\src\quanlykhachsan\backend\customer\GuestIngestionService.java -Raw
Write-Host "  Booking failure correlation present: $($controller.Contains('BookingFailureLog.record'))"
Write-Host "  Encrypted identity capture present: $($guest.Contains('vault.save'))"
try {
    $port = ([uri]$BackendOrigin).Port
    $listener = Get-NetTCPConnection -LocalPort $port -State Listen | Select-Object -First 1
    if ($listener) {
        $backendProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
        Write-Host "Backend process: PID=$($backendProcess.ProcessId); executable=$($backendProcess.ExecutablePath)"
        $jcmd = Join-Path (Split-Path $backendProcess.ExecutablePath -Parent) 'jcmd.exe'
        if (Test-Path $jcmd) {
            # Do not print VM.command_line: arguments can contain credentials.
            & $jcmd $backendProcess.ProcessId VM.system_properties 2>$null |
                Where-Object { $_ -match '^(java.class.path|user.dir)=' } | ForEach-Object { Write-Host $_ }
        }
    }
} catch { Write-Host 'Runtime paths unavailable. Keep the existing backend console open.' }
Write-Host 'Recent redacted booking diagnostics:'
$found = $false
Get-ChildItem -File -Filter 'backend*.log' | ForEach-Object {
    Select-String -Path $_.FullName -Pattern '\[BookingFailure\]' | Select-Object -Last 10 | ForEach-Object {
        $safe = [regex]::Matches($_.Line, '(?:reference=[0-9a-f-]{36}|operation=[A-Za-z0-9/-]+|cause=[A-Za-z0-9_$]+|sqlState=[A-Za-z0-9]+|vendorCode=-?\d+|at=quanlykhachsan\.[A-Za-z0-9_.$#:-]+)')
        if ($safe.Count) { Write-Host ($safe.Value -join ' '); $found = $true }
    }
}
if (-not $found) { Write-Host 'No actual POST failure record found. A launcher AES error does not identify the running POST failure.' }
Write-Host 'Keep the current database and its original keys. This script does not start/restart the backend.'
