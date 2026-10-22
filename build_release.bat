@echo off
echo ========================================================
echo   KMRL Timetable - Building Signed Release APK
echo ========================================================
cd /d "%~dp0"
call gradlew.bat assembleRelease
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Build failed with error code %ERRORLEVEL%!
    pause
    exit /b %ERRORLEVEL%
)
echo.
echo ========================================================
echo   [SUCCESS] Release build published to releases/ folder!
echo ========================================================
dir /b /o-d releases\*.apk | findstr /i "Release.apk"
echo.
