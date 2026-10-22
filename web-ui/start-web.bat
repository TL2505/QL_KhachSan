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

echo Starting the static server in a separate minimized window...
start "Aurelia Hotel Web UI Server" /min node server.js
node -e "const http=require('http');const deadline=Date.now()+30000;function check(){const req=http.get('http://127.0.0.1:%PORT%/',res=>{res.resume();if(res.statusCode===200)process.exit(0);retry()});req.setTimeout(1500,()=>req.destroy());req.on('error',retry);function retry(){if(Date.now()>=deadline){console.error('Web server did not become ready within 30 seconds.');process.exit(1)}setTimeout(check,500)}}check()"
if errorlevel 1 (
    echo.
    echo Server startup failed. Check the server window and whether another application is using port %PORT%.
    pause
    exit /b 1
)

echo Opening the web UI at http://localhost:%PORT%/...
start "" "http://localhost:%PORT%/"
exit /b 0
