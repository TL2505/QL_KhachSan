@echo off
title Aurelia Hotel Web UI
echo ======================================================
echo  Aurelia Grand Palace - 5-Star Web Booking Engine
echo  Khoi dong Web Server tai http://localhost:3000...
echo ======================================================
set PORT=3000
start http://localhost:3000
node server.js
pause
