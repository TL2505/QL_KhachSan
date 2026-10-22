$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'backend-common.ps1')
$testRoot=Join-Path $projectRoot ('.run/environment-tests/'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
$names=@('HOTEL_ENV_TEST','AES_VAULT_KEY','AES_VAULT_KEY_FILE','HMAC_IDENTITY_KEY','HMAC_IDENTITY_KEY_FILE')
$saved=@{}
foreach($name in $names){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process');[Environment]::SetEnvironmentVariable($name,$null,'Process')}
$file=Join-Path $testRoot 'synthetic.env'
function Assert-Equal($actual,$expected,$description){if($actual -ne $expected){throw "FAILED: $description"};Write-Host "PASS: $description"}
try {
    [IO.File]::WriteAllLines($file,@('# synthetic only','HOTEL_ENV_TEST="test=value#kept"'))
    Import-BackendEnvironment $file
    Assert-Equal $env:HOTEL_ENV_TEST 'test=value#kept' 'quoted values preserve equals and hash characters'
    $env:HOTEL_ENV_TEST='process-value'
    Import-BackendEnvironment $file
    Assert-Equal $env:HOTEL_ENV_TEST 'process-value' 'process source takes precedence'
    $env:AES_VAULT_KEY_FILE='synthetic-process-file'
    [IO.File]::WriteAllLines($file,@('AES_VAULT_KEY=synthetic-file-value'))
    Import-BackendEnvironment $file
    Assert-Equal ([string]$env:AES_VAULT_KEY) '' 'process _FILE overrides .env direct source as a pair'
    [Environment]::SetEnvironmentVariable('AES_VAULT_KEY_FILE',$null,'Process')
    [IO.File]::WriteAllLines($file,@('AES_VAULT_KEY=synthetic','AES_VAULT_KEY_FILE=synthetic-file'))
    $failed=$false;try{Import-BackendEnvironment $file}catch{$failed=$true}
    Assert-Equal $failed $true 'conflicting .env secret sources are rejected'
    [IO.File]::WriteAllLines($file,@('HOTEL_ENV_TEST=a','HOTEL_ENV_TEST=b'))
    $failed=$false;try{Import-BackendEnvironment $file}catch{$failed=$true}
    Assert-Equal $failed $true 'duplicate keys are rejected'
    [IO.File]::WriteAllLines($file,@('invalid entry'))
    $failed=$false;try{Import-BackendEnvironment $file}catch{$failed=$true}
    Assert-Equal $failed $true 'malformed lines are rejected without echoing values'
    $env:HOTEL_ENV_TEST='caller-value'
    $snapshot=Save-BackendEnvironment
    $env:HOTEL_ENV_TEST='temporary-config-value'
    $env:HOTEL_ENV_NEW_TEMP='temporary'
    Restore-BackendEnvironment $snapshot
    Assert-Equal $env:HOTEL_ENV_TEST 'caller-value' 'caller configuration is restored after a script'
    Assert-Equal ([string]$env:HOTEL_ENV_NEW_TEMP) '' 'temporary configuration cannot leak into the next database check'
} finally {foreach($name in $names){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}}
