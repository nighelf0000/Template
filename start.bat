@echo off
chcp 936 >nul

title Template [DEV] :8080

echo ========================================
echo  Template - 开发环境启动
echo ========================================
echo.

REM ================ 环境检查 ================

REM 1. Java 版本检查（需 >= 17）
echo [检查] Java 版本...
java -version 2>&1 | findstr /R "1[7-9]\. 2[0-9]\." >nul
if %ERRORLEVEL% NEQ 0 (
    echo [错误] 未检测到 Java 17 或更高版本，请安装 JDK 17+
    echo        当前 Java 版本：
    java -version 2>&1
    pause
    exit /b 1
)
echo [OK] Java 版本满足要求（17+）
echo.

REM 2. mvnw.cmd 存在性检查
echo [检查] Maven 包装器...
if not exist "mvnw.cmd" (
    echo [错误] 未找到 mvnw.cmd 文件，请确认项目已正确初始化
    pause
    exit /b 1
)
echo [OK] mvnw.cmd 存在
echo.

REM 3. 端口 8080 占用检查
echo [检查] 端口 8080 是否可用...
netstat -ano | findstr "LISTENING" | findstr ":8080 " >nul
if %ERRORLEVEL% EQU 0 (
    echo [错误] 端口 8080 已被占用，以下进程正在使用该端口：
    echo.
    netstat -ano | findstr "LISTENING" | findstr ":8080 "
    pause
    exit /b 1
)
echo [OK] 端口 8080 可用
echo.

REM ================ Maven 打包 ================
echo [构建] 开始 Maven 打包（跳过测试）...
echo.
call mvnw.cmd clean package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [错误] Maven 打包失败，请检查编译错误后重试
    pause
    exit /b 1
)
echo.
echo [OK] Maven 打包成功
echo.

REM ================ 启动应用 ================
echo [启动] 正在启动 Template 应用...
echo.
java -jar target\template-system-1.0.0.jar

pause
