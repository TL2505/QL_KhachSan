@echo off
setlocal
title Aurelia Hotel - Web UI

where.exe node >nul 2>&1
if errorlevel 1 (
    echo Khong tim thay Node.js. Hay cai Node.js roi thu lai.
    pause
    exit /b 1
)

if not exist "%~dp0web-ui\server.js" (
    echo Khong tim thay web-ui\server.js trong thu muc du an.
    pause
    exit /b 1
)

echo Dang khoi dong Web UI tai http://localhost:3000/portal.html ...
start "Aurelia Hotel - Web UI" /D "%~dp0web-ui" cmd.exe /k node server.js
timeout /t 2 /nobreak >nul
start "" "http://localhost:3000/portal.html"

echo.
echo Backend API can chay tai http://localhost:8081 de Web UI goi API.
exit /b 0
