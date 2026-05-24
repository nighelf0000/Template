@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion
set "PYTHONIOENCODING=utf-8"

REM ============================================================================
REM start.bat -- doc-struct Windows launcher
REM
REM Bootstrap script: detect Python -> setup venv -> install deps -> run CLI
REM
REM Phases:
REM   1 - Python runtime detection
REM   2 - Virtual environment management
REM   3 - Dependency installation check
REM   4 - Argument passthrough to CLI
REM   5 - Exit code propagation
REM
REM Target: Windows 10/11
REM ============================================================================

set "PROJECT_DIR=%~dp0"
set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"
set "VENV_DIR=%PROJECT_DIR%\.venv"
set "SENTINEL_FILE=%VENV_DIR%\install_sentinel"
set "PYPROJECT_TOML=%PROJECT_DIR%\pyproject.toml"

REM ===== Phase 1: Python runtime detection =====
REM Strategy: py launcher -> python -> python3
REM Target: find Python >= 3.10

set "PY_CMD="
set "PY_VERSION_STR="

REM Method 1: via py launcher (recommended on Windows)
REM Try known versions from high to low, pick the first available
for %%v in (20 19 18 17 16 15 14 13 12 11 10) do (
    if "!PY_CMD!"=="" (
        py -3.%%v --version >nul 2>&1
        if !ERRORLEVEL! equ 0 (
            set "PY_CMD=py -3.%%v"
            set "PY_VERSION_STR=3.%%v"
        )
    )
)

REM Method 2: fallback to python command (PATH)
if "!PY_CMD!"=="" (
    python --version >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        for /f "tokens=2 delims= " %%v in ('python --version 2^>^&1') do (
            set "PY_VERSION_STR=%%v"
            for /f "tokens=1,2 delims=." %%a in ("%%v") do (
                set "PY_MINOR=%%b"
                if %%a geq 3 if not "!PY_MINOR:~1,1!"=="" set "PY_CMD=python"
            )
        )
    )
)

REM Method 3: fallback to python3 command (alternative)
if "!PY_CMD!"=="" (
    python3 --version >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        for /f "tokens=2 delims= " %%v in ('python3 --version 2^>^&1') do (
            set "PY_VERSION_STR=%%v"
            for /f "tokens=1,2 delims=." %%a in ("%%v") do (
                set "PY_MINOR=%%b"
                if %%a geq 3 if not "!PY_MINOR:~1,1!"=="" set "PY_CMD=python3"
            )
        )
    )
)

REM All methods failed -> show error and exit
if "!PY_CMD!"=="" (
    if defined PY_VERSION_STR (
        echo [ERROR] Python >= 3.10 required, current version is !PY_VERSION_STR!
    ) else (
        echo [ERROR] Python not found. Please install Python 3.10 or later.
    )
    pause
    exit /b 1
)

REM ===== Phase 2: Virtual environment management =====
REM Location: .venv\ under project root
REM Created on first run, skipped on subsequent runs

if not exist "%VENV_DIR%" (
    echo Creating virtual environment...
    "!PY_CMD!" -m venv "%VENV_DIR%"
    if !ERRORLEVEL! neq 0 (
        echo [ERROR] Failed to create virtual environment. Check permissions.
        pause
        exit /b 1
    )
    echo Virtual environment created.
) else (
    REM venv already exists, skip creation
)

REM ===== Phase 3: Dependency installation check =====
REM Uses .venv\install_sentinel to track pyproject.toml MD5 hash
REM Re-installs only when pyproject.toml has changed

REM Verify pyproject.toml exists
if not exist "%PYPROJECT_TOML%" (
    echo [ERROR] pyproject.toml not found. Make sure you run from the project root.
    pause
    exit /b 1
)

REM Calculate current MD5 hash of pyproject.toml
set "CURRENT_HASH="
for /f %%i in ('certutil -hashfile "%PYPROJECT_TOML%" MD5 2^>nul ^| findstr /v "MD5 CertUtil"') do (
    if "!CURRENT_HASH!"=="" set "CURRENT_HASH=%%i"
)

REM Determine if install/update is needed
set "NEED_INSTALL=0"
if not exist "%SENTINEL_FILE%" (
    REM Sentinel missing -> first install
    set "NEED_INSTALL=1"
) else (
    REM Sentinel exists -> compare hashes
    set /p "SAVED_HASH=" < "%SENTINEL_FILE%"
    if not "!SAVED_HASH:~0,32!"=="!CURRENT_HASH!" set "NEED_INSTALL=1"
)

if !NEED_INSTALL! equ 1 (
    echo Installing/updating dependencies...
    "%VENV_DIR%\Scripts\pip.exe" install -e "%PROJECT_DIR%"
    if !ERRORLEVEL! neq 0 (
        echo [ERROR] Dependency installation failed. Check your network connection.
        pause
        exit /b 1
    )
    REM Update sentinel file with new hash
    > "%SENTINEL_FILE%" echo !CURRENT_HASH!
    echo Dependencies installed.
) else (
    REM Hash matches, skip installation
)

REM ===== Phase 4: Argument passthrough to CLI =====
REM All arguments (%%) are forwarded as-is to doc-struct.exe

"%VENV_DIR%\Scripts\doc-struct.exe" %*
set "CLI_EXIT_CODE=%ERRORLEVEL%"

REM ===== Phase 5: Exit code propagation =====
REM If no arguments provided, CLI shows help and exits immediately.
REM Pause so the user can read the output before the window closes.
if "%*"=="" pause

exit /b %CLI_EXIT_CODE%
