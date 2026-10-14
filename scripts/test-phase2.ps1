param([switch]$Offline)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
$environmentKeys=@('DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','HOTEL_RUN_DB_INTEGRATION','HOTEL_VAT_RATE','HOTEL_MARIADB_TEST_URL','HOTEL_MARIADB_TEST_USER','HOTEL_MARIADB_TEST_PASSWORD')
$originalEnvironment=@{};foreach($key in $environmentKeys){$originalEnvironment[$key]=[Environment]::GetEnvironmentVariable($key,'Process')}
try {
    # Use only an explicitly disposable schema. No production credentials are supplied by this script.
    if(-not $env:DB_NAME){
        $passwordFixture=Join-Path $projectRoot '.phase2-runtime/test-password.txt'
        if(-not (Test-Path -LiteralPath $passwordFixture)){throw 'Set DB_HOST, DB_PORT, DB_NAME (*_test), DB_USER and DB_PASSWORD for a disposable MariaDB 11 instance.'}
        $env:DB_HOST='127.0.0.1';$env:DB_PORT='33317';$env:DB_NAME='hotel_phase2_test';$env:DB_USER='root'
        $env:DB_PASSWORD=Get-Content -LiteralPath $passwordFixture -Raw
    }
    if(-not $env:DB_NAME.EndsWith('_test')){throw 'The regression suite requires a disposable *_test database.'}
    $env:HOTEL_RUN_DB_INTEGRATION='true'
    $env:HOTEL_VAT_RATE='0.08' # Explicit synthetic fixture, isolated to this test process.
    $env:HOTEL_MARIADB_TEST_URL="jdbc:mysql://$($env:DB_HOST):$($env:DB_PORT)/?useUnicode=true&characterEncoding=UTF-8"
    $env:HOTEL_MARIADB_TEST_USER=$env:DB_USER;$env:HOTEL_MARIADB_TEST_PASSWORD=$env:DB_PASSWORD
    $mavenArguments=@('clean','test','-Dtest=Phase2RegressionSuite')
    if($Offline){$mavenArguments=@('-o')+$mavenArguments}
    & mvn @mavenArguments
    if($LASTEXITCODE -ne 0){throw 'Phase 2 regression failed; inspect target/surefire-reports.'}
    $reports=Get-ChildItem target/surefire-reports -Filter 'TEST-*.xml'
    foreach($report in $reports){[xml]$result=Get-Content $report.FullName;if([int]$result.testsuite.skipped -gt 0){throw "Acceptance cannot pass with skipped tests: $($report.Name)"}}
}finally {foreach($key in $environmentKeys){[Environment]::SetEnvironmentVariable($key,$originalEnvironment[$key],'Process')};Pop-Location}
