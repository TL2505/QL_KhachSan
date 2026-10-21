@echo off
setlocal
title Aurelia Hotel - Stop Backend

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\stop-demo.ps1"
set "RESULT=%ERRORLEVEL%"

if not "%RESULT%"=="0" (
    echo.
    echo Backend could not be stopped. Review the message above.
    pause
    exit /b %RESULT%
)

echo.
pause
exit /b 0
