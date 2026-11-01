param([Parameter(Mandatory=$true)][string]$Destination, [string]$EnvFile)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'backend-common.ps1')
$environmentBefore=Save-BackendEnvironment
try {
if (-not $EnvFile) { $EnvFile=Join-Path $projectRoot '.env' }
if (Test-Path -LiteralPath $Destination) { throw 'Destination already exists. Use a new path so an earlier database key set is preserved.' }
Import-BackendEnvironment $EnvFile
$lines=@('# Matching identity keys for the accompanying database dump. Treat this file as a secret.',('# Source DB_NAME='+$env:DB_NAME))
foreach ($name in @('AES_VAULT_KEY','HMAC_IDENTITY_KEY')) {
    $value=[Environment]::GetEnvironmentVariable($name,'Process')
    $file=[Environment]::GetEnvironmentVariable("${name}_FILE",'Process')
    if ($value -and $file) { throw "Configure only one source for $name." }
    if ($file) { $value=[IO.File]::ReadAllText($file).Trim() }
    if (-not $value) { throw "$name is missing. A database with encrypted identities cannot be handed off without its original keys." }
    try { $bytes=[Convert]::FromBase64String($value) } catch { throw "$name must be Base64 encoded." }
    try { if ($bytes.Length -ne 32) { throw "$name must decode to 32 bytes." } } finally { [Array]::Clear($bytes,0,$bytes.Length) }
    $lines+=($name+'='+$value)
}
$path=[IO.Path]::GetFullPath($Destination)
$parent=Split-Path $path -Parent
if (-not (Test-Path -LiteralPath $parent -PathType Container)) { throw 'Destination directory must already exist.' }
[IO.File]::WriteAllLines($path,$lines)
Write-Host 'Identity key companion written. Send through an approved secret channel, paired with the exact SQL backup. Values are not logged.'
} finally { Restore-BackendEnvironment $environmentBefore }
