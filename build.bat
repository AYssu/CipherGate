@echo off
setlocal enabledelayedexpansion

echo ======================================
echo CipherGate 项目构建脚本 (Windows)
echo ======================================
echo.

:: 检查 Docker
where docker >nul 2>nul
if %errorlevel% neq 0 (
    echo [错误] Docker 未安装或未添加到 PATH
    exit /b 1
)

:: 检查 Docker Compose
docker compose version >nul 2>nul
if %errorlevel% neq 0 (
    docker-compose --version >nul 2>nul
    if %errorlevel% neq 0 (
        echo [错误] Docker Compose 未安装
        exit /b 1
    )
)

echo [√] 环境检查通过
echo.

:: 构建后端
echo ======================================
echo 开始构建后端...
echo ======================================
echo.

if exist "gradlew.bat" (
    call gradlew.bat clean bootJar --no-daemon
) else (
    gradle clean bootJar --no-daemon
)

if %errorlevel% neq 0 (
    echo [错误] 后端构建失败
    exit /b 1
)

:: 检查 JAR 文件
if not exist "build\libs\*.jar" (
    echo [错误] JAR 文件未生成
    exit /b 1
)

docker build -f Dockerfile.prod -t ciphergate-backend:latest .
if %errorlevel% neq 0 (
    echo [错误] 后端 Docker 镜像构建失败
    exit /b 1
)

echo [√] 后端构建完成
echo.

:: 构建前端
echo ======================================
echo 开始构建前端...
echo ======================================
echo.

cd frontend

if not exist "node_modules" (
    call npm ci
)

call npm run build
if %errorlevel% neq 0 (
    echo [错误] 前端构建失败
    cd ..
    exit /b 1
)

if not exist "dist" (
    echo [错误] 前端构建产物未生成
    cd ..
    exit /b 1
)

docker build -f Dockerfile.prod -t ciphergate-frontend:latest .
if %errorlevel% neq 0 (
    echo [错误] 前端 Docker 镜像构建失败
    cd ..
    exit /b 1
)

cd ..

echo [√] 前端构建完成
echo.

echo ======================================
echo 构建完成！
echo ======================================
echo.
echo 下一步操作：
echo 1. 本地开发: copy .env.example .env 并修改配置
echo 2. 生产部署: 使用 .env.prod（已包含在部署包中）
echo 3. 启动服务: docker-compose -f docker-compose.prod.yaml up -d
echo.
echo 查看日志: docker-compose -f docker-compose.prod.yaml logs -f
echo 停止服务: docker-compose -f docker-compose.prod.yaml down
echo.

endlocal
