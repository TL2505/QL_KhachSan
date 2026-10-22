@echo off
setlocal
title Aurelia Hotel - Start Backend

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-demo-background.ps1"
set "RESULT=%ERRORLEVEL%"

if not "%RESULT%"=="0" (
    echo.
    echo Backend could not be started. See the message above and .run\backend-stderr.log.
    pause
    exit /b %RESULT%
)

echo.
echo Opening the hotel website at http://localhost:8081/...
start "" "http://localhost:8081/"
pause
exit /b 0
