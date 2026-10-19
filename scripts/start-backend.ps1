param([string]$MavenCommand)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not $MavenCommand) {
    # The repository's legacy mvn.cmd points to NetBeans; use installed Maven.
    $localShim = Join-Path $projectRoot 'mvn.cmd'
    $maven = Get-Command mvn.cmd -All -ErrorAction SilentlyContinue |
        Where-Object { $_.Source -ne $localShim } | Select-Object -First 1
    if (-not $maven) { throw 'Install Maven and add its bin directory to PATH, or pass -MavenCommand with its full path.' }
    $MavenCommand = $maven.Source
}
if (-not $env:JWT_SECRET) {
    $bytes = New-Object byte[] 48
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $env:JWT_SECRET = [Convert]::ToBase64String($bytes)
    Write-Host 'Generated a JWT secret for this local PowerShell session.'
}
if ([Text.Encoding]::UTF8.GetByteCount($env:JWT_SECRET) -lt 32) {
    throw 'JWT_SECRET must contain at least 32 UTF-8 bytes.'
}
if (-not $env:HOTEL_VAT_RATE) { $env:HOTEL_VAT_RATE = '0.08' }
Push-Location $projectRoot
try {
    & $MavenCommand '-DskipTests' 'compile' 'org.codehaus.mojo:exec-maven-plugin:3.1.0:java' '-Dexec.mainClass=quanlykhachsan.backend.Main'
    if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE." }
} finally { Pop-Location }
