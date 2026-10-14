$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$runtimeRoot=[IO.Path]::GetFullPath((Join-Path $projectRoot '.phase2-runtime'))
$serverExe=[IO.Path]::GetFullPath((Join-Path $runtimeRoot 'mariadb-11.4.2-winx64/bin/mariadbd.exe'))
if(-not (Test-Path -LiteralPath $serverExe) -or -not (Test-Path -LiteralPath (Join-Path $runtimeRoot 'data'))){throw 'The optional local portable test runtime is unavailable. Supply an isolated MariaDB 11 *_test schema instead.'}
$pidFile=Join-Path $runtimeRoot 'server.pid'
if(Test-Path -LiteralPath $pidFile){$ownedId=[int](Get-Content -LiteralPath $pidFile);$ownedProcess=Get-Process -Id $ownedId -ErrorAction SilentlyContinue;if($ownedProcess -and $ownedProcess.Path -eq $serverExe){Write-Output 'Disposable MariaDB test instance is already running.';return}}
if([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners().Port -contains 33317){throw 'Port 33317 is occupied; no existing process will be changed.'}
$data=Join-Path $runtimeRoot 'data'
$process=Start-Process -FilePath $serverExe -ArgumentList @("--datadir=$data",'--bind-address=127.0.0.1','--port=33317',"--log-error=$runtimeRoot/server-error.log",'--console') -WindowStyle Hidden -WorkingDirectory $projectRoot -RedirectStandardOutput (Join-Path $runtimeRoot 'server.log') -RedirectStandardError (Join-Path $runtimeRoot 'server-stderr.log') -PassThru
$process.Id | Set-Content -LiteralPath $pidFile
Write-Output 'Started the disposable test instance on 127.0.0.1:33317.'
