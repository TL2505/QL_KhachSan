param([string]$MavenCommand='mvn', [string]$Test='Phase2RegressionSuite', [switch]$LocalFixture, [switch]$Offline, [switch]$Benchmark)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$keys=@('DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','AES_VAULT_KEY','HMAC_IDENTITY_KEY','HOTEL_RUN_DB_INTEGRATION','HOTEL_VAT_RATE','HOTEL_MARIADB_TEST_URL','HOTEL_MARIADB_TEST_USER','HOTEL_MARIADB_TEST_PASSWORD')
$saved=@{};foreach($key in $keys){$saved[$key]=[Environment]::GetEnvironmentVariable($key,'Process')}
Push-Location $projectRoot
try {
    if($LocalFixture){
        # Only the task-created disposable instance, never the application's default database.
        foreach($file in @('phase3-db.pid','phase3-db-password.txt','phase3-vault-test.key','phase3-hmac-test.key')){
            if(-not(Test-Path -LiteralPath (Join-Path $projectRoot "target/$file"))){throw 'The explicitly initialized disposable Phase 3 fixture is unavailable.'}
        }
        $env:DB_HOST='127.0.0.1';$env:DB_PORT='33318';$env:DB_NAME='hotel_phase3_test';$env:DB_USER='root'
        $env:DB_PASSWORD=Get-Content target/phase3-db-password.txt -Raw
        $env:AES_VAULT_KEY=Get-Content target/phase3-vault-test.key -Raw
        $env:HMAC_IDENTITY_KEY=Get-Content target/phase3-hmac-test.key -Raw
    }
    if(-not $env:DB_NAME -or -not $env:DB_NAME.EndsWith('_test')){throw 'An explicitly disposable *_test MariaDB schema is required.'}
    if(-not $env:AES_VAULT_KEY -or -not $env:HMAC_IDENTITY_KEY){throw 'Supply independent synthetic test AES_VAULT_KEY and HMAC_IDENTITY_KEY.'}
    $env:HOTEL_RUN_DB_INTEGRATION='true';$env:HOTEL_VAT_RATE='0.08'
    $env:HOTEL_MARIADB_TEST_URL="jdbc:mysql://$($env:DB_HOST):$($env:DB_PORT)/?useUnicode=true&characterEncoding=UTF-8"
    $env:HOTEL_MARIADB_TEST_USER=$env:DB_USER;$env:HOTEL_MARIADB_TEST_PASSWORD=$env:DB_PASSWORD
    $arguments=@('test',"-Dtest=$Test");if($Offline){$arguments=@('-o')+$arguments}
    if($Benchmark){$arguments+="-Dphase3.benchmark=true"}
    & $MavenCommand @arguments
    if($LASTEXITCODE -ne 0){throw 'Phase 3 tests failed; inspect target/surefire-reports.'}
} finally {
    foreach($key in $keys){[Environment]::SetEnvironmentVariable($key,$saved[$key],'Process')}
    Pop-Location
}
