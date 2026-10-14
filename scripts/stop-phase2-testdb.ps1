$ErrorActionPreference='Stop'
$runtimeRoot=Join-Path (Split-Path $PSScriptRoot -Parent) '.phase2-runtime'
$pidFile=Join-Path $runtimeRoot 'server.pid'
if(-not (Test-Path -LiteralPath $pidFile)){return}
$ownedId=[int](Get-Content -LiteralPath $pidFile);$ownedProcess=Get-Process -Id $ownedId -ErrorAction SilentlyContinue
if(-not $ownedProcess){return}
$expectedExe=[IO.Path]::GetFullPath((Join-Path $runtimeRoot 'mariadb-11.4.2-winx64/bin/mariadbd.exe'))
if($ownedProcess.Path -ne $expectedExe){throw 'PID no longer identifies the task-owned test server. Nothing was stopped.'}
$previousPassword=[Environment]::GetEnvironmentVariable('MYSQL_PWD','Process')
try {
    $env:MYSQL_PWD=Get-Content -LiteralPath (Join-Path $runtimeRoot 'test-password.txt') -Raw
    & (Join-Path $runtimeRoot 'mariadb-11.4.2-winx64/bin/mariadb-admin.exe') --host=127.0.0.1 --port=33317 --user=root shutdown
    if($LASTEXITCODE -ne 0){throw 'Graceful test-server shutdown failed.'}
    $ownedProcess.WaitForExit(10000) | Out-Null
}finally {[Environment]::SetEnvironmentVariable('MYSQL_PWD',$previousPassword,'Process')}
