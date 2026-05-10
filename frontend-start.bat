@echo off
chcp 936 >nul

title Template Frontend

echo =========================================
echo  Template - 前端开发服务器
echo =========================================
echo.

REM ============ 环境检查 ============

REM 1. Node.js 检查
echo [检查] Node.js 环境...
node --version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [错误] 未检测到 Node.js，请先安装 Node.js 16+
    pause
    exit /b 1
)
echo [OK] Node.js 可用
echo.

REM 2. 切换至 frontend 目录
cd /d "%~dp0frontend"

REM 3. 依赖检查与安装
if not exist "node_modules\" (
    echo [INFO] 首次运行，正在安装依赖...
    call npm install
    if %ERRORLEVEL% NEQ 0 (
        echo [ERROR] 依赖安装失败，请检查网络和 Node.js 安装
        pause
        exit /b 1
    )
    echo [INFO] 依赖安装完成
)
echo.

REM ============ 启动服务 ============
echo [启动] 启动 Vite 开发服务器...
echo [地址] http://localhost:3000
echo =========================================
echo.
call npm run dev
pause
