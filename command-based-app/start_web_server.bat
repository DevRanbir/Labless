@echo off
:: ============================================================
:: start_web_server.bat
:: Starts the Gmail LLM Labeler web interface
:: ============================================================

:: Resolve the directory where this .bat file lives (project root)
set "PROJECT_DIR=%~dp0"
:: Remove trailing backslash
if "%PROJECT_DIR:~-1%"=="\" set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"

echo.
echo ============================================================
echo  Gmail LLM Labeler - Web Interface
echo  Project : %PROJECT_DIR%
echo ============================================================
echo.

:: Change to project directory
cd /d "%PROJECT_DIR%"

:: Check if Flask is installed
python -c "import flask" 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Flask is not installed. Installing...
    pip install flask
    echo.
)

:: Start the web server
echo Starting web server...
echo.
echo Access the dashboard at: http://localhost:5000
echo Press Ctrl+C to stop the server
echo.
echo ============================================================
echo.

python web_app.py

pause
