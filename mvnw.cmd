@echo off
rem Root delegation wrapper for Talent and Resource Allocation project
setlocal
set SCRIPT_DIR=%~dp0
if exist "%SCRIPT_DIR%backend\mvnw.cmd" (
    call "%SCRIPT_DIR%backend\mvnw.cmd" -f "%SCRIPT_DIR%backend\pom.xml" %*
) else (
    echo [ERROR] backend\mvnw.cmd not found at %SCRIPT_DIR%backend
    exit /b 1
)
endlocal
