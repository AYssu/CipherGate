@echo off
setlocal

set "BUNDLE_DIR=deploy-bundle"
set "ZIP_NAME=ciphergate-deploy.zip"

echo [1/5] Build backend JAR...
call gradlew.bat clean bootJar --no-daemon
if errorlevel 1 goto :fail

echo [2/5] Build frontend dist...
pushd frontend
if not exist "node_modules" (
  call npm install --include=dev --no-audit --no-fund
  if errorlevel 1 (
    popd
    goto :fail
  )
)
if not exist "node_modules\.bin\tsc.cmd" (
  echo [INFO] typescript not found, reinstalling dev dependencies...
  call npm install --include=dev --no-audit --no-fund
  if errorlevel 1 (
    popd
    goto :fail
  )
)
call npm run build
if errorlevel 1 (
  popd
  goto :fail
)
popd

echo [3/5] Prepare bundle...
if exist "%BUNDLE_DIR%" rmdir /s /q "%BUNDLE_DIR%"
mkdir "%BUNDLE_DIR%\app"
mkdir "%BUNDLE_DIR%\frontend"

set "JAR_FILE="
for /f "delims=" %%f in ('dir /b /o:-d "build\libs\*.jar" ^| findstr /v /i "\-plain\.jar$"') do (
  if not defined JAR_FILE set "JAR_FILE=build\libs\%%f"
)
if not defined JAR_FILE (
  echo [ERROR] No runnable bootJar found in build\libs
  goto :fail
)
echo [INFO] Using jar: %JAR_FILE%
copy /y "%JAR_FILE%" "%BUNDLE_DIR%\app\app.jar" >nul
if errorlevel 1 goto :fail
xcopy /e /i /y "frontend\dist" "%BUNDLE_DIR%\frontend\dist" >nul
if errorlevel 1 goto :fail
copy /y "frontend\nginx.conf" "%BUNDLE_DIR%\frontend\nginx.conf" >nul
if errorlevel 1 goto :fail
copy /y "docker-compose.server.yml" "%BUNDLE_DIR%\docker-compose.server.yml" >nul
if errorlevel 1 goto :fail

if not exist ".env.server" (
  echo [ERROR] Missing .env.server. Copy from .env.server.example and edit it.
  goto :fail
)
copy /y ".env.server" "%BUNDLE_DIR%\.env" >nul
if errorlevel 1 goto :fail

echo [4/5] Create zip package...
if exist "%ZIP_NAME%" del /f /q "%ZIP_NAME%"
powershell -NoProfile -Command "Compress-Archive -Path '%BUNDLE_DIR%\*' -DestinationPath '%ZIP_NAME%' -Force"
if errorlevel 1 goto :fail

echo [5/5] Done.
echo Bundle folder: %BUNDLE_DIR%
echo Zip package : %ZIP_NAME%
echo Upload one of them to server, then run:
echo   docker compose -f docker-compose.server.yml up -d
goto :eof

:fail
echo Build failed.
exit /b 1
