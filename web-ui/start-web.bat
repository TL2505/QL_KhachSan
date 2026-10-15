@echo off
setlocal
cd /d "%~dp0"
title Aurelia Hotel Web UI
echo ======================================================
echo  Aurelia Grand Palace - 5-Star Web Booking Engine
echo  Khoi dong Web Server tai http://localhost:3000...
echo ======================================================
set "PORT=3000"

where node >nul 2>nul
if errorlevel 1 (
    echo Khong tim thay Node.js. Hay cai Node.js va mo lai terminal de cap nhat PATH.
    pause
    exit /b 1
)

node -e "require('http').get('http://127.0.0.1:%PORT%/',r=>process.exit(r.statusCode===200?0:1)).on('error',()=>process.exit(1))" >nul 2>nul
if not errorlevel 1 (
    echo Aurelia Web UI is already running on port %PORT%.
    start "" "http://localhost:%PORT%/"
    exit /b 0
)

echo Starting the static server. Leave this window open while using the site.
node server.js
if errorlevel 1 (
    echo.
    echo Server startup failed. Check whether another application is using port %PORT%.
    pause
    exit /b 1
)
pause
