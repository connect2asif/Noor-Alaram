@echo off
cd /d "%~dp0"
echo.
echo ========================================================
echo      TILAWAT ALARM - ANDROID STUDIO SETUP
 echo ========================================================
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\setup-wrapper.ps1"
if errorlevel 1 (
  echo.
  echo Could not download the official Gradle wrapper.
  echo Make sure you are connected to the internet, then retry.
  pause
  exit /b 1
)
echo.
echo SETUP COMPLETE. Open this folder with Android Studio.
pause
