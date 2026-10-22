@echo off
setlocal
title Aurelia Hotel - Backend

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-backend.ps1" -Background
set "RESULT=%ERRORLEVEL%"

if not "%RESULT%"=="0" (
    echo.
    echo Khong khoi dong duoc backend. Xem thong bao loi phia tren.
    echo Loi DB: kiem tra DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD trong .env.
    echo DB_URL/JDBC_URL neu co se uu tien hon DB_HOST/DB_NAME.
    echo Loi key: kiem tra AES_VAULT_KEY, HMAC_IDENTITY_KEY va JWT_SECRET.
    echo Giu dung key goc cua database; khong tao key moi cho du lieu da ma hoa.
    echo Neu cong 8081 dang duoc dung, dung backend cu truoc khi chay lai.
    echo Log: .run\backend-real-stdout.log va .run\backend-real-stderr.log
    pause
)

exit /b %RESULT%
