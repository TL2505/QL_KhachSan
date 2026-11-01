# Safe Web Resource Synchronization Script for Windows PowerShell
# Invariant 5 (P1) - Asset Parity
# Synchronizes HTML, JS, CSS and CSS source inputs from web-ui/ to web-ui/public/
# Excludes public/, node_modules/, tests/, and removes server.js from destination.

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$SrcDir = Join-Path $ProjectRoot "web-ui"
$DestDir = Join-Path $ProjectRoot "web-ui\public"

Write-Host "Synchronizing static assets from $SrcDir to $DestDir..." -ForegroundColor Cyan

if (-not (Test-Path $DestDir)) {
    New-Item -ItemType Directory -Path $DestDir -Force | Out-Null
}

$files = Get-ChildItem -Path $SrcDir -Recurse -File | Where-Object {
    $_.FullName -notmatch '\\public($|\\)' -and
    $_.FullName -notmatch '\\node_modules($|\\)' -and
    $_.FullName -notmatch '\\tests($|\\)' -and
    ($_.Extension -in @('.html', '.js', '.css', '.jpg', '.jpeg', '.png', '.svg', '.ico', '.webp', '.woff', '.woff2') -or $_.Name -like '*.input.css') -and
    $_.Name -ne 'server.js'
}

$copiedCount = 0
foreach ($file in $files) {
    $relativePath = $file.FullName.Substring($SrcDir.Length + 1)
    $targetPath = Join-Path $DestDir $relativePath
    $targetParent = Split-Path -Parent $targetPath
    if (-not (Test-Path $targetParent)) {
        New-Item -ItemType Directory -Path $targetParent -Force | Out-Null
    }
    Copy-Item -Path $file.FullName -Destination $targetPath -Force
    $copiedCount++
}

# Ensure server.js is removed from public if present
$serverJs = Join-Path $DestDir "server.js"
if (Test-Path $serverJs) {
    Remove-Item -Path $serverJs -Force
    Write-Host "Removed server.js from $DestDir" -ForegroundColor Yellow
}

Write-Host "Successfully synchronized $copiedCount files to web-ui/public/." -ForegroundColor Green
