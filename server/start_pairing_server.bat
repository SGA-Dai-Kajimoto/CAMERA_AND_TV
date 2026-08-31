@echo off
rem Start the TV pairing server.
rem
rem ASCII only. cmd walks a batch file by byte offset, so multi-byte characters
rem (even inside rem lines) get mis-parsed and executed as commands.
rem Japanese guidance is printed by device_flow.py instead.
setlocal

cd /d "%~dp0"
set "VENV_PY=%~dp0..\.venv\Scripts\python.exe"

if not exist "%VENV_PY%" (
    echo [ERROR] venv not found: %VENV_PY%
    echo.
    echo Create it first:
    echo   python -m venv .venv
    echo   .venv\Scripts\python.exe -m pip install -r server\requirements.txt
    echo.
    pause
    exit /b 1
)

if not defined PORT set "PORT=8000"

rem A second instance, or another project on the same port, fails in a way
rem that is hard to tell apart from a firewall block. Catch it here.
netstat -ano | findstr /r /c:":%PORT% .*LISTENING" > nul
if not errorlevel 1 (
    echo [ERROR] Port %PORT% is already in use.
    echo.
    netstat -ano | findstr /r /c:":%PORT% .*LISTENING"
    echo.
    echo Close that process, or start on another port:
    echo   set PORT=8010 ^&^& "%~nx0"
    echo.
    pause
    exit /b 1
)

"%VENV_PY%" device_flow.py

echo.
echo Server stopped.
pause
