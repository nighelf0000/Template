@echo off
setlocal

title Template - Stop :8080 / :3000

echo ========================================
echo  Template - Stop Application
echo ========================================
echo.

for %%P in (8080 3000) do call :KillPort %%P

echo.
pause
exit /b 0

:KillPort
set PORT=%1
echo [Check] Looking for process on port %PORT%...

for /f "tokens=5" %%a in ('netstat -ano ^| findstr "LISTENING" ^| findstr ":%PORT% "') do set PID=%%a

if "%PID%"=="" (
    echo [Info] No process listening on port %PORT%.
    echo.
    goto :eof
)

echo [Found] Process on port %PORT%:
echo.
tasklist /FI "PID eq %PID%" /FO TABLE
echo.

set /p CONFIRM="Kill this process? (Y/N): "
if /i not "%CONFIRM%"=="Y" (
    echo [Skip] Port %PORT% process kept alive.
    echo.
    goto :eof
)

echo.
echo [Exec] Force killing PID %PID%...
taskkill /F /PID %PID% >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [Error] Cannot kill PID %PID%. Try Run as Administrator.
    echo.
    goto :eof
)

echo [Wait] Checking port release...
ping 127.0.0.1 -n 2 >nul

netstat -ano | findstr "LISTENING" | findstr ":%PORT% " >nul
if %ERRORLEVEL% EQU 0 (
    echo [Fail] Port %PORT% still in use. Try Run as Administrator.
) else (
    echo [OK] Port %PORT% released.
)
echo.
goto :eof
