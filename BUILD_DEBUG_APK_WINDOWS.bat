@echo off
cd /d "%~dp0"
if not exist "gradle\wrapper\gradle-wrapper.jar" (
  echo First run SETUP_FIRST_WINDOWS.bat
  pause
  exit /b 1
)
call gradlew.bat assembleDebug
if errorlevel 1 (
  echo The build failed. Check the error shown above.
  pause
  exit /b 1
)
echo.
echo APK ready: app\build\outputs\apk\debug\app-debug.apk
pause
