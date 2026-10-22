$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$pidFile=Join-Path $projectRoot '.run/backend-real.pid'
if (-not (Test-Path -LiteralPath $pidFile)) { Write-Host 'No recorded backend process. For a foreground launch, use Ctrl+C in its terminal.'; return }
$backendId=0
if (-not [int]::TryParse(([IO.File]::ReadAllText($pidFile).Trim()),[ref]$backendId)) { throw 'Invalid backend PID file.' }
$process=Get-CimInstance Win32_Process -Filter "ProcessId=$backendId"
if (-not $process) { Write-Host 'Recorded backend has already stopped.'; return }
if ($process.Name -notin @('java.exe','javaw.exe') -or $process.CommandLine -notlike '*quanlykhachsan.backend.Main*' -or
    $process.CommandLine.IndexOf((Join-Path $projectRoot '.run/backend-build'),[StringComparison]::OrdinalIgnoreCase) -lt 0) { throw 'Recorded PID does not match this workspace backend. No process was stopped.' }
Stop-Process -Id $backendId
Wait-Process -Id $backendId -Timeout 10 -ErrorAction SilentlyContinue
Write-Host "Backend stopped (PID $backendId). Database and .env are retained."
