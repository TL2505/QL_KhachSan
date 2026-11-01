@echo off
setlocal
title Aurelia Hotel - Stop Backend

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\stop-backend.ps1"
set "RESULT=%ERRORLEVEL%"

if not "%RESULT%"=="0" (
    echo.
    echo Khong dung duoc backend. Xem thong bao loi phia tren.
    echo Script chi dung dung tien trinh backend cua du an nay.
    pause
)

exit /b %RESULT%
