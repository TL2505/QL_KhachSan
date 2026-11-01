# SHA-256 Hash Code Comparison Script for Windows PowerShell
# Validates Invariant 5 (P1) - Byte-for-Byte Asset Parity

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$SrcDir = Join-Path $ProjectRoot "web-ui"
$DestDir = Join-Path $ProjectRoot "web-ui\public"

$CoreFiles = @(
    "portal.html",
    "staff.html",
    "portal-app.js",
    "portal.css",
    "portal-admin.css",
    "js/api.js",
    "js/customer/bookings.js",
    "js/staff/room-map.js",
    "js/staff/hotkeys.js"
)

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "  INVARIANT 5 (P1): SHA-256 ASSET PARITY VERIFICATION (WINDOWS POWERSHELL)" -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan

$allPass = $true
$results = @()

foreach ($rel in $CoreFiles) {
    $normalizedRel = $rel -replace "/", "\"
    $f1 = Join-Path $SrcDir $normalizedRel
    $f2 = Join-Path $DestDir $normalizedRel
    
    if (-not (Test-Path $f1)) {
        Write-Host "[-] $rel : SOURCE MISSING" -ForegroundColor Red
        $allPass = $false
        continue
    }
    if (-not (Test-Path $f2)) {
        Write-Host "[-] $rel : DESTINATION MISSING" -ForegroundColor Red
        $allPass = $false
        continue
    }
    
    $h1 = (Get-FileHash -Path $f1 -Algorithm SHA256).Hash.ToLower()
    $h2 = (Get-FileHash -Path $f2 -Algorithm SHA256).Hash.ToLower()
    
    if ($h1 -eq $h2) {
        Write-Host "[+] $rel : MATCH (PASS)" -ForegroundColor Green
        Write-Host "    SHA-256: $h1" -ForegroundColor DarkGray
        $results += [PSCustomObject]@{ File = $rel; Status = "MATCH (PASS)"; Hash = $h1 }
    } else {
        Write-Host "[-] $rel : MISMATCH (FAIL)" -ForegroundColor Red
        Write-Host "    Source: $h1" -ForegroundColor DarkRed
        Write-Host "    Public: $h2" -ForegroundColor DarkRed
        $allPass = $false
        $results += [PSCustomObject]@{ File = $rel; Status = "MISMATCH (FAIL)"; Hash = "$h1 != $h2" }
    }
}

Write-Host "--------------------------------------------------------------------------------"
if ($allPass) {
    Write-Host "VERIFICATION RESULT: ALL CORE FILES MATCH SHA-256 (100% PARITY - GATE-01 PASS)" -ForegroundColor Green
    exit 0
} else {
    Write-Host "VERIFICATION RESULT: PARITY MISMATCH DETECTED (GATE-01 FAIL)" -ForegroundColor Red
    exit 1
}
