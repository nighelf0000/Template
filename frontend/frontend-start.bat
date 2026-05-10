@echo off
chcp 65001 >nul
title Template Frontend
cd /d "%~dp0"

echo ====================================
echo  Template 前端项目启动脚本
echo ====================================

REM 检查 node_modules 是否存在
if not exist "node_modules\" (
    echo [INFO] 首次运行，正在安装依赖...
    call npm install
    if %errorlevel% neq 0 (
        echo [ERROR] 依赖安装失败，请检查网络和 Node.js 安装
        pause
        exit /b 1
    )
    echo [INFO] 依赖安装完成
)

echo [INFO] 启动开发服务器...
echo [INFO] 默认地址: http://localhost:3000
echo ====================================
call npm run dev
pause
