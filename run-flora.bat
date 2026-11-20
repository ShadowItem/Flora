@echo off
setlocal
cd /d "%~dp0"

where mvn >nul 2>nul
if errorlevel 1 (
    echo Apache Maven was not found on PATH.
    echo Install Maven or add its bin folder to PATH, then try again.
    pause
    exit /b 1
)

echo Starting Flora. Maven may download dependencies on the first run.
call mvn javafx:run
set "APP_EXIT=%ERRORLEVEL%"
echo.
echo Maven process finished with exit code %APP_EXIT%.
pause
exit /b %APP_EXIT%
