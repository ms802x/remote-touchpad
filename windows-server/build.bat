@echo off
REM Run this once on the Windows laptop to build a standalone .exe.
REM Produces dist\RemoteTouchpad.exe - double-click that to run,
REM no Python install needed afterwards.

pip install -r requirements.txt
python -m PyInstaller --onefile --noconsole --name RemoteTouchpad --icon assets\app_icon.ico --add-data "assets;assets" tray_server.py

echo.
echo Build complete. Find it at dist\RemoteTouchpad.exe
pause
