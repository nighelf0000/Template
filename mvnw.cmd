@echo off
setlocal enabledelayedexpansion
set "MAVEN_USER_HOME=%USERPROFILE%\.m2"
set "WRAPPER_JAR=%~dp0.mvn\wrapper\maven-wrapper.jar"
:: Remove trailing backslash from project dir to avoid argument parsing issues
set "PROJ=%~dp0"
if "%PROJ:~-1%"=="\" set "PROJ=%PROJ:~0,-1%"

java -cp "%WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%PROJ%" org.apache.maven.wrapper.MavenWrapperMain %*
exit /b %ERRORLEVEL%
