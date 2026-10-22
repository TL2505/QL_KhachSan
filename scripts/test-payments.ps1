param([string]$EnvFile='.env',[int]$Port=3306,[string]$MavenCommand='')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'backend-common.ps1')
$environmentBefore=Save-BackendEnvironment
$locationBefore=Get-Location
$created=$false
try {
    Set-Location $projectRoot
    Import-BackendEnvironment (Join-Path $projectRoot $EnvFile)
    $env:DB_HOST='127.0.0.1';$env:DB_PORT=[string]$Port
    $env:DB_NAME='hotel_payments_'+[guid]::NewGuid().ToString('N').Substring(0,12)+'_test'
    $env:HOTEL_PAYMENT_TEST_OWNER=[guid]::NewGuid().ToString('N')
    $env:DB_USER='root';$env:DB_PASSWORD=$env:DB_ROOT_PASSWORD
    if($env:DB_ROOT_PASSWORD_FILE){$env:DB_PASSWORD=[IO.File]::ReadAllText($env:DB_ROOT_PASSWORD_FILE).Trim()}
    Remove-Item Env:DB_PASSWORD_FILE -ErrorAction SilentlyContinue
    Remove-Item Env:JDBC_URL -ErrorAction SilentlyContinue
    $env:DB_URL="jdbc:mysql://127.0.0.1:$Port/$($env:DB_NAME)?useSSL=false&serverTimezone=UTC"
    $env:HOTEL_RUN_DB_INTEGRATION='true';$env:NOTIFICATION_EMAIL_ENABLED='false'
    $maven=Get-BackendMaven $MavenCommand $projectRoot
    $java=Get-BackendJava 'java'
    & $maven -q '-Dhotel.build.directory=.run/payment-build' -DskipTests compile
    if($LASTEXITCODE -ne 0){throw 'Payment build failed'}
    $classpathFile=Join-Path $projectRoot '.run/payment-build/classpath.txt'
    & $maven -q dependency:build-classpath "-Dmdep.outputFile=$classpathFile"
    if($LASTEXITCODE -ne 0){throw 'Classpath build failed'}
    $classpath=(Join-Path $projectRoot '.run/payment-build/classes')+';'+[IO.File]::ReadAllText($classpathFile).Trim()
    & $java -cp $classpath (Join-Path $PSScriptRoot 'PaymentsTestDatabase.java') create
    if($LASTEXITCODE -ne 0){throw 'Disposable schema setup failed'}
    $created=$true
    & $maven -q '-Dhotel.build.directory=.run/payment-build' '-Dtest=PayOsSignatureTest,PayOsWebhookControllerTest,SmtpMailSenderTest,PaymentsNotificationsIntegrationTest,BankWebhookControllerTest' test
    if($LASTEXITCODE -ne 0){throw 'Payment/notification checks failed'}
    Write-Host 'Payment and notification checks passed. No external payment or email was sent.'
} finally {
    if($created){& $java -cp $classpath (Join-Path $PSScriptRoot 'PaymentsTestDatabase.java') drop;if($LASTEXITCODE -ne 0){Write-Warning 'Owned test schema cleanup failed; inspect the printed test schema name.'}}
    Restore-BackendEnvironment $environmentBefore
    Set-Location $locationBefore
}
