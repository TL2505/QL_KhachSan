$ErrorActionPreference = 'Stop'
$port = 8081
$backendMainClass = 'quanlykhachsan.backend.Main'

$listeners = @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
if ($listeners.Count -eq 0) {
    Write-Host "No backend is listening on port $port."
    exit 0
}

$processIds = @($listeners | Select-Object -ExpandProperty OwningProcess -Unique)
if ($processIds.Count -ne 1) {
    throw "Multiple processes are listening on port $port. No process was stopped."
}

$backendProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $($processIds[0])"
if (-not $backendProcess -or $backendProcess.Name -notin @('java.exe', 'javaw.exe') -or
    $backendProcess.CommandLine -notlike "*$backendMainClass*") {
    throw "PID $($processIds[0]) owns port $port but does not match the hotel backend. No process was stopped."
}

Stop-Process -Id $backendProcess.ProcessId
for ($attempt = 0; $attempt -lt 10; $attempt++) {
    Start-Sleep -Milliseconds 500
    $remaining = @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
    if ($remaining.Count -eq 0) {
        Write-Host "Hotel backend stopped (PID $($backendProcess.ProcessId))."
        exit 0
    }
}

throw "PID $($backendProcess.ProcessId) was stopped, but port $port is still listening. Check which process owns the port."
