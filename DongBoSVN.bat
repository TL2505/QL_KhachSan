@echo off
chcp 65001 >nul
echo ========================================================
echo   DONG BO MA NGUON TU DU AN CHINH SANG THU MUC SVN
echo ========================================================
echo.
echo Dang tien hanh copy cac file moi va file thay doi...
echo.

robocopy src SVN_Repo_KhachSan\src /MIR /NFL /NDL /NJH /NJS /nc /ns /np

echo.
echo ========================================================
echo   DONG BO HOAN TAT THANH CONG! 
echo   Tat ca code moi da duoc dua vao SVN_Repo_KhachSan.
echo   Bay gio ban co the bam chuot phai vao thu muc do
echo   va chon SVN Commit de nop bai roi nhe.
echo ========================================================
pause
