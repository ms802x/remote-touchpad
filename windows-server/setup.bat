@echo off
REM One-click setup: installs dependencies, builds RemoteTouchpad.exe,
REM and launches it. Just double-click this file on the Windows laptop.

cd /d "%~dp0"

echo === Installing dependencies ===
pip install -r requirements.txt
if errorlevel 1 (
    echo.
    echo Failed to install dependencies. Make sure Python + pip are installed
    echo and on your PATH, then try again.
    pause
    exit /b 1
)

echo.
echo === Building RemoteTouchpad.exe ===
python -m PyInstaller --onefile --noconsole --name RemoteTouchpad --icon assets\app_icon.ico --add-data "assets;assets" tray_server.py
if errorlevel 1 (
    echo.
    echo Build failed. See the error above.
    pause
    exit /b 1
)

echo.
echo === Launching RemoteTouchpad ===
start "" "%~dp0dist\RemoteTouchpad.exe"

echo.
echo Done! Look for the icon in your system tray (bottom-right, near the clock).
echo Right-click it and choose "Show address" to get the IP to type into the phone app.
echo.
echo The .exe is saved at: %~dp0dist\RemoteTouchpad.exe
echo You can double-click it directly next time instead of running this script again.
pause
