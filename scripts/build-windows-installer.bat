@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%.."

pushd "%PROJECT_DIR%"

where java >nul 2>nul
if errorlevel 1 (
  echo Java 17+ is required.
  popd
  exit /b 1
)

call gradlew.bat packageExe
set "RESULT=%ERRORLEVEL%"

if "%RESULT%"=="0" (
  echo.
  echo Windows installer created under build\compose\binaries\main-exe\
)

popd
exit /b %RESULT%
