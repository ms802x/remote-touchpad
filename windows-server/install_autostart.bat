@echo off
REM Optional: makes RemoteTouchpad.exe launch automatically when you log in.
REM Run this AFTER build.bat has produced dist\RemoteTouchpad.exe.

set EXE=%~dp0dist\RemoteTouchpad.exe
set STARTUP=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup

if not exist "%EXE%" (
    echo Could not find %EXE% - run build.bat first.
    pause
    exit /b 1
)

copy "%EXE%" "%STARTUP%\RemoteTouchpad.exe"
echo Installed. RemoteTouchpad will now start automatically on login.
pause
